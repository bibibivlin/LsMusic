package com.linxyi.lsmusic.playback

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean
import androidx.core.net.toUri

/** Only live application sessions register a callback; old alarms never resurrect playback. */
class SleepTimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val token = intent.data?.lastPathSegment ?: return
        val callback = callbacks.remove(token) ?: return
        val pending = goAsync()
        val finished = AtomicBoolean(false)
        val handler = Handler(Looper.getMainLooper())
        val finish = Runnable { if (finished.compareAndSet(false, true)) pending.finish() }
        // Release the broadcast even if an unreachable renderer never answers.
        handler.postDelayed(finish, 8_000L)
        callback {
            handler.removeCallbacks(finish)
            finish.run()
        }
    }

    internal companion object {
        val callbacks = mutableMapOf<String, ((() -> Unit) -> Unit)>()
    }
}

internal class SleepTimerScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private var pending: PendingIntent? = null
    private var token: String? = null

    fun canSchedule(): Boolean = alarmManager.canScheduleExactAlarms()

    @SuppressLint("ScheduleExactAlarm") // Rechecked here; revocation is also caught at the caller.
    fun schedule(token: String, deadlineElapsedMs: Long, onAlarm: (() -> Unit) -> Unit) {
        check(canSchedule()) { "Exact alarm access is required" }
        cancel()
        val intent = Intent(context, SleepTimerAlarmReceiver::class.java).apply {
            data = "lsmusic://sleep-timer/$token".toUri()
        }
        val operation = PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        this.token = token
        pending = operation
        SleepTimerAlarmReceiver.callbacks[token] = onAlarm
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, deadlineElapsedMs, operation)
        } catch (error: RuntimeException) {
            cancel()
            throw error
        }
    }

    fun cancel() {
        token?.let { SleepTimerAlarmReceiver.callbacks.remove(it) }
        pending?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
        token = null
        pending = null
    }
}
