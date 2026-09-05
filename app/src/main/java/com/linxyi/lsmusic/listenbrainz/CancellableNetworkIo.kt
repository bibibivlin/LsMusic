package com.linxyi.lsmusic.listenbrainz

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.HttpURLConnection
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Blocking URL connections must not hold up a coroutine's cancellation or exit deadline. */
internal class NetworkIoCancellation {
    private val cancelled = AtomicBoolean(false)
    private val connection = AtomicReference<HttpURLConnection?>(null)

    fun checkActive() {
        if (cancelled.get()) throw CancellationException("Network request cancelled")
    }

    fun attach(value: HttpURLConnection) {
        connection.set(value)
        checkActive()
    }

    fun detach(value: HttpURLConnection) {
        connection.compareAndSet(value, null)
    }

    fun cancel() {
        cancelled.set(true)
        connection.getAndSet(null)?.let { active ->
            // Some implementations wait for the reader inside disconnect(). Never do that on Main.
            disconnectExecutor.execute { active.disconnect() }
        }
    }
}

private val networkExecutor = Executors.newFixedThreadPool(4) { runnable ->
    Thread(runnable, "ListenBrainz-IO").apply { isDaemon = true }
}
private val disconnectExecutor = Executors.newCachedThreadPool { runnable ->
    Thread(runnable, "ListenBrainz-cancel").apply { isDaemon = true }
}

internal suspend fun <T> cancellableNetworkIo(block: (NetworkIoCancellation) -> T): T =
    suspendCancellableCoroutine { continuation ->
        val cancellation = NetworkIoCancellation()
        val task = networkExecutor.submit {
            val result = runCatching {
                cancellation.checkActive()
                block(cancellation).also { cancellation.checkActive() }
            }
            continuation.resumeWith(result)
        }
        continuation.invokeOnCancellation {
            cancellation.cancel()
            task.cancel(true)
        }
    }
