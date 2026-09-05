package com.linxyi.lsmusic.ui

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.linxyi.lsmusic.MainActivity
import com.linxyi.lsmusic.dlna.MediaEntry
import com.linxyi.lsmusic.playback.LocalPlaybackService
import com.linxyi.lsmusic.playback.RemotePlaybackService
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Run with an isolated application ID; never loads a user's saved reporting configuration. */
class ExitIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Before
    fun requireIsolatedApplication() {
        assumeTrue("Playback integration requires the .qa test application", context.packageName == "com.linxyi.lsmusic.qa")
        listOf(Manifest.permission.POST_NOTIFICATIONS, "android.permission.ACCESS_LOCAL_NETWORK").forEach { permission ->
            val output = instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} $permission")
            ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes() }
        }
    }

    @Test
    fun exitStopsRealLocalPlaybackRemovesNotificationAndClosesTask() {
        val audio = createSilentWave()
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            scenario = ActivityScenario.launch(MainActivity::class.java)
            lateinit var viewModel: LsMusicViewModel
            scenario.onActivity { activity ->
                viewModel = ViewModelProvider(activity)[LsMusicViewModel::class.java]
                assertFalse(viewModel.uiState.value.preferences.listenBrainzEnabled)
                assertTrue(viewModel.uiState.value.preferences.listenBrainzToken.isEmpty())
                viewModel.selectRenderer(LsMusicViewModel.LOCAL_RENDERER_ID)
                viewModel.playNow(MediaEntry(
                    id = "exit-audio-fixture", parentId = "0", title = "Exit fixture",
                    isContainer = false, resourceUri = audio.toURI().toString(), duration = "00:00:30",
                ))
            }
            waitFor { viewModel.uiState.value.positionMs > 500L }
            waitFor { context.getSystemService(NotificationManager::class.java).activeNotifications.isNotEmpty() }
            scenario.onActivity {
                viewModel.exitApp()
                viewModel.exitApp()
            }
            waitFor { scenario.state == Lifecycle.State.DESTROYED }
            waitFor { !playbackServiceRunning() }
            assertEquals(ExitStatus.COMPLETE, viewModel.uiState.value.exitStatus)
            assertTrue(context.getSystemService(NotificationManager::class.java).activeNotifications.isEmpty())

            // A new Activity/ViewModel must not pick up the old service queue or restart audio.
            ActivityScenario.launch(MainActivity::class.java).use { reopened ->
                reopened.onActivity { activity ->
                    val fresh = ViewModelProvider(activity)[LsMusicViewModel::class.java]
                    assertNull(fresh.uiState.value.currentTrack)
                    fresh.exitApp()
                }
                waitFor { reopened.state == Lifecycle.State.DESTROYED }
            }
        } finally {
            scenario?.close()
            context.stopService(Intent(context, LocalPlaybackService::class.java))
            audio.delete()
        }
    }

    @Test
    fun remoteSessionNotificationIsRemovedAndAnOldCommandCannotRestartIt() {
        context.startForegroundService(Intent(context, RemotePlaybackService::class.java).apply {
            action = RemotePlaybackService.ACTION_UPDATE
            putExtra(RemotePlaybackService.EXTRA_MEDIA_ID, "remote-exit-fixture")
            putExtra(RemotePlaybackService.EXTRA_TITLE, "Remote exit fixture")
            putExtra(RemotePlaybackService.EXTRA_PLAYING, true)
        })
        try {
            val notifications = context.getSystemService(NotificationManager::class.java)
            waitFor { notifications.activeNotifications.any { it.id == 1201 } }
            context.stopService(Intent(context, RemotePlaybackService::class.java))
            waitFor { !playbackServiceRunning() && notifications.activeNotifications.none { it.id == 1201 } }
            assertFalse(notifications.activeNotifications.any { it.id == 1201 })
            // Replaying a stale notification PendingIntent must remain inert.
            context.startService(Intent(context, RemotePlaybackService::class.java).apply {
                action = RemotePlaybackService.ACTION_COMMAND
                putExtra(RemotePlaybackService.EXTRA_COMMAND, RemotePlaybackService.COMMAND_PLAY)
            })
            instrumentation.waitForIdleSync()
            waitFor { !playbackServiceRunning() && notifications.activeNotifications.none { it.id == 1201 } }
            assertFalse(notifications.activeNotifications.any { it.id == 1201 })
        } finally {
            context.stopService(Intent(context, RemotePlaybackService::class.java))
        }
    }

    @Suppress("DEPRECATION")
    private fun playbackServiceRunning(): Boolean =
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getRunningServices(100).any {
            it.service.className == LocalPlaybackService::class.java.name ||
                it.service.className == RemotePlaybackService::class.java.name
        }

    private fun waitFor(predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000L
        while (!predicate()) {
            check(SystemClock.elapsedRealtime() < deadline) { "Playback cleanup did not reach the expected state" }
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
        return File(context.cacheDir, "exit-fixture.wav").apply { writeBytes(bytes) }
    }
}
