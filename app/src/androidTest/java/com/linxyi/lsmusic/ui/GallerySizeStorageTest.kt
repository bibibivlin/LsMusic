package com.linxyi.lsmusic.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import com.linxyi.lsmusic.backup.AppBackupCodec
import com.linxyi.lsmusic.backup.AppBackupData
import com.linxyi.lsmusic.backup.BackupSession
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GallerySizeStorageTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val context = object : ContextWrapper(base) {
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            base.getSharedPreferences("gallery-size-test-$name", mode)
    }
    private val outer = galleryScreenKey(1080, 2376)
    private val inner = galleryScreenKey(2076, 2152)

    @Before @After
    fun clearFixtures() {
        listOf("ls_music_preferences", "ls_music_secrets").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test
    fun bothScreens_surviveStoreRecreationAndUnrelatedSettingChanges() {
        val initial = AppPreferences(gallerySize = GallerySize.LARGE)
            .withGallerySizeForScreen(outer, GallerySize.COMPACT)
            .withGallerySizeForScreen(inner, GallerySize.STANDARD)
        AppPreferencesStore(context).save(initial)
        val recreated = AppPreferencesStore(context)
        assertEquals(initial, recreated.load())
        recreated.save(recreated.load().copy(useGridByDefault = false))
        val loaded = AppPreferencesStore(context).load()
        assertEquals(GallerySize.COMPACT, loaded.gallerySizeForScreen(outer))
        assertEquals(GallerySize.STANDARD, loaded.gallerySizeForScreen(inner))
        assertEquals(GallerySize.LARGE, loaded.gallerySize)
    }

    @Test
    fun legacyInstall_keepsItsDefaultAfterTheFirstScreenIsCustomized() {
        context.getSharedPreferences("ls_music_preferences", Context.MODE_PRIVATE)
            .edit().putString("gallery_size", "LARGE").commit()
        val store = AppPreferencesStore(context)
        assertEquals(GallerySize.LARGE, store.load().gallerySizeForScreen(outer))
        store.save(store.load().withGallerySizeForScreen(outer, GallerySize.COMPACT))
        assertEquals(GallerySize.LARGE, AppPreferencesStore(context).load().gallerySizeForScreen(inner))
    }

    @Test
    fun backupRoundTrip_restoresScreenChoicesAndLegacyImportRemovesOverrides() {
        val store = AppPreferencesStore(context)
        val original = AppPreferences()
            .withGallerySizeForScreen(outer, GallerySize.COMPACT)
            .withGallerySizeForScreen(inner, GallerySize.LARGE)
        store.save(original)
        val backup = AppBackupCodec.decode(AppBackupCodec.encode(
            AppBackupData(store.backupPreferences(), null, BackupSession(), emptyList()),
        ))
        store.save(AppPreferences())
        assertTrue(store.load().gallerySizesByScreen.isEmpty())
        store.replaceFromBackup(backup.preferences, null)
        assertEquals(original, AppPreferencesStore(context).load())
        store.replaceFromBackup(mapOf("gallery_size" to "LARGE"), null)
        assertTrue(store.load().gallerySizesByScreen.isEmpty())
        assertEquals(GallerySize.LARGE, store.load().gallerySizeForScreen(outer))
        assertEquals(GallerySize.LARGE, store.load().gallerySizeForScreen(inner))
    }

    @Test
    fun wrongScreenPreferenceType_isRejectedBeforeMutation() {
        val store = AppPreferencesStore(context)
        val original = AppPreferences().withGallerySizeForScreen(outer, GallerySize.COMPACT)
        store.save(original)
        assertThrows(IllegalArgumentException::class.java) {
            store.replaceFromBackup(mapOf("$GALLERY_SIZE_BY_SCREEN_PREFIX$inner" to 42), null)
        }
        assertEquals(original, store.load())
    }
}
