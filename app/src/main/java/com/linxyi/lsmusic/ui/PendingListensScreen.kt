package com.linxyi.lsmusic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.linxyi.lsmusic.listenbrainz.PendingListen
import com.linxyi.lsmusic.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun PendingListensScreen(
    state: LsMusicUiState,
    onBack: () -> Unit,
    onRetry: (Set<String>?) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
    bottomContentPadding: Dp,
) {
    var recordToDelete by remember { mutableStateOf<PendingListen?>(null) }
    var confirmingClear by remember { mutableStateOf(false) }
    val records = state.pendingListens
    val isUploading = state.isPendingListensUploading
    val canUpload = state.preferences.listenBrainzEnabled && state.preferences.listenBrainzToken.isNotBlank()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 20.dp, 20.dp, bottomContentPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back_to_network_settings))
                }
                Text(
                    stringResource(R.string.pending_listens_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
        item(key = "summary") {
            Text(
                if (records.isEmpty()) {
                    stringResource(R.string.pending_listens_empty)
                } else {
                    pluralStringResource(
                        R.plurals.pending_listens_local_summary,
                        records.size,
                        records.size,
                    ) + if (canUpload) "" else stringResource(R.string.pending_listens_enable_hint)
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (records.isNotEmpty()) {
            item(key = "actions") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FilledTonalButton(
                        onClick = { onRetry(null) },
                        enabled = !isUploading && canUpload,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (isUploading) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(end = 8.dp).size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Text(stringResource(R.string.uploading))
                        } else {
                            Icon(Icons.Rounded.Refresh, null)
                            Text(stringResource(R.string.retry_now), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    OutlinedButton(
                        onClick = { confirmingClear = true },
                        enabled = !isUploading,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, null)
                        Text(stringResource(R.string.clear_list), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        items(
            items = records,
            key = PendingListen::id,
            contentType = { "pending-listen" },
        ) { record ->
            PendingListenCard(
                record = record,
                retryEnabled = !isUploading && canUpload,
                deleteEnabled = !isUploading,
                onRetry = { onRetry(setOf(record.id)) },
                onDelete = { recordToDelete = record },
            )
        }
        if (records.isEmpty()) {
            item(key = "empty-space") {
                Box(
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.uploaded_records_removed))
                }
            }
        }
    }

    recordToDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            title = { Text(stringResource(R.string.delete_pending_title)) },
            text = { Text(stringResource(R.string.delete_pending_body, record.track.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemove(record.id)
                        recordToDelete = null
                    },
                    enabled = !isUploading,
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { recordToDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (confirmingClear) {
        AlertDialog(
            onDismissRequest = { confirmingClear = false },
            title = { Text(stringResource(R.string.clear_pending_title)) },
            text = { Text(stringResource(R.string.clear_pending_body, records.size)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClear()
                        confirmingClear = false
                    },
                    enabled = !isUploading && records.isNotEmpty(),
                ) { Text(stringResource(R.string.delete_all)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingClear = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun PendingListenCard(
    record: PendingListen,
    retryEnabled: Boolean,
    deleteEnabled: Boolean,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        record.track.title.ifBlank { stringResource(R.string.unknown_track) },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOf(record.track.creator, record.track.album)
                            .filter(String::isNotBlank)
                            .joinToString(" · ")
                            .ifBlank { stringResource(R.string.unknown_artist) },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onDelete, enabled = deleteEnabled) {
                    Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.delete_pending_record))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.played_at, formatPendingListenDate(record.startedAtEpochSeconds)),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.played_duration, formatPendingListenDuration(record.listenedMs)) +
                    record.durationMs.takeIf { it > 0L }?.let {
                        " · " + stringResource(R.string.track_duration, formatPendingListenDuration(it))
                    }.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (record.attemptCount > 0) {
                Text(
                    stringResource(R.string.upload_attempts, record.attemptCount) +
                        record.lastAttemptAtEpochSeconds?.let {
                            " · " + stringResource(R.string.last_attempt, formatPendingListenDate(it))
                        }.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            record.lastError?.let { error ->
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.latest_failure, error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onRetry, enabled = retryEnabled) {
                Icon(Icons.Rounded.Refresh, null)
                Text(stringResource(R.string.retry_this_record), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

private val pendingListenDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private fun formatPendingListenDate(epochSeconds: Long): String = Instant.ofEpochSecond(epochSeconds)
    .atZone(ZoneId.systemDefault())
    .format(pendingListenDateFormatter)

private fun formatPendingListenDuration(valueMs: Long): String {
    val totalSeconds = valueMs.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = totalSeconds % 3_600L / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
