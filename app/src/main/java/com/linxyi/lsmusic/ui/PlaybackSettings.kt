package com.linxyi.lsmusic.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.linxyi.lsmusic.R
import kotlinx.coroutines.delay

internal fun LazyListScope.playbackSettingsItems(
    preferences: AppPreferences,
    timer: SleepTimerState,
    onEnqueueWhilePlaying: (Boolean) -> Unit,
    onMiniPlayerEnabled: (Boolean) -> Unit,
    onClearQueueOnPlay: (Boolean) -> Unit,
    onStartSleepTimer: (Int, Boolean) -> Unit,
    onCancelSleepTimer: () -> Unit,
) {
    item(key = "enqueue-while-playing", contentType = "switch") {
        SwitchSettingCard(
            stringResource(R.string.enqueue_while_playing), stringResource(R.string.enqueue_while_playing_description),
            preferences.enqueueWhilePlaying, onCheckedChange = onEnqueueWhilePlaying,
            modifier = Modifier.testTag("setting-enqueue-while-playing"),
        )
    }
    item(key = "mini-player", contentType = "switch") {
        SwitchSettingCard(
            stringResource(R.string.mini_player), stringResource(R.string.mini_player_description),
            preferences.miniPlayerEnabled, onCheckedChange = onMiniPlayerEnabled,
            modifier = Modifier.testTag("setting-mini-player"),
        )
    }
    item(key = "clear-queue-on-play", contentType = "switch") {
        SwitchSettingCard(
            stringResource(R.string.clear_queue_on_play), stringResource(R.string.clear_queue_on_play_description),
            preferences.clearQueueOnPlay, onCheckedChange = onClearQueueOnPlay,
            modifier = Modifier.testTag("setting-clear-queue-on-play"),
        )
    }
    item(key = "sleep-timer", contentType = "timer") {
        SleepTimerSetting(preferences, timer, onStartSleepTimer, onCancelSleepTimer)
    }
}

@Composable
private fun SleepTimerSetting(
    preferences: AppPreferences,
    timer: SleepTimerState,
    onStart: (Int, Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    val presets = listOf(15, 30, 45, 60, 90)
    var minutes by rememberSaveable(preferences.sleepTimerMinutes) { mutableStateOf(preferences.sleepTimerMinutes.toString()) }
    var custom by rememberSaveable { mutableStateOf(preferences.sleepTimerMinutes !in presets) }
    var finishTrack by rememberSaveable(preferences.sleepTimerFinishTrack) { mutableStateOf(preferences.sleepTimerFinishTrack) }
    val validMinutes = minutes.toIntOrNull()?.takeIf { it in 1..180 }
    val applying = timer.phase == SleepTimerPhase.APPLYING
    SettingCard(stringResource(R.string.sleep_timer), stringResource(R.string.sleep_timer_description)) {
        SleepTimerStatus(timer)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            presets.forEach { value ->
                FilterChip(
                    selected = !custom && validMinutes == value,
                    onClick = { custom = false; minutes = value.toString() },
                    enabled = !applying,
                    label = { Text(stringResource(R.string.duration_minutes, value)) },
                )
            }
            FilterChip(
                selected = custom, onClick = { custom = true }, enabled = !applying,
                label = { Text(stringResource(R.string.sleep_timer_custom)) },
            )
        }
        if (custom) {
            OutlinedTextField(
                value = minutes,
                onValueChange = { value -> if (value.length <= 3 && value.all(Char::isDigit)) minutes = value },
                modifier = Modifier.fillMaxWidth().testTag("sleep-timer-minutes"),
                singleLine = true,
                enabled = !applying,
                isError = validMinutes == null,
                label = { Text(stringResource(R.string.sleep_timer_minutes_label)) },
                supportingText = { Text(stringResource(R.string.sleep_timer_minutes_range)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.sleep_timer_finish_track), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.sleep_timer_finish_track_description), style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = finishTrack, onCheckedChange = { finishTrack = it }, enabled = !applying,
                modifier = Modifier.testTag("sleep-timer-finish-track"),
            )
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { validMinutes?.let { onStart(it, finishTrack) } },
            enabled = validMinutes != null && !applying,
            modifier = Modifier.fillMaxWidth().testTag("sleep-timer-start"),
        ) {
            Text(stringResource(
                if (timer.phase in setOf(SleepTimerPhase.COUNTING_DOWN, SleepTimerPhase.FINISHING_TRACK)) {
                    R.string.sleep_timer_restart
                } else R.string.sleep_timer_start,
            ))
        }
        if (timer.phase != SleepTimerPhase.OFF) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().testTag("sleep-timer-cancel")) {
                Text(stringResource(R.string.sleep_timer_cancel))
            }
        }
    }
}

@Composable
internal fun SleepTimerStatus(timer: SleepTimerState) {
    val remaining by produceState(timer.remainingSeconds(SystemClock.elapsedRealtime()), timer) {
        while (timer.phase == SleepTimerPhase.COUNTING_DOWN) {
            value = timer.remainingSeconds(SystemClock.elapsedRealtime())
            delay(1_000L)
        }
    }
    val context = LocalContext.current
    Text(
        text = when (timer.phase) {
            SleepTimerPhase.OFF -> stringResource(R.string.sleep_timer_off)
            SleepTimerPhase.COUNTING_DOWN -> stringResource(R.string.sleep_timer_remaining, remaining / 60L, remaining % 60L)
            SleepTimerPhase.FINISHING_TRACK -> stringResource(R.string.sleep_timer_waiting)
            SleepTimerPhase.APPLYING -> stringResource(R.string.sleep_timer_applying)
            SleepTimerPhase.FAILED -> timer.error?.resolve(context).orEmpty()
        },
        color = if (timer.phase == SleepTimerPhase.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        modifier = Modifier.testTag("sleep-timer-status"),
    )
}

@Composable
internal fun SleepTimerPermissionEffect(request: SleepTimerRequest?, onResult: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var launched by rememberSaveable(request) { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        launched = false
        onResult()
    }
    if (request == null || launched) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sleep_timer_permission_title)) },
        text = { Text(stringResource(R.string.sleep_timer_permission_description)) },
        confirmButton = {
            TextButton(onClick = {
                try {
                    launched = true
                    launcher.launch(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()))
                } catch (_: ActivityNotFoundException) {
                    launched = false
                    onResult()
                }
            }) { Text(stringResource(R.string.sleep_timer_grant_permission)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.sleep_timer_cancel)) } },
    )
}
