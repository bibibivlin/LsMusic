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
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回网络设置")
                }
                Text(
                    "待上传记录",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
        item(key = "summary") {
            Text(
                if (records.isEmpty()) {
                    "没有待上传的 ListenBrainz 播放记录。"
                } else {
                    "${records.size} 条记录保存在本机。自动重试和手动重试都会使用曲目实际开始播放的时间。" +
                        if (canUpload) "" else " 请返回网络设置并启用 ListenBrainz、保存有效令牌。"
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
                            Text("正在上传")
                        } else {
                            Icon(Icons.Rounded.Refresh, null)
                            Text("立即重试", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    OutlinedButton(
                        onClick = { confirmingClear = true },
                        enabled = !isUploading,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, null)
                        Text("清空列表", modifier = Modifier.padding(start = 8.dp))
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
                    Text("上传成功的记录会自动从这里移除。")
                }
            }
        }
    }

    recordToDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            title = { Text("删除这条待上传记录？") },
            text = { Text("“${record.track.title}”删除后将无法再上传到 ListenBrainz。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemove(record.id)
                        recordToDelete = null
                    },
                    enabled = !isUploading,
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { recordToDelete = null }) { Text("取消") }
            },
        )
    }

    if (confirmingClear) {
        AlertDialog(
            onDismissRequest = { confirmingClear = false },
            title = { Text("清空全部待上传记录？") },
            text = { Text("这 ${records.size} 条记录将永久删除，之后无法恢复上传。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClear()
                        confirmingClear = false
                    },
                    enabled = !isUploading && records.isNotEmpty(),
                ) { Text("全部删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingClear = false }) { Text("取消") }
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
                        record.track.title.ifBlank { "未知曲目" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOf(record.track.creator, record.track.album)
                            .filter(String::isNotBlank)
                            .joinToString(" · ")
                            .ifBlank { "未知艺术家" },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onDelete, enabled = deleteEnabled) {
                    Icon(Icons.Rounded.DeleteOutline, "删除这条记录")
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "播放于 ${formatPendingListenDate(record.startedAtEpochSeconds)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "已播放 ${formatPendingListenDuration(record.listenedMs)}" +
                    record.durationMs.takeIf { it > 0L }?.let {
                        " / 曲目 ${formatPendingListenDuration(it)}"
                    }.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (record.attemptCount > 0) {
                Text(
                    "已尝试 ${record.attemptCount} 次" +
                        record.lastAttemptAtEpochSeconds?.let {
                            " · 最近 ${formatPendingListenDate(it)}"
                        }.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            record.lastError?.let { error ->
                Spacer(Modifier.height(4.dp))
                Text(
                    "最近失败：$error",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onRetry, enabled = retryEnabled) {
                Icon(Icons.Rounded.Refresh, null)
                Text("重试此记录", modifier = Modifier.padding(start = 8.dp))
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
