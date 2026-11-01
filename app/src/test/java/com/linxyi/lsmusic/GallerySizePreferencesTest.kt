package com.linxyi.lsmusic

import com.linxyi.lsmusic.backup.AppBackupCodec
import com.linxyi.lsmusic.backup.AppBackupData
import com.linxyi.lsmusic.backup.BackupSession
import com.linxyi.lsmusic.ui.AppPreferences
import com.linxyi.lsmusic.ui.GALLERY_SIZE_BY_SCREEN_PREFIX
import com.linxyi.lsmusic.ui.GallerySize
import com.linxyi.lsmusic.ui.LsMusicUiState
import com.linxyi.lsmusic.ui.galleryScreenKey
import com.linxyi.lsmusic.ui.readScreenGallerySizes
import org.junit.Assert.*
import org.junit.Test

class GallerySizePreferencesTest {
    private val outer = galleryScreenKey(1080, 2376)
    private val inner = galleryScreenKey(2076, 2152)

    @Test
    fun screenKeys_distinguishPanelsAndIgnoreRotation() {
        assertNotEquals(outer, inner)
        assertEquals(outer, galleryScreenKey(2376, 1080))
        assertEquals(inner, galleryScreenKey(2152, 2076))
        assertEquals("1800x1800", galleryScreenKey(1800, 1800))
    }

    @Test
    fun screenKeys_rejectUnavailableDimensions() {
        assertThrows(IllegalArgumentException::class.java) { galleryScreenKey(0, 100) }
        assertThrows(IllegalArgumentException::class.java) { galleryScreenKey(100, -1) }
    }

    @Test
    fun newAndUnconfiguredScreens_retainTheLegacyDefault() {
        assertEquals(GallerySize.STANDARD, AppPreferences().gallerySizeForScreen(outer))
        val preferences = AppPreferences(gallerySize = GallerySize.LARGE)
            .withGallerySizeForScreen(outer, GallerySize.COMPACT)
        assertEquals(GallerySize.COMPACT, preferences.gallerySizeForScreen(outer))
        assertEquals(GallerySize.LARGE, preferences.gallerySizeForScreen(inner))
        assertEquals(GallerySize.LARGE, preferences.gallerySizeForScreen(null))
    }

    @Test
    fun changingOneScreen_preservesOtherScreensAndTheLegacyDefault() {
        val preferences = AppPreferences()
            .withGallerySizeForScreen(outer, GallerySize.COMPACT)
            .withGallerySizeForScreen(inner, GallerySize.LARGE)
            .withGallerySizeForScreen(outer, GallerySize.STANDARD)
        assertEquals(GallerySize.STANDARD, preferences.gallerySizeForScreen(outer))
        assertEquals(GallerySize.LARGE, preferences.gallerySizeForScreen(inner))
        assertEquals(GallerySize.STANDARD, preferences.gallerySize)
        assertEquals(2, preferences.gallerySizesByScreen.size)
    }

    @Test
    fun switchingScreens_updatesTheDisplayedSelectionWithoutChangingPreferences() {
        val preferences = AppPreferences()
            .withGallerySizeForScreen(outer, GallerySize.COMPACT)
            .withGallerySizeForScreen(inner, GallerySize.LARGE)
        val state = LsMusicUiState(preferences = preferences, galleryScreenKey = outer)
        assertEquals(GallerySize.COMPACT, state.gallerySize)
        val unfolded = state.copy(galleryScreenKey = inner)
        assertEquals(GallerySize.LARGE, unfolded.gallerySize)
        assertEquals(GallerySize.COMPACT, unfolded.copy(galleryScreenKey = outer).gallerySize)
        assertSame(preferences, unfolded.preferences)
    }

    @Test
    fun invalidScreenValues_fallBackWithoutDiscardingOtherScreens() {
        val sizes = readScreenGallerySizes(mapOf(
            "$GALLERY_SIZE_BY_SCREEN_PREFIX$outer" to "COMPACT",
            "$GALLERY_SIZE_BY_SCREEN_PREFIX$inner" to "FUTURE_SIZE",
            "${GALLERY_SIZE_BY_SCREEN_PREFIX}invalid" to 42,
            GALLERY_SIZE_BY_SCREEN_PREFIX to "LARGE",
            "theme_mode" to "DARK",
        ))
        val preferences = AppPreferences(gallerySize = GallerySize.LARGE, gallerySizesByScreen = sizes)
        assertEquals(mapOf(outer to GallerySize.COMPACT), sizes)
        assertEquals(GallerySize.LARGE, preferences.gallerySizeForScreen(inner))
    }

    @Test
    fun backupRoundTrip_preservesBothScreensWithoutCredentials() {
        val backup = AppBackupData(
            preferences = mapOf(
                "gallery_size" to "STANDARD",
                "$GALLERY_SIZE_BY_SCREEN_PREFIX$outer" to "COMPACT",
                "$GALLERY_SIZE_BY_SCREEN_PREFIX$inner" to "LARGE",
            ),
            secrets = null,
            session = BackupSession(),
            pendingListens = emptyList(),
        )
        val restored = AppBackupCodec.decode(AppBackupCodec.encode(backup))
        assertEquals(backup.preferences, restored.preferences)
        assertEquals(
            mapOf(outer to GallerySize.COMPACT, inner to GallerySize.LARGE),
            readScreenGallerySizes(restored.preferences),
        )
        assertNull(restored.secrets)
    }

    @Test
    fun legacyBackup_doesNotRequireScreenPreferences() {
        val backup = AppBackupData(mapOf("gallery_size" to "LARGE"), null, BackupSession(), emptyList())
        val restored = AppBackupCodec.decode(AppBackupCodec.encode(backup))
        assertTrue(readScreenGallerySizes(restored.preferences).isEmpty())
        assertEquals("LARGE", restored.preferences["gallery_size"])
    }
}
