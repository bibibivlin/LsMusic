package com.linxyi.lsmusic.ui

import androidx.annotation.StringRes
import com.linxyi.lsmusic.R

enum class AppDestination {
    LIBRARY, QUEUE, NOW_PLAYING, SETTINGS,
    SETTINGS_APPEARANCE, SETTINGS_LYRICS, SETTINGS_NETWORK, SETTINGS_ABOUT, PENDING_LISTENS;

    val settingsParent: AppDestination?
        get() = when (this) {
            SETTINGS_APPEARANCE, SETTINGS_LYRICS, SETTINGS_NETWORK, SETTINGS_ABOUT -> SETTINGS
            PENDING_LISTENS -> SETTINGS_NETWORK
            else -> null
        }

    val navigationDestination: AppDestination
        get() = if (settingsParent != null) SETTINGS else this

    @get:StringRes
    val settingsTitleRes: Int
        get() = when (this) {
            SETTINGS_APPEARANCE -> R.string.settings_appearance
            SETTINGS_LYRICS -> R.string.settings_lyrics
            SETTINGS_NETWORK -> R.string.settings_network
            SETTINGS_ABOUT -> R.string.settings_about
            else -> R.string.nav_settings
        }

    @get:StringRes
    val navigationLabelRes: Int
        get() = when (this) {
            LIBRARY -> R.string.nav_library
            QUEUE -> R.string.nav_queue
            NOW_PLAYING -> R.string.nav_now_playing
            SETTINGS -> R.string.nav_settings
            else -> R.string.nav_settings
        }
}

enum class ExitStatus { IDLE, STOPPING, SAVE_FAILED, COMPLETE }
