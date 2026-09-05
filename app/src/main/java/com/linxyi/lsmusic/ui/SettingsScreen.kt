@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.linxyi.lsmusic.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.linxyi.lsmusic.lyrics.LyricsProviderId
import com.linxyi.lsmusic.lyrics.LyricsTranslationMode
import com.linxyi.lsmusic.ui.theme.presetColorScheme
import kotlin.math.roundToInt
import android.content.Intent
import androidx.core.net.toUri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.linxyi.lsmusic.R

@Composable
internal fun SettingsScreen(
    state: LsMusicUiState,
    preferences: AppPreferences,
    onRefresh: () -> Unit,
    onSelectServer: (String) -> Unit,
    onSelectRenderer: (String) -> Unit,
    onGallerySize: (GallerySize) -> Unit,
    onDefaultGridLayout: (Boolean) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onPresetPalette: (PresetPalette) -> Unit,
    onLyricsEnabled: (Boolean) -> Unit,
    onLyricsProviderOrder: (List<LyricsProviderId>) -> Unit,
    onLyricsTranslationMode: (LyricsTranslationMode) -> Unit,
    onLyricsSourceVisible: (Boolean) -> Unit,
    onLyricsEffectsEnabled: (Boolean) -> Unit,
    onLyricsFontSizeSp: (Int) -> Unit,
    onClearLyricsCache: () -> Unit,
    onListenBrainzEnabled: (Boolean) -> Unit,
    onListenBrainzToken: (String) -> Unit,
    onListenBrainzMinimumSeconds: (Int) -> Unit,
    onListenBrainzMinimumPercent: (Int) -> Unit,
    onOpenPendingListens: () -> Unit,
    bottomContentPadding: Dp,
    onNavigate: (AppDestination) -> Unit = {},
    onExit: () -> Unit = {},
) {
    var listenBrainzTokenDraft by rememberSaveable(preferences.listenBrainzToken) {
        mutableStateOf(preferences.listenBrainzToken)
    }
    val normalizedTokenDraft = listenBrainzTokenDraft.trim()
    val tokenValidation = state.listenBrainzTokenValidation
    val validationAppliesToDraft = tokenValidation.checkedToken == normalizedTokenDraft &&
        normalizedTokenDraft.isNotEmpty()
    val tokenValidationStatus = if (validationAppliesToDraft) {
        tokenValidation.status
    } else {
        ListenBrainzTokenValidationStatus.IDLE
    }
    val isCheckingToken = tokenValidationStatus == ListenBrainzTokenValidationStatus.CHECKING
    val resources = LocalResources.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("settings-list"),
        contentPadding = PaddingValues(20.dp, 24.dp, 20.dp, bottomContentPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "header", contentType = "header") {
            SettingsHeader(stringResource(state.destination.settingsTitleRes), state.destination.settingsParent != null) {
                state.destination.settingsParent?.let(onNavigate)
            }
        }
        if (state.destination == AppDestination.SETTINGS) {
            item {
                SettingCard(
                    title = stringResource(R.string.settings_devices_title),
                    description = if (state.isSearching) stringResource(R.string.settings_scanning_devices)
                    else stringResource(R.string.settings_choose_source),
                ) {
                    DeviceStrip(
                        state = state,
                        onSelectServer = onSelectServer,
                        onSelectRenderer = onSelectRenderer,
                    )
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Refresh, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.scan_local_network))
                    }
                }
            }
            settingsCategoryItems(onNavigate)
            item(key = "exit", contentType = "exit") {
                Button(
                    onClick = onExit,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ExitToApp, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.exit))
                }
            }
        }
        if (state.destination == AppDestination.SETTINGS_APPEARANCE) {
            item {
                SettingCard(
                    title = stringResource(R.string.settings_gallery_size),
                    description = stringResource(R.string.settings_gallery_description),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GallerySize.entries.forEach { size ->
                            FilterChip(
                                selected = preferences.gallerySize == size,
                                onClick = { onGallerySize(size) },
                                label = { Text(size.displayName()) },
                            )
                        }
                    }
                }
            }
            item {
                SwitchSettingCard(
                    title = stringResource(R.string.settings_default_layout),
                    description = if (preferences.useGridByDefault) stringResource(R.string.settings_grid_preferred)
                    else stringResource(R.string.settings_list_preferred),
                    checked = preferences.useGridByDefault,
                    onCheckedChange = onDefaultGridLayout,
                )
            }
            item {
                DynamicColorSettingCard(
                    useDynamicColor = preferences.useDynamicColor,
                    selectedPalette = preferences.presetPalette,
                    onDynamicColorChange = onDynamicColor,
                    onPresetPalette = onPresetPalette,
                )
            }
            item {
                SettingCard(
                    title = stringResource(R.string.settings_color_mode),
                    description = stringResource(R.string.settings_color_mode_description),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = preferences.themeMode == mode,
                                onClick = { onThemeMode(mode) },
                                label = { Text(mode.displayName()) },
                            )
                        }
                    }
                }
            }
        }
        if (state.destination == AppDestination.SETTINGS_LYRICS) {
            item {
                SwitchSettingCard(
                    title = stringResource(R.string.settings_online_lyrics),
                    description = if (preferences.lyricsEnabled) {
                        stringResource(R.string.settings_online_lyrics_enabled)
                    } else {
                        stringResource(R.string.settings_online_lyrics_disabled)
                    },
                    checked = preferences.lyricsEnabled,
                    onCheckedChange = onLyricsEnabled,
                )
            }
            item {
                SettingCard(
                    title = stringResource(R.string.settings_lyrics_provider_order),
                    description = stringResource(R.string.settings_lyrics_provider_order_description),
                ) {
                    LyricsProviderOrderSetting(
                        order = preferences.lyricsProviderOrder,
                        enabled = preferences.lyricsEnabled,
                        onOrderChange = onLyricsProviderOrder,
                    )
                }
            }
            item {
                SettingCard(
                    title = stringResource(R.string.settings_lyrics_translation),
                    description = stringResource(R.string.settings_lyrics_translation_description),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LyricsTranslationMode.entries.forEach { mode ->
                            FilterChip(
                                selected = preferences.lyricsTranslationMode == mode,
                                onClick = { onLyricsTranslationMode(mode) },
                                enabled = preferences.lyricsEnabled,
                                label = { Text(mode.displayName()) },
                            )
                        }
                    }
                }
            }
            item {
                SwitchSettingCard(
                    title = stringResource(R.string.settings_show_lyrics_source),
                    description = stringResource(R.string.settings_show_lyrics_source_description),
                    checked = preferences.lyricsSourceVisible,
                    enabled = preferences.lyricsEnabled,
                    onCheckedChange = onLyricsSourceVisible,
                )
            }
            item {
                SwitchSettingCard(
                    title = stringResource(R.string.settings_lyrics_effects),
                    description = stringResource(R.string.settings_lyrics_effects_description),
                    checked = preferences.lyricsEffectsEnabled,
                    enabled = preferences.lyricsEnabled,
                    onCheckedChange = onLyricsEffectsEnabled,
                )
            }
            item {
                SettingCard(
                    title = stringResource(R.string.settings_lyrics_font_size),
                    description = stringResource(R.string.settings_lyrics_font_size_description, preferences.lyricsFontSizeSp),
                ) {
                    Slider(
                        value = preferences.lyricsFontSizeSp.toFloat(),
                        onValueChange = { onLyricsFontSizeSp((it / 2f).roundToInt() * 2) },
                        enabled = preferences.lyricsEnabled,
                        valueRange = 18f..40f,
                        steps = 10,
                    )
                }
            }
            item {
                SettingCard(
                    title = stringResource(R.string.settings_lyrics_cache),
                    description = stringResource(R.string.settings_lyrics_cache_description),
                ) {
                    Text(
                        stringResource(R.string.cache_usage, formatByteSize(state.lyricsCacheBytes)),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onClearLyricsCache,
                        enabled = !state.isClearingLyricsCache,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (state.isClearingLyricsCache) stringResource(R.string.clearing_lyrics_cache)
                        else stringResource(R.string.clear_lyrics_cache))
                    }
                }
            }
        }
        if (state.destination == AppDestination.SETTINGS_NETWORK) {
            item {
                SwitchSettingCard(
                    title = stringResource(R.string.settings_listenbrainz),
                    description = if (preferences.listenBrainzEnabled) {
                        if (preferences.listenBrainzToken.isBlank()) stringResource(R.string.settings_listenbrainz_missing_token)
                        else stringResource(R.string.settings_listenbrainz_enabled)
                    } else {
                        stringResource(R.string.settings_listenbrainz_disabled)
                    },
                    checked = preferences.listenBrainzEnabled,
                    onCheckedChange = onListenBrainzEnabled,
                )
        }
        if (state.pendingListens.isNotEmpty()) {
            item {
                SettingCard(
                    title = stringResource(R.string.pending_listens_title),
                    description = pluralStringResource(R.plurals.pending_listens_summary, state.pendingListens.size, state.pendingListens.size),
                ) {
                    FilledTonalButton(
                        onClick = onOpenPendingListens,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.view_and_manage))
                    }
                }
            }
        }
        item {
            SettingCard(
                title = stringResource(R.string.listenbrainz_api),
                description = stringResource(R.string.listenbrainz_token_description),
            ) {
                OutlinedTextField(
                    value = listenBrainzTokenDraft,
                    onValueChange = { listenBrainzTokenDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.listenbrainz_token_label)) },
                    singleLine = true,
                    enabled = !isCheckingToken,
                    visualTransformation = PasswordVisualTransformation(),
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isCheckingToken) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = when {
                            validationAppliesToDraft -> tokenValidation.message?.resolve(resources).orEmpty()
                            normalizedTokenDraft.isEmpty() && preferences.listenBrainzToken.isNotBlank() ->
                                stringResource(R.string.token_will_be_cleared)
                            normalizedTokenDraft == preferences.listenBrainzToken && normalizedTokenDraft.isNotEmpty() ->
                                stringResource(R.string.token_saved_revalidate)
                            normalizedTokenDraft.isNotEmpty() -> stringResource(R.string.token_not_validated)
                            else -> stringResource(R.string.token_enter_prompt)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when (tokenValidationStatus) {
                            ListenBrainzTokenValidationStatus.VALID -> MaterialTheme.colorScheme.primary
                            ListenBrainzTokenValidationStatus.INVALID,
                            ListenBrainzTokenValidationStatus.ERROR -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(
                    onClick = { onListenBrainzToken(normalizedTokenDraft) },
                    enabled = !isCheckingToken && (
                        normalizedTokenDraft.isNotEmpty() || preferences.listenBrainzToken.isNotBlank()
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        when {
                            isCheckingToken -> stringResource(R.string.token_checking)
                            normalizedTokenDraft.isEmpty() -> stringResource(R.string.clear_token)
                            normalizedTokenDraft == preferences.listenBrainzToken -> stringResource(R.string.revalidate_token)
                            else -> stringResource(R.string.validate_and_save_token)
                        },
                    )
                }
            }
        }
        item {
            SettingCard(
                title = stringResource(R.string.upload_rules),
                description = stringResource(R.string.upload_rules_description),
            ) {
                Text(stringResource(R.string.minimum_playback_time, formatRuleDuration(preferences.listenBrainzMinimumSeconds)))
                Slider(
                    value = preferences.listenBrainzMinimumSeconds.toFloat(),
                    onValueChange = { onListenBrainzMinimumSeconds((it / 30f).roundToInt() * 30) },
                    valueRange = 30f..600f,
                    steps = 18,
                )
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.minimum_playback_percent, preferences.listenBrainzMinimumPercent))
                Slider(
                    value = preferences.listenBrainzMinimumPercent.toFloat(),
                    onValueChange = { onListenBrainzMinimumPercent((it / 5f).roundToInt() * 5) },
                    valueRange = 10f..100f,
                    steps = 17,
                )
                Text(
                    stringResource(
                        R.string.current_upload_rule,
                        formatRuleDuration(preferences.listenBrainzMinimumSeconds),
                        preferences.listenBrainzMinimumPercent,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Text(
                stringResource(R.string.listenbrainz_reporting_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp, end = 4.dp),
            )
        }
        }
        if (state.destination == AppDestination.SETTINGS_ABOUT) {
                aboutSettingsItems()
        }
    }
}

@Composable
private fun formatRuleDuration(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return when {
        minutes == 0 -> stringResource(R.string.duration_seconds, remainingSeconds)
        remainingSeconds == 0 -> stringResource(R.string.duration_minutes, minutes)
        else -> stringResource(R.string.duration_minutes_seconds, minutes, remainingSeconds)
    }
}

private fun formatByteSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MiB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.1f KiB".format(bytes / 1024.0)
    else -> "$bytes B"
}

@Composable
private fun GallerySize.displayName(): String = stringResource(
    when (this) {
        GallerySize.COMPACT -> R.string.gallery_compact
        GallerySize.STANDARD -> R.string.gallery_standard
        GallerySize.LARGE -> R.string.gallery_large
    },
)

@Composable
private fun ThemeMode.displayName(): String = stringResource(
    when (this) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    },
)

@Composable
private fun LyricsProviderId.displayName(): String = stringResource(
    when (this) {
        LyricsProviderId.NETEASE -> R.string.lyrics_provider_netease
        LyricsProviderId.QQ -> R.string.lyrics_provider_qq
    },
)

@Composable
private fun LyricsTranslationMode.displayName(): String = stringResource(
    when (this) {
        LyricsTranslationMode.ORIGINAL -> R.string.lyrics_original
        LyricsTranslationMode.BILINGUAL -> R.string.lyrics_bilingual
        LyricsTranslationMode.CHINESE_ONLY -> R.string.lyrics_chinese_only
    },
)

@Composable
private fun PresetPalette.displayName(): String = stringResource(
    when (this) {
        PresetPalette.MIST -> R.string.palette_mist
        PresetPalette.VIOLET -> R.string.palette_violet
        PresetPalette.ROSE -> R.string.palette_rose
        PresetPalette.ORANGE -> R.string.palette_orange
        PresetPalette.GREEN -> R.string.palette_green
        PresetPalette.TEAL -> R.string.palette_teal
        PresetPalette.BLUE -> R.string.palette_blue
    },
)

@Composable
private fun LyricsProviderOrderSetting(
    order: List<LyricsProviderId>,
    enabled: Boolean,
    onOrderChange: (List<LyricsProviderId>) -> Unit,
) {
    var displayedOrder by remember(order) { mutableStateOf(order) }
    val listState = rememberLazyListState()
    val reorderState = remember(listState) {
        LazyListReorderState(listState = listState, itemIndexOffset = 0)
    }
    reorderState.onMove = { fromIndex, toIndex ->
        displayedOrder = moveListItem(displayedOrder, fromIndex, toIndex)
    }
    val commitOrder = {
        if (displayedOrder != order) onOrderChange(displayedOrder)
    }
    val listHeight = LyricsProviderItemHeight * displayedOrder.size +
        LyricsProviderItemSpacing * (displayedOrder.size - 1).coerceAtLeast(0) +
        ReorderVisualPadding * 2

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().height(listHeight),
        contentPadding = PaddingValues(vertical = ReorderVisualPadding),
        verticalArrangement = Arrangement.spacedBy(LyricsProviderItemSpacing),
        userScrollEnabled = false,
    ) {
        itemsIndexed(displayedOrder, key = { _, provider -> provider.name }) { _, provider ->
            val itemKey = provider.name
            val dragged = reorderState.isDragging(itemKey)
            val pinnableContainer = LocalPinnableContainer.current
            val scale by animateFloatAsState(if (dragged) 1.035f else 1f, label = "lyrics-provider-drag-scale")
            val elevation by animateDpAsState(if (dragged) 4.dp else 0.dp, label = "lyrics-provider-drag-elevation")
            val containerColor by animateColorAsState(
                if (dragged) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
                label = "lyrics-provider-drag-color",
            )
            Surface(
                modifier = reorderPlacementModifier(dragged)
                    .fillMaxWidth()
                    .zIndex(if (dragged) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (dragged) reorderState.draggedItemOffset else 0f
                        scaleX = scale
                        scaleY = scale
                    },
                shape = RoundedCornerShape(18.dp),
                color = containerColor,
                shadowElevation = elevation,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.DragHandle,
                        stringResource(R.string.reorder_lyrics_provider, provider.displayName()),
                        modifier = Modifier
                            .size(40.dp)
                            .lazyListReorderHandle(
                                enabled = enabled,
                                itemKey = itemKey,
                                reorderState = reorderState,
                                pinnableContainer = pinnableContainer,
                                onDragEnd = commitOrder,
                                onDragCancel = commitOrder,
                            )
                            .padding(8.dp),
                        tint = if (enabled) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = .38f)
                        },
                    )
                    Text(provider.displayName(), modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SettingCard(
    title: String,
    description: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun SwitchSettingCard(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(16.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
    }
}

@Composable
private fun DynamicColorSettingCard(
    useDynamicColor: Boolean,
    selectedPalette: PresetPalette,
    onDynamicColorChange: (Boolean) -> Unit,
    onPresetPalette: (PresetPalette) -> Unit,
) {
    val previews = remember {
        PresetPalette.entries.associateWith { palette ->
            presetColorScheme(palette, darkTheme = false).let { it.primary to it.onPrimary }
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.dynamic_color), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (useDynamicColor) {
                            stringResource(R.string.dynamic_color_wallpaper)
                        } else {
                            stringResource(R.string.dynamic_color_preset)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Switch(checked = useDynamicColor, onCheckedChange = onDynamicColorChange)
            }
            AnimatedVisibility(visible = !useDynamicColor) {
                Column {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        stringResource(R.string.preset_colors),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        PresetPalette.entries.forEach { palette ->
                            val selected = palette == selectedPalette
                            val (accent, onAccent) = previews.getValue(palette)
                            val paletteDescription = stringResource(R.string.palette_accessibility, palette.displayName())
                            Surface(
                                modifier = Modifier
                                    .size(48.dp)
                                    .selectable(
                                        selected = selected,
                                        role = Role.RadioButton,
                                        onClick = { onPresetPalette(palette) },
                                    )
                                    .semantics {
                                        contentDescription = paletteDescription
                                    },
                                shape = CircleShape,
                                color = accent,
                                border = BorderStroke(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                ),
                            ) {
                                if (selected) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(22.dp),
                                            tint = onAccent,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


private val LyricsProviderItemHeight = 52.dp

private val LyricsProviderItemSpacing = 8.dp

@Composable
private fun SettingsHeader(title: String, canGoBack: Boolean, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (canGoBack) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back_to_settings))
            }
        }
        Text(title, style = MaterialTheme.typography.headlineLarge)
    }
}

private fun LazyListScope.settingsCategoryItems(onNavigate: (AppDestination) -> Unit) {
    item(key = "appearance", contentType = "category") {
        SettingsLink(
            titleRes = R.string.settings_appearance,
            descriptionRes = R.string.settings_appearance_description,
            testKey = "appearance",
            icon = Icons.Rounded.Palette,
        ) {
            onNavigate(AppDestination.SETTINGS_APPEARANCE)
        }
    }
    item(key = "lyrics", contentType = "category") {
        SettingsLink(
            titleRes = R.string.settings_lyrics,
            descriptionRes = R.string.settings_lyrics_description,
            testKey = "lyrics",
            icon = Icons.Rounded.MusicNote,
        ) {
            onNavigate(AppDestination.SETTINGS_LYRICS)
        }
    }
    item(key = "network", contentType = "category") {
        SettingsLink(
            titleRes = R.string.settings_network,
            descriptionRes = R.string.settings_network_description,
            testKey = "network",
            icon = Icons.Rounded.Language,
        ) {
            onNavigate(AppDestination.SETTINGS_NETWORK)
        }
    }
    item(key = "about", contentType = "category") {
        SettingsLink(
            titleRes = R.string.settings_about,
            descriptionRes = R.string.settings_about_description,
            testKey = "about",
            icon = Icons.Rounded.Info,
        ) {
            onNavigate(AppDestination.SETTINGS_ABOUT)
        }
    }
}

@Composable
private fun SettingsLink(
    @androidx.annotation.StringRes titleRes: Int,
    @androidx.annotation.StringRes descriptionRes: Int,
    testKey: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val title = stringResource(titleRes)
    val description = stringResource(descriptionRes)
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("settings-link-$testKey"),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
        }
    }
}

private fun LazyListScope.aboutSettingsItems() {
    item(key = "app", contentType = "about") {
        val context = LocalContext.current
        val packageInfo = remember(context) { context.packageManager.getPackageInfo(context.packageName, 0) }
        val icon = remember(context) { context.packageManager.getApplicationIcon(context.packageName).toBitmap().asImageBitmap() }
        SettingCard(stringResource(R.string.app_name), stringResource(R.string.version_info, packageInfo.versionName.orEmpty(), packageInfo.longVersionCode)) {
            Image(icon, stringResource(R.string.app_icon), Modifier.size(72.dp))
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.about_description))
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.mit_license), style = MaterialTheme.typography.bodySmall)
        }
    }
    val links = listOf(
        Triple(R.string.project_homepage, "project-homepage", ""),
        Triple(R.string.releases, "releases", "/releases"),
        Triple(R.string.issue_tracker, "issue-tracker", "/issues"),
        Triple(R.string.privacy_statement, "privacy-statement", "/blob/main/PRIVACY.md"),
        Triple(R.string.open_source_license, "open-source-license", "/blob/main/LICENSE"),
        Triple(R.string.third_party_notices, "third-party-notices", "/blob/main/THIRD_PARTY_NOTICES.md"),
    )
    links.forEach { (label, testKey, path) ->
        item(key = label, contentType = "link") {
            val context = LocalContext.current
            val noBrowserAvailable = stringResource(R.string.no_browser_available)
            SettingsLink(label, R.string.open_in_browser, testKey, Icons.Rounded.Info) {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/bibibivlin/LsMusic$path".toUri()))
                } catch (_: android.content.ActivityNotFoundException) {
                    Toast.makeText(context, noBrowserAvailable, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@Composable
internal fun ExitProgressDialog(status: ExitStatus, error: UiText?, onRetry: () -> Unit) {
    if (status == ExitStatus.IDLE || status == ExitStatus.COMPLETE) return
    val resources = LocalResources.current
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(if (status == ExitStatus.SAVE_FAILED) stringResource(R.string.exit_save_failed_title) else stringResource(R.string.exiting)) },
        text = {
            if (status == ExitStatus.SAVE_FAILED) {
                Text((error ?: UiText.Resource(R.string.exit_save_failed_body)).resolve(resources))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(R.string.exit_stopping_body))
                }
            }
        },
        confirmButton = {
            if (status == ExitStatus.SAVE_FAILED) {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.retry_save_exit)) }
            }
        },
    )
}
