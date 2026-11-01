@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.linxyi.lsmusic.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.linxyi.lsmusic.dlna.selectThumbnailArtworkUri

private val AlbumMotionEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
internal val AlbumArtworkCornerRadius = 18.dp
internal const val ALBUM_OPEN_DURATION_MILLIS = 520
internal const val ALBUM_CLOSE_DURATION_MILLIS = 420

internal data class AlbumNavigationMotion(
    val albumKey: BrowsePageKey,
    val opening: Boolean,
) {
    val durationMillis: Int
        get() = if (opening) ALBUM_OPEN_DURATION_MILLIS else ALBUM_CLOSE_DURATION_MILLIS
}

/** Only a direct parent/album pair may share artwork, never an unrelated server or folder. */
internal fun albumNavigationMotion(
    fromKey: BrowsePageKey,
    fromPath: List<BrowseLocation>,
    toKey: BrowsePageKey,
    toPath: List<BrowseLocation>,
): AlbumNavigationMotion? {
    if (fromKey.serverId.isBlank() || fromKey.serverId != toKey.serverId || fromKey == toKey) return null
    fun isAlbumChild(parent: List<BrowseLocation>, child: List<BrowseLocation>): Boolean =
        child.size == parent.size + 1 &&
            child.last().pageKind == LibraryPageKind.ALBUM &&
            child.dropLast(1).map { it.id } == parent.map { it.id }

    return when {
        isAlbumChild(fromPath, toPath) -> AlbumNavigationMotion(toKey, opening = true)
        isAlbumChild(toPath, fromPath) -> AlbumNavigationMotion(fromKey, opening = false)
        else -> null
    }
}

internal val LibraryUiState.pageKey: BrowsePageKey
    get() = browsePageKey ?: BrowsePageKey("", path.lastOrNull()?.id.orEmpty())

internal val LibraryUiState.pageKind: LibraryPageKind
    get() = path.lastOrNull()?.pageKind ?: LibraryPageKind.DIRECTORY

internal class LibraryPageMotionScope(
    val sharedScope: SharedTransitionScope,
    val visibilityScope: AnimatedVisibilityScope,
    val isCurrentPage: Boolean,
    val isNavigating: Boolean,
    val navigation: AlbumNavigationMotion?,
    val returningArtworkUri: String?,
) {
    val isSettled: Boolean
        get() = visibilityScope.transition.currentState == EnterExitState.Visible &&
            visibilityScope.transition.targetState == EnterExitState.Visible &&
            !sharedScope.isTransitionActive
}

internal val LocalLibraryPageMotion = staticCompositionLocalOf<LibraryPageMotionScope?> { null }

/** Retain the outgoing page only for the transition; browse caching remains in the ViewModel. */
@Composable
internal fun LibraryPageTransition(
    state: LibraryUiState,
    content: @Composable (LibraryUiState) -> Unit,
) {
    // Device changes are a fresh library, even when both servers use the same object IDs.
    key(state.pageKey.serverId) {
        SharedTransitionLayout(Modifier.fillMaxSize()) {
            val sharedScope = this
            val transition = updateTransition(state, label = "library-navigation")
            val navigation = albumNavigationMotion(
                transition.currentState.pageKey,
                transition.currentState.path,
                transition.targetState.pageKey,
                transition.targetState.path,
            )
            val returningArtworkUri = if (navigation?.opening == false) {
                val album = transition.currentState
                album.albumArtwork?.takeIf { it.pageKey == navigation.albumKey }?.displayUri
                    ?: album.path.lastOrNull()?.let { selectThumbnailArtworkUri(it.artworkCandidates, it.artworkUri) }
            } else {
                null
            }
            transition.AnimatedContent(
                modifier = Modifier.fillMaxSize(),
                // Loading, artwork and playback updates must not restart page navigation.
                contentKey = { it.pageKey },
                transitionSpec = {
                    (fadeIn(tween(240, easing = AlbumMotionEasing)) togetherWith
                        fadeOut(tween(150))).using(null)
                },
            ) { page ->
                val isCurrentPage = page.pageKey == state.pageKey
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
                    LocalLibraryPageMotion provides LibraryPageMotionScope(
                        sharedScope = sharedScope,
                        visibilityScope = this,
                        isCurrentPage = isCurrentPage,
                        isNavigating = transition.currentState.pageKey != transition.targetState.pageKey,
                        navigation = navigation,
                        returningArtworkUri = returningArtworkUri,
                    ),
                ) {
                    Box(Modifier.fillMaxSize().then(interactionModifier)) { content(page) }
                }
            }
        }
    }
}

/** Applied before the artwork's fixed size and clipping so the whole tile moves in the overlay. */
@Composable
internal fun Modifier.sharedAlbumArtwork(
    albumKey: BrowsePageKey?,
    cornerRadius: Dp,
): Modifier {
    val motion = LocalLibraryPageMotion.current
    if (motion == null || albumKey == null ||
        (motion.isNavigating && motion.navigation?.albumKey != albumKey)
    ) return clip(RoundedCornerShape(cornerRadius))
    val duration = motion.navigation?.durationMillis ?: ALBUM_OPEN_DURATION_MILLIS
    val shape = RoundedCornerShape(cornerRadius)
    return with(motion.sharedScope) {
        // Clip the content inside the shared element; an overlay clip also constrains the flight
        // to its original card bounds and cuts off the cover as it grows.
        this@sharedAlbumArtwork.sharedElement(
            sharedContentState = rememberSharedContentState(albumKey),
            animatedVisibilityScope = motion.visibilityScope,
            boundsTransform = BoundsTransform { _, _ -> tween(duration, easing = AlbumMotionEasing) },
        ).clip(shape)
    }
}

/** Bounded staggering: only visible rows participate, and scrolling later never replays it. */
@Composable
internal fun Modifier.albumDetailEnter(delayMillis: Int = 90): Modifier {
    val motion = LocalLibraryPageMotion.current ?: return this
    val distance = with(LocalDensity.current) { 16.dp.roundToPx() }
    return with(motion.visibilityScope) {
        this@albumDetailEnter.animateEnterExit(
            enter = fadeIn(tween(260, delayMillis)) + slideInVertically(
                animationSpec = tween(340, delayMillis, AlbumMotionEasing),
                initialOffsetY = { distance },
            ),
            exit = fadeOut(tween(110)) + slideOutVertically(
                animationSpec = tween(160, easing = AlbumMotionEasing),
                targetOffsetY = { distance / 2 },
            ),
        )
    }
}
