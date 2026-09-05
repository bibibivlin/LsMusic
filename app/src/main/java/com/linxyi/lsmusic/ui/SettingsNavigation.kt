package com.linxyi.lsmusic.ui

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

    val settingsTitle: String
        get() = when (this) {
            SETTINGS_APPEARANCE -> "界面"
            SETTINGS_LYRICS -> "歌词"
            SETTINGS_NETWORK -> "网络"
            SETTINGS_ABOUT -> "关于"
            else -> "设置"
        }
}

enum class ExitStatus { IDLE, STOPPING, SAVE_FAILED, COMPLETE }
