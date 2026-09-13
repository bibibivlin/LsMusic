package com.linxyi.lsmusic.playback

import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionError
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class LocalPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var sleepBoundaryToken: String? = null
    private var sleepBoundaryQueueId: String? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            setHandleAudioBecomingNoisy(true)
        }
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_IDLE) clearSleepBoundary(player, completed = false)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (sleepBoundaryQueueId != null && mediaItem?.mediaId != sleepBoundaryQueueId) {
                    clearSleepBoundary(player, completed = false)
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (sleepBoundaryToken == null || playWhenReady) return
                if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                    clearSleepBoundary(player, completed = true)
                    player.stop()
                    player.seekTo(0L)
                } else if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
                    clearSleepBoundary(player, completed = false)
                }
            }
        })
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
                    MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(
                            MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                                .add(SessionCommand(COMMAND_SLEEP_BOUNDARY, Bundle.EMPTY)).build(),
                        ).build()

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle,
                ): ListenableFuture<SessionResult> {
                    if (customCommand.customAction != COMMAND_SLEEP_BOUNDARY || controller.uid != applicationInfo.uid) {
                        return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
                    }
                    val queueId = args.getString(EXTRA_QUEUE_ID)
                    val token = args.getString(EXTRA_TIMER_TOKEN)
                    if (token != null && player.currentMediaItem?.mediaId != queueId) {
                        return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
                    }
                    sleepBoundaryToken = token
                    sleepBoundaryQueueId = queueId
                    player.pauseAtEndOfMediaItems = token != null
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SHUTDOWN) {
            releasePlayback()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        releasePlayback()
        super.onDestroy()
    }

    private fun releasePlayback() {
        mediaSession?.run {
            player.stop()
            player.clearMediaItems()
            player.release()
            release()
        }
        mediaSession = null
    }

    private fun clearSleepBoundary(player: ExoPlayer, completed: Boolean) {
        val token = sleepBoundaryToken ?: return
        sleepBoundaryToken = null
        sleepBoundaryQueueId = null
        player.pauseAtEndOfMediaItems = false
        sendBroadcast(Intent(ACTION_SLEEP_BOUNDARY).setPackage(packageName).apply {
            putExtra(EXTRA_TIMER_TOKEN, token)
            putExtra(EXTRA_COMPLETED, completed)
        })
    }

    companion object {
        const val COMMAND_SLEEP_BOUNDARY = "com.linxyi.lsmusic.command.SLEEP_BOUNDARY"
        const val ACTION_SLEEP_BOUNDARY = "com.linxyi.lsmusic.action.SLEEP_BOUNDARY"
        const val EXTRA_TIMER_TOKEN = "sleep_timer_token"
        const val EXTRA_QUEUE_ID = "sleep_queue_id"
        const val EXTRA_COMPLETED = "sleep_completed"
        const val ACTION_SHUTDOWN = "com.linxyi.lsmusic.action.SHUTDOWN_LOCAL_PLAYBACK"
    }
}
