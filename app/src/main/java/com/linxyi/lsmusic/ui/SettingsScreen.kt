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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.DialogProperties

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
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("settings-list"),
        contentPadding = PaddingValues(20.dp, 24.dp, 20.dp, bottomContentPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "header", contentType = "header") {
            SettingsHeader(state.destination.settingsTitle, state.destination.settingsParent != null) {
                state.destination.settingsParent?.let(onNavigate)
            }
        }
        if (state.destination == AppDestination.SETTINGS) {
            item {
                SettingCard(
                    title = "媒体库与播放设备",
                    description = if (state.isSearching) "正在扫描局域网内的 DLNA 设备…" else "选择音乐来源和播放目标。",
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
                        Text("扫描局域网设备")
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
                    Text("退出")
                }
            }
        }
        if (state.destination == AppDestination.SETTINGS_APPEARANCE) {
            item {
                SettingCard(
                    title = "封面大小",
                    description = "画廊会根据屏幕可用宽度自动增加列数。",
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GallerySize.entries.forEach { size ->
                            FilterChip(
                                selected = preferences.gallerySize == size,
                                onClick = { onGallerySize(size) },
                                label = { Text(size.label) },
                            )
                        }
                    }
                }
            }
            item {
                SwitchSettingCard(
                    title = "默认媒体库布局",
                    description = if (preferences.useGridByDefault) "优先使用封面画廊。" else "优先使用紧凑列表。",
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
                    title = "颜色模式",
                    description = "选择应用的明暗外观。",
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = preferences.themeMode == mode,
                                onClick = { onThemeMode(mode) },
                                label = { Text(mode.label) },
                            )
                        }
                    }
                }
            }
        }
        if (state.destination == AppDestination.SETTINGS_LYRICS) {
            item {
                SwitchSettingCard(
                    title = "在线获取歌词",
                    description = if (preferences.lyricsEnabled) {
                        "打开播放页歌词面板时，从在线来源查找歌词。"
                    } else {
                        "关闭后播放页不会显示歌词入口，也不会读取歌词缓存或访问歌词服务。"
                    },
                    checked = preferences.lyricsEnabled,
                    onCheckedChange = onLyricsEnabled,
                )
            }
            item {
                SettingCard(
                    title = "歌词来源优先级",
                    description = "按顺序查找网易云音乐和 QQ 音乐。长按拖动手柄调整优先级。",
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
                    title = "歌词翻译",
                    description = "仅中文在没有译文时自动显示原文。",
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
                                label = { Text(mode.label) },
                            )
                        }
                    }
                }
            }
            item {
                SwitchSettingCard(
                    title = "显示歌词来源",
                    description = "在歌词区域底部显示当前使用的在线来源。",
                    checked = preferences.lyricsSourceVisible,
                    enabled = preferences.lyricsEnabled,
                    onCheckedChange = onLyricsSourceVisible,
                )
            }
            item {
                SwitchSettingCard(
                    title = "歌词特效",
                    description = "启用模糊渐变、缩放、错峰位移和逐字扫光；关闭可降低图形负载。",
                    checked = preferences.lyricsEffectsEnabled,
                    enabled = preferences.lyricsEnabled,
                    onCheckedChange = onLyricsEffectsEnabled,
                )
            }
            item {
                SettingCard(
                    title = "歌词字体大小",
                    description = "当前 ${preferences.lyricsFontSizeSp}sp，可在 18–40sp 间调整。",
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
                    title = "歌词缓存",
                    description = "网络歌词缓存在可被系统回收的应用缓存目录中，成功结果保留 30 天。",
                ) {
                    Text(
                        "当前占用：${formatByteSize(state.lyricsCacheBytes)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onClearLyricsCache,
                        enabled = !state.isClearingLyricsCache,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (state.isClearingLyricsCache) "正在清除…" else "清除歌词缓存")
                    }
                }
            }
        }
        if (state.destination == AppDestination.SETTINGS_NETWORK) {
            item {
                SwitchSettingCard(
                    title = "ListenBrainz 播放记录",
                    description = if (preferences.listenBrainzEnabled) {
                        if (preferences.listenBrainzToken.isBlank()) "请填写 API 令牌后开始上报。" else "上报正在播放和满足规则的播放记录。"
                    } else {
                        "关闭时不会向 ListenBrainz 发送任何播放信息。"
                    },
                    checked = preferences.listenBrainzEnabled,
                    onCheckedChange = onListenBrainzEnabled,
                )
        }
        if (state.pendingListens.isNotEmpty()) {
            item {
                SettingCard(
                    title = "待上传记录",
                    description = "${state.pendingListens.size} 条播放记录尚未上传成功，已安全保存在本机。",
                ) {
                    FilledTonalButton(
                        onClick = onOpenPendingListens,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null)
                        Spacer(Modifier.width(8.dp))
                        Text("查看并管理")
                    }
                }
            }
        }
        item {
            SettingCard(
                title = "ListenBrainz API",
                description = "令牌仅保存在本机且不会进入系统备份。可在 ListenBrainz 账户设置中获取。",
            ) {
                OutlinedTextField(
                    value = listenBrainzTokenDraft,
                    onValueChange = { listenBrainzTokenDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("用户令牌（API Token）") },
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
                            validationAppliesToDraft -> tokenValidation.message.orEmpty()
                            normalizedTokenDraft.isEmpty() && preferences.listenBrainzToken.isNotBlank() ->
                                "保存后将清除当前令牌。"
                            normalizedTokenDraft == preferences.listenBrainzToken && normalizedTokenDraft.isNotEmpty() ->
                                "当前令牌已保存；可重新校验令牌和网络连接。"
                            normalizedTokenDraft.isNotEmpty() -> "此令牌尚未校验，校验成功后才会保存。"
                            else -> "请输入 ListenBrainz 用户令牌。"
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
                            isCheckingToken -> "正在校验…"
                            normalizedTokenDraft.isEmpty() -> "清除令牌"
                            normalizedTokenDraft == preferences.listenBrainzToken -> "重新校验令牌"
                            else -> "校验并保存"
                        },
                    )
                }
            }
        }
        item {
            SettingCard(
                title = "上传规则",
                description = "播放时长或播放百分比任一达到设定值，曲目结束后即正式记录。",
            ) {
                Text("最小播放时长：${formatRuleDuration(preferences.listenBrainzMinimumSeconds)}")
                Slider(
                    value = preferences.listenBrainzMinimumSeconds.toFloat(),
                    onValueChange = { onListenBrainzMinimumSeconds((it / 30f).roundToInt() * 30) },
                    valueRange = 30f..600f,
                    steps = 18,
                )
                Spacer(Modifier.height(8.dp))
                Text("最小播放百分比：${preferences.listenBrainzMinimumPercent}%")
                Slider(
                    value = preferences.listenBrainzMinimumPercent.toFloat(),
                    onValueChange = { onListenBrainzMinimumPercent((it / 5f).roundToInt() * 5) },
                    valueRange = 10f..100f,
                    steps = 17,
                )
                Text(
                    "当前规则：播放 ${formatRuleDuration(preferences.listenBrainzMinimumSeconds)}，或达到曲目时长的 ${preferences.listenBrainzMinimumPercent}%。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Text(
                "播放记录仅在启用 ListenBrainz 并填写令牌后上报。",
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

private fun formatRuleDuration(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return when {
        minutes == 0 -> "${remainingSeconds} 秒"
        remainingSeconds == 0 -> "${minutes} 分钟"
        else -> "${minutes} 分 ${remainingSeconds} 秒"
    }
}

private fun formatByteSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MiB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.1f KiB".format(bytes / 1024.0)
    else -> "$bytes B"
}

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
            val scale by animateFloatAsState(if (dragged) 1.035f else 1f, label = "歌词来源拖动缩放")
            val elevation by animateDpAsState(if (dragged) 4.dp else 0.dp, label = "歌词来源拖动阴影")
            val containerColor by animateColorAsState(
                if (dragged) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
                label = "歌词来源拖动颜色",
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
                        "拖动调整 ${provider.label} 优先级",
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
                    Text(provider.label, modifier = Modifier.weight(1f))
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
                    Text("动态配色", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (useDynamicColor) {
                            "使用系统从壁纸生成的配色。"
                        } else {
                            "选择 Material 3 Expressive 预设配色。"
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
                        "预设配色",
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
                            Surface(
                                modifier = Modifier
                                    .size(48.dp)
                                    .selectable(
                                        selected = selected,
                                        role = Role.RadioButton,
                                        onClick = { onPresetPalette(palette) },
                                    )
                                    .semantics {
                                        contentDescription = "${palette.label}配色"
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
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回设置")
            }
        }
        Text(title, style = MaterialTheme.typography.headlineLarge)
    }
}

private fun LazyListScope.settingsCategoryItems(onNavigate: (AppDestination) -> Unit) {
    item(key = "appearance", contentType = "category") {
        SettingsLink("界面", "封面、布局与主题配色", Icons.Rounded.Palette) {
            onNavigate(AppDestination.SETTINGS_APPEARANCE)
        }
    }
    item(key = "lyrics", contentType = "category") {
        SettingsLink("歌词", "在线来源、翻译与显示效果", Icons.Rounded.MusicNote) {
            onNavigate(AppDestination.SETTINGS_LYRICS)
        }
    }
    item(key = "network", contentType = "category") {
        SettingsLink("网络", "ListenBrainz 播放记录与上传规则", Icons.Rounded.Language) {
            onNavigate(AppDestination.SETTINGS_NETWORK)
        }
    }
    item(key = "about", contentType = "category") {
        SettingsLink("关于", "软件版本、开源项目与隐私说明", Icons.Rounded.Info) {
            onNavigate(AppDestination.SETTINGS_ABOUT)
        }
    }
}

@Composable
private fun SettingsLink(title: String, description: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("settings-link-$title"),
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
        SettingCard("L’s Music", "版本 ${packageInfo.versionName} (${packageInfo.longVersionCode})") {
            Image(icon, "应用图标", Modifier.size(72.dp))
            Spacer(Modifier.height(16.dp))
            Text("浏览家庭网络音乐库，通过 DLNA 设备或手机本机播放音乐。支持播放队列、在线同步歌词和可选的 ListenBrainz 播放记录。")
            Spacer(Modifier.height(12.dp))
            Text("MIT 开源许可证", style = MaterialTheme.typography.bodySmall)
        }
    }
    val links = listOf(
        "项目主页" to "",
        "版本发布" to "/releases",
        "问题反馈" to "/issues",
        "隐私说明" to "/blob/main/PRIVACY.md",
        "开源许可证" to "/blob/main/LICENSE",
        "第三方声明" to "/blob/main/THIRD_PARTY_NOTICES.md",
    )
    links.forEach { (label, path) ->
        item(key = label, contentType = "link") {
            val context = LocalContext.current
            SettingsLink(label, "在浏览器中打开", Icons.Rounded.Info) {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/bibibivlin/LsMusic$path".toUri()))
                } catch (_: android.content.ActivityNotFoundException) {
                    Toast.makeText(context, "没有可用于打开链接的浏览器", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@Composable
internal fun ExitProgressDialog(status: ExitStatus, error: String?, onRetry: () -> Unit) {
    if (status == ExitStatus.IDLE || status == ExitStatus.COMPLETE) return
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(if (status == ExitStatus.SAVE_FAILED) "记录尚未保存" else "正在退出…") },
        text = {
            if (status == ExitStatus.SAVE_FAILED) {
                Text(error ?: "播放已停止，无法保存播放记录，请重试。")
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(16.dp))
                    Text("正在停止播放并保存播放记录…")
                }
            }
        },
        confirmButton = {
            if (status == ExitStatus.SAVE_FAILED) {
                TextButton(onClick = onRetry) { Text("重试保存并退出") }
            }
        },
    )
}
