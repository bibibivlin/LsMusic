package com.linxyi.lsmusic.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PlaybackPreferencesTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val context = object : ContextWrapper(base) {
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            base.getSharedPreferences("playback-test-$name", mode)
    }

    @Before @After
    fun clearFixtures() {
        listOf("ls_music_preferences", "ls_music_secrets").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test
    fun existingInstallWithoutPlaybackKeysUsesRequestedDefaults() {
        context.getSharedPreferences("ls_music_preferences", Context.MODE_PRIVATE)
            .edit().putBoolean("grid_default", false).commit()
        val loaded = AppPreferencesStore(context).load()
        assertEquals(false, loaded.enqueueWhilePlaying)
        assertEquals(true, loaded.miniPlayerEnabled)
        assertEquals(true, loaded.clearQueueOnPlay)
        assertEquals(30, loaded.sleepTimerMinutes)
        assertEquals(false, loaded.sleepTimerFinishTrack)
    }

    @Test
    fun playbackPreferencesSurviveStoreRecreationWithoutPersistingAnActiveTimer() {
        val preferences = AppPreferences(
            enqueueWhilePlaying = true, miniPlayerEnabled = false, clearQueueOnPlay = false,
            sleepTimerMinutes = 75, sleepTimerFinishTrack = true,
        )
        AppPreferencesStore(context).save(preferences)
        assertEquals(preferences, AppPreferencesStore(context).load())
        assertEquals(SleepTimerPhase.OFF, LsMusicUiState(preferences = AppPreferencesStore(context).load()).sleepTimer.phase)
    }
}
