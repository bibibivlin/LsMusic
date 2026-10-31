package com.linxyi.lsmusic.ui

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.linxyi.lsmusic.R
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal class DataBackupActions(val export: () -> Unit, val import: () -> Unit)

/** Launchers live above settings navigation so rotation or a destination change cannot lose a result. */
@Composable
internal fun rememberDataBackupActions(
    state: DataBackupUiState,
    onExport: (Uri, Boolean) -> Unit,
    onInspectImport: (Uri) -> Unit,
    onImport: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onRecover: () -> Unit = {},
): DataBackupActions {
    var showExport by rememberSaveable { mutableStateOf(false) }
    var exportSecrets by rememberSaveable { mutableStateOf(false) }
    var pickerUnavailable by rememberSaveable { mutableStateOf(false) }
    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) onExport(uri, exportSecrets)
        exportSecrets = false
    }
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onInspectImport(uri)
    }
    DataBackupDialogs(
        state = state,
        showExport = showExport,
        onExport = { includeSecrets ->
            exportSecrets = includeSecrets
            showExport = false
            try {
                createDocument.launch("LsMusic-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))}.lsmusic.json")
            } catch (_: ActivityNotFoundException) { pickerUnavailable = true }
        },
        onCloseExport = { showExport = false },
        onImport = onImport,
        onDismiss = onDismiss,
        onRecover = onRecover,
    )
    if (pickerUnavailable) AlertDialog(
        onDismissRequest = { pickerUnavailable = false },
        title = { Text(stringResource(R.string.settings_about)) },
        text = { Text(stringResource(R.string.backup_picker_unavailable)) },
        confirmButton = { TextButton(onClick = { pickerUnavailable = false }) { Text(stringResource(R.string.backup_ok)) } },
    )
    return DataBackupActions(
        export = { showExport = true },
        import = {
            try { openDocument.launch(arrayOf("application/json", "application/octet-stream", "*/*")) }
            catch (_: ActivityNotFoundException) { pickerUnavailable = true }
        },
    )
}

@Composable
internal fun DataBackupButtons(onExport: () -> Unit, onImport: () -> Unit, enabled: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        DataBackupButton(
            R.string.backup_export, Icons.Rounded.FileUpload, onExport, enabled,
            Modifier.weight(1f).testTag("export-data"),
        )
        DataBackupButton(
            R.string.backup_import, Icons.Rounded.FileDownload, onImport, enabled,
            Modifier.weight(1f).testTag("import-data"),
        )
    }
}

@Composable
private fun DataBackupButton(label: Int, icon: ImageVector, onClick: () -> Unit, enabled: Boolean, modifier: Modifier) {
    // Match SettingsLink's surface, shape, padding, icon and typography.
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Text(
                stringResource(label),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun DataBackupDialogs(
    state: DataBackupUiState,
    showExport: Boolean,
    onExport: (Boolean) -> Unit,
    onCloseExport: () -> Unit,
    onImport: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onRecover: () -> Unit = {},
) {
    var includeExportSecrets by rememberSaveable(showExport) { mutableStateOf(false) }
    var includeImportSecrets by rememberSaveable(state.phase == DataBackupPhase.IDLE) { mutableStateOf(false) }
    val resources = LocalResources.current
    if (showExport) AlertDialog(
        onDismissRequest = onCloseExport,
        title = { Text(stringResource(R.string.backup_export)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.backup_scope))
                Spacer(Modifier.height(16.dp))
                SecretsChoice(includeExportSecrets, { includeExportSecrets = it }, R.string.backup_include_token)
                Text(stringResource(R.string.backup_secret_warning), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onExport(includeExportSecrets) }) { Text(stringResource(R.string.backup_choose_location)) } },
        dismissButton = { TextButton(onClick = onCloseExport) { Text(stringResource(R.string.cancel)) } },
    )
    if (state.phase == DataBackupPhase.PREVIEW) AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_import_confirm)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.backup_summary, state.preferenceCount, state.queueCount, state.pendingListenCount))
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.backup_replace_warning))
                Spacer(Modifier.height(12.dp))
                if (state.containsSecrets) {
                    SecretsChoice(includeImportSecrets, { includeImportSecrets = it }, R.string.backup_import_token)
                    Text(stringResource(R.string.backup_secret_warning), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Text(stringResource(R.string.backup_keep_token), style = MaterialTheme.typography.bodySmall)
                state.message?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it.resolve(resources), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onImport(state.containsSecrets && includeImportSecrets) }) { Text(stringResource(R.string.backup_replace)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
    if (state.phase == DataBackupPhase.RECOVERY_REQUIRED) AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.settings_about)) },
        text = { Text(stringResource(R.string.backup_recovery_required)) },
        confirmButton = { TextButton(onClick = onRecover) { Text(stringResource(R.string.backup_retry_recovery)) } },
    )
    if (state.isBusy) AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.settings_about)) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(16.dp))
                Text(stringResource(when (state.phase) {
                    DataBackupPhase.EXPORTING -> R.string.backup_exporting
                    DataBackupPhase.READING -> R.string.backup_reading
                    else -> R.string.backup_importing
                }))
            }
        },
        confirmButton = {},
    )
    if (state.phase == DataBackupPhase.IDLE && state.message != null) AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_about)) },
        text = { Text(state.message.resolve(resources)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.backup_ok)) } },
    )
}

@Composable
private fun SecretsChoice(checked: Boolean, onChecked: (Boolean) -> Unit, label: Int) {
    Row(
        Modifier.fillMaxWidth().toggleable(checked, role = Role.Checkbox, onValueChange = onChecked)
            .padding(vertical = 8.dp).testTag("backup-include-secrets"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
    }
}
