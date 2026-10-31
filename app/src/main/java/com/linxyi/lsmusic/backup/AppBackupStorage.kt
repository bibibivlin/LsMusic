package com.linxyi.lsmusic.backup

import android.content.Context
import android.util.AtomicFile
import com.linxyi.lsmusic.listenbrainz.PendingListenDiskStore
import com.linxyi.lsmusic.listenbrainz.PendingListenRepository
import com.linxyi.lsmusic.ui.AppPreferencesStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException

internal class AppSessionStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "playback-session.json"))

    fun load(): BackupSession = try {
        file.openRead().use { AppBackupCodec.decodeSession(JSONObject(it.bufferedReader().readText())) }
    } catch (_: Exception) {
        BackupSession()
    }

    fun save(session: BackupSession) = file.writeSafely(AppBackupCodec.encodeSession(session).toString())
}

internal class AppBackupStorage(
    private val context: Context,
    private val preferences: AppPreferencesStore,
    private val pending: PendingListenRepository,
    private val sessions: AppSessionStore,
) {
    suspend fun snapshot(session: BackupSession, includeSecrets: Boolean): AppBackupData = withContext(Dispatchers.IO) {
        pending.snapshotForBackup { records ->
            AppBackupData(
                preferences = preferences.backupPreferences(),
                secrets = if (includeSecrets) nonEmptyBackupSecrets(preferences.backupSecrets()) else null,
                session = session,
                pendingListens = records,
            )
        }
    }

    /** Roll back on a failed write, or on next process start if interrupted by process death. */
    suspend fun restore(data: AppBackupData, includeSecrets: Boolean, previousSession: BackupSession) =
        withContext(NonCancellable + Dispatchers.IO) {
            pending.withBackupLock { records, replaceRecords ->
                check(!needsRecovery()) { "Previous import requires recovery" }
                val importedSecrets = nonEmptyBackupSecrets(data.secrets)
                val secretValues = if (includeSecrets && importedSecrets.isNotEmpty()) {
                    preferences.backupSecrets() + importedSecrets
                } else null
                preferences.validateBackup(data.preferences, secretValues)
                val previous = AppBackupData(
                    preferences.backupPreferences(), preferences.backupSecrets(), previousSession, records,
                )
                val journal = journal(context)
                val recoveryData = AppBackupCodec.encode(previous)
                require(recoveryData.toByteArray(Charsets.UTF_8).size <= AppBackupCodec.MAX_BYTES)
                journal.writeSafely(recoveryData)
                try {
                    preferences.replaceFromBackup(data.preferences, secretValues)
                    replaceRecords(data.pendingListens)
                    sessions.save(data.session)
                    journal.delete()
                } catch (error: Exception) {
                    // Leave the journal intact if rollback itself fails (e.g. full disk).
                    preferences.replaceFromBackup(previous.preferences, previous.secrets)
                    replaceRecords(previous.pendingListens)
                    sessions.save(previous.session)
                    journal.delete()
                    throw error
                }
            }
        }

    fun needsRecovery(): Boolean = journal(context).let {
        it.baseFile.exists() || File("${it.baseFile.path}.bak").exists()
    }

    suspend fun recoverInProcess(): AppBackupData = withContext(NonCancellable + Dispatchers.IO) {
        pending.withBackupLock { _, replaceRecords ->
            val file = journal(context)
            val previous = file.openRead().use(AppBackupCodec::read)
            preferences.replaceFromBackup(previous.preferences, previous.secrets)
            replaceRecords(previous.pendingListens)
            sessions.save(previous.session)
            file.delete()
            previous
        }
    }

    companion object {
        private fun journal(context: Context) = AtomicFile(File(context.noBackupFilesDir, "data-import-rollback.json"))

        /** Runs before activities or background upload services can observe partially restored data. */
        fun recoverInterruptedImport(context: Context) {
            val file = journal(context)
            val previous = try {
                file.openRead().use(AppBackupCodec::read)
            } catch (_: FileNotFoundException) {
                file.delete()
                return
            }
            AppPreferencesStore(context).replaceFromBackup(previous.preferences, previous.secrets)
            PendingListenDiskStore(context).save(previous.pendingListens)
            AppSessionStore(context).save(previous.session)
            file.delete()
        }
    }
}

private fun AtomicFile.writeSafely(text: String) {
    val bytes = text.toByteArray(Charsets.UTF_8)
    val output = startWrite()
    try {
        output.write(bytes)
        finishWrite(output)
        // AtomicFile logs some rename failures instead of throwing them.
        check(baseFile.isFile && baseFile.length() == bytes.size.toLong()) { "Unable to commit data file" }
    } catch (error: Throwable) {
        failWrite(output)
        throw error
    }
}
