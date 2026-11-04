@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.linxyi.lsmusic.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.linxyi.lsmusic.dlna.selectThumbnailArtworkUri

private val AlbumMotionEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
internal val AlbumArtworkCornerRadius = 18.dp
internal const val ALBUM_OPEN_DURATION_MILLIS = COVER_OPEN_DURATION_MILLIS
internal const val ALBUM_CLOSE_DURATION_MILLIS = COVER_CLOSE_DURATION_MILLIS

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

private val LibraryUiState.parentPageKey: BrowsePageKey?
    get() = path.dropLast(1).lastOrNull()?.let { BrowsePageKey(pageKey.serverId, it.id) }

internal data class AlbumArtworkTransitionKey(val parentKey: BrowsePageKey, val albumKey: BrowsePageKey)

private class RetainedLibraryPage(initialState: LibraryUiState, initiallyVisible: Boolean) {
    var state by mutableStateOf(initialState)
    val visibility = MutableTransitionState(if (initiallyVisible) EnterExitState.Visible else EnterExitState.PreEnter)
    val covers = mutableStateMapOf<Any, MutableTransitionState<EnterExitState>>()
}

/** Page fades and cover flights have independent lifetimes, but share the same viewport. */
private class LibraryPageVisibilityScope(
    override val transition: Transition<EnterExitState>,
) : AnimatedVisibilityScope

internal class LibraryPageMotionScope(
    val sharedScope: SharedTransitionScope,
    val visibilityScope: AnimatedVisibilityScope,
    val pageVisibility: State<Float>,
    val isCurrentPage: Boolean,
    val pageKey: BrowsePageKey,
    val parentPageKey: BrowsePageKey?,
    val currentPageKey: BrowsePageKey,
    val currentParentPageKey: BrowsePageKey?,
    val navigation: AlbumNavigationMotion?,
    val returningArtwork: Map<BrowsePageKey, String>,
    val covers: MutableMap<Any, MutableTransitionState<EnterExitState>>,
    val handoff: CoverPressHandoff,
) {
    val isSettled: Boolean
        get() = visibilityScope.transition.currentState == EnterExitState.Visible &&
            visibilityScope.transition.targetState == EnterExitState.Visible &&
            covers.values.all { it.isIdle }
}

// Motion changes at navigation boundaries; only its readers should be invalidated.
internal val LocalLibraryPageMotion = compositionLocalOf<LibraryPageMotionScope?> { null }

/** Retain each outgoing page only until its own fade and cover pairs have finished. */
@Composable
internal fun LibraryPageTransition(
    state: LibraryUiState,
    content: @Composable (LibraryUiState) -> Unit,
) {
    // Device changes are a fresh library, even when both servers use the same object IDs.
    key(state.pageKey.serverId) {
        val coroutineScope = rememberCoroutineScope()
        val handoff = remember { CoverPressHandoff(coroutineScope) }
        val pages = remember { mutableStateListOf(RetainedLibraryPage(state, initiallyVisible = true)) }
        val currentPage = pages.firstOrNull { it.state.pageKey == state.pageKey }
            ?: RetainedLibraryPage(state, initiallyVisible = false).also { pages.add(it) }
        currentPage.state = state
        pages.forEach { page ->
            page.visibility.targetState = if (page === currentPage) EnterExitState.Visible else EnterExitState.PostExit
        }
        SharedTransitionLayout(Modifier.fillMaxSize().clipToBounds()) {
            val sharedScope = this
            ProtectTopEdgeDuringCoverFlight(sharedScope.isTransitionActive)
            val finishedPages = pages.filter { page ->
                page !== currentPage && page.visibility.isIdle && page.covers.keys.none { coverKey ->
                    // A reversed flight can finish its outgoing endpoint before the incoming
                    // one. Keep both endpoints until this cover pair (not the whole library) lands.
                    pages.any { it.covers[coverKey]?.isIdle == false }
                }
            }
            LaunchedEffect(currentPage, finishedPages) {
                finishedPages.forEach { handoff.clear(it.state.pageKey) }
                pages.removeAll(finishedPages.toSet())
            }
            val currentPageIdle = currentPage.visibility.isIdle && currentPage.covers.values.all { it.isIdle }
            LaunchedEffect(currentPage, currentPageIdle) {
                if (currentPageIdle) handoff.clear(currentPage.state.pageKey)
            }
            val returningArtwork = pages.mapNotNull { retained ->
                val album = retained.state
                if (album.pageKind != LibraryPageKind.ALBUM || retained === currentPage) return@mapNotNull null
                val uri = album.albumArtwork?.takeIf { it.pageKey == album.pageKey }?.displayUri
                    ?: album.path.lastOrNull()?.let { selectThumbnailArtworkUri(it.artworkCandidates, it.artworkUri) }
                uri?.let { album.pageKey to it }
            }.toMap()
            pages.forEach { retained ->
                key(retained.state.pageKey) {
                    val page = retained.state
                    val isCurrentPage = retained === currentPage
                    val transition = rememberTransition(retained.visibility, label = "library-page-visibility")
                    val visibilityScope = remember(transition) { LibraryPageVisibilityScope(transition) }
                    val parent = pages.firstOrNull { it.state.pageKey == page.parentPageKey }?.state
                    val navigation = parent?.let {
                        albumNavigationMotion(it.pageKey, it.path, page.pageKey, page.path)
                            ?.copy(opening = isCurrentPage)
                    }
                    val topEdgeVisibility = transition.animateFloat(
                        transitionSpec = {
                            if (targetState == EnterExitState.Visible) tween(240, easing = AlbumMotionEasing) else tween(150)
                        },
                        label = "library-top-edge-visibility",
                    ) { visibility -> if (visibility == EnterExitState.Visible) 1f else 0f }
                    val motion = remember(
                        sharedScope, visibilityScope, topEdgeVisibility, isCurrentPage, page.pageKey, page.parentPageKey,
                        state.pageKey, state.parentPageKey, navigation, returningArtwork, handoff,
                    ) {
                        LibraryPageMotionScope(
                            sharedScope = sharedScope,
                            visibilityScope = visibilityScope,
                            pageVisibility = topEdgeVisibility,
                            isCurrentPage = isCurrentPage,
                            pageKey = page.pageKey,
                            parentPageKey = page.parentPageKey,
                            currentPageKey = state.pageKey,
                            currentParentPageKey = state.parentPageKey,
                            navigation = navigation,
                            returningArtwork = returningArtwork,
                            covers = retained.covers,
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
                    val pageFade = with(visibilityScope) {
                        Modifier.animateEnterExit(
                            enter = fadeIn(tween(240, easing = AlbumMotionEasing)),
                            exit = fadeOut(tween(150)),
                        )
                    }
                    CompositionLocalProvider(
                        LocalLibraryPageMotion provides motion,
                        LocalTopEdgeVisibility provides topEdgeVisibility,
                    ) {
                        Box(
                            Modifier.fillMaxSize().zIndex(if (isCurrentPage) 1f else 0f)
                                .then(pageFade).then(interactionModifier),
                        ) { content(page) }
                    }
                }
            }
        }
    }
}

/** Keep the directory's floating control above both the cover and the top protection layer. */
@Composable
internal fun Modifier.libraryFloatingSearchOverlay(): Modifier {
    val library = LocalLibraryPageMotion.current ?: return this
    val player = LocalPlayerPageMotion.current
    val overlay = player?.sharedScope ?: library.sharedScope
    val overlayModifier = with(overlay) {
        Modifier.renderInSharedTransitionScopeOverlay(zIndexInOverlay = 1f, renderInOverlay = { true })
    }
    val libraryFade = with(library.visibilityScope) {
        Modifier.animateEnterExit(
            enter = fadeIn(tween(240, easing = AlbumMotionEasing)),
            exit = fadeOut(tween(150)),
        )
    }
    val playerFade = if (player == null) Modifier else with(player.visibilityScope) {
        Modifier.animateEnterExit(
            enter = fadeIn(tween(300, 60, AlbumMotionEasing)),
            exit = fadeOut(tween(180, easing = AlbumMotionEasing)),
        )
    }
    return this.then(overlayModifier).then(libraryFade).then(playerFade)
}

/** Applied before the artwork's fixed size and clipping so the whole tile moves in the overlay. */
@Composable
internal fun Modifier.sharedAlbumArtwork(
    albumKey: BrowsePageKey?,
    cornerRadius: Dp,
): Modifier {
    val motion = LocalLibraryPageMotion.current
    if (motion == null || albumKey == null) return clip(RoundedCornerShape(cornerRadius))
    val isDetailCover = motion.pageKey == albumKey
    val parentKey = if (isDetailCover) motion.parentPageKey else motion.pageKey
    if (parentKey == null) return clip(RoundedCornerShape(cornerRadius))
    val expanded = motion.currentPageKey == albumKey && motion.currentParentPageKey == parentKey
    val coverKey = AlbumArtworkTransitionKey(parentKey, albumKey)
    val visibility = remember(coverKey) {
        MutableTransitionState(
            if (motion.visibilityScope.transition.currentState == EnterExitState.Visible &&
                motion.visibilityScope.transition.targetState == EnterExitState.Visible
            ) EnterExitState.Visible else EnterExitState.PreEnter,
        )
    }
    val coverVisible = if (isDetailCover) expanded else !expanded
    visibility.targetState = if (coverVisible) {
        EnterExitState.Visible
    } else {
        EnterExitState.PostExit
    }
    val coverTransition = rememberTransition(visibility, label = "album-cover-visibility")
    val coverScope = remember(coverTransition) { LibraryPageVisibilityScope(coverTransition) }
    DisposableEffect(motion.covers, coverKey, visibility) {
        motion.covers[coverKey] = visibility
        onDispose { motion.covers.remove(coverKey) }
    }
    // Keep every source registered, including the next album while another cover is returning.
    // Visibility belongs to each cover pair, not to its fading directory page.
    return sharedCoverArtwork(
        sharedScope = motion.sharedScope,
        visibilityScope = coverScope,
        contentKey = coverKey,
        durationMillis = if (expanded) ALBUM_OPEN_DURATION_MILLIS else ALBUM_CLOSE_DURATION_MILLIS,
        cornerRadius = { cornerRadius },
        handoff = motion.handoff,
        handoffKey = albumKey,
        // SharedTransitionScope remains active for other albums. A completed return must draw
        // in its own (possibly hidden) directory, never float above the next album's track list.
        renderInOverlayDuringTransition = !visibility.isIdle,
        // A return may overlap the next opening, but must fade with its departing directory.
        // Only the selected album's shared cover stays opaque above the new page.
        overlayAlpha = { if (motion.isCurrentPage || expanded) 1f else motion.pageVisibility.value },
    )
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
