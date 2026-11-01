package com.linxyi.lsmusic.ui

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSession
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.linxyi.lsmusic.MainActivity
import com.linxyi.lsmusic.dlna.MediaEntry
import com.linxyi.lsmusic.dlna.RemotePlaybackState
import com.linxyi.lsmusic.playback.LocalPlaybackService
import com.linxyi.lsmusic.playback.PlaybackNotificationIntent
import com.linxyi.lsmusic.playback.RemotePlaybackService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Uses an isolated application so playback never touches the user's accounts or queue. */
class PlaybackNotificationNavigationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val showTestActivityOnLockScreen = ActivityLifecycleCallback { activity, stage ->
        if (activity is MainActivity && stage == Stage.CREATED) {
            activity.setShowWhenLocked(true)
            activity.setTurnScreenOn(true)
        }
    }

    @Before
    fun prepareIsolatedApplication() {
        assumeTrue("Requires the isolated .qa application", context.packageName == "com.linxyi.lsmusic.qa")
        listOf(Manifest.permission.POST_NOTIFICATIONS, "android.permission.ACCESS_LOCAL_NETWORK").forEach { permission ->
            ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} $permission"),
            ).use { it.readBytes() }
        }
        instrumentation.runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback(showTestActivityOnLockScreen)
        }
    }

    @After
    fun stopFixtureServices() {
        if (context.packageName != "com.linxyi.lsmusic.qa") return
        instrumentation.runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(showTestActivityOnLockScreen)
        }
        context.stopService(Intent(context, LocalPlaybackService::class.java))
        context.stopService(Intent(context, RemotePlaybackService::class.java))
    }

    @Test
    fun notificationLaunchOpensPlayerAndRecreationDoesNotReopenIt() {
        val launch = Intent(context, MainActivity::class.java).apply {
            action = PlaybackNotificationIntent.ACTION_OPEN_NOW_PLAYING
        }
        ActivityScenario.launch<MainActivity>(launch).use { scenario ->
            scenario.onActivity { activity ->
                val viewModel = ViewModelProvider(activity)[LsMusicViewModel::class.java]
                assertEquals(AppDestination.NOW_PLAYING, viewModel.uiState.value.destination)
                viewModel.setDestination(AppDestination.SETTINGS)
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val viewModel = ViewModelProvider(activity)[LsMusicViewModel::class.java]
                assertEquals(AppDestination.SETTINGS, viewModel.uiState.value.destination)
            }
        }
    }

    @Test
    fun remoteNotificationAndMediaSessionOpenPlayerInExistingActivity() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var viewModel: LsMusicViewModel
            lateinit var activity: MainActivity
            scenario.onActivity { started ->
                activity = started
                viewModel = ViewModelProvider(started)[LsMusicViewModel::class.java]
                assertReportingIsDisabled(viewModel)
            }
            try {
                context.startForegroundService(Intent(context, RemotePlaybackService::class.java).apply {
                    action = RemotePlaybackService.ACTION_UPDATE
                    putExtra(RemotePlaybackService.EXTRA_MEDIA_ID, "notification-remote-fixture")
                    putExtra(RemotePlaybackService.EXTRA_TITLE, "Remote notification fixture")
                    putExtra(RemotePlaybackService.EXTRA_PLAYING, true)
                })
                waitFor { notifications.activeNotifications.any { it.id == 1201 } }
                val notification = notifications.activeNotifications.first { it.id == 1201 }.notification
                assertBothEntrancesOpenPlayer(notification, activity, viewModel)
            } finally {
                context.stopService(Intent(context, RemotePlaybackService::class.java))
                finishScenario(scenario, activity)
            }
        }
    }

    @Test
    fun localNotificationAndMediaSessionOpenPlayerWithoutInterruptingPlayback() {
        val audio = createSilentWave()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var viewModel: LsMusicViewModel
                lateinit var activity: MainActivity
                scenario.onActivity { started ->
                    activity = started
                    viewModel = ViewModelProvider(started)[LsMusicViewModel::class.java]
                    assertReportingIsDisabled(viewModel)
                    viewModel.selectRenderer(LsMusicViewModel.LOCAL_RENDERER_ID)
                    viewModel.playNow(MediaEntry(
                        id = "notification-local-fixture", parentId = "0", title = "Local notification fixture",
                        isContainer = false, resourceUri = audio.toURI().toString(), duration = "00:00:30",
                    ))
                }
                try {
                    waitFor {
                        notifications.activeNotifications.any {
                            it.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() == "Local notification fixture"
                        }
                    }
                    val notification = notifications.activeNotifications.first {
                        it.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() == "Local notification fixture"
                    }.notification
                    waitFor { viewModel.uiState.value.playbackState == RemotePlaybackState.PLAYING && viewModel.uiState.value.positionMs > 0L }
                    val generation = viewModel.uiState.value.playbackGeneration
                    val position = viewModel.uiState.value.positionMs
                    assertBothEntrancesOpenPlayer(notification, activity, viewModel)
                    waitFor { viewModel.uiState.value.positionMs > position }
                    assertEquals(RemotePlaybackState.PLAYING, viewModel.uiState.value.playbackState)
                    assertEquals(generation, viewModel.uiState.value.playbackGeneration)
                    assertEquals("notification-local-fixture", viewModel.uiState.value.currentTrack?.id)
                } finally {
                    finishScenario(scenario, activity) { viewModel.clearQueue() }
                }
            }
        } finally {
            audio.delete()
        }
    }

    private fun assertBothEntrancesOpenPlayer(
        notification: Notification,
        activity: MainActivity,
        viewModel: LsMusicViewModel,
    ) {
        val token = if (Build.VERSION.SDK_INT >= 33) {
            notification.extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
        } else {
            @Suppress("DEPRECATION")
            notification.extras.getParcelable<MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION)
        }
        val sessionActivity = MediaController(context, requireNotNull(token)).sessionActivity
        val contentIntent = requireNotNull(notification.contentIntent)
        for (entrance in listOf(requireNotNull(sessionActivity), contentIntent)) {
            assertEntranceOpensPlayer(entrance, activity, viewModel)
        }
    }

    private fun assertEntranceOpensPlayer(
        entrance: PendingIntent,
        activity: MainActivity,
        viewModel: LsMusicViewModel,
    ) {
        instrumentation.runOnMainSync {
            viewModel.setDestination(AppDestination.SETTINGS)
        }
        entrance.send()
        waitFor {
            var originalIsResumed = false
            instrumentation.runOnMainSync {
                originalIsResumed = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).any { it === activity }
            }
            originalIsResumed && viewModel.uiState.value.destination == AppDestination.NOW_PLAYING
        }
    }

    private fun finishScenario(
        scenario: ActivityScenario<MainActivity>,
        activity: MainActivity,
        cleanup: () -> Unit = {},
    ) {
        // Avoid waiting for the now-playing screen's ongoing animations before finishing it.
        instrumentation.runOnMainSync {
            cleanup()
            activity.finish()
        }
        waitFor { scenario.state == Lifecycle.State.DESTROYED }
    }

    private fun assertReportingIsDisabled(viewModel: LsMusicViewModel) {
        assertFalse(viewModel.uiState.value.preferences.listenBrainzEnabled)
        assertTrue(viewModel.uiState.value.preferences.listenBrainzToken.isEmpty())
    }

    private fun waitFor(predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 12_000L
        while (!predicate()) {
            check(SystemClock.elapsedRealtime() < deadline) { "Notification navigation did not reach the expected state" }
            SystemClock.sleep(25L)
        }
    }

    private fun createSilentWave(): File {
        val sampleRate = 8_000
        val dataSize = sampleRate * 30 * 2
        val bytes = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + dataSize); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1); putShort(1); putInt(sampleRate); putInt(sampleRate * 2)
            putShort(2); putShort(16); put("data".toByteArray()); putInt(dataSize)
        }.array()
        return File(context.cacheDir, "notification-fixture.wav").apply { writeBytes(bytes) }
    }
}
