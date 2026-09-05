package com.linxyi.lsmusic.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.linxyi.lsmusic.dlna.RemotePlaybackState

internal fun remoteRendererToStopOnExit(state: LsMusicUiState): String? =
    state.selectedRendererId?.takeIf {
        it != LsMusicViewModel.LOCAL_RENDERER_ID && state.currentTrack != null &&
            state.playbackState != RemotePlaybackState.STOPPED
    }

internal data class ExitProgress(
    val status: ExitStatus = ExitStatus.IDLE,
    val error: String? = null,
    val warning: String? = null,
)

/** Owns one exit attempt across recomposition/rotation and retains failed writes for a retry. */
internal class AppExitCoordinator(
    private val scope: CoroutineScope,
    private val begin: () -> Unit,
    private val stopPlayback: suspend () -> Boolean,
    private val persist: suspend () -> Unit,
    private val finishReporting: suspend (persisted: Boolean) -> Unit,
    private val onProgress: (ExitProgress) -> Unit,
    private val timeoutMs: Long = 5_000L,
) {
    var progress = ExitProgress()
        private set
    private var stopping: Deferred<Boolean>? = null

    fun exit() {
        if (progress.status != ExitStatus.IDLE && progress.status != ExitStatus.SAVE_FAILED) return
        val firstAttempt = progress.status == ExitStatus.IDLE
        publish(ExitProgress(ExitStatus.STOPPING))
        if (firstAttempt) begin()
        scope.launch {
            val stopped = stopping ?: scope.async {
                try {
                    withTimeoutOrNull(timeoutMs) { stopPlayback() } ?: false
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    false
                }
            }.also { stopping = it }
            val saveError = try {
                persist()
                null
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                "播放已停止，但无法保存播放记录。请释放设备存储空间后重试。"
            }
            // A failed local write is never followed by an upload or a successful exit.
            // Cleanup of the server's ephemeral status also runs on a failed-save attempt.
            try {
                withTimeoutOrNull(timeoutMs) { finishReporting(saveError == null) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Durable records remain queued; transient network failures do not block exit.
            }
            val warning = if (stopped.await()) null else "未能确认远程设备已停止"
            publish(ExitProgress(
                status = if (saveError == null) ExitStatus.COMPLETE else ExitStatus.SAVE_FAILED,
                error = saveError,
                warning = warning,
            ))
        }
    }

    private fun publish(value: ExitProgress) {
        progress = value
        onProgress(value)
    }
}
