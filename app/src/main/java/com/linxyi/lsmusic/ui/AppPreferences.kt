package com.linxyi.lsmusic.ui

import android.content.Context

enum class GallerySize(val label: String, val minCellSize: Int) {
    COMPACT("紧凑", 96),
    STANDARD("标准", 160),
    LARGE("大封面", 216),
}

enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色"),
}

data class AppPreferences(
    val gallerySize: GallerySize = GallerySize.STANDARD,
    val useGridByDefault: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = true,
    val listenBrainzEnabled: Boolean = false,
    val listenBrainzToken: String = "",
    val listenBrainzMinimumSeconds: Int = 240,
    val listenBrainzMinimumPercent: Int = 50,
)

class AppPreferencesStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val secrets = context.getSharedPreferences(SECRETS_NAME, Context.MODE_PRIVATE)

    fun load(): AppPreferences = AppPreferences(
        gallerySize = preferences.enumValue(KEY_GALLERY_SIZE, GallerySize.STANDARD),
        useGridByDefault = preferences.getBoolean(KEY_GRID_DEFAULT, true),
        themeMode = preferences.enumValue(KEY_THEME_MODE, ThemeMode.SYSTEM),
        useDynamicColor = preferences.getBoolean(KEY_DYNAMIC_COLOR, true),
        listenBrainzEnabled = preferences.getBoolean(KEY_LISTENBRAINZ_ENABLED, false),
        listenBrainzToken = secrets.getString(KEY_LISTENBRAINZ_TOKEN, "").orEmpty(),
        listenBrainzMinimumSeconds = preferences.getInt(KEY_LISTENBRAINZ_MINIMUM_SECONDS, 240)
            .coerceIn(30, 600),
        listenBrainzMinimumPercent = preferences.getInt(KEY_LISTENBRAINZ_MINIMUM_PERCENT, 50)
            .coerceIn(10, 100),
    )

    fun lastServerId(): String? = preferences.getString(KEY_LAST_SERVER_ID, null)

    fun lastRendererId(): String? = preferences.getString(KEY_LAST_RENDERER_ID, null)

    fun saveLastServerId(id: String) = preferences.edit().putString(KEY_LAST_SERVER_ID, id).apply()

    fun saveLastRendererId(id: String) = preferences.edit().putString(KEY_LAST_RENDERER_ID, id).apply()

    fun save(value: AppPreferences) {
        preferences.edit()
            .putString(KEY_GALLERY_SIZE, value.gallerySize.name)
            .putBoolean(KEY_GRID_DEFAULT, value.useGridByDefault)
            .putString(KEY_THEME_MODE, value.themeMode.name)
            .putBoolean(KEY_DYNAMIC_COLOR, value.useDynamicColor)
            .putBoolean(KEY_LISTENBRAINZ_ENABLED, value.listenBrainzEnabled)
            .putInt(KEY_LISTENBRAINZ_MINIMUM_SECONDS, value.listenBrainzMinimumSeconds)
            .putInt(KEY_LISTENBRAINZ_MINIMUM_PERCENT, value.listenBrainzMinimumPercent)
            .apply()
        secrets.edit().putString(KEY_LISTENBRAINZ_TOKEN, value.listenBrainzToken).apply()
    }

    private inline fun <reified T : Enum<T>> android.content.SharedPreferences.enumValue(
        key: String,
        default: T,
    ): T = getString(key, null)?.let { value -> enumValues<T>().firstOrNull { it.name == value } } ?: default

    private companion object {
        const val PREFERENCES_NAME = "ls_music_preferences"
        const val SECRETS_NAME = "ls_music_secrets"
        const val KEY_LAST_SERVER_ID = "last_server_id"
        const val KEY_LAST_RENDERER_ID = "last_renderer_id"
        const val KEY_GALLERY_SIZE = "gallery_size"
        const val KEY_GRID_DEFAULT = "grid_default"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val KEY_LISTENBRAINZ_ENABLED = "listenbrainz_enabled"
        const val KEY_LISTENBRAINZ_TOKEN = "listenbrainz_token"
        const val KEY_LISTENBRAINZ_MINIMUM_SECONDS = "listenbrainz_minimum_seconds"
        const val KEY_LISTENBRAINZ_MINIMUM_PERCENT = "listenbrainz_minimum_percent"
    }
}
