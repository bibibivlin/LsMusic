package com.linxyi.lsmusic.backup

import com.linxyi.lsmusic.listenbrainz.ListenBrainzPlaybackTracker
import com.linxyi.lsmusic.listenbrainz.PendingListen
import com.linxyi.lsmusic.listenbrainz.PendingListenRepository
import com.linxyi.lsmusic.listenbrainz.shouldSubmitListen
import com.linxyi.lsmusic.ui.AppPreferences

/** Finalize against the old reporting rules before applying imported settings. */
internal fun finishPlaybackForImport(
    tracker: ListenBrainzPlaybackTracker,
    preferences: AppPreferences,
    stoppedAtElapsedMs: Long,
    queuedAtEpochSeconds: Long,
): PendingListen? {
    val configured = preferences.listenBrainzEnabled && preferences.listenBrainzToken.isNotBlank()
    val report = tracker.finish(stoppedAtElapsedMs, reportingEnabled = configured) ?: return null
    if (!shouldSubmitListen(
            listenedMs = report.listenedMs,
            durationMs = report.durationMs,
            minimumSeconds = preferences.listenBrainzMinimumSeconds,
            minimumPercent = preferences.listenBrainzMinimumPercent,
        )
    ) return null
    return PendingListen.fromReport(report, queuedAtEpochSeconds)
}

/** A pending record cannot carry its previous token, so do not associate it with a new account. */
internal fun tokenChangeNeedsPlaybackStopped(
    currentToken: String,
    importedToken: String?,
    reportingEnabled: Boolean,
    playbackOrSessionActive: Boolean,
    hasUnpersistedListens: Boolean,
): Boolean = !importedToken.isNullOrBlank() && importedToken != currentToken &&
    ((reportingEnabled && currentToken.isNotBlank() && playbackOrSessionActive) || hasUnpersistedListens)

internal fun AppBackupData.withPreservedListens(preserved: List<PendingListen>): AppBackupData {
    if (preserved.isEmpty()) return this
    val preservedIds = preserved.mapTo(mutableSetOf()) { it.id }
    return AppBackupData(
        preferences = preferences,
        secrets = secrets,
        session = session,
        pendingListens = pendingListens.filterNot { it.id in preservedIds } + preserved.distinctBy { it.id },
    )
}

/** Persist first, and on a later retry preserve only records still awaiting upload. */
internal suspend fun AppBackupData.persistAndIncludeCurrentListens(
    repository: PendingListenRepository,
    unpersisted: MutableMap<String, PendingListen>,
    pendingIds: MutableSet<String>,
): AppBackupData {
    val unsaved = unpersisted.values.toList()
    pendingIds.addAll(unsaved.map { it.id })
    unsaved.forEach { record ->
        repository.enqueue(record)
        unpersisted.remove(record.id)
    }
    return withPreservedListens(repository.records.value.filter { it.id in pendingIds })
}
