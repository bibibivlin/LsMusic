@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.linxyi.lsmusic.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

internal const val COVER_OPEN_DURATION_MILLIS = 620
internal const val COVER_CLOSE_DURATION_MILLIS = 500

/** A lightly underdamped spring gives the cover one small overshoot and preserves velocity on reversal. */
internal fun <T> coverFlightSpring(durationMillis: Int, threshold: T): SpringSpec<T> {
    val speed = COVER_OPEN_DURATION_MILLIS.toFloat() / durationMillis
    return spring(dampingRatio = .86f, stiffness = 165f * speed * speed, visibilityThreshold = threshold)
}

/** The overlay must start with the size the user saw while pressing its parent. */
internal class CoverPressHandoff(private val scope: CoroutineScope) {
    private class PressCorrection {
        val scale = Animatable(1f)
        var animation: Job? = null
    }

    private val corrections = mutableStateMapOf<Any, PressCorrection>()

    fun capture(key: Any, scale: Float) {
        val correction = corrections.getOrPut(key) { PressCorrection() }
        correction.animation?.cancel()
        correction.animation = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            correction.scale.snapTo(scale)
            if (abs(scale - 1f) > .001f) {
                correction.scale.animateTo(1f, coverFlightSpring(COVER_OPEN_DURATION_MILLIS, .00005f))
            }
        }
    }

    fun scaleFor(key: Any): Float = corrections[key]?.scale?.value ?: 1f

    fun clear(key: Any) {
        corrections.remove(key)?.animation?.cancel()
    }

    fun clear() {
        corrections.values.forEach { it.animation?.cancel() }
        corrections.clear()
    }
}

private class NavigationPress {
    val scale = Animatable(1f)
    var animation: Job? = null
    var opening = false
}

/** Uses clickable's scroll cancellation, keyboard and accessibility handling. Nested buttons keep
 * their own interactions. Read scale only in the layer so a press never remeasures a lazy card. */
@Composable
internal fun Modifier.pressableNavigationClick(
    enabled: Boolean = true,
    onRelease: (Float) -> Unit = {},
    onAbandon: () -> Unit = {},
    onClick: () -> Unit,
): Modifier {
    val interactions = remember { MutableInteractionSource() }
    val press = remember { NavigationPress() }
    val scope = rememberCoroutineScope()
    val currentClick = rememberUpdatedState(onClick)
    val currentRelease = rememberUpdatedState(onRelease)
    val currentAbandon = rememberUpdatedState(onAbandon)
    val currentEnabled = rememberUpdatedState(enabled)
    LaunchedEffect(interactions, enabled) {
        if (!enabled) {
            press.animation?.cancel()
            if (!press.opening) press.scale.snapTo(1f)
            return@LaunchedEffect
        }
        if (press.opening) {
            press.opening = false
            press.scale.snapTo(1f)
        }
        interactions.interactions.collect { interaction ->
            if (!press.opening) {
                when (interaction) {
                    is PressInteraction.Press -> {
                        press.animation?.cancel()
                        press.animation = scope.launch { press.scale.animateTo(.96f, tween(90)) }
                    }
                    is PressInteraction.Release, is PressInteraction.Cancel -> {
                        press.animation?.cancel()
                        press.animation = scope.launch {
                            press.scale.animateTo(1f, spring(dampingRatio = .8f, stiffness = 650f))
                        }
                    }
                }
            }
        }
    }
    return graphicsLayer {
        scaleX = press.scale.value
        scaleY = scaleX
    }.clickable(interactionSource = interactions, indication = null, enabled = enabled) {
        if (!press.opening) {
            press.opening = true
            press.animation?.cancel()
            currentRelease.value(press.scale.value)
            currentClick.value()
            press.animation = scope.launch {
                // If navigation cannot proceed, release the card instead of leaving it pressed.
                delay(350)
                if (currentEnabled.value && press.opening) {
                    press.opening = false
                    currentAbandon.value()
                    press.scale.animateTo(1f, spring(dampingRatio = .8f, stiffness = 650f))
                }
            }
        }
    }
}

private class CoverMeasureState(var lookaheadConstraints: Constraints? = null)

/** Scale the already measured image, rather than remeasuring its painter on every flight frame.
 * Keep the clip OUTSIDE that scaling layer, in local animated bounds. An overlay-space path can
 * lag a lazy page's placement/scroll offsets, and scaling a fixed clip inflates its corners. */
@Composable
internal fun Modifier.sharedCoverArtwork(
    sharedScope: SharedTransitionScope,
    visibilityScope: AnimatedVisibilityScope,
    contentKey: Any,
    durationMillis: Int,
    cornerRadius: () -> Dp,
    handoff: CoverPressHandoff? = null,
    handoffKey: Any = contentKey,
    renderInOverlayDuringTransition: Boolean = true,
    overlayAlpha: () -> Float = { 1f },
): Modifier = with(sharedScope) {
    val sharedState = rememberSharedContentState(contentKey)
    val currentRadius = rememberUpdatedState(cornerRadius)
    val measurement = remember { CoverMeasureState() }
    val boundsTransform = BoundsTransform { _, _ ->
        coverFlightSpring(durationMillis, Rect(.5f, .5f, .5f, .5f))
    }
    val sharedModifier = this@sharedCoverArtwork.sharedElement(
        sharedContentState = sharedState,
        animatedVisibilityScope = visibilityScope,
        boundsTransform = boundsTransform,
        renderInOverlayDuringTransition = renderInOverlayDuringTransition,
    )
    sharedModifier.graphicsLayer {
        alpha = if (renderInOverlayDuringTransition && sharedState.isMatchFound && sharedScope.isTransitionActive) {
            overlayAlpha()
        } else 1f
        // The overlay retains the transformed source position but drops the parent's scale.
        // Carry that scale from the pressed frame through the same flight, anchored at its
        // already-transformed top-left. The source itself remains scaled until it fades away.
        val correction = if (handoff != null && sharedState.isMatchFound && sharedScope.isTransitionActive &&
            visibilityScope.transition.targetState == EnterExitState.Visible
        ) {
            handoff.scaleFor(handoffKey)
        } else 1f
        transformOrigin = TransformOrigin(0f, 0f)
        scaleX = correction
        scaleY = correction
    }.graphicsLayer {
        clip = true
        shape = RoundedCornerShape(currentRadius.value())
    }.layout { measurable, constraints ->
        if (isLookingAhead) measurement.lookaheadConstraints = constraints
        val child = measurable.measure(measurement.lookaheadConstraints ?: constraints)
        val width = constraints.constrainWidth(child.width)
        val height = constraints.constrainHeight(child.height)
        layout(width, height) {
            child.placeWithLayer(0, 0) {
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = if (child.width == 0) 1f else width.toFloat() / child.width
                scaleY = if (child.height == 0) 1f else height.toFloat() / child.height
            }
        }
    }
}
