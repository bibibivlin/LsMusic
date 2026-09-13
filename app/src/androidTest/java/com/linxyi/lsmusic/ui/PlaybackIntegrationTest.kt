package com.linxyi.lsmusic.ui

import android.Manifest
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
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.After
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Real Media3 and AlarmManager tests, restricted to a separate app with no user accounts. */
class PlaybackIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val showTestActivityOnLockScreen = ActivityLifecycleCallback { activity, stage ->
        if (activity is MainActivity && stage == Stage.CREATED) {
            activity.setShowWhenLocked(true)
            activity.setTurnScreenOn(true)
        }
    }

    @Before
    fun requireIsolatedApplication() {
        assumeTrue("Requires the isolated .qa application", context.packageName == "com.linxyi.lsmusic.qa")
        shell("pm grant ${context.packageName} ${Manifest.permission.POST_NOTIFICATIONS}")
        shell("pm grant ${context.packageName} android.permission.ACCESS_LOCAL_NETWORK")
        shell("appops set ${context.packageName} SCHEDULE_EXACT_ALARM allow")
        instrumentation.runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback(showTestActivityOnLockScreen)
        }
    }

    @After
    fun removeTestWindowFlags() {
        instrumentation.runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(showTestActivityOnLockScreen)
        }
    }

    @Test
    fun duplicateQueueActionsPreservePlaybackAndSelectTheRequestedOccurrence() = withPlayback { scenario, vm, track ->
        scenario.onActivity {
            vm.setClearQueueOnPlay(true)
            vm.setEnqueueWhilePlaying(false)
            vm.playAll(listOf(track, track.copy(id = "other")))
        }
        waitFor { vm.uiState.value.positionMs > 150L }
        val firstGeneration = vm.uiState.value.playbackGeneration
        val firstId = vm.uiState.value.currentQueueItem?.queueId
        scenario.onActivity {
            vm.setEnqueueWhilePlaying(true)
            vm.playNow(track)
            assertEquals(3, vm.uiState.value.queue.size)
            assertEquals(firstId, vm.uiState.value.currentQueueItem?.queueId)
            assertEquals(firstGeneration, vm.uiState.value.playbackGeneration)
            vm.playQueueItem(vm.uiState.value.queue.last().queueId)
        }
        waitFor { vm.uiState.value.positionMs > 150L }
        assertEquals(2, vm.uiState.value.currentQueueIndex)
        val duplicateId = vm.uiState.value.currentQueueItem?.queueId
        scenario.onActivity {
            vm.moveQueueItem(2, 0)
            vm.removeFromQueue(1)
            assertEquals(duplicateId, vm.uiState.value.currentQueueItem?.queueId)
            assertEquals(2, vm.uiState.value.queue.size)
            vm.setEnqueueWhilePlaying(false)
            vm.setClearQueueOnPlay(false)
            vm.playNow(track)
        }
        waitFor { vm.uiState.value.positionMs > 150L }
        assertEquals(2, vm.uiState.value.currentQueueIndex)
        assertNotEquals(duplicateId, vm.uiState.value.currentQueueItem?.queueId)
        scenario.onActivity {
            vm.togglePlayback()
            vm.setEnqueueWhilePlaying(true)
            vm.setClearQueueOnPlay(true)
            vm.playNow(track.copy(id = "replacement"))
        }
        waitFor { vm.uiState.value.positionMs > 150L }
        assertEquals(1, vm.uiState.value.queue.size)
        assertEquals("replacement", vm.uiState.value.currentTrack?.id)
    }

    @Test
    fun exactTimerPausesBackgroundPlaybackAndPreservesPositionAndGeneration() = withPlayback { scenario, vm, track ->
        scenario.onActivity {
            vm.setClearQueueOnPlay(true)
            vm.setEnqueueWhilePlaying(false)
            vm.playNow(track)
        }
        waitFor { vm.uiState.value.positionMs > 150L }
        val generation = vm.uiState.value.playbackGeneration
        scenario.onActivity { vm.requestSleepTimer(1, false) }
        assertEquals(SleepTimerPhase.COUNTING_DOWN, vm.uiState.value.sleepTimer.phase)
        scenario.moveToState(Lifecycle.State.CREATED)
        shell("input keyevent 223")
        try {
            waitFor(75_000L) { vm.uiState.value.sleepTimer.phase == SleepTimerPhase.OFF }
            assertEquals(RemotePlaybackState.PAUSED, vm.uiState.value.playbackState)
            assertTrue(vm.uiState.value.positionMs > 45_000L)
            assertEquals(generation, vm.uiState.value.playbackGeneration)
            assertEquals(1, vm.uiState.value.queue.size)
        } finally {
            shell("input keyevent 224")
            shell("wm dismiss-keyguard")
            scenario.moveToState(Lifecycle.State.RESUMED)
        }
    }

    @Test
    fun finishTrackTimerOverridesRepeatOneAndRestartsFromBeginning() = withPlayback { scenario, vm, track ->
        scenario.onActivity {
            vm.setClearQueueOnPlay(true)
            vm.setEnqueueWhilePlaying(false)
            vm.playNow(track)
            vm.cycleRepeatMode()
        }
        waitFor { vm.uiState.value.positionMs > 150L }
        val generation = vm.uiState.value.playbackGeneration
        scenario.onActivity { vm.requestSleepTimer(1, true) }
        waitFor(75_000L) { vm.uiState.value.sleepTimer.phase == SleepTimerPhase.FINISHING_TRACK }
        scenario.onActivity { vm.seekTo(88_000L) }
        waitFor { vm.uiState.value.playbackState == RemotePlaybackState.STOPPED }
        assertEquals(SleepTimerPhase.OFF, vm.uiState.value.sleepTimer.phase)
        assertEquals(0L, vm.uiState.value.positionMs)
        assertEquals(generation, vm.uiState.value.playbackGeneration)
        scenario.onActivity { vm.togglePlayback() }
        waitFor { vm.uiState.value.positionMs in 100L..5_000L }
        assertEquals(1, vm.uiState.value.queue.size)
    }

    @Test
    fun timerCanBeChangedCancelledAndRemainsCancelledAfterItsOldAlarm() = withPlayback { scenario, vm, _ ->
        scenario.onActivity { vm.requestSleepTimer(1, false) }
        val oldToken = vm.uiState.value.sleepTimer.token
        scenario.onActivity { vm.requestSleepTimer(2, true) }
        assertNotEquals(oldToken, vm.uiState.value.sleepTimer.token)
        assertTrue(vm.uiState.value.sleepTimer.finishCurrentTrack)
        scenario.recreate()
        scenario.onActivity {
            assertEquals(SleepTimerPhase.COUNTING_DOWN, vm.uiState.value.sleepTimer.phase)
            vm.cancelSleepTimer()
        }
        assertEquals(SleepTimerPhase.OFF, vm.uiState.value.sleepTimer.phase)
        // A broadcast from a consumed/previous session has no callback and cannot affect playback.
        context.sendBroadcast(android.content.Intent(context, com.linxyi.lsmusic.playback.SleepTimerAlarmReceiver::class.java).apply {
            data = android.net.Uri.parse("lsmusic://sleep-timer/$oldToken")
        })
        instrumentation.waitForIdleSync()
        assertEquals(SleepTimerPhase.OFF, vm.uiState.value.sleepTimer.phase)
        assertNull(vm.uiState.value.currentTrack)
    }

    private fun withPlayback(block: (ActivityScenario<MainActivity>, LsMusicViewModel, MediaEntry) -> Unit) {
        val audio = createSilentWave()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var vm: LsMusicViewModel
            scenario.onActivity {
                vm = ViewModelProvider(it)[LsMusicViewModel::class.java]
                assertFalse(vm.uiState.value.preferences.listenBrainzEnabled)
                assertTrue(vm.uiState.value.preferences.listenBrainzToken.isEmpty())
            }
            try {
                block(scenario, vm, MediaEntry(
                    id = "playback-fixture", parentId = "0", title = "Silent playback fixture", isContainer = false,
                    resourceUri = audio.toURI().toString(), duration = "00:01:30",
                ))
            } finally {
                scenario.onActivity { vm.cancelSleepTimer(); vm.exitApp() }
                waitFor { vm.uiState.value.exitStatus == ExitStatus.COMPLETE }
                audio.delete()
            }
        }
    }

    private fun waitFor(timeoutMs: Long = 12_000L, predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (!predicate()) {
            check(SystemClock.elapsedRealtime() < deadline) { "Playback/timer did not reach the expected state" }
            SystemClock.sleep(50L)
        }
    }

    private fun shell(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
    }

    private fun createSilentWave(): File {
        val sampleRate = 8_000
        val dataSize = sampleRate * 90 * 2
        val bytes = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + dataSize); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1); putShort(1); putInt(sampleRate); putInt(sampleRate * 2)
            putShort(2); putShort(16); put("data".toByteArray()); putInt(dataSize)
        }.array()
        return File(context.cacheDir, "playback-fixture.wav").apply { writeBytes(bytes) }
    }
}
