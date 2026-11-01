package com.linxyi.lsmusic.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.linxyi.lsmusic.MainActivity

internal object PlaybackNotificationIntent {
    const val ACTION_OPEN_NOW_PLAYING = "com.linxyi.lsmusic.action.OPEN_NOW_PLAYING"

    fun create(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_NOW_PLAYING
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
