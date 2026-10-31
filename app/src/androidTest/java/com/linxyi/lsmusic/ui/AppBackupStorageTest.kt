package com.linxyi.lsmusic.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.util.AtomicFile
import androidx.test.platform.app.InstrumentationRegistry
import com.linxyi.lsmusic.backup.AppBackupCodec
import com.linxyi.lsmusic.backup.AppBackupData
import com.linxyi.lsmusic.backup.AppBackupStorage
import com.linxyi.lsmusic.backup.AppSessionStore
import com.linxyi.lsmusic.backup.BackupSession
import com.linxyi.lsmusic.backup.withPreservedListens
import com.linxyi.lsmusic.dlna.DlnaDevice
import com.linxyi.lsmusic.dlna.DlnaDeviceKind
import com.linxyi.lsmusic.dlna.MediaEntry
import com.linxyi.lsmusic.listenbrainz.PendingListen
import com.linxyi.lsmusic.listenbrainz.PendingListenDiskStore
import com.linxyi.lsmusic.listenbrainz.PendingListenRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID

class AppBackupStorageTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(base.cacheDir, "backup-test-${UUID.randomUUID()}")
    private val context = object : ContextWrapper(base) {
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = base.getSharedPreferences("backup-test-$name", mode)
        override fun getNoBackupFilesDir(): File = File(root, "no-backup").apply { mkdirs() }
        override fun getFilesDir(): File = File(root, "files").apply { mkdirs() }
    }
    private val preferences = AppPreferencesStore(context)
    private val sessions = AppSessionStore(context)
    private val track = MediaEntry("track", "album", "Offline track", isContainer = false, didlMetadata = "<original/>")
    private val record = PendingListen("pending", track, 1700000000, 240000, 180000, 1700000180)
    private val disk = PendingListenDiskStore(context)
    private lateinit var pending: PendingListenRepository
    private lateinit var storage: AppBackupStorage

    @Before fun setup() {
        clear()
        pending = PendingListenRepository(emptyList(), disk::save)
        storage = AppBackupStorage(context, preferences, pending, sessions)
    }

    @After fun clear() {
        listOf("ls_music_preferences", "ls_music_secrets").forEach { context.getSharedPreferences(it, 0).edit().clear().commit() }
        root.deleteRecursively()
    }

    @Test fun export_withoutTokenIncludesAllSettingsDevicesSortsFutureKeysAndPendingListens() = runBlocking {
        val generated = UUID.randomUUID().toString()
        val original = AppPreferences(
            gallerySize = GallerySize.COMPACT, useGridByDefault = false, themeMode = ThemeMode.DARK,
            useDynamicColor = false, presetPalette = PresetPalette.ROSE, enqueueWhilePlaying = true,
            miniPlayerEnabled = false, clearQueueOnPlay = false, sleepTimerMinutes = 75, sleepTimerFinishTrack = true,
            lyricsEnabled = true, lyricsProviderOrder = com.linxyi.lsmusic.lyrics.LyricsProviderId.entries.reversed(),
            lyricsTranslationMode = com.linxyi.lsmusic.lyrics.LyricsTranslationMode.BILINGUAL,
            lyricsSourceVisible = false, lyricsEffectsEnabled = false, lyricsFontSizeSp = 36,
            listenBrainzEnabled = true, listenBrainzToken = generated, listenBrainzMinimumSeconds = 150, listenBrainzMinimumPercent = 75,
        )
        preferences.save(original)
        val server = DlnaDevice("uuid:stable", "Server", "Maker", "Model", DlnaDeviceKind.MEDIA_SERVER)
        preferences.saveLastServer(server)
        preferences.saveLastRenderer(server.copy(id = "uuid:renderer", kind = DlnaDeviceKind.MEDIA_RENDERER))
        preferences.saveAlbumSort(server.id, AlbumSort.entries.last())
        context.getSharedPreferences("ls_music_preferences", 0).edit().putBoolean("future_switch", true).commit()
        pending.enqueue(record)
        val session = BackupSession(listOf(QueueItem("occurrence", track)), 0, PlaybackOrder(RepeatMode.ALL, true, setOf("occurrence")))
        val backup = AppBackupCodec.decode(AppBackupCodec.encode(storage.snapshot(session, false)))
        assertNull(backup.secrets)
        assertFalse(AppBackupCodec.encode(backup).contains(generated))
        preferences.save(AppPreferences(listenBrainzToken = generated.reversed()))
        pending.clear()
        storage.restore(backup, false, BackupSession())
        // Credential values are compared as booleans to keep a failing assertion out of logs.
        assertTrue(preferences.load() == original.copy(listenBrainzToken = generated.reversed()))
        assertEquals(server, preferences.lastServer())
        assertEquals("uuid:renderer", preferences.lastRenderer()?.id)
        assertEquals(AlbumSort.entries.last(), preferences.albumSort(server.id))
        assertEquals(true, preferences.backupPreferences()["future_switch"])
        assertEquals(listOf(record), pending.records.value)
        assertEquals(listOf(record), disk.load())
        assertEquals(session, AppSessionStore(context).load())
    }

    @Test fun import_onlyExplicitlySelectedSecretsReplaceExistingCredentialsIncludingFutureKeys() = runBlocking {
        val generated = UUID.randomUUID().toString()
        preferences.save(AppPreferences(listenBrainzToken = generated))
        val secrets = context.getSharedPreferences("ls_music_secrets", 0)
        secrets.edit().putString("future_api_key", generated.reversed()).commit()
        val backup = storage.snapshot(BackupSession(), true)
        preferences.save(AppPreferences(listenBrainzToken = generated.reversed()))
        storage.restore(backup, false, BackupSession())
        assertTrue(preferences.load().listenBrainzToken == generated.reversed())
        storage.restore(backup, true, BackupSession())
        assertTrue(preferences.load().listenBrainzToken == generated)
        assertTrue(preferences.backupSecrets()["future_api_key"] == generated.reversed())
    }

    @Test fun invalidPreferenceTypes_areRejectedBeforeAnyStoreChanges() = runBlocking {
        preferences.save(AppPreferences(themeMode = ThemeMode.DARK))
        pending.enqueue(record)
        val old = preferences.backupPreferences()
        val bad = AppBackupData(mapOf("listenbrainz_enabled" to "wrong"), null, BackupSession(), emptyList())
        assertTrue(runCatching { storage.restore(bad, false, BackupSession()) }.isFailure)
        assertEquals(old, preferences.backupPreferences())
        assertEquals(listOf(record), pending.records.value)
    }

    @Test fun backupWithNoSavedToken_neverClearsTheDestinationTokenEvenIfSecretsAreSelected() = runBlocking {
        preferences.save(AppPreferences())
        val backup = storage.snapshot(BackupSession(), true)
        assertTrue(backup.secrets.isNullOrEmpty())
        val generated = UUID.randomUUID().toString()
        preferences.save(AppPreferences(listenBrainzToken = generated))
        storage.restore(backup, true, BackupSession())
        assertTrue(preferences.load().listenBrainzToken == generated)
    }

    @Test fun import_keepsTheJustFinishedListenAlongsideImportedRecords() = runBlocking {
        val imported = record.copy(id = "imported", startedAtEpochSeconds = 1_700_000_000)
        val current = record.copy(id = "finished", startedAtEpochSeconds = 1_700_000_300)
        val backup = AppBackupData(emptyMap(), null, BackupSession(), listOf(imported))
        pending.enqueue(current)
        storage.restore(backup.withPreservedListens(listOf(current)), false, BackupSession())
        assertEquals(listOf(imported, current), pending.records.value)
        assertEquals(listOf(imported, current), disk.load())
        assertEquals(1_700_000_300L, disk.load().last().startedAtEpochSeconds)
    }

    @Test fun failedPendingWrite_rollsBackPreferencesAndDoesNotPublishImportedRecords() = runBlocking {
        preferences.save(AppPreferences(themeMode = ThemeMode.DARK))
        val failing = PendingListenRepository(listOf(record), { error("Simulated storage failure") })
        val importer = AppBackupStorage(context, preferences, failing, sessions)
        val old = preferences.backupPreferences()
        assertTrue(runCatching {
            importer.restore(AppBackupData(mapOf("theme_mode" to "LIGHT"), null, BackupSession(), emptyList()), false, BackupSession())
        }.isFailure)
        assertEquals(old, preferences.backupPreferences())
        assertEquals(listOf(record), failing.records.value)
        assertFalse(File(context.noBackupFilesDir, "data-import-rollback.json").exists())
    }

    @Test fun interruptedImport_recoversAllStoresBeforeAnyBackgroundUploadCanStart() = runBlocking {
        preferences.save(AppPreferences(themeMode = ThemeMode.DARK))
        pending.enqueue(record)
        val session = BackupSession(listOf(QueueItem("first", track)), 0)
        val previous = storage.snapshot(session, true)
        val journal = AtomicFile(File(context.noBackupFilesDir, "data-import-rollback.json"))
        val output = journal.startWrite()
        AppBackupCodec.write(previous, output)
        journal.finishWrite(output)
        preferences.save(AppPreferences(themeMode = ThemeMode.LIGHT))
        disk.save(emptyList())
        sessions.save(BackupSession())
        AppBackupStorage.recoverInterruptedImport(context)
        assertEquals(previous.preferences, preferences.backupPreferences())
        assertEquals(listOf(record), disk.load())
        assertEquals(session, sessions.load())
        assertFalse(journal.baseFile.exists())
    }

    @Test fun rollbackFailure_keepsOriginalJournalAndRejectsAnotherImportUntilRecovery() = runBlocking {
        preferences.save(AppPreferences(themeMode = ThemeMode.DARK))
        pending.enqueue(record)
        val original = preferences.backupPreferences()
        val destination = File(context.noBackupFilesDir, "playback-session.json")
        // AtomicFile cannot replace a nonempty directory. This fails both commit and rollback.
        destination.mkdirs()
        File(destination, "blocker").writeText("fixture")
        val imported = AppBackupData(mapOf("theme_mode" to "LIGHT"), null, BackupSession(), emptyList())
        assertTrue(runCatching { storage.restore(imported, false, BackupSession()) }.isFailure)
        assertTrue(storage.needsRecovery())
        assertTrue(runCatching { storage.restore(imported, false, BackupSession()) }.isFailure)
        destination.deleteRecursively()
        val recovered = storage.recoverInProcess()
        assertEquals(original, recovered.preferences)
        assertEquals(original, preferences.backupPreferences())
        assertEquals(listOf(record), pending.records.value)
        assertFalse(storage.needsRecovery())
    }
}
