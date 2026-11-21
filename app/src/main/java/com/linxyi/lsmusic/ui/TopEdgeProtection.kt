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
import androidx.compose.runtime.mutableStateSetOf
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
import kotlin.math.pow
import kotlin.math.roundToInt

internal val LocalContentTopInset = staticCompositionLocalOf { 0.dp }
internal val LocalTopEdgeVisibility = staticCompositionLocalOf<State<Float>?> { null }
private val LocalTopEdgeProtection = staticCompositionLocalOf<TopEdgeProtectionState?> { null }
private val TopEdgeBlurFadeHeight = 76.dp
private val TopEdgeActivationHeight = 64.dp
private val StatusBarScrimFadeHeight = 40.dp
private val CoverStatusFadeHeight = 24.dp
private val TopEdgeBlurRadius = 12.dp
private const val TopEdgeBlurSteps = 4
private const val TopEdgeStrengthSteps = 64
private const val TopEdgeGradientSteps = 32

private class TopEdgeSource(val strength: (Float, Float, Float) -> Float) {
    val bounds = mutableStateOf(Rect.Zero)
}

private class TopEdgeProtectionState {
    val sources = mutableStateSetOf<TopEdgeSource>()
    val coverFlights = mutableStateSetOf<State<Boolean>>()
    val origin = mutableStateOf(Offset.Zero)
}

internal data class TopEdgeRegion(
    val left: Float,
    val right: Float,
    val strength: Float,
)

/** Adjacent blur radii are mixed, so sharp content never shows through a much blurrier copy. */
internal fun topEdgeBlurLayerWeight(level: Int, radius: Float): Float =
    (1f - abs(radius - level)).coerceIn(0f, 1f)

/** [progress] runs from the screen's top edge to the end of the list blur. */
internal fun topEdgeVisualBlurStrength(
    strength: Float,
    coverFlight: Boolean,
    progress: Float,
    coverFraction: Float,
): Float {
    val position = progress.coerceIn(0f, 1f)
    val remaining = 1f - position
    // Ease out to zero radius so fine detail returns gradually at the end of the strip.
    val listFade = remaining * remaining * (1f + position)
    val coverFade = if (coverFlight) (1f - position / coverFraction).coerceAtLeast(0f) else 0f
    return maxOf(strength.coerceIn(0f, 1f) * listFade, coverFade)
}

private class TopEdgeMaskCache(
    private val listBlurHeight: Float,
    coverBlurHeight: Float,
) {
    private val coverFraction = coverBlurHeight / listBlurHeight
    private val blurSegments = ((0..TopEdgeGradientSteps).map { it.toFloat() / TopEdgeGradientSteps } + coverFraction)
        .distinct().sorted().zipWithNext()
    private val scrollMasks = mutableMapOf<Int, List<Brush>>()
    private val coverFlightMasks = mutableMapOf<Int, List<Brush>>()

    fun masks(strength: Float, coverFlight: Boolean): List<Brush> {
        val strengthStep = (strength * TopEdgeStrengthSteps).roundToInt().coerceIn(0, TopEdgeStrengthSteps)
        val cache = if (coverFlight) coverFlightMasks else scrollMasks
        return cache.getOrPut(strengthStep) {
            val effectiveStrength = strengthStep.toFloat() / TopEdgeStrengthSteps
            fun radiusAt(progress: Float): Float = TopEdgeBlurSteps *
                topEdgeVisualBlurStrength(effectiveStrength, coverFlight, progress, coverFraction)

            val peakRadius = radiusAt(0f)
            val lastLevel = ceil(peakRadius).toInt()
            val stops = buildList {
                var startRadius = peakRadius
                add(0f to startRadius)
                blurSegments.forEach { (start, end) ->
                    val endRadius = radiusAt(end)
                    // Split at every radius boundary so each interval mixes at most two layers.
                    for (level in lastLevel - 1 downTo 1) {
                        if (level < startRadius && level > endRadius) {
                            val progress = start + (end - start) * (startRadius - level) / (startRadius - endRadius)
                            add(progress to level.toFloat())
                        }
                    }
                    add(end to endRadius)
                    startRadius = endRadius
                }
            }
            (0..lastLevel).map { level ->
                val colors = stops.map { (stop, radius) ->
                    stop to Color.White.copy(alpha = topEdgeBlurLayerWeight(level, radius))
                }.toTypedArray()
                Brush.verticalGradient(*colors, startY = 0f, endY = listBlurHeight)
            }
        }
    }
}

/** Overlapping pages share a single blur region during a navigation transition. */
internal fun mergeTopEdgeRegions(regions: List<TopEdgeRegion>): List<TopEdgeRegion> {
    val boundaries = regions.flatMap { listOf(it.left, it.right) }.distinct().sorted()
    return boundaries.zipWithNext().mapNotNull { (left, right) ->
        val strength = regions.asSequence()
            .filter { it.left < right && it.right > left }
            .maxOfOrNull { it.strength } ?: return@mapNotNull null
        TopEdgeRegion(left, right, strength)
    }
}

/** The flying artwork is drawn outside either page and can still cross the system status bar. */
@Composable
internal fun ProtectTopEdgeDuringCoverFlight(active: Boolean) {
    val protection = LocalTopEdgeProtection.current ?: return
    val current = rememberUpdatedState(active)
    DisposableEffect(protection, current) {
        protection.coverFlights += current
        onDispose { protection.coverFlights -= current }
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

// CIE L* scaled to 0..1. The input is relative luminance in linear light.
private fun perceivedLightness(luminance: Float): Float =
    if (luminance <= .008856f) 9.033f * luminance else 1.16f * luminance.pow(1f / 3f) - .16f

private fun topEdgeScrimBrush(background: Color, safeTop: Float, fadeHeight: Float): Brush {
    val darkIcons = useDarkStatusBarIcons(background)
    val worstBackdrop = if (darkIcons) Color.Black else Color.White
    val backdropLightness = perceivedLightness(worstBackdrop.luminance())
    fun lightnessChange(luminance: Float): Float = abs(perceivedLightness(luminance) - backdropLightness)
    fun changeAtOpacity(opacity: Float): Float =
        lightnessChange(background.copy(alpha = opacity).compositeOver(worstBackdrop).luminance())

    fun opacityForChange(change: Float): Float {
        if (change <= 0f) return 0f
        var low = 0f
        var high = 1f
        repeat(16) {
            val middle = (low + high) / 2f
            if (changeAtOpacity(middle) >= change) high = middle else low = middle
        }
        return high
    }

    // Solve for 4.5:1 contrast at the status bar bottom, with a margin for pixel rounding.
    val contrastLuminance = if (darkIcons) 4.5f * .05f - .05f else 1.05f / 4.5f - .05f
    val statusOpacity = (opacityForChange(lightnessChange(contrastLuminance)) + .03f).coerceAtMost(1f)
    val peakOpacity = (statusOpacity + .20f * safeTop / maxOf(safeTop, fadeHeight)).coerceAtMost(1f)
    val statusChange = changeAtOpacity(statusOpacity)
    val topChange = changeAtOpacity(peakOpacity) - statusChange
    val statusHeight = safeTop.coerceAtLeast(1f)
    val tailSlope = (.5f * topChange * fadeHeight / statusHeight).coerceIn(0f, 2f * statusChange)
    val totalHeight = safeTop + fadeHeight
    val stops = Array(TopEdgeGradientSteps + 1) { step ->
        val position = step.toFloat() / TopEdgeGradientSteps
        val y = position * totalHeight
        val change = if (y <= safeTop) {
            // The linear term keeps the slope from flattening before the two curves meet.
            val remaining = (safeTop - y) / statusHeight
            statusChange + topChange * remaining * (.5f + .5f * remaining)
        } else {
            // Match the slope at the status bar bottom, then ease to zero.
            val progress = ((y - safeTop) / fadeHeight).coerceIn(0f, 1f)
            val remaining = 1f - progress
            remaining * remaining * (statusChange + (2f * statusChange - tailSlope) * progress)
        }
        position to background.copy(alpha = opacityForChange(change))
    }
    return Brush.verticalGradient(*stops, startY = 0f, endY = totalHeight)
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
    val blurLayers = List(TopEdgeBlurSteps) { rememberGraphicsLayer() }
    val layerPaint = remember { Paint() }
    val addPaint = remember { Paint().apply { blendMode = BlendMode.Plus } }
    val density = LocalDensity.current
    val safeTop = with(density) { topInset.toPx() }
    val listBlurHeight = safeTop + with(density) { TopEdgeBlurFadeHeight.toPx() }
    val activationHeight = with(density) { TopEdgeActivationHeight.toPx() }
    val coverBlurHeight = safeTop + with(density) { CoverStatusFadeHeight.toPx() }
    val scrimFadeHeight = with(density) { StatusBarScrimFadeHeight.toPx() }
    val scrimHeight = safeTop + scrimFadeHeight
    val background = MaterialTheme.colorScheme.background
    val scrim = remember(safeTop, scrimFadeHeight, background) {
        topEdgeScrimBrush(background, safeTop, scrimFadeHeight)
    }
    val maskCache = remember(listBlurHeight, coverBlurHeight) {
        TopEdgeMaskCache(listBlurHeight, coverBlurHeight)
    }
    val blurRadius = with(density) { TopEdgeBlurRadius.toPx() }
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
                    if (safeTop <= 0f) {
                        drawContent()
                        return@drawWithContent
                    }
                    val sources = state.sources.mapNotNull { source ->
                        val bounds = source.bounds.value.translate(-state.origin.value)
                        val strength = source.strength(safeTop, activationHeight, bounds.top)
                        val left = bounds.left.coerceIn(0f, size.width)
                        val right = bounds.right.coerceIn(0f, size.width)
                        if (strength > 0f && right > left) TopEdgeRegion(left, right, strength) else null
                    }
                    val coverFlight = state.coverFlights.any { it.value }
                    // The flight spans the entire viewport, including gaps between scroll sources.
                    val regions = mergeTopEdgeRegions(
                        if (coverFlight) sources + TopEdgeRegion(0f, size.width, 0f) else sources,
                    )
                    if (regions.isEmpty()) {
                        drawContent()
                        return@drawWithContent
                    }
                    val activeBlurHeight = if (sources.isNotEmpty()) listBlurHeight else coverBlurHeight
                    // Include the last pixel of the fade before switching back to the original content.
                    val stripHeight = ceil(activeBlurHeight).coerceAtMost(size.height)
                    if (stripHeight <= 0f || size.width <= 0f) {
                        drawContent()
                        return@drawWithContent
                    }
                    val captureHeight = (stripHeight + blurRadius * 2f).coerceAtMost(size.height)
                    val captureSize = IntSize(ceil(size.width).toInt(), ceil(captureHeight).toInt())
                    captured.record(size = captureSize) {
                        this@drawWithContent.drawContent()
                    }
                    val regionMasks = regions.map { it to maskCache.masks(it.strength, coverFlight) }
                    repeat(regionMasks.maxOf { (_, masks) -> masks.lastIndex }) { index ->
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

                    regionMasks.forEach { (region, masks) ->
                        clipRect(left = region.left, right = region.right, bottom = stripHeight) {
                            val bounds = Rect(region.left, 0f, region.right, stripHeight)
                            canvas.saveLayer(bounds, layerPaint)
                            for (level in masks.indices) {
                                // Add premultiplied neighboring radii rather than stacking them:
                                // transparency then behaves the same for text and opaque covers.
                                canvas.saveLayer(bounds, addPaint)
                                drawLayer(if (level == 0) captured else blurLayers[level - 1])
                                // Fill the clip so a mask edge cannot leave part of the last row unmasked.
                                drawRect(masks[level], blendMode = BlendMode.DstIn)
                                canvas.restore()
                            }
                            canvas.restore()
                        }
                        clipRect(left = region.left, right = region.right) {
                            drawRect(
                                scrim,
                                size = Size(size.width, scrimHeight.coerceAtMost(size.height)),
                                alpha = if (coverFlight) 1f else region.strength,
                            )
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
        protection.sources += source
        onDispose { protection.sources -= source }
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
