package com.linxyi.lsmusic.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import com.linxyi.lsmusic.R
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Publishes a DLNA renderer as Android's active media session and notification. */
class RemotePlaybackService : Service() {
    private lateinit var mediaSession: MediaSession
    private val mainHandler = Handler(Looper.getMainLooper())
    private val artworkExecutor = Executors.newSingleThreadExecutor()
    private var artworkUri: String? = null
    private var artwork: Bitmap? = null
    private var sessionData: SessionData? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        mediaSession = MediaSession(this, SESSION_TAG).apply {
            setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = dispatchCommand(COMMAND_PLAY)
                override fun onPause() = dispatchCommand(COMMAND_PAUSE)
                override fun onStop() = dispatchCommand(COMMAND_STOP)
                override fun onSkipToNext() = dispatchCommand(COMMAND_NEXT)
                override fun onSkipToPrevious() = dispatchCommand(COMMAND_PREVIOUS)
                override fun onSeekTo(pos: Long) = dispatchCommand(COMMAND_SEEK, pos)
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_UPDATE -> updateSession(intent)
            ACTION_COMMAND -> dispatchCommand(intent.getStringExtra(EXTRA_COMMAND), intent.getLongExtra(EXTRA_POSITION_MS, 0L))
            ACTION_STOP_SERVICE -> {
                mediaSession.isActive = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        artworkExecutor.shutdownNow()
        mediaSession.release()
        super.onDestroy()
    }

    private fun updateSession(intent: Intent) {
        val data = SessionData(
            mediaId = intent.getStringExtra(EXTRA_MEDIA_ID).orEmpty(),
            mediaUri = intent.getStringExtra(EXTRA_MEDIA_URI).orEmpty(),
            title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
            artist = intent.getStringExtra(EXTRA_ARTIST).orEmpty(),
            album = intent.getStringExtra(EXTRA_ALBUM).orEmpty(),
            genre = intent.getStringExtra(EXTRA_GENRE).orEmpty(),
            trackNumber = intent.getIntExtra(EXTRA_TRACK_NUMBER, 0).takeIf { it > 0 },
            artworkUri = intent.getStringExtra(EXTRA_ARTWORK_URI)?.takeIf { it.isNotBlank() },
            position = intent.getLongExtra(EXTRA_POSITION_MS, 0L).coerceAtLeast(0L),
            duration = intent.getLongExtra(EXTRA_DURATION_MS, 0L).coerceAtLeast(0L),
            playing = intent.getBooleanExtra(EXTRA_PLAYING, false),
        )
        sessionData = data
        if (artworkUri != data.artworkUri) {
            artworkUri = data.artworkUri
            artwork = null
            data.artworkUri?.let(::loadArtwork)
        }
        publishSession(data)
    }

    private fun publishSession(data: SessionData) {
        mediaSession.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, data.mediaId)
                .putString(MediaMetadata.METADATA_KEY_MEDIA_URI, data.mediaUri)
                .putString(MediaMetadata.METADATA_KEY_TITLE, data.title)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, data.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, data.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST, data.artist)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, data.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, data.album)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION, data.album)
                .putString(MediaMetadata.METADATA_KEY_GENRE, data.genre)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, data.duration)
                .apply {
                    data.trackNumber?.let { putLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER, it.toLong()) }
                    data.artworkUri?.let {
                        putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, it)
                        putString(MediaMetadata.METADATA_KEY_ART_URI, it)
                        putString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI, it)
                    }
                    artwork?.let {
                        putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it)
                        putBitmap(MediaMetadata.METADATA_KEY_ART, it)
                        putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, it)
                    }
                }
                .build(),
        )
        mediaSession.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_STOP or
                        PlaybackState.ACTION_SEEK_TO or
                        PlaybackState.ACTION_SKIP_TO_NEXT or
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS,
                )
                .setState(
                    if (data.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    data.position,
                    if (data.playing) 1f else 0f,
                    SystemClock.elapsedRealtime(),
                )
                .build(),
        )
        mediaSession.isActive = true
        startForeground(NOTIFICATION_ID, buildNotification(data))
    }

    private fun buildNotification(data: SessionData): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(data.title)
            .setContentText(listOf(data.artist, data.album).filter { it.isNotBlank() }.joinToString(" · "))
            .setLargeIcon(artwork)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .addAction(notificationAction(android.R.drawable.ic_media_previous, "上一首", COMMAND_PREVIOUS))
            .addAction(
                notificationAction(
                    if (data.playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (data.playing) "暂停" else "播放",
                    if (data.playing) COMMAND_PAUSE else COMMAND_PLAY,
                ),
            )
            .addAction(notificationAction(android.R.drawable.ic_media_next, "下一首", COMMAND_NEXT))
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2),
            )
            .build()

    private fun notificationAction(icon: Int, title: String, command: String): Notification.Action =
        Notification.Action.Builder(icon, title, commandIntent(command)).build()

    private fun loadArtwork(uri: String) {
        artworkExecutor.execute {
            val bitmap = runCatching {
                val connection = (URL(uri).openConnection() as HttpURLConnection)
                try {
                    connection.connectTimeout = ARTWORK_TIMEOUT_MS
                    connection.readTimeout = ARTWORK_TIMEOUT_MS
                    connection.inputStream.use(BitmapFactory::decodeStream)
                } finally {
                    connection.disconnect()
                }
            }.getOrNull() ?: return@execute
            mainHandler.post {
                if (artworkUri == uri) {
                    artwork = bitmap
                    sessionData?.let(::publishSession)
                }
            }
        }
    }

    private fun commandIntent(command: String): PendingIntent = PendingIntent.getService(
        this,
        command.hashCode(),
        Intent(this, RemotePlaybackService::class.java).apply {
            action = ACTION_COMMAND
            putExtra(EXTRA_COMMAND, command)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun dispatchCommand(command: String?, positionMs: Long = 0L) {
        if (command.isNullOrBlank()) return
        sendBroadcast(
            Intent(ACTION_REMOTE_CONTROL).setPackage(packageName).apply {
                putExtra(EXTRA_COMMAND, command)
                putExtra(EXTRA_POSITION_MS, positionMs)
            },
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "远程播放", NotificationManager.IMPORTANCE_LOW).apply {
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private data class SessionData(
        val mediaId: String,
        val mediaUri: String,
        val title: String,
        val artist: String,
        val album: String,
        val genre: String,
        val trackNumber: Int?,
        val artworkUri: String?,
        val position: Long,
        val duration: Long,
        val playing: Boolean,
    )

    companion object {
        const val ACTION_UPDATE = "com.linxyi.lsmusic.action.UPDATE_REMOTE_MEDIA_SESSION"
        const val ACTION_STOP_SERVICE = "com.linxyi.lsmusic.action.STOP_REMOTE_MEDIA_SESSION"
        const val ACTION_COMMAND = "com.linxyi.lsmusic.action.REMOTE_MEDIA_COMMAND"
        const val ACTION_REMOTE_CONTROL = "com.linxyi.lsmusic.action.REMOTE_MEDIA_CONTROL"
        const val EXTRA_COMMAND = "command"
        const val EXTRA_MEDIA_ID = "mediaId"
        const val EXTRA_MEDIA_URI = "mediaUri"
        const val EXTRA_TITLE = "title"
        const val EXTRA_ARTIST = "artist"
        const val EXTRA_ALBUM = "album"
        const val EXTRA_GENRE = "genre"
        const val EXTRA_TRACK_NUMBER = "trackNumber"
        const val EXTRA_ARTWORK_URI = "artworkUri"
        const val EXTRA_POSITION_MS = "positionMs"
        const val EXTRA_DURATION_MS = "durationMs"
        const val EXTRA_PLAYING = "playing"
        const val COMMAND_PLAY = "play"
        const val COMMAND_PAUSE = "pause"
        const val COMMAND_STOP = "stop"
        const val COMMAND_NEXT = "next"
        const val COMMAND_PREVIOUS = "previous"
        const val COMMAND_SEEK = "seek"

        private const val CHANNEL_ID = "remote_playback"
        private const val NOTIFICATION_ID = 1201
        private const val SESSION_TAG = "LsMusicRemotePlayback"
        private const val ARTWORK_TIMEOUT_MS = 8_000
    }
}
