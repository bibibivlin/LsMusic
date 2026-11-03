@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.linxyi.lsmusic.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import coil3.size.Scale
import com.linxyi.lsmusic.R
import com.linxyi.lsmusic.dlna.MediaEntry

private val PlayerMotionEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val MiniArtworkCornerRadius = 16.dp
private val PlayerArtworkCornerRadius = 28.dp

private data class PlaybackArtworkKey(
    val parentId: String,
    val id: String,
    val resourceUri: String?,
    val artworkUri: String?,
)

private fun MediaEntry.playbackArtworkKey() = PlaybackArtworkKey(
    parentId, id, resourceUri,
    artworkUri?.takeIf(String::isNotBlank) ?: thumbnailArtworkUri?.takeIf(String::isNotBlank),
)

internal class PlayerPageMotionScope(
    val sharedScope: SharedTransitionScope,
    val visibilityScope: AnimatedVisibilityScope,
    val cornerRadius: State<Dp>,
    val durationMillis: Int,
    val enteringFromMiniPlayer: Boolean,
    val isCurrentPage: Boolean,
    val handoff: CoverPressHandoff,
) {
    fun captureArtworkPress(track: MediaEntry, scale: Float) {
        handoff.capture(track.playbackArtworkKey(), scale)
    }
}

internal val LocalPlayerPageMotion = compositionLocalOf<PlayerPageMotionScope?> { null }
private data class PlaybackArtworkPainter(val key: PlaybackArtworkKey, val painter: Painter)
private val LocalPlaybackArtworkPainter = compositionLocalOf<PlaybackArtworkPainter?> { null }

/** Both navigation entry points change the same destination, so they share exactly one flight. */
@Composable
internal fun PlayerPageTransition(
    destination: AppDestination,
    track: MediaEntry?,
    miniPlayerEnabled: Boolean,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    content: @Composable (AppDestination) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val handoff = remember { CoverPressHandoff(coroutineScope) }
    SharedTransitionLayout(modifier.fillMaxSize()) {
        val sharedScope = this
        val transition = updateTransition(destination, label = "player-navigation")
        // Keep one painter alive across both endpoints and until the outgoing player is gone.
        // Only the current track is decoded, bounded to the largest artwork used by the player.
        val artwork = if (track != null && (miniPlayerEnabled ||
                destination == AppDestination.NOW_PLAYING || transition.currentState == AppDestination.NOW_PLAYING)
        ) {
            PlaybackArtworkPainter(track.playbackArtworkKey(), rememberPlaybackArtworkPainter(track))
        } else {
            null
        }
        val opening = transition.targetState == AppDestination.NOW_PLAYING
        LaunchedEffect(transition.currentState, transition.targetState, sharedScope.isTransitionActive) {
            if (transition.currentState == transition.targetState && !sharedScope.isTransitionActive) handoff.clear()
        }
        val duration = if (opening) COVER_OPEN_DURATION_MILLIS else COVER_CLOSE_DURATION_MILLIS
        val cornerRadius = transition.animateDp(
            transitionSpec = { tween(duration, easing = PlayerMotionEasing) },
            label = "player-artwork-corners",
        ) { if (it == AppDestination.NOW_PLAYING) PlayerArtworkCornerRadius else MiniArtworkCornerRadius }
        transition.AnimatedContent(
            modifier = Modifier.fillMaxSize(),
            // Other tabs still switch in place, and settings keep their own nested transition.
            contentKey = { it == AppDestination.NOW_PLAYING },
            transitionSpec = {
                (fadeIn(tween(300, 60, PlayerMotionEasing)) togetherWith
                    fadeOut(tween(180, easing = PlayerMotionEasing))).using(null)
            },
        ) { page ->
            val isCurrentPage = (page == AppDestination.NOW_PLAYING) == opening
            val enteringFromMiniPlayer = miniPlayerEnabled && track != null && opening &&
                transition.currentState != AppDestination.NOW_PLAYING
            // Playback ticks change page content, but must not invalidate every motion reader.
            val motion = remember(
                sharedScope, this, cornerRadius, duration, enteringFromMiniPlayer, isCurrentPage, handoff,
            ) {
                PlayerPageMotionScope(
                    sharedScope = sharedScope,
                    visibilityScope = this,
                    cornerRadius = cornerRadius,
                    durationMillis = duration,
                    enteringFromMiniPlayer = enteringFromMiniPlayer,
                    isCurrentPage = isCurrentPage,
                    handoff = handoff,
                )
            }
            val interactionModifier = if (isCurrentPage) Modifier else Modifier
                .clearAndSetSemantics { }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    }
                }
            CompositionLocalProvider(
                LocalPlaybackArtworkPainter provides artwork,
                LocalPlayerPageMotion provides motion,
            ) {
                // Fixed viewport constraints keep the hero at its final size from the first frame.
                TopEdgeProtection(topInset, Modifier.fillMaxSize().then(interactionModifier)) { content(page) }
            }
        }
    }
}

@Composable
private fun rememberPlaybackArtworkPainter(track: MediaEntry): Painter {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { 440.dp.roundToPx().coerceAtLeast(1) }
    val artworkKey = track.playbackArtworkKey()
    return key(artworkKey) {
        val request = remember(context, artworkKey, sizePx) {
            ImageRequest.Builder(context)
                .data(artworkKey.artworkUri)
                .size(sizePx)
                .scale(Scale.FILL)
                .precision(Precision.INEXACT)
                .crossfade(false)
                .build()
        }
        rememberAsyncImagePainter(request)
    }
}

@Composable
internal fun PlaybackArtwork(track: MediaEntry, size: Dp, expanded: Boolean) {
    val motion = LocalPlayerPageMotion.current
    val artworkKey = track.playbackArtworkKey()
    val painter = LocalPlaybackArtworkPainter.current?.takeIf { it.key == artworkKey }?.painter
        ?: rememberPlaybackArtworkPainter(track)
    val colors = remember(track.title) {
        if (track.title.hashCode() % 2 == 0) {
            listOf(Color(0xFF6147D7), Color(0xFFFF78A9), Color(0xFFFFC857))
        } else {
            listOf(Color(0xFF167D8D), Color(0xFF6957DE), Color(0xFFEC6B9D))
        }
    }
    val radius = if (expanded) PlayerArtworkCornerRadius else MiniArtworkCornerRadius
    val sharedModifier = if (motion == null) {
        Modifier.graphicsLayer {
            shape = RoundedCornerShape(radius)
            clip = true
        }
    } else {
        Modifier.sharedCoverArtwork(
            sharedScope = motion.sharedScope,
            visibilityScope = motion.visibilityScope,
            contentKey = artworkKey,
            durationMillis = motion.durationMillis,
            cornerRadius = { motion.cornerRadius.value },
            handoff = motion.handoff,
        )
    }
    val background = remember(colors) { Brush.radialGradient(colors) }
    Box(
        modifier = sharedModifier
            .size(size)
            .testTag(if (expanded) "now-playing-artwork" else "mini-player-artwork")
            .background(background),
    ) {
        // Relative geometry makes the no-artwork state continuous at every intermediate size too.
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color.Black.copy(alpha = .76f), radius = this.size.minDimension * .29f)
            drawCircle(Color.White.copy(alpha = .85f), radius = this.size.minDimension * .047f)
        }
        Image(
            painter = painter,
            contentDescription = stringResource(R.string.album_cover, track.title),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Move only the controls, never the layout containing the shared artwork's destination bounds. */
@Composable
internal fun Modifier.playerControlsEnter(delayMillis: Int = 100): Modifier {
    val motion = LocalPlayerPageMotion.current ?: return this
    val distance = with(LocalDensity.current) { 18.dp.roundToPx() }
    return with(motion.visibilityScope) {
        this@playerControlsEnter.animateEnterExit(
            enter = fadeIn(tween(280, delayMillis, PlayerMotionEasing)) + slideInVertically(
                tween(380, delayMillis, PlayerMotionEasing),
                initialOffsetY = { distance },
            ),
            exit = fadeOut(tween(130)) + slideOutVertically(
                tween(180, easing = PlayerMotionEasing),
                targetOffsetY = { distance / 2 },
            ),
        )
    }
}
