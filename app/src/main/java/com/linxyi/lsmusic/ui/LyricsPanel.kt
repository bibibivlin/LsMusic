package com.linxyi.lsmusic.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linxyi.lsmusic.lyrics.LyricsDocument
import com.linxyi.lsmusic.lyrics.LyricsLine
import com.linxyi.lsmusic.lyrics.LyricsLoadState
import com.linxyi.lsmusic.lyrics.LyricsTranslationMode
import com.linxyi.lsmusic.lyrics.activeLyricsLineIndex
import com.linxyi.lsmusic.lyrics.displayTexts
import com.linxyi.lsmusic.lyrics.interpolatedLyricsPosition
import com.linxyi.lsmusic.lyrics.wordSweepProgress
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun LyricsPanel(
    loadState: LyricsLoadState,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    translationMode: LyricsTranslationMode,
    sourceVisible: Boolean,
    effectsEnabled: Boolean,
    fontSizeSp: Int,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.clickable(onClickLabel = "关闭歌词") { onClose() }) {
        when (loadState) {
            LyricsLoadState.Idle,
            LyricsLoadState.Loading -> LyricsMessage {
                CircularProgressIndicator()
                Spacer(Modifier.height(14.dp))
                Text("正在查找在线歌词")
            }
            LyricsLoadState.NotFound -> LyricsMessage {
                Text(
                    "无歌词",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            is LyricsLoadState.Error -> LyricsMessage {
                Text("歌词加载失败", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text(
                    "请检查网络后重试",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onRetry,
                ) { Text("点击重试") }
            }
            is LyricsLoadState.Loaded -> LoadedLyrics(
                document = loadState.document,
                positionMs = positionMs,
                durationMs = durationMs,
                isPlaying = isPlaying,
                translationMode = translationMode,
                sourceVisible = sourceVisible,
                effectsEnabled = effectsEnabled,
                fontSizeSp = fontSizeSp,
            )
        }
    }
}

@Composable
private fun LyricsMessage(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content,
    )
}

@Composable
private fun LoadedLyrics(
    document: LyricsDocument,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    translationMode: LyricsTranslationMode,
    sourceVisible: Boolean,
    effectsEnabled: Boolean,
    fontSizeSp: Int,
) {
    val lines = document.lines
    val interpolatedPosition by interpolatedPlaybackPosition(positionMs, durationMs, isPlaying)
    val activeIndex = activeLyricsLineIndex(lines, interpolatedPosition)
    val listState = rememberLazyListState()
    val isUserDragging by listState.interactionSource.collectIsDraggedAsState()
    var manuallyBrowsing by remember { mutableStateOf(false) }
    var suppressDistanceEffects by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val estimatedLineSizePx = remember(fontSizeSp, density) {
        with(density) { (fontSizeSp * ESTIMATED_LINE_HEIGHT_MULTIPLIER).sp.toPx().roundToInt() }
    }
    val distanceEffectsVisible = effectsEnabled && !suppressDistanceEffects
    val edgeFadeAmount by animateFloatAsState(
        targetValue = if (distanceEffectsVisible) 1f else 0f,
        animationSpec = tween(EFFECT_TRANSITION_DURATION_MS, easing = FastOutSlowInEasing),
        label = "歌词边缘渐隐",
    )

    LaunchedEffect(isUserDragging) {
        if (isUserDragging) {
            manuallyBrowsing = true
            suppressDistanceEffects = true
        } else if (manuallyBrowsing) {
            delay(MANUAL_SCROLL_RESUME_DELAY_MS)
            manuallyBrowsing = false
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val verticalContentPadding = if (document.isSynced) maxOf(96.dp, maxHeight / 2) else 96.dp
        LaunchedEffect(activeIndex, manuallyBrowsing, verticalContentPadding, estimatedLineSizePx) {
            if (manuallyBrowsing) return@LaunchedEffect
            if (activeIndex >= 0) centerLyricsLine(listState, activeIndex, estimatedLineSizePx)
            suppressDistanceEffects = false
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to androidx.compose.ui.graphics.Color.Black.copy(alpha = 1f - edgeFadeAmount),
                            .12f to androidx.compose.ui.graphics.Color.Black,
                            .84f to androidx.compose.ui.graphics.Color.Black,
                            1f to androidx.compose.ui.graphics.Color.Black.copy(alpha = 1f - edgeFadeAmount),
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = verticalContentPadding),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            itemsIndexed(
                items = lines,
                key = { _, line -> line.stableId },
                contentType = { _, line -> if (line.translation == null) "original" else "translated" },
            ) { index, line ->
                val distance = if (activeIndex < 0) 0 else abs(index - activeIndex)
                val active = index == activeIndex
                val blur by animateDpAsState(
                    targetValue = if (distanceEffectsVisible && !active) {
                        (distance * 1.8f).coerceAtMost(8f).dp
                    } else {
                        0.dp
                    },
                    animationSpec = tween(EFFECT_TRANSITION_DURATION_MS, easing = FastOutSlowInEasing),
                    label = "歌词模糊",
                )
                val itemAlpha by animateFloatAsState(
                    targetValue = if (!distanceEffectsVisible || active) {
                        1f
                    } else {
                        (1f - distance * .11f).coerceAtLeast(.35f)
                    },
                    animationSpec = tween(EFFECT_TRANSITION_DURATION_MS, easing = FastOutSlowInEasing),
                    label = "歌词透明度",
                )
                val scale by animateFloatAsState(
                    targetValue = if (!distanceEffectsVisible || active) {
                        1f
                    } else {
                        (1f - distance * .015f).coerceAtLeast(.92f)
                    },
                    animationSpec = tween(EFFECT_TRANSITION_DURATION_MS, easing = FastOutSlowInEasing),
                    label = "歌词缩放",
                )
                val stagger by animateFloatAsState(
                    targetValue = if (distanceEffectsVisible && !active) {
                        lyricsStaggerOffsetDp(distance)
                    } else {
                        0f
                    },
                    animationSpec = tween(EFFECT_TRANSITION_DURATION_MS, easing = FastOutSlowInEasing),
                    label = "歌词错峰",
                )
                LyricsLineContent(
                    line = line,
                    active = active,
                    positionMs = interpolatedPosition,
                    translationMode = translationMode,
                    effectsEnabled = effectsEnabled,
                    fontSizeSp = fontSizeSp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = itemAlpha
                            scaleX = scale
                            scaleY = scale
                            translationX = stagger.dp.toPx()
                        }
                        .blur(blur, BlurredEdgeTreatment.Unbounded),
                )
            }
        }
        if (sourceVisible) {
            Text(
                "歌词来源：${document.provider.label}",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .72f),
            )
        }
    }
}

internal fun lyricsStaggerOffsetDp(distance: Int): Float =
    (distance.coerceAtLeast(0) * LYRICS_STAGGER_STEP_DP).coerceAtMost(LYRICS_STAGGER_MAX_DP)

private suspend fun centerLyricsLine(
    listState: LazyListState,
    index: Int,
    estimatedLineSizePx: Int,
) {
    snapshotFlow { listState.layoutInfo.viewportSize.height }.first { it > 0 }
    if (listState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
        listState.animateScrollToItem(index, estimatedLineSizePx / 2)
    }
    val layoutInfo = listState.layoutInfo
    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
    val itemCenter = item.offset + item.size / 2f
    val adjustment = itemCenter - viewportCenter
    if (abs(adjustment) > 1f) {
        listState.animateScrollBy(
            value = adjustment,
            animationSpec = tween(LINE_SCROLL_DURATION_MS, easing = FastOutSlowInEasing),
        )
    }
}

@Composable
private fun LyricsLineContent(
    line: LyricsLine,
    active: Boolean,
    positionMs: Long,
    translationMode: LyricsTranslationMode,
    effectsEnabled: Boolean,
    fontSizeSp: Int,
    modifier: Modifier,
) {
    val (primaryText, secondaryText) = line.displayTexts(translationMode)
    val emphasis by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = if (effectsEnabled) {
            tween(ACTIVE_LINE_TRANSITION_DURATION_MS, easing = FastOutSlowInEasing)
        } else {
            snap()
        },
        label = "当前歌词强调",
    )
    val sweepProgress = if (active && effectsEnabled && translationMode != LyricsTranslationMode.CHINESE_ONLY) {
        wordSweepProgress(line, positionMs)
    } else if (active) {
        1f
    } else {
        0f
    }
    val baseColor = lerp(
        MaterialTheme.colorScheme.onSurface.copy(alpha = .56f),
        MaterialTheme.colorScheme.onSurface.copy(alpha = .30f),
        emphasis,
    )
    val secondaryColor = lerp(
        MaterialTheme.colorScheme.onSurfaceVariant,
        MaterialTheme.colorScheme.onSurface,
        emphasis,
    )
    Column(modifier) {
        Box(Modifier.fillMaxWidth()) {
            Text(
                primaryText,
                fontSize = fontSizeSp.sp,
                lineHeight = (fontSizeSp * 1.18f).sp,
                fontWeight = FontWeight.SemiBold,
                color = baseColor,
            )
            Text(
                primaryText,
                modifier = Modifier
                    .graphicsLayer { alpha = emphasis }
                    .drawWithContent {
                        val visibleProgress = if (active) sweepProgress else 1f
                        clipRect(right = size.width * visibleProgress) { this@drawWithContent.drawContent() }
                    },
                fontSize = fontSizeSp.sp,
                lineHeight = (fontSizeSp * 1.18f).sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (secondaryText != null) {
            Spacer(Modifier.height(5.dp))
            Text(
                secondaryText,
                fontSize = (fontSizeSp * .62f).sp,
                lineHeight = (fontSizeSp * .78f).sp,
                color = secondaryColor,
            )
        }
    }
}

@Composable
private fun interpolatedPlaybackPosition(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
) = produceState(positionMs, positionMs, durationMs, isPlaying) {
    value = positionMs
    if (!isPlaying) return@produceState
    var anchorNanos = 0L
    while (isActive) {
        withFrameNanos { frameNanos ->
            if (anchorNanos == 0L) anchorNanos = frameNanos
            val elapsedMs = (frameNanos - anchorNanos) / 1_000_000L
            value = interpolatedLyricsPosition(positionMs, elapsedMs, true, durationMs)
        }
    }
}

private const val MANUAL_SCROLL_RESUME_DELAY_MS = 3_000L
private const val LINE_SCROLL_DURATION_MS = 520
private const val EFFECT_TRANSITION_DURATION_MS = 360
private const val ACTIVE_LINE_TRANSITION_DURATION_MS = 480
private const val ESTIMATED_LINE_HEIGHT_MULTIPLIER = 1.5f
private const val LYRICS_STAGGER_STEP_DP = 4f
private const val LYRICS_STAGGER_MAX_DP = 12f
