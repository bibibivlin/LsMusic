package com.linxyi.lsmusic

import com.linxyi.lsmusic.dlna.MediaEntry
import com.linxyi.lsmusic.listenbrainz.ListenBrainzClient
import com.linxyi.lsmusic.listenbrainz.PendingListen
import com.linxyi.lsmusic.listenbrainz.PendingListenRepository
import com.linxyi.lsmusic.listenbrainz.buildClearNowPlayingPayload
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ExitReportingTest {
    private val record = PendingListen(
        id = "exit-record",
        track = MediaEntry(id = "track", parentId = "0", title = "Offline fixture", isContainer = false),
        startedAtEpochSeconds = 1_700_000_000L,
        durationMs = 300_000L,
        listenedMs = 180_000L,
        queuedAtEpochSeconds = 1_700_000_180L,
    )

    @Test
    fun clearNowPlaying_onlyTargetsThisClientAndAcceptsAnotherClientsStatus() = runBlocking {
        assertEquals("L's Music", buildClearNowPlayingPayload().getString("client"))
        lateinit var request: FakeConnection
        val client = ListenBrainzClient { url -> FakeConnection(url, status = { 404 }).also { request = it } }
        client.clearNowPlaying("offline-placeholder")
        assertEquals("/1/playing-now/delete", request.url.path)
        assertEquals("POST", request.requestMethod)
        assertEquals("L's Music", JSONObject(request.body.toString("UTF-8")).getString("client"))
        assertTrue(request.disconnected)
    }

    @Test
    fun enqueue_failedAtomicSaveKeepsStateAndRetryWithSameIdIsIdempotent() = runBlocking {
        var fail = true
        var writes = 0
        val repository = PendingListenRepository(emptyList()) {
            if (fail) throw IOException("fixture disk full")
            writes++
        }
        assertTrue(runCatching { repository.enqueue(record) }.isFailure)
        assertTrue(repository.records.value.isEmpty())
        fail = false
        repository.enqueue(record)
        repository.enqueue(record)
        assertEquals(listOf(record), repository.records.value)
        assertEquals(1, writes)
    }

    @Test
    fun permanentListen_isPersistedBeforeHttpAndRemovedOnlyOnSuccess() = runBlocking {
        var saved = emptyList<PendingListen>()
        val repository = PendingListenRepository(emptyList()) { saved = it }
        repository.enqueue(record)
        val client = ListenBrainzClient { url ->
            assertEquals(record.startedAtEpochSeconds, saved.single().startedAtEpochSeconds)
            FakeConnection(url) { 200 }
        }
        val result = repository.upload("offline-placeholder", client = client)
        assertEquals(1, result.uploadedCount)
        assertTrue(saved.isEmpty())
        assertTrue(repository.records.value.isEmpty())
    }

    @Test
    fun failedUploadRetainsOriginalTimestampAndActualPlayedDuration() = runBlocking {
        val repository = PendingListenRepository(listOf(record)) {}
        lateinit var request: FakeConnection
        val client = ListenBrainzClient { url -> FakeConnection(url) { 503 }.also { request = it } }
        assertEquals(0, repository.upload("offline-placeholder", client = client).uploadedCount)
        val retained = repository.records.value.single()
        assertEquals(record.startedAtEpochSeconds, retained.startedAtEpochSeconds)
        assertEquals(record.listenedMs, retained.listenedMs)
        val payload = JSONObject(request.body.toString("UTF-8")).getJSONArray("payload").getJSONObject(0)
        assertEquals(record.startedAtEpochSeconds, payload.getLong("listened_at"))
        assertEquals(180L, payload.getJSONObject("track_metadata").getJSONObject("additional_info").getLong("duration_played"))
    }

    @Test
    fun foregroundAndBackgroundUploadSerializeAndDoNotSubmitTheSameRecordTwice() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val requests = AtomicInteger()
        val repository = PendingListenRepository(listOf(record)) {}
        val client = ListenBrainzClient { url -> FakeConnection(url) {
            requests.incrementAndGet()
            entered.complete(Unit)
            check(release.await(2, TimeUnit.SECONDS))
            200
        } }
        val first = async(Dispatchers.Default) { repository.upload("offline-placeholder", client = client) }
        withTimeout(2_000L) { entered.await() }
        val second = async(Dispatchers.Default) { repository.upload("offline-placeholder", client = client) }
        release.countDown()
        assertEquals(1, first.await().uploadedCount + second.await().uploadedCount)
        assertEquals(1, requests.get())
        assertTrue(repository.records.value.isEmpty())
    }

    @Test
    fun cancellingBlockedHttpReturnsPromptlyAndKeepsDurableRecord() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val repository = PendingListenRepository(listOf(record)) {}
        val client = ListenBrainzClient { url -> FakeConnection(url) {
            entered.complete(Unit)
            release.await(3, TimeUnit.SECONDS)
            200
        } }
        val upload = launch { repository.upload("offline-placeholder", client = client) }
        try {
            withTimeout(2_000L) { entered.await() }
            withTimeout(1_000L) { upload.cancelAndJoin() }
            assertEquals(record.id, repository.records.value.single().id)
            assertFalse(repository.isUploading.value)
        } finally {
            release.countDown()
        }
    }

    private class FakeConnection(url: URL, private val status: () -> Int) : HttpURLConnection(url) {
        val body = ByteArrayOutputStream()
        var disconnected = false
        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getOutputStream() = body
        override fun getInputStream() = ByteArrayInputStream(byteArrayOf())
        override fun getErrorStream() = ByteArrayInputStream(byteArrayOf())
        override fun getResponseCode() = status()
    }
}
