package com.linxyi.lsmusic.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

internal val LocalContentTopInset = staticCompositionLocalOf { 0.dp }
internal val LocalTopEdgeVisibility = staticCompositionLocalOf<State<Float>?> { null }
private val LocalTopEdgeProtection = staticCompositionLocalOf<TopEdgeProtectionState?> { null }
private val TopEdgeFadeHeight = 64.dp
private val CoverStatusFadeHeight = 24.dp
private val TopEdgeBlurRadius = 12.dp
private const val TopEdgeBlurSteps = 4
private const val TopEdgeStrengthSteps = 64

private class TopEdgeSource(val strength: (Float, Float, Float) -> Float) {
    val bounds = mutableStateOf(Rect.Zero)
}

private class TopEdgeProtectionState {
    val sources = mutableStateMapOf<Any, TopEdgeSource>()
    val coverFlights = mutableStateMapOf<Any, State<Boolean>>()
    val origin = mutableStateOf(Offset.Zero)
}

internal data class TopEdgeRegion(
    val left: Float,
    val right: Float,
    val strength: Float,
    val coverStrength: Float = 0f,
)

/** Adjacent blur radii are mixed, so sharp content never shows through a much blurrier copy. */
internal fun topEdgeBlurLayerWeight(level: Int, strength: Float, progress: Float): Float {
    val radius = TopEdgeBlurSteps * strength.coerceIn(0f, 1f) * (1f - progress.coerceIn(0f, 1f))
    return topEdgeBlurWeightForRadius(level, radius)
}

private fun topEdgeBlurWeightForRadius(level: Int, radius: Float): Float =
    (1f - abs(radius - level)).coerceIn(0f, 1f)

internal fun topEdgeVisualBlurStrength(
    strength: Float,
    coverStrength: Float,
    progress: Float,
    coverFraction: Float,
): Float {
    val position = progress.coerceIn(0f, 1f)
    val coverFade = coverFraction.coerceIn(.001f, 1f)
    return maxOf(
        strength.coerceIn(0f, 1f) * (1f - position),
        coverStrength.coerceIn(0f, 1f) * (1f - position / coverFade).coerceAtLeast(0f),
    )
}

private data class TopEdgeMasks(val levels: List<Brush>, val scrim: Brush)

private class TopEdgeMaskCache(private val background: Color, private val scrimAlpha: Float) {
    private var previousSafeTop = -1f
    private var previousFadeHeight = -1f
    private var previousCoverFadeHeight = -1f
    private val masks = mutableMapOf<Int, TopEdgeMasks>()

    fun masks(
        safeTop: Float,
        fadeHeight: Float,
        coverFadeHeight: Float,
        strength: Float,
        coverStrength: Float,
    ): TopEdgeMasks {
        if (previousSafeTop != safeTop || previousFadeHeight != fadeHeight || previousCoverFadeHeight != coverFadeHeight) {
            masks.clear()
            previousSafeTop = safeTop
            previousFadeHeight = fadeHeight
            previousCoverFadeHeight = coverFadeHeight
        }
        val strengthStep = (strength * TopEdgeStrengthSteps).roundToInt().coerceIn(0, TopEdgeStrengthSteps)
        val coverStep = (coverStrength * TopEdgeStrengthSteps).roundToInt().coerceIn(0, TopEdgeStrengthSteps)
        val key = strengthStep * (TopEdgeStrengthSteps + 1) + coverStep
        return masks.getOrPut(key) {
            val effectiveStrength = strengthStep.toFloat() / TopEdgeStrengthSteps
            val effectiveCover = coverStep.toFloat() / TopEdgeStrengthSteps
            val coverFraction = (coverFadeHeight / fadeHeight).coerceIn(.001f, 1f)
            fun radiusAt(progress: Float): Float = TopEdgeBlurSteps *
                topEdgeVisualBlurStrength(effectiveStrength, effectiveCover, progress, coverFraction)
            val fullHeight = safeTop + fadeHeight
            val crossing = if (effectiveCover > effectiveStrength && coverFraction < 1f) {
                (effectiveCover - effectiveStrength) / (effectiveCover / coverFraction - effectiveStrength)
            } else 0f
            val segments = buildList {
                add(0f)
                if (crossing > 0f && crossing < 1f) add(crossing)
                add(1f)
            }
            val stops = buildList {
                add(0f)
                add(safeTop / fullHeight)
                segments.zipWithNext().forEach { (start, end) ->
                    val startRadius = radiusAt(start)
                    val endRadius = radiusAt(end)
                    for (level in TopEdgeBlurSteps - 1 downTo 1) {
                        if (level < startRadius && level > endRadius) {
                            val progress = start + (end - start) * (startRadius - level) / (startRadius - endRadius)
                            add((safeTop + fadeHeight * progress) / fullHeight)
                        }
                    }
                    add((safeTop + fadeHeight * end) / fullHeight)
                }
            }
            val levels = (0..TopEdgeBlurSteps).map { level ->
                val colors = stops.map { stop ->
                    val progress = ((stop * fullHeight - safeTop) / fadeHeight).coerceIn(0f, 1f)
                    stop to Color.White.copy(alpha = topEdgeBlurWeightForRadius(level, radiusAt(progress)))
                }.toTypedArray()
                Brush.verticalGradient(*colors, startY = 0f, endY = fullHeight)
            }
            val tint = stops.map { stop ->
                val progress = ((stop * fullHeight - safeTop) / fadeHeight).coerceIn(0f, 1f)
                stop to background.copy(alpha = scrimAlpha * radiusAt(progress) / TopEdgeBlurSteps)
            }.toTypedArray()
            TopEdgeMasks(levels, Brush.verticalGradient(*tint, startY = 0f, endY = fullHeight))
        }
    }
}

/** Overlapping pages must not apply the same tint twice during a navigation transition. */
internal fun mergeTopEdgeRegions(regions: List<TopEdgeRegion>): List<TopEdgeRegion> {
    val boundaries = regions.flatMap { listOf(it.left, it.right) }.distinct().sorted()
    return boundaries.zipWithNext().mapNotNull { (left, right) ->
        val overlaps = regions.asSequence()
            .filter { it.left < right && it.right > left }
            .toList()
        if (overlaps.isEmpty()) return@mapNotNull null
        TopEdgeRegion(left, right, overlaps.maxOf { it.strength }, overlaps.maxOf { it.coverStrength })
    }
}

/** The flying artwork is drawn outside either page and can still cross the system status bar. */
@Composable
internal fun ProtectTopEdgeDuringCoverFlight(active: Boolean) {
    val protection = LocalTopEdgeProtection.current ?: return
    val current = rememberUpdatedState(active)
    DisposableEffect(protection, current) {
        protection.coverFlights[current] = current
        onDispose { protection.coverFlights.remove(current) }
    }
}

/** The input is the first visible item's top in the protection layer's coordinates. */
internal fun topEdgeProtectionStrength(itemTop: Float, safeTop: Float, fadeHeight: Float): Float {
    if (safeTop <= 0f || fadeHeight <= 0f) return 0f
    return ((safeTop + fadeHeight - itemTop) / fadeHeight).coerceIn(0f, 1f)
}

internal fun topEdgeListStrength(
    itemTop: Float,
    safeTop: Float,
    fadeHeight: Float,
    firstIndex: Int,
    scrollOffset: Int,
): Float {
    val strength = topEdgeProtectionStrength(itemTop, safeTop, fadeHeight)
    if (firstIndex != 0) return strength
    if (scrollOffset <= 0) return 0f
    val initialGap = (itemTop + scrollOffset - safeTop).coerceAtLeast(1f)
    return minOf(strength, (scrollOffset / initialGap).coerceIn(0f, 1f))
}

internal fun useDarkStatusBarIcons(background: Color): Boolean = background.luminance() >= 0.179f

/** Pick enough tint to keep either system icon color legible over even black or white artwork. */
internal fun topEdgeScrimAlpha(background: Color, darkIcons: Boolean): Float {
    val worstBackground = if (darkIcons) Color.Black else Color.White
    fun contrast(alpha: Float): Float {
        val luminance = background.copy(alpha = alpha).compositeOver(worstBackground).luminance()
        return if (darkIcons) (luminance + .05f) / .05f else 1.05f / (luminance + .05f)
    }
    var low = 0f
    var high = 1f
    repeat(16) {
        val middle = (low + high) / 2f
        if (contrast(middle) >= 4.5f) high = middle else low = middle
    }
    return (high + .03f).coerceAtMost(1f)
}

/**
 * Capture only the top strip into hardware layers, and replace that part of the original drawing.
 * The display list is replayed without composing or measuring the scrollable content again.
 */
@Composable
internal fun TopEdgeProtection(
    topInset: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state = remember { TopEdgeProtectionState() }
    val captured = rememberGraphicsLayer()
    val blurLayers = listOf(
        rememberGraphicsLayer(),
        rememberGraphicsLayer(),
        rememberGraphicsLayer(),
        rememberGraphicsLayer(),
    )
    val layerPaint = remember { Paint() }
    val addPaint = remember { Paint().apply { blendMode = BlendMode.Plus } }
    val background = MaterialTheme.colorScheme.background
    val scrimAlpha = remember(background) { topEdgeScrimAlpha(background, useDarkStatusBarIcons(background)) }
    val maskCache = remember(background, scrimAlpha) { TopEdgeMaskCache(background, scrimAlpha) }
    val blurRadius = with(LocalDensity.current) { TopEdgeBlurRadius.toPx() }
    val blurEffects = remember(blurRadius) {
        (1..TopEdgeBlurSteps).map { level ->
            val radius = blurRadius * level / TopEdgeBlurSteps
            BlurEffect(null, radius, radius)
        }
    }

    CompositionLocalProvider(LocalTopEdgeProtection provides state, LocalContentTopInset provides topInset) {
        Box(
            modifier.fillMaxSize()
                .onGloballyPositioned { state.origin.value = it.positionInRoot() }
                .drawWithContent {
                    val safeTop = topInset.toPx()
                    val fadeHeight = TopEdgeFadeHeight.toPx()
                    val coverFadeHeight = CoverStatusFadeHeight.toPx().coerceAtMost(fadeHeight)
                    if (safeTop <= 0f) {
                        drawContent()
                        return@drawWithContent
                    }
                    val sources = state.sources.values.mapNotNull { source ->
                        val bounds = source.bounds.value.translate(-state.origin.value)
                        val strength = source.strength(safeTop, fadeHeight, bounds.top)
                        val left = bounds.left.coerceIn(0f, size.width)
                        val right = bounds.right.coerceIn(0f, size.width)
                        if (strength > 0f && right > left) TopEdgeRegion(left, right, strength) else null
                    }
                    val coverFlight = state.coverFlights.values.any { it.value }
                    val regions = mergeTopEdgeRegions(
                        if (coverFlight) sources + TopEdgeRegion(0f, size.width, 0f, coverStrength = 1f) else sources,
                    )
                    if (regions.isEmpty()) {
                        drawContent()
                        return@drawWithContent
                    }
                    val activeFadeHeight = if (regions.any { it.strength > 0f }) fadeHeight else coverFadeHeight
                    val stripHeight = (safeTop + activeFadeHeight).coerceAtMost(size.height)
                    if (stripHeight <= 0f || size.width <= 0f) {
                        drawContent()
                        return@drawWithContent
                    }
                    val captureHeight = (stripHeight + blurRadius * 2f).coerceAtMost(size.height)
                    val captureSize = IntSize(ceil(size.width).toInt(), ceil(captureHeight).toInt())
                    captured.record(size = captureSize) {
                        this@drawWithContent.drawContent()
                    }
                    val maximumLevel = ceil(regions.maxOf { maxOf(it.strength, it.coverStrength) } * TopEdgeBlurSteps).toInt()
                        .coerceIn(0, TopEdgeBlurSteps)
                    repeat(maximumLevel) { index ->
                        blurLayers[index].renderEffect = blurEffects[index]
                        blurLayers[index].record(size = captureSize) { drawLayer(captured) }
                    }

                    // Do not draw the sharp original underneath translucent text or artwork.
                    val canvas = drawContext.canvas
                    canvas.save()
                    regions.forEach { region ->
                        canvas.clipRect(region.left, 0f, region.right, stripHeight, ClipOp.Difference)
                    }
                    drawContent()
                    canvas.restore()

                    regions.forEach { region ->
                        clipRect(left = region.left, right = region.right, bottom = stripHeight) {
                            val bounds = Rect(region.left, 0f, region.right, stripHeight)
                            val masks = maskCache.masks(
                                safeTop, fadeHeight, coverFadeHeight, region.strength, region.coverStrength,
                            )
                            val regionLevel = ceil(maxOf(region.strength, region.coverStrength) * TopEdgeBlurSteps).toInt()
                                .coerceIn(0, maximumLevel)
                            canvas.saveLayer(bounds, layerPaint)
                            for (level in 0..regionLevel) {
                                // Add premultiplied neighboring radii rather than stacking them:
                                // transparency then behaves the same for text and opaque covers.
                                canvas.saveLayer(bounds, addPaint)
                                drawLayer(if (level == 0) captured else blurLayers[level - 1])
                                drawRect(masks.levels[level], size = Size(size.width, stripHeight), blendMode = BlendMode.DstIn)
                                canvas.restore()
                            }
                            canvas.restore()
                            drawRect(brush = masks.scrim, size = Size(size.width, stripHeight))
                        }
                    }
                },
        ) { content() }
    }
}

@Composable
private fun Modifier.topEdgeSource(strength: (Float, Float, Float) -> Float): Modifier = composed {
    val protection = LocalTopEdgeProtection.current ?: return@composed this
    val currentStrength = rememberUpdatedState(strength)
    val currentVisibility = rememberUpdatedState(LocalTopEdgeVisibility.current)
    val source = remember(protection) {
        TopEdgeSource { safeTop, fadeHeight, top ->
            currentStrength.value(safeTop, fadeHeight, top) * (currentVisibility.value?.value ?: 1f)
        }
    }
    DisposableEffect(protection, source) {
        protection.sources[source] = source
        onDispose { protection.sources.remove(source) }
    }
    onGloballyPositioned { coordinates ->
        val position = coordinates.positionInRoot()
        source.bounds.value = Rect(position, Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()))
    }
}

@Composable
internal fun Modifier.protectTopEdge(state: LazyGridState): Modifier = topEdgeSource { safeTop, fadeHeight, top ->
    val info = state.layoutInfo
    val first = info.visibleItemsInfo.minOfOrNull { it.offset.y }
    if (first == null) {
        if (state.firstVisibleItemIndex > 0) 1f else (state.firstVisibleItemScrollOffset / fadeHeight).coerceIn(0f, 1f)
    } else {
        topEdgeListStrength(
            top + first + info.beforeContentPadding, safeTop, fadeHeight,
            state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset,
        )
    }
}

@Composable
internal fun Modifier.protectTopEdge(state: LazyListState): Modifier = topEdgeSource { safeTop, fadeHeight, top ->
    val info = state.layoutInfo
    val first = info.visibleItemsInfo.minOfOrNull { it.offset }
    if (first == null) {
        if (state.firstVisibleItemIndex > 0) 1f else (state.firstVisibleItemScrollOffset / fadeHeight).coerceIn(0f, 1f)
    } else {
        topEdgeListStrength(
            top + first + info.beforeContentPadding, safeTop, fadeHeight,
            state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset,
        )
    }
}

@Composable
internal fun Modifier.protectTopEdge(state: ScrollState, initialTopPadding: Dp): Modifier {
    val initialPaddingPx = with(LocalDensity.current) { initialTopPadding.toPx() }
    return topEdgeSource { safeTop, fadeHeight, top ->
        topEdgeListStrength(top + safeTop + initialPaddingPx - state.value, safeTop, fadeHeight, 0, state.value)
    }
}
