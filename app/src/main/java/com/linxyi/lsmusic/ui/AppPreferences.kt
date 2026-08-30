package com.linxyi.lsmusic.ui

import android.content.Context
import com.linxyi.lsmusic.dlna.DlnaDevice
import com.linxyi.lsmusic.dlna.DlnaDeviceKind
import com.linxyi.lsmusic.lyrics.LyricsProviderId
import com.linxyi.lsmusic.lyrics.LyricsTranslationMode
import com.linxyi.lsmusic.lyrics.normalizedProviderOrder

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

enum class PresetPalette(
    val label: String,
    val seedArgb: Long,
) {
    MIST("雾青", 0xFF5F7C74),
    VIOLET("紫罗兰", 0xFF6750A4),
    ROSE("玫瑰", 0xFFB3265E),
    ORANGE("暖橙", 0xFF8B5000),
    GREEN("青翠", 0xFF386A20),
    TEAL("青蓝", 0xFF006B5F),
    BLUE("湛蓝", 0xFF005FAF),
}

data class AppPreferences(
    val gallerySize: GallerySize = GallerySize.STANDARD,
    val useGridByDefault: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = true,
    val presetPalette: PresetPalette = PresetPalette.MIST,
    val lyricsEnabled: Boolean = false,
    val lyricsProviderOrder: List<LyricsProviderId> = LyricsProviderId.entries,
    val lyricsTranslationMode: LyricsTranslationMode = LyricsTranslationMode.ORIGINAL,
    val lyricsSourceVisible: Boolean = true,
    val lyricsEffectsEnabled: Boolean = true,
    val lyricsFontSizeSp: Int = 28,
    val listenBrainzEnabled: Boolean = false,
    val listenBrainzToken: String = "",
    val listenBrainzMinimumSeconds: Int = 240,
    val listenBrainzMinimumPercent: Int = 50,
)

internal fun parseLyricsProviderOrder(value: String?): List<LyricsProviderId> = normalizedProviderOrder(
    value?.split(',')
        ?.mapNotNull { name -> LyricsProviderId.entries.firstOrNull { it.name == name } }
        .orEmpty(),
)

internal fun normalizedLyricsFontSizeSp(value: Int): Int = value.coerceIn(18, 40).let { it - it % 2 }

class AppPreferencesStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val secrets = context.getSharedPreferences(SECRETS_NAME, Context.MODE_PRIVATE)

    fun load(): AppPreferences = AppPreferences(
        gallerySize = preferences.enumValue(KEY_GALLERY_SIZE, GallerySize.STANDARD),
        useGridByDefault = preferences.getBoolean(KEY_GRID_DEFAULT, true),
        themeMode = preferences.enumValue(KEY_THEME_MODE, ThemeMode.SYSTEM),
        useDynamicColor = preferences.getBoolean(KEY_DYNAMIC_COLOR, true),
        presetPalette = preferences.enumValue(KEY_PRESET_PALETTE, PresetPalette.MIST),
        lyricsEnabled = preferences.getBoolean(KEY_LYRICS_ENABLED, false),
        lyricsProviderOrder = parseLyricsProviderOrder(preferences.getString(KEY_LYRICS_PROVIDER_ORDER, null)),
        lyricsTranslationMode = preferences.enumValue(
            KEY_LYRICS_TRANSLATION_MODE,
            LyricsTranslationMode.ORIGINAL,
        ),
        lyricsSourceVisible = preferences.getBoolean(KEY_LYRICS_SOURCE_VISIBLE, true),
        lyricsEffectsEnabled = preferences.getBoolean(KEY_LYRICS_EFFECTS_ENABLED, true),
        lyricsFontSizeSp = normalizedLyricsFontSizeSp(preferences.getInt(KEY_LYRICS_FONT_SIZE_SP, 28)),
        listenBrainzEnabled = preferences.getBoolean(KEY_LISTENBRAINZ_ENABLED, false),
        listenBrainzToken = secrets.getString(KEY_LISTENBRAINZ_TOKEN, "").orEmpty(),
        listenBrainzMinimumSeconds = preferences.getInt(KEY_LISTENBRAINZ_MINIMUM_SECONDS, 240)
            .coerceIn(30, 600),
        listenBrainzMinimumPercent = preferences.getInt(KEY_LISTENBRAINZ_MINIMUM_PERCENT, 50)
            .coerceIn(10, 100),
    )

    fun lastServer(): DlnaDevice? = loadLastDevice(
        idKey = KEY_LAST_SERVER_ID,
        nameKey = KEY_LAST_SERVER_NAME,
        manufacturerKey = KEY_LAST_SERVER_MANUFACTURER,
        modelKey = KEY_LAST_SERVER_MODEL,
        kind = DlnaDeviceKind.MEDIA_SERVER,
    )

    fun lastRenderer(): DlnaDevice? = loadLastDevice(
        idKey = KEY_LAST_RENDERER_ID,
        nameKey = KEY_LAST_RENDERER_NAME,
        manufacturerKey = KEY_LAST_RENDERER_MANUFACTURER,
        modelKey = KEY_LAST_RENDERER_MODEL,
        kind = DlnaDeviceKind.MEDIA_RENDERER,
    )

    fun saveLastServer(device: DlnaDevice) = saveLastDevice(
        device = device,
        idKey = KEY_LAST_SERVER_ID,
        nameKey = KEY_LAST_SERVER_NAME,
        manufacturerKey = KEY_LAST_SERVER_MANUFACTURER,
        modelKey = KEY_LAST_SERVER_MODEL,
    )

    fun saveLastRenderer(device: DlnaDevice) = saveLastDevice(
        device = device,
        idKey = KEY_LAST_RENDERER_ID,
        nameKey = KEY_LAST_RENDERER_NAME,
        manufacturerKey = KEY_LAST_RENDERER_MANUFACTURER,
        modelKey = KEY_LAST_RENDERER_MODEL,
    )

    fun save(value: AppPreferences) {
        preferences.edit()
            .putString(KEY_GALLERY_SIZE, value.gallerySize.name)
            .putBoolean(KEY_GRID_DEFAULT, value.useGridByDefault)
            .putString(KEY_THEME_MODE, value.themeMode.name)
            .putBoolean(KEY_DYNAMIC_COLOR, value.useDynamicColor)
            .putString(KEY_PRESET_PALETTE, value.presetPalette.name)
            .putBoolean(KEY_LYRICS_ENABLED, value.lyricsEnabled)
            .putString(
                KEY_LYRICS_PROVIDER_ORDER,
                normalizedProviderOrder(value.lyricsProviderOrder).joinToString(",", transform = LyricsProviderId::name),
            )
            .putString(KEY_LYRICS_TRANSLATION_MODE, value.lyricsTranslationMode.name)
            .putBoolean(KEY_LYRICS_SOURCE_VISIBLE, value.lyricsSourceVisible)
            .putBoolean(KEY_LYRICS_EFFECTS_ENABLED, value.lyricsEffectsEnabled)
            .putInt(KEY_LYRICS_FONT_SIZE_SP, normalizedLyricsFontSizeSp(value.lyricsFontSizeSp))
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

    private fun loadLastDevice(
        idKey: String,
        nameKey: String,
        manufacturerKey: String,
        modelKey: String,
        kind: DlnaDeviceKind,
    ): DlnaDevice? {
        val id = preferences.getString(idKey, null)?.takeIf { it.isNotBlank() } ?: return null
        return DlnaDevice(
            id = id,
            name = preferences.getString(nameKey, "").orEmpty(),
            manufacturer = preferences.getString(manufacturerKey, "").orEmpty(),
            model = preferences.getString(modelKey, "").orEmpty(),
            kind = kind,
        )
    }

    private fun saveLastDevice(
        device: DlnaDevice,
        idKey: String,
        nameKey: String,
        manufacturerKey: String,
        modelKey: String,
    ) {
        preferences.edit()
            .putString(idKey, device.id)
            .putString(nameKey, device.name)
            .putString(manufacturerKey, device.manufacturer)
            .putString(modelKey, device.model)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "ls_music_preferences"
        const val SECRETS_NAME = "ls_music_secrets"
        const val KEY_LAST_SERVER_ID = "last_server_id"
        const val KEY_LAST_SERVER_NAME = "last_server_name"
        const val KEY_LAST_SERVER_MANUFACTURER = "last_server_manufacturer"
        const val KEY_LAST_SERVER_MODEL = "last_server_model"
        const val KEY_LAST_RENDERER_ID = "last_renderer_id"
        const val KEY_LAST_RENDERER_NAME = "last_renderer_name"
        const val KEY_LAST_RENDERER_MANUFACTURER = "last_renderer_manufacturer"
        const val KEY_LAST_RENDERER_MODEL = "last_renderer_model"
        const val KEY_GALLERY_SIZE = "gallery_size"
        const val KEY_GRID_DEFAULT = "grid_default"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val KEY_PRESET_PALETTE = "preset_palette"
        const val KEY_LYRICS_ENABLED = "lyrics_enabled"
        const val KEY_LYRICS_PROVIDER_ORDER = "lyrics_provider_order"
        const val KEY_LYRICS_TRANSLATION_MODE = "lyrics_translation_mode"
        const val KEY_LYRICS_SOURCE_VISIBLE = "lyrics_source_visible"
        const val KEY_LYRICS_EFFECTS_ENABLED = "lyrics_effects_enabled"
        const val KEY_LYRICS_FONT_SIZE_SP = "lyrics_font_size_sp"
        const val KEY_LISTENBRAINZ_ENABLED = "listenbrainz_enabled"
        const val KEY_LISTENBRAINZ_TOKEN = "listenbrainz_token"
        const val KEY_LISTENBRAINZ_MINIMUM_SECONDS = "listenbrainz_minimum_seconds"
        const val KEY_LISTENBRAINZ_MINIMUM_PERCENT = "listenbrainz_minimum_percent"
    }
}
