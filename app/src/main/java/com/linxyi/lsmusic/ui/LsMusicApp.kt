@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.linxyi.lsmusic.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.SyncDisabled
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.layout.PinnableContainer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linxyi.lsmusic.R
import com.linxyi.lsmusic.dlna.DlnaDevice
import com.linxyi.lsmusic.dlna.MediaEntry
import com.linxyi.lsmusic.dlna.RemotePlaybackState
import com.linxyi.lsmusic.dlna.DlnaDeviceKind
import com.linxyi.lsmusic.dlna.selectThumbnailArtworkUri
import com.linxyi.lsmusic.lyrics.LyricsProviderId
import com.linxyi.lsmusic.lyrics.LyricsTranslationMode
import com.linxyi.lsmusic.ui.theme.LsMusicTheme
import com.linxyi.lsmusic.ui.theme.presetColorScheme
import coil3.compose.AsyncImage
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import coil3.size.Scale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.math.roundToInt

private data class DestinationItem(
    val destination: AppDestination,
    val icon: ImageVector,
)

@Immutable
internal data class LibraryUiState(
    val entries: List<MediaEntry>,
    val albumSort: AlbumSort,
    val path: List<BrowseLocation>,
    val browsePageKey: BrowsePageKey?,
    val browseViewState: BrowseViewState,
    val preferences: AppPreferences,
    val isSearching: Boolean,
    val servers: List<DlnaDevice>,
    val selectedServerId: String?,
    val browseLoadStatus: BrowseLoadStatus,
    val albumArtwork: AlbumArtworkUiState?,
    val currentTrackId: String?,
    val playbackState: RemotePlaybackState,
) {
    val isBrowsing: Boolean
        get() = browseLoadStatus == BrowseLoadStatus.LOADING
}

private const val MEDIA_ENTRY_KEY_PREFIX = "media:"
private const val LIBRARY_GRID_HEADER_COUNT = 3
private const val LIBRARY_TOP_ITEM_INDEX = 0
private const val LIBRARY_SEARCH_ITEM_INDEX = 1
private val LibrarySearchControlHeight = 56.dp
private val LibraryFastScrollerTopInset = LibrarySearchControlHeight + 32.dp
private val LibraryFastScrollerTouchWidth = 48.dp
private val LibraryFastScrollerMinimumThumbHeight = 48.dp
private val ReorderEdgeScrollSize = 72.dp
private val ReorderMaximumScrollPerFrame = 20.dp
internal val ReorderVisualPadding = 8.dp
private const val ReorderPlacementAnimationDurationMillis = 110
private const val QueuePostDragClickSuppressionMs = 450L
private const val ALBUM_DETAIL_HEADER_COUNT = 1
private const val ALBUM_ART_PREFETCH_SCREENS = 2
private const val MAX_ACTIVE_ART_PREFETCHES = 4
private const val ALBUM_ART_PREFETCH_RESUME_DELAY_MS = 150L
private val LargeSquareContentMaxSize = 360.dp
private val DefaultScreenBottomPadding = 32.dp
private val MiniPlayerHeight = 68.dp
private val MiniPlayerBottomSpacing = 12.dp
private val MiniPlayerContentInset = MiniPlayerHeight + MiniPlayerBottomSpacing
private val MiniPlayerMaxWidth = 720.dp

internal fun LazyItemScope.reorderPlacementModifier(dragged: Boolean): Modifier =
    if (dragged) {
        Modifier
    } else {
        Modifier.animateItem(
            fadeInSpec = null,
            placementSpec = tween(durationMillis = ReorderPlacementAnimationDurationMillis),
            fadeOutSpec = null,
        )
    }

private val artworkPalettes = listOf(
    listOf(Color(0xFF7454E8), Color(0xFFE263A9)),
    listOf(Color(0xFF1A9A8A), Color(0xFF8CC85A)),
    listOf(Color(0xFFE27A45), Color(0xFFF0B85A)),
    listOf(Color(0xFF376DCC), Color(0xFF6B54E8)),
)

private fun mediaEntryKey(entry: MediaEntry): String =
    "$MEDIA_ENTRY_KEY_PREFIX${entry.parentId}:${entry.id}"

private fun initialBrowseItemIndex(
    entries: List<MediaEntry>,
    viewState: BrowseViewState,
    headerCount: Int,
): Int {
    val anchorIndex = viewState.anchorEntryKey?.let { anchorKey ->
        entries.indexOfFirst { mediaEntryKey(it) == anchorKey }
            .takeIf { it >= 0 }
            ?.plus(headerCount)
    }
    val maximumIndex = (headerCount + entries.lastIndex).coerceAtLeast(0)
    return (anchorIndex ?: viewState.fallbackItemIndex).coerceIn(0, maximumIndex)
}

private fun albumArtworkRequest(
    context: android.content.Context,
    artworkUri: String,
    sizePx: Int,
): ImageRequest = ImageRequest.Builder(context)
    .data(artworkUri)
    .memoryCacheKey(albumArtworkThumbnailMemoryCacheKey(artworkUri))
    .size(sizePx.coerceAtLeast(1))
    .scale(Scale.FILL)
    .precision(Precision.INEXACT)
    .build()

private fun albumArtworkThumbnailMemoryCacheKey(artworkUri: String): String =
    "album-thumbnail:$artworkUri"

@Composable
private fun AlbumArtworkPrefetchEffect(
    pageKey: BrowsePageKey,
    entries: List<MediaEntry>,
    gridState: LazyGridState,
    requestSizePx: Int,
    enabled: Boolean,
    paused: Boolean,
) {
    val context = LocalContext.current
    val imageLoader = remember(context) { SingletonImageLoader.get(context) }

    LaunchedEffect(pageKey, entries, gridState, requestSizePx, enabled, paused) {
        if (!enabled || paused) return@LaunchedEffect
        delay(ALBUM_ART_PREFETCH_RESUME_DELAY_MS)
        val requests = LinkedHashMap<String, Job>()
        val requestSemaphore = Semaphore(MAX_ACTIVE_ART_PREFETCHES)
        var previousFirstEntryIndex = -1
        try {
            snapshotFlow {
                val visibleEntryIndices = gridState.layoutInfo.visibleItemsInfo
                    .asSequence()
                    .map { it.index - LIBRARY_GRID_HEADER_COUNT }
                    .filter { it in entries.indices }
                    .toList()
                visibleEntryIndices.firstOrNull()?.let { first -> first to visibleEntryIndices.last() }
            }.distinctUntilChanged().collect { visibleRange ->
                val (firstVisible, lastVisible) = visibleRange ?: return@collect
                val scrollingForward = previousFirstEntryIndex < 0 || firstVisible >= previousFirstEntryIndex
                previousFirstEntryIndex = firstVisible
                val visibleCount = (lastVisible - firstVisible + 1).coerceAtLeast(1)
                val prefetchCount = (visibleCount * ALBUM_ART_PREFETCH_SCREENS).coerceIn(6, 24)
                val prefetchIndices = directionalPrefetchIndices(
                    firstVisibleIndex = firstVisible,
                    lastVisibleIndex = lastVisible,
                    lastEntryIndex = entries.lastIndex,
                    prefetchCount = prefetchCount,
                    forward = scrollingForward,
                )
                val visibleUris = entries.subList(firstVisible, lastVisible + 1)
                    .mapNotNull { it.thumbnailArtworkUri?.takeIf(String::isNotBlank) }
                    .toSet()
                val prefetchUris = prefetchIndices
                    .mapNotNull { index -> entries.getOrNull(index)?.thumbnailArtworkUri?.takeIf(String::isNotBlank) }
                    .toSet()
                val retainedUris = visibleUris + prefetchUris

                val iterator = requests.iterator()
                while (iterator.hasNext()) {
                    val request = iterator.next()
                    if (!request.value.isActive || request.key !in retainedUris) {
                        request.value.cancel()
                        iterator.remove()
                    }
                }

                prefetchUris.forEach { uri ->
                    if (uri !in requests) {
                        requests[uri] = launch {
                            try {
                                requestSemaphore.withPermit {
                                    imageLoader.execute(albumArtworkRequest(context, uri, requestSizePx))
                                }
                            } catch (cancellation: CancellationException) {
                                throw cancellation
                            } catch (_: Exception) {
                                // Prefetch failures are silent; visible AsyncImage requests can retry normally.
                            }
                        }
                    }
                }
            }
        } finally {
            requests.values.forEach(Job::cancel)
        }
    }
}

private val destinations = listOf(
    DestinationItem(AppDestination.LIBRARY, Icons.Rounded.LibraryMusic),
    DestinationItem(AppDestination.QUEUE, Icons.AutoMirrored.Rounded.PlaylistPlay),
    DestinationItem(AppDestination.NOW_PLAYING, Icons.Rounded.GraphicEq),
    DestinationItem(AppDestination.SETTINGS, Icons.Rounded.Settings),
)

@Composable
fun LsMusicApp(viewModel: LsMusicViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val darkTheme = when (state.preferences.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    LsMusicTheme(
        darkTheme = darkTheme,
        dynamicColor = state.preferences.useDynamicColor,
        presetPalette = state.preferences.presetPalette,
    ) {
        SystemNavigationBarAppearance()
        val snackbar = remember { SnackbarHostState() }
        val resources = LocalResources.current
        val localeKey = resources.configuration.locales.toLanguageTags()

        LaunchedEffect(state.error, localeKey) {
            state.error?.let {
                snackbar.showSnackbar(it.resolve(resources))
                viewModel.consumeError()
            }
        }

        BackHandler(
            enabled = state.exitStatus != ExitStatus.IDLE || state.destination.settingsParent != null ||
                (state.destination == AppDestination.LIBRARY && state.path.size > 1),
        ) {
            if (state.exitStatus != ExitStatus.IDLE) return@BackHandler
            if (state.destination.settingsParent != null) {
                viewModel.setDestination(requireNotNull(state.destination.settingsParent))
            } else {
                viewModel.navigateTo(state.path.lastIndex - 1)
            }
        }

        ExitProgressDialog(state.exitStatus, state.exitError, viewModel::exitApp)
        LsMusicContent(
            state = state,
            snackbar = snackbar,
            onDestination = viewModel::setDestination,
            onRefresh = viewModel::refreshDevices,
            onSelectServer = viewModel::selectServer,
            onSelectRenderer = viewModel::selectRenderer,
            onOpen = viewModel::open,
            onNavigateTo = viewModel::navigateTo,
            onPlay = viewModel::playNow,
            onQueue = viewModel::addToQueue,
            onPlayAll = viewModel::playAll,
            onShufflePlay = viewModel::shufflePlay,
            onQueueAll = viewModel::addAllToQueue,
            onAlbumSort = viewModel::setAlbumSort,
            onSaveBrowseViewState = viewModel::saveBrowseViewState,
            onResolveAlbumArtwork = viewModel::resolveAlbumArtwork,
            onTogglePlayback = viewModel::togglePlayback,
            onPrevious = viewModel::previous,
            onNext = viewModel::next,
            onCycleRepeat = viewModel::cycleRepeatMode,
            onToggleShuffle = viewModel::toggleShuffle,
            onSeek = viewModel::seekTo,
            onRemoveQueue = viewModel::removeFromQueue,
            onMoveQueue = viewModel::moveQueueItem,
            onClearQueue = viewModel::clearQueue,
            onGallerySize = viewModel::setGallerySize,
            onDefaultGridLayout = viewModel::setDefaultGridLayout,
            onThemeMode = viewModel::setThemeMode,
            onDynamicColor = viewModel::setDynamicColor,
            onPresetPalette = viewModel::setPresetPalette,
            onLoadLyrics = viewModel::loadLyrics,
            onRetryLyrics = viewModel::retryLyrics,
            onLyricsEnabled = viewModel::setLyricsEnabled,
            onLyricsProviderOrder = viewModel::setLyricsProviderOrder,
            onLyricsTranslationMode = viewModel::setLyricsTranslationMode,
            onLyricsSourceVisible = viewModel::setLyricsSourceVisible,
            onLyricsEffectsEnabled = viewModel::setLyricsEffectsEnabled,
            onLyricsFontSizeSp = viewModel::setLyricsFontSizeSp,
            onClearLyricsCache = viewModel::clearLyricsCache,
            onListenBrainzEnabled = viewModel::setListenBrainzEnabled,
            onListenBrainzToken = viewModel::validateAndSaveListenBrainzToken,
            onListenBrainzMinimumSeconds = viewModel::setListenBrainzMinimumSeconds,
            onListenBrainzMinimumPercent = viewModel::setListenBrainzMinimumPercent,
            onRetryPendingListens = viewModel::retryPendingListens,
            onRemovePendingListen = viewModel::removePendingListen,
            onClearPendingListens = viewModel::clearPendingListens,
            onExit = viewModel::exitApp,
        )
    }
}

@Composable
internal fun LsMusicContent(
    state: LsMusicUiState,
    snackbar: SnackbarHostState,
    onDestination: (AppDestination) -> Unit,
    onRefresh: () -> Unit,
    onSelectServer: (String) -> Unit,
    onSelectRenderer: (String) -> Unit,
    onOpen: (MediaEntry) -> Unit,
    onNavigateTo: (Int) -> Unit,
    onPlay: (MediaEntry) -> Unit,
    onQueue: (MediaEntry) -> Unit,
    onPlayAll: (List<MediaEntry>) -> Unit,
    onShufflePlay: (List<MediaEntry>) -> Unit,
    onQueueAll: (List<MediaEntry>) -> Unit,
    onAlbumSort: (AlbumSort) -> Unit,
    onSaveBrowseViewState: (BrowsePageKey, BrowseViewState) -> Unit,
    onResolveAlbumArtwork: (BrowsePageKey, Int) -> Unit,
    onTogglePlayback: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onSeek: (Long) -> Unit,
    onRemoveQueue: (Int) -> Unit,
    onMoveQueue: (Int, Int) -> Unit,
    onClearQueue: () -> Unit,
    onGallerySize: (GallerySize) -> Unit,
    onDefaultGridLayout: (Boolean) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onPresetPalette: (PresetPalette) -> Unit,
    onLoadLyrics: () -> Unit,
    onRetryLyrics: () -> Unit,
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
    onRetryPendingListens: (Set<String>?) -> Unit,
    onRemovePendingListen: (String) -> Unit,
    onClearPendingListens: () -> Unit,
    onExit: () -> Unit = {},
) {
    val destinationStateHolder = rememberSaveableStateHolder()
    val libraryState = remember(
        state.entries,
        state.albumSort,
        state.path,
        state.browsePageKey,
        state.browseViewState,
        state.preferences,
        state.isSearching,
        state.servers,
        state.selectedServerId,
        state.browseLoadStatus,
        state.albumArtwork,
        state.currentTrack?.id,
        state.playbackState,
    ) {
        LibraryUiState(
            entries = state.entries,
            albumSort = state.albumSort,
            path = state.path,
            browsePageKey = state.browsePageKey,
            browseViewState = state.browseViewState,
            preferences = state.preferences,
            isSearching = state.isSearching,
            servers = state.servers,
            selectedServerId = state.selectedServerId,
            browseLoadStatus = state.browseLoadStatus,
            albumArtwork = state.albumArtwork,
            currentTrackId = state.currentTrack?.id,
            playbackState = state.playbackState,
        )
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = maxWidth >= 720.dp
        val showMiniPlayer = state.currentTrack != null && state.destination != AppDestination.NOW_PLAYING
        val bottomContentPadding = DefaultScreenBottomPadding +
            if (showMiniPlayer) MiniPlayerContentInset else 0.dp
        Row(Modifier.fillMaxSize()) {
            if (expanded) {
                AppNavigationRail(state.destination, onDestination)
            }
            Scaffold(
                modifier = Modifier.weight(1f),
                snackbarHost = {
                    SnackbarHost(
                        hostState = snackbar,
                        modifier = Modifier.padding(
                            bottom = if (showMiniPlayer) MiniPlayerContentInset else 0.dp,
                        ),
                    )
                },
                bottomBar = {
                    if (!expanded) AppNavigationBar(state.destination, onDestination)
                },
            ) { padding ->
                // The now-playing page measures its artwork from the final available height.
                // Avoid AnimatedContent's intermediate size constraints, which make it visibly resize
                // once while navigating from another destination on large and foldable screens.
                Box(Modifier.fillMaxSize().padding(padding)) {
                    destinationStateHolder.SaveableStateProvider(state.destination.navigationDestination.name) {
                        when (state.destination) {
                            AppDestination.LIBRARY -> LibraryScreen(
                                libraryState,
                                onOpen,
                                onNavigateTo,
                                onPlay,
                                onQueue,
                                onPlayAll,
                                onShufflePlay,
                                onQueueAll,
                                onAlbumSort,
                                onSaveBrowseViewState,
                                onResolveAlbumArtwork,
                                bottomContentPadding,
                                onOpenSettings = { onDestination(AppDestination.SETTINGS) },
                            )
                            AppDestination.QUEUE -> QueueScreen(
                                state,
                                onPlay,
                                onRemoveQueue,
                                onMoveQueue,
                                onClearQueue,
                                bottomContentPadding,
                            )
                            AppDestination.NOW_PLAYING -> NowPlayingScreen(
                                state = state,
                                onTogglePlayback = onTogglePlayback,
                                onPrevious = onPrevious,
                                onNext = onNext,
                                onCycleRepeat = onCycleRepeat,
                                onToggleShuffle = onToggleShuffle,
                                onSeek = onSeek,
                                onLoadLyrics = onLoadLyrics,
                                onRetryLyrics = onRetryLyrics,
                            )
                            AppDestination.SETTINGS,
                            AppDestination.SETTINGS_APPEARANCE,
                            AppDestination.SETTINGS_LYRICS,
                            AppDestination.SETTINGS_NETWORK,
                            AppDestination.SETTINGS_ABOUT,
                            AppDestination.PENDING_LISTENS -> SettingsPageTransition(state.destination) { page ->
                                if (page == AppDestination.PENDING_LISTENS) {
                                    PendingListensScreen(
                                        state = state,
                                        onBack = { onDestination(AppDestination.SETTINGS_NETWORK) },
                                        onRetry = onRetryPendingListens,
                                        onRemove = onRemovePendingListen,
                                        onClear = onClearPendingListens,
                                        bottomContentPadding = bottomContentPadding,
                                    )
                                } else {
                                    SettingsScreen(
                                        state = state.copy(destination = page),
                                        preferences = state.preferences,
                                        onRefresh = onRefresh,
                                        onSelectServer = onSelectServer,
                                        onSelectRenderer = onSelectRenderer,
                                        onGallerySize = onGallerySize,
                                        onDefaultGridLayout = onDefaultGridLayout,
                                        onThemeMode = onThemeMode,
                                        onDynamicColor = onDynamicColor,
                                        onPresetPalette = onPresetPalette,
                                        onLyricsEnabled = onLyricsEnabled,
                                        onLyricsProviderOrder = onLyricsProviderOrder,
                                        onLyricsTranslationMode = onLyricsTranslationMode,
                                        onLyricsSourceVisible = onLyricsSourceVisible,
                                        onLyricsEffectsEnabled = onLyricsEffectsEnabled,
                                        onLyricsFontSizeSp = onLyricsFontSizeSp,
                                        onClearLyricsCache = onClearLyricsCache,
                                        onListenBrainzEnabled = onListenBrainzEnabled,
                                        onListenBrainzToken = onListenBrainzToken,
                                        onListenBrainzMinimumSeconds = onListenBrainzMinimumSeconds,
                                        onListenBrainzMinimumPercent = onListenBrainzMinimumPercent,
                                        onOpenPendingListens = { onDestination(AppDestination.PENDING_LISTENS) },
                                        onNavigate = onDestination,
                                        onExit = onExit,
                                        bottomContentPadding = bottomContentPadding,
                                    )
                                }
                            }
                        }
                    }
                    if (showMiniPlayer) {
                        MiniPlayer(
                            state = state,
                            onOpen = { onDestination(AppDestination.NOW_PLAYING) },
                            onTogglePlayback = onTogglePlayback,
                            onNext = onNext,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(
                                    PaddingValues(
                                        start = 12.dp,
                                        end = 12.dp,
                                        bottom = MiniPlayerBottomSpacing,
                                    ),
                                )
                                .widthIn(max = MiniPlayerMaxWidth),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppNavigationBar(selected: AppDestination, onDestination: (AppDestination) -> Unit) {
    val selectedNavigationDestination = selected.navigationDestination
    Box(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .navigationBarsPadding(),
    ) {
        NavigationBar(
            modifier = Modifier.fillMaxWidth(),
            containerColor = Color.Transparent,
        ) {
            destinations.forEach { item ->
                NavigationBarItem(
                    selected = selectedNavigationDestination == item.destination,
                    onClick = { onDestination(item.destination) },
                    icon = { Icon(item.icon, null) },
                    label = { Text(stringResource(item.destination.navigationLabelRes)) },
                )
            }
        }
    }
}

@Composable
private fun SystemNavigationBarAppearance() {
    val view = LocalView.current
    val colorScheme = MaterialTheme.colorScheme
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        window.isNavigationBarContrastEnforced = false
        WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars =
            colorScheme.surfaceContainer.luminance() > .5f
    }
}

@Composable
private fun AppNavigationRail(selected: AppDestination, onDestination: (AppDestination) -> Unit) {
    val selectedNavigationDestination = selected.navigationDestination
    NavigationRail(Modifier.fillMaxHeight().width(132.dp)) {
        Spacer(Modifier.height(24.dp))
        AlbumMark(54.dp)
        Spacer(Modifier.height(28.dp))
        destinations.forEach { item ->
            NavigationRailItem(
                modifier = Modifier.width(116.dp),
                selected = selectedNavigationDestination == item.destination,
                onClick = { onDestination(item.destination) },
                icon = { Icon(item.icon, null) },
                label = {
                    Text(
                        stringResource(item.destination.navigationLabelRes),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

@Composable
private fun LibraryScreen(
    state: LibraryUiState,
    onOpen: (MediaEntry) -> Unit,
    onNavigateTo: (Int) -> Unit,
    onPlay: (MediaEntry) -> Unit,
    onQueue: (MediaEntry) -> Unit,
    onPlayAll: (List<MediaEntry>) -> Unit,
    onShufflePlay: (List<MediaEntry>) -> Unit,
    onQueueAll: (List<MediaEntry>) -> Unit,
    onAlbumSort: (AlbumSort) -> Unit,
    onSaveBrowseViewState: (BrowsePageKey, BrowseViewState) -> Unit,
    onResolveAlbumArtwork: (BrowsePageKey, Int) -> Unit,
    bottomContentPadding: Dp,
    onOpenSettings: () -> Unit,
) {
    val pageKey = state.browsePageKey ?: BrowsePageKey("", state.path.lastOrNull()?.id.orEmpty())
    key(pageKey) {
        when (state.path.lastOrNull()?.pageKind ?: LibraryPageKind.DIRECTORY) {
            LibraryPageKind.ALBUM -> AlbumDetailScreen(
                state = state,
                pageKey = pageKey,
                initialViewState = state.browseViewState,
                onSaveBrowseViewState = onSaveBrowseViewState,
                onResolveAlbumArtwork = onResolveAlbumArtwork,
                onPlay = onPlay,
                onQueue = onQueue,
                onPlayAll = onPlayAll,
                onShufflePlay = onShufflePlay,
                onQueueAll = onQueueAll,
                bottomContentPadding = bottomContentPadding,
            )
            LibraryPageKind.RESOLVING -> ResolvingLibraryPage(
                path = state.path,
                onNavigateTo = onNavigateTo,
            )
            LibraryPageKind.DIRECTORY -> LibraryDirectoryScreen(
                state = state,
                pageKey = pageKey,
                initialViewState = state.browseViewState,
                onOpen = onOpen,
                onNavigateTo = onNavigateTo,
                onPlay = onPlay,
                onQueue = onQueue,
                onPlayAll = onPlayAll,
                onShufflePlay = onShufflePlay,
                onQueueAll = onQueueAll,
                onAlbumSort = onAlbumSort,
                onSaveBrowseViewState = onSaveBrowseViewState,
                bottomContentPadding = bottomContentPadding,
                onOpenSettings = onOpenSettings,
            )
        }
    }
}

@Composable
private fun ResolvingLibraryPage(
    path: List<BrowseLocation>,
    onNavigateTo: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 32.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (path.size > 1) {
                FilledTonalIconButton(onClick = { onNavigateTo(path.lastIndex - 1) }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back_to_parent))
                }
                Spacer(Modifier.width(8.dp))
            }
            Breadcrumbs(path, onNavigateTo, Modifier.weight(1f))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            LoadingPanel(stringResource(R.string.loading_music_content))
        }
    }
}

@Composable
internal fun LibraryDirectoryScreen(
    state: LibraryUiState,
    modifier: Modifier = Modifier,
    pageKey: BrowsePageKey,
    initialViewState: BrowseViewState,
    onOpen: (MediaEntry) -> Unit,
    onNavigateTo: (Int) -> Unit,
    onPlay: (MediaEntry) -> Unit,
    onQueue: (MediaEntry) -> Unit,
    onPlayAll: (List<MediaEntry>) -> Unit,
    onShufflePlay: (List<MediaEntry>) -> Unit,
    onQueueAll: (List<MediaEntry>) -> Unit,
    onAlbumSort: (AlbumSort) -> Unit,
    onSaveBrowseViewState: (BrowsePageKey, BrowseViewState) -> Unit,
    bottomContentPadding: Dp,
    onOpenSettings: () -> Unit,
) {
    var query by remember { mutableStateOf(initialViewState.query) }
    var useGrid by remember {
        mutableStateOf(initialViewState.useGrid ?: state.preferences.useGridByDefault)
    }
    val isAlbumCollection = remember(state.entries) {
        state.entries.isNotEmpty() &&
            state.entries.all { it.isContainer } &&
            state.entries.any { it.isAlbum }
    }
    val visibleEntries = remember(state.entries, query, state.albumSort, isAlbumCollection) {
        val filtered = if (query.isBlank()) state.entries
        else state.entries.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.creator.contains(query, ignoreCase = true) ||
                it.albumArtist.contains(query, ignoreCase = true)
        }
        if (isAlbumCollection) filtered.sortedAlbums(state.albumSort) else filtered
    }
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = initialBrowseItemIndex(
            entries = visibleEntries,
            viewState = initialViewState,
            headerCount = LIBRARY_GRID_HEADER_COUNT,
        ),
        initialFirstVisibleItemScrollOffset = initialViewState.scrollOffset.coerceAtLeast(0),
    )
    val searchFocusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    var isFastScrollerDragging by remember { mutableStateOf(false) }
    val showFloatingSearchButton by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > LIBRARY_SEARCH_ITEM_INDEX
        }
    }
    val showFastScroller by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            layoutInfo.totalItemsCount > layoutInfo.visibleItemsInfo.size &&
                (gridState.canScrollBackward || gridState.canScrollForward)
        }
    }
    val currentQuery by rememberUpdatedState(query)
    val currentUseGrid by rememberUpdatedState(useGrid)
    val contentStatus = resolveLibraryContentStatus(
        browseLoadStatus = state.browseLoadStatus,
        isSearching = state.isSearching,
        hasSelectedServer = state.selectedServerId != null,
        selectedServerAvailable = state.servers.any { it.id == state.selectedServerId },
        visibleEntriesEmpty = visibleEntries.isEmpty(),
    )

    DisposableEffect(pageKey, gridState) {
        onDispose {
            val anchorKey = gridState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == gridState.firstVisibleItemIndex }
                ?.key as? String
            onSaveBrowseViewState(
                pageKey,
                BrowseViewState(
                    query = currentQuery,
                    useGrid = currentUseGrid,
                    anchorEntryKey = anchorKey?.takeIf { it.startsWith(MEDIA_ENTRY_KEY_PREFIX) },
                    fallbackItemIndex = gridState.firstVisibleItemIndex,
                    scrollOffset = gridState.firstVisibleItemScrollOffset,
                ),
            )
        }
    }

    BoxWithConstraints(modifier) {
        val gridSpacing = 12.dp
        val density = LocalDensity.current
        val availableWidth = (maxWidth - 40.dp).coerceAtLeast(1.dp)
        val minimumCellWidth = state.preferences.gallerySize.minCellSize.dp
        val columnCount = if (useGrid) {
            ((availableWidth + gridSpacing) / (minimumCellWidth + gridSpacing)).toInt().coerceAtLeast(1)
        } else {
            1
        }
        val artworkWidth = (availableWidth - gridSpacing * (columnCount - 1)) / columnCount
        val artworkRequestSizePx = with(density) { artworkWidth.roundToPx() }
        val minimumFastScrollerTopInsetPx = with(density) { LibraryFastScrollerTopInset.roundToPx() }
        val gridSpacingPx = with(density) { gridSpacing.roundToPx() }
        val fastScrollerTopInsetPx by remember(
            gridState,
            minimumFastScrollerTopInsetPx,
            gridSpacingPx,
        ) {
            derivedStateOf {
                val layoutInfo = gridState.layoutInfo
                val lastHeader = layoutInfo.visibleItemsInfo
                    .firstOrNull { it.index == LIBRARY_GRID_HEADER_COUNT - 1 }
                when {
                    lastHeader != null -> maxOf(
                        minimumFastScrollerTopInsetPx,
                        lastHeader.offset.y + lastHeader.size.height + gridSpacingPx,
                    )
                    gridState.firstVisibleItemIndex >= LIBRARY_GRID_HEADER_COUNT ->
                        minimumFastScrollerTopInsetPx
                    else -> null
                }
            }
        }
        val fastScrollerTopInset = fastScrollerTopInsetPx
            ?.let { insetPx -> with(density) { insetPx.toDp() } }
            ?.takeIf { topInset ->
                maxHeight - topInset - bottomContentPadding >= LibraryFastScrollerMinimumThumbHeight
            }

        AlbumArtworkPrefetchEffect(
            pageKey = pageKey,
            entries = visibleEntries,
            gridState = gridState,
            requestSizePx = artworkRequestSizePx,
            enabled = useGrid && isAlbumCollection && !state.isBrowsing && visibleEntries.isNotEmpty(),
            paused = isFastScrollerDragging,
        )

        LazyVerticalGrid(
            columns = if (useGrid) GridCells.Adaptive(minimumCellWidth) else GridCells.Fixed(1),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = 24.dp,
                end = 20.dp,
                bottom = bottomContentPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(gridSpacing),
            horizontalArrangement = Arrangement.spacedBy(gridSpacing),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, contentType = "library-header") {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
            }
            item(span = { GridItemSpan(maxLineSpan) }, contentType = "library-header") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(LibrarySearchControlHeight)
                        .focusRequester(searchFocusRequester),
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = {
                        if (query.isNotBlank()) IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, stringResource(R.string.clear_search))
                        }
                    },
                    placeholder = { Text(stringResource(R.string.search_current_directory)) },
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }, contentType = "library-header") {
                if (isAlbumCollection) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (state.path.size > 1) {
                                FilledTonalIconButton(onClick = { onNavigateTo(state.path.lastIndex - 1) }) {
                                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back_to_parent))
                                }
                                Spacer(Modifier.width(8.dp))
                            }
                            Breadcrumbs(state.path, onNavigateTo, Modifier.weight(1f))
                        }
                        AlbumCollectionToolbar(
                            albumCount = visibleEntries.size,
                            sort = state.albumSort,
                            useGrid = useGrid,
                            onSort = onAlbumSort,
                            onToggleLayout = { useGrid = !useGrid },
                        )
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.path.size > 1) {
                            FilledTonalIconButton(onClick = { onNavigateTo(state.path.lastIndex - 1) }) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back_to_parent))
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        Breadcrumbs(state.path, onNavigateTo, Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        FilledTonalIconButton(onClick = { useGrid = !useGrid }) {
                            Icon(
                                if (useGrid) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                                 if (useGrid) stringResource(R.string.switch_to_list) else stringResource(R.string.switch_to_grid),
                            )
                        }
                    }
                }
            }

            when (contentStatus) {
                LibraryContentStatus.LOADING -> item(
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "library-status",
                ) {
                    LoadingPanel(stringResource(R.string.loading_music_library))
                }
                LibraryContentStatus.NO_SERVER -> item(
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "library-status",
                ) {
                    EmptyPanel(
                        icon = Icons.Rounded.Devices,
                        title = stringResource(R.string.library_not_found_title),
                        body = stringResource(R.string.library_not_found_body),
                        action = stringResource(R.string.open_device_settings),
                        onAction = onOpenSettings,
                    )
                }
                LibraryContentStatus.SERVER_UNAVAILABLE -> item(
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "library-status",
                ) {
                    EmptyPanel(
                        icon = Icons.Rounded.Devices,
                        title = stringResource(R.string.library_unavailable_title),
                        body = stringResource(R.string.library_unavailable_body),
                        action = stringResource(R.string.open_device_settings),
                        onAction = onOpenSettings,
                    )
                }
                LibraryContentStatus.LOAD_FAILED -> item(
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "library-status",
                ) {
                    EmptyPanel(
                        icon = Icons.Rounded.MusicNote,
                        title = stringResource(R.string.library_load_failed_title),
                        body = stringResource(R.string.library_load_failed_body),
                        action = stringResource(R.string.open_device_settings),
                        onAction = onOpenSettings,
                    )
                }
                LibraryContentStatus.EMPTY -> item(
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "library-status",
                ) {
                    EmptyPanel(
                        icon = Icons.Rounded.MusicNote,
                        title = stringResource(if (query.isBlank()) R.string.empty_directory_title else R.string.no_matching_music_title),
                        body = stringResource(if (query.isBlank()) R.string.empty_directory_body else R.string.no_matching_music_body),
                    )
                }
                LibraryContentStatus.CONTENT -> gridItemsIndexed(
                    items = visibleEntries,
                    key = { _, it -> mediaEntryKey(it) },
                    contentType = { _, _ -> if (useGrid) "media-grid-card" else "media-list-row" },
                ) { index, entry ->
                    if (useGrid) {
                        MediaGridCard(
                            entry = entry,
                            gallerySize = state.preferences.gallerySize,
                            artworkRequestSizePx = artworkRequestSizePx,
                            onOpen = { onOpen(entry) },
                            onQueue = { onQueue(entry) },
                        )
                    } else {
                        MediaEntryRow(
                            entry = entry,
                            emphasized = index % 5 == 0,
                            onOpen = { onOpen(entry) },
                            onPlay = { onPlay(entry) },
                            onQueue = { onQueue(entry) },
                        )
                    }
                }
            }
        }

        fastScrollerTopInset?.let { topInset ->
            AnimatedVisibility(
                visible = showFastScroller,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(
                        top = topInset,
                        bottom = bottomContentPadding,
                    )
                    .width(LibraryFastScrollerTouchWidth),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                LibraryFastScroller(
                    gridState = gridState,
                    onDragStateChanged = { isFastScrollerDragging = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        AnimatedVisibility(
            visible = showFloatingSearchButton,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 20.dp, end = 20.dp),
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
        ) {
            FloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        gridState.animateScrollToItem(LIBRARY_TOP_ITEM_INDEX)
                        searchFocusRequester.requestFocus()
                    }
                },
                modifier = Modifier.size(LibrarySearchControlHeight),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Icon(Icons.Rounded.Search, stringResource(R.string.search_current_directory))
            }
        }
    }
}

@Composable
private fun LibraryFastScroller(
    gridState: LazyGridState,
    onDragStateChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layoutInfo = gridState.layoutInfo
    val totalItemCount = layoutInfo.totalItemsCount
    val visibleItemCount = layoutInfo.visibleItemsInfo.size.coerceAtMost(totalItemCount)
    if (totalItemCount <= 0 || visibleItemCount <= 0) return

    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val trackHeightPx = with(density) { maxHeight.toPx() }
        if (trackHeightPx <= 0f) return@BoxWithConstraints

        val minimumThumbHeightPx = with(density) { LibraryFastScrollerMinimumThumbHeight.toPx() }
        val thumbHeightPx = (trackHeightPx * visibleItemCount / totalItemCount)
            .coerceAtLeast(minimumThumbHeightPx.coerceAtMost(trackHeightPx))
            .coerceAtMost(trackHeightPx)
        val maximumThumbOffsetPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)
        val positionFraction = fastScrollPositionFraction(
            firstVisibleItemIndex = gridState.firstVisibleItemIndex,
            visibleItemCount = visibleItemCount,
            totalItemCount = totalItemCount,
        )
        val settledThumbOffsetPx = positionFraction * maximumThumbOffsetPx
        val thumbHeight = with(density) { thumbHeightPx.toDp() }
        var draggedThumbOffsetPx by remember { mutableStateOf<Float?>(null) }
        var requestedItemIndex by remember { mutableStateOf<Int?>(null) }
        val displayedThumbOffsetPx = draggedThumbOffsetPx ?: settledThumbOffsetPx
        val currentSettledThumbOffsetPx by rememberUpdatedState(settledThumbOffsetPx)
        val currentThumbHeightPx by rememberUpdatedState(thumbHeightPx)
        val currentMaximumThumbOffsetPx by rememberUpdatedState(maximumThumbOffsetPx)
        val currentVisibleItemCount by rememberUpdatedState(visibleItemCount)
        val currentTotalItemCount by rememberUpdatedState(totalItemCount)
        val currentOnDragStateChanged by rememberUpdatedState(onDragStateChanged)
        val fastScrollDescription = stringResource(R.string.fast_scroll_library)

        DisposableEffect(Unit) {
            onDispose { currentOnDragStateChanged(false) }
        }

        LaunchedEffect(requestedItemIndex) {
            val targetItemIndex = requestedItemIndex ?: return@LaunchedEffect
            gridState.scrollToItem(targetItemIndex)
            if (requestedItemIndex == targetItemIndex) requestedItemIndex = null
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = fastScrollDescription
                    progressBarRangeInfo = ProgressBarRangeInfo(positionFraction, 0f..1f)
                    setProgress { requestedFraction ->
                        requestedItemIndex = fastScrollTargetItemIndex(
                            positionFraction = requestedFraction,
                            visibleItemCount = visibleItemCount,
                            totalItemCount = totalItemCount,
                        )
                        true
                    }
                }
                .pointerInput(gridState) {
                    detectVerticalDragGestures(
                        onDragStart = { position ->
                            currentOnDragStateChanged(true)
                            val settledOffset = currentSettledThumbOffsetPx
                            val thumbEnd = settledOffset + currentThumbHeightPx
                            val startOffset = if (position.y in settledOffset..thumbEnd) {
                                settledOffset
                            } else {
                                (position.y - currentThumbHeightPx / 2f)
                                    .coerceIn(0f, currentMaximumThumbOffsetPx)
                            }
                            draggedThumbOffsetPx = startOffset
                            val requestedFraction = if (currentMaximumThumbOffsetPx > 0f) {
                                startOffset / currentMaximumThumbOffsetPx
                            } else {
                                0f
                            }
                            requestedItemIndex = fastScrollTargetItemIndex(
                                positionFraction = requestedFraction,
                                visibleItemCount = currentVisibleItemCount,
                                totalItemCount = currentTotalItemCount,
                            )
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            val newOffset = ((draggedThumbOffsetPx ?: currentSettledThumbOffsetPx) + dragAmount)
                                .coerceIn(0f, currentMaximumThumbOffsetPx)
                            draggedThumbOffsetPx = newOffset
                            val requestedFraction = if (currentMaximumThumbOffsetPx > 0f) {
                                newOffset / currentMaximumThumbOffsetPx
                            } else {
                                0f
                            }
                            requestedItemIndex = fastScrollTargetItemIndex(
                                positionFraction = requestedFraction,
                                visibleItemCount = currentVisibleItemCount,
                                totalItemCount = currentTotalItemCount,
                            )
                        },
                        onDragEnd = {
                            draggedThumbOffsetPx = null
                            currentOnDragStateChanged(false)
                        },
                        onDragCancel = {
                            draggedThumbOffsetPx = null
                            currentOnDragStateChanged(false)
                        },
                    )
                },
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 6.dp)
                    .fillMaxHeight()
                    .width(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 4.dp)
                    .offset { IntOffset(0, displayedThumbOffsetPx.roundToInt()) }
                    .width(8.dp)
                    .height(thumbHeight)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)),
            )
        }
    }
}

@Composable
private fun AlbumCollectionToolbar(
    albumCount: Int,
    sort: AlbumSort,
    useGrid: Boolean,
    onSort: (AlbumSort) -> Unit,
    onToggleLayout: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(start = 18.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    pluralStringResource(R.plurals.album_count, albumCount, albumCount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.browse_and_sort),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AlbumSortPicker(sort, onSort)
            Spacer(Modifier.width(6.dp))
            FilledTonalIconButton(onClick = onToggleLayout) {
                Icon(
                    if (useGrid) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                    if (useGrid) stringResource(R.string.switch_to_list) else stringResource(R.string.switch_to_grid),
                )
            }
        }
    }
}

@Composable
private fun AlbumSortPicker(
    selected: AlbumSort,
    onSelected: (AlbumSort) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { expanded = true },
            label = { Text(selected.shortLabel()) },
            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Sort, null, Modifier.size(18.dp)) },
            trailingIcon = { Icon(Icons.Rounded.ExpandMore, null, Modifier.size(18.dp)) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AlbumSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(option.menuLabel())
                            option.explanation()?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    leadingIcon = {
                        if (option == selected) Icon(Icons.Rounded.Check, null)
                        else Spacer(Modifier.size(24.dp))
                    },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun AlbumSort.shortLabel(): String = stringResource(
    when (this) {
        AlbumSort.SERVER_DEFAULT -> R.string.album_sort_server_default_short
        AlbumSort.YEAR_ASCENDING -> R.string.album_sort_year_ascending_short
        AlbumSort.YEAR_DESCENDING -> R.string.album_sort_year_descending_short
        AlbumSort.ALBUM_ARTIST -> R.string.album_sort_album_artist_short
        AlbumSort.TITLE -> R.string.album_sort_title_short
    },
)

@Composable
private fun AlbumSort.menuLabel(): String = stringResource(
    when (this) {
        AlbumSort.SERVER_DEFAULT -> R.string.album_sort_server_default
        AlbumSort.YEAR_ASCENDING -> R.string.album_sort_year_ascending
        AlbumSort.YEAR_DESCENDING -> R.string.album_sort_year_descending
        AlbumSort.ALBUM_ARTIST -> R.string.album_sort_album_artist_short
        AlbumSort.TITLE -> R.string.album_sort_title_short
    },
)

@Composable
private fun AlbumSort.explanation(): String? = when (this) {
    AlbumSort.SERVER_DEFAULT -> stringResource(R.string.album_sort_server_default_explanation)
    AlbumSort.TITLE -> stringResource(R.string.album_sort_title_explanation)
    else -> null
}

@Composable
private fun AlbumDetailScreen(
    state: LibraryUiState,
    pageKey: BrowsePageKey,
    initialViewState: BrowseViewState,
    onSaveBrowseViewState: (BrowsePageKey, BrowseViewState) -> Unit,
    onResolveAlbumArtwork: (BrowsePageKey, Int) -> Unit,
    onPlay: (MediaEntry) -> Unit,
    onQueue: (MediaEntry) -> Unit,
    onPlayAll: (List<MediaEntry>) -> Unit,
    onShufflePlay: (List<MediaEntry>) -> Unit,
    onQueueAll: (List<MediaEntry>) -> Unit,
    bottomContentPadding: Dp,
) {
    val tracks = remember(state.entries) { state.entries.filterNot { it.isContainer } }
    val representativeTrack = tracks.firstOrNull()
    val currentLocation = state.path.lastOrNull()
    val title = currentLocation?.title ?: representativeTrack?.album.orEmpty()
    val resolvedArtworkUri = state.albumArtwork
        ?.takeIf { it.pageKey == pageKey }
        ?.displayUri
    val headerArtworkEntry = currentLocation?.let { location ->
        MediaEntry(
            id = location.id,
            parentId = state.path.getOrNull(state.path.lastIndex - 1)?.id.orEmpty(),
            title = title,
            creator = location.albumArtist.orEmpty(),
            albumArtist = location.albumArtist.orEmpty(),
            year = location.year,
            artworkUri = resolvedArtworkUri
                ?: selectThumbnailArtworkUri(location.artworkCandidates, location.artworkUri)
                ?: representativeTrack?.thumbnailArtworkUri,
            isContainer = true,
            isAlbum = true,
        )
    } ?: representativeTrack?.copy(
        isContainer = true,
        isAlbum = true,
    )
    val artists = currentLocation?.albumArtist.orEmpty().ifBlank {
        tracks.map { it.creator }.filter { it.isNotBlank() }.distinct().take(2).joinToString(" · ")
    }
    val trackCount = tracks.size
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialBrowseItemIndex(
            entries = tracks,
            viewState = initialViewState,
            headerCount = ALBUM_DETAIL_HEADER_COUNT,
        ),
        initialFirstVisibleItemScrollOffset = initialViewState.scrollOffset.coerceAtLeast(0),
    )

    DisposableEffect(pageKey, listState) {
        onDispose {
            val anchorKey = listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == listState.firstVisibleItemIndex }
                ?.key as? String
            onSaveBrowseViewState(
                pageKey,
                initialViewState.copy(
                    anchorEntryKey = anchorKey?.takeIf { it.startsWith(MEDIA_ENTRY_KEY_PREFIX) },
                    fallbackItemIndex = listState.firstVisibleItemIndex,
                    scrollOffset = listState.firstVisibleItemScrollOffset,
                ),
            )
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = 24.dp,
            end = 20.dp,
            bottom = bottomContentPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                headerArtworkEntry?.let { entry ->
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        val artworkSize = minOf(maxWidth, maxHeight, LargeSquareContentMaxSize)
                        val artworkSizePx = with(LocalDensity.current) { artworkSize.roundToPx() }
                        LaunchedEffect(pageKey, artworkSizePx, state.entries, state.browseLoadStatus) {
                            if (state.browseLoadStatus == BrowseLoadStatus.LOADED) {
                                onResolveAlbumArtwork(pageKey, artworkSizePx)
                            }
                        }
                        ArtworkTile(
                            entry = entry,
                            size = artworkSize,
                            imageIdentity = pageKey,
                            requestSizePx = artworkSizePx,
                            useCachedAlbumThumbnailAsPlaceholder = true,
                            preferThumbnailSource = false,
                            placeholderArtworkUri = currentLocation?.let { location ->
                                selectThumbnailArtworkUri(location.artworkCandidates, location.artworkUri)
                            } ?: representativeTrack?.thumbnailArtworkUri,
                            filterQuality = FilterQuality.High,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                if (artists.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(artists, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (state.isBrowsing) stringResource(R.string.loading_music_content)
                    else pluralStringResource(R.plurals.track_count, trackCount, trackCount),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(
                        onClick = { onPlayAll(tracks) },
                        enabled = !state.isBrowsing && trackCount > 0,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.play_all))
                    }
                    OutlinedButton(
                        onClick = { onShufflePlay(tracks) },
                        enabled = !state.isBrowsing && trackCount > 0,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp),
                    ) {
                        Icon(Icons.Rounded.Shuffle, null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.shuffle_play))
                    }
                    OutlinedButton(
                        onClick = { onQueueAll(tracks) },
                        enabled = !state.isBrowsing && trackCount > 0,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp),
                    ) {
                        Icon(Icons.Rounded.Add, null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.add_to_queue))
                    }
                }
            }
        }
        if (!state.isBrowsing && trackCount == 0) {
            item {
                EmptyPanel(
                    icon = Icons.Rounded.MusicNote,
                    title = stringResource(R.string.album_no_playable_tracks_title),
                    body = stringResource(R.string.album_no_playable_tracks_body),
                )
            }
        }
        itemsIndexed(
            items = tracks,
            key = { _, item -> mediaEntryKey(item) },
        ) { index, track ->
                TrackCollectionRow(
                    track = track,
                    number = index + 1,
                    isPlaying = state.currentTrackId == track.id && state.playbackState == RemotePlaybackState.PLAYING,
                    onPlay = { onPlay(track) },
                    onQueue = { onQueue(track) },
                )
        }
    }
}

@Composable
private fun TrackCollectionRow(
    track: MediaEntry,
    number: Int,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onQueue: () -> Unit,
) {
    val supportingContentColor = if (isPlaying) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onPlay),
        color = if (isPlaying) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) {
                if (isPlaying) {
                    Icon(
                        Icons.Rounded.GraphicEq,
                        stringResource(R.string.playing),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else {
                    Text(number.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    track.creator.ifBlank { track.album.ifBlank { stringResource(R.string.unknown_artist) } },
                    style = MaterialTheme.typography.bodySmall,
                    color = supportingContentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                track.duration?.let { formatPlaybackTime(LsMusicViewModel.parseTimeMs(it)) } ?: "--:--",
                style = MaterialTheme.typography.labelMedium,
                color = supportingContentColor,
            )
            IconButton(onClick = onQueue) { Icon(Icons.Rounded.Add, stringResource(R.string.add_to_queue)) }
        }
    }
}

@Composable
internal fun DeviceStrip(
    state: LsMusicUiState,
    onSelectServer: (String) -> Unit,
    onSelectRenderer: (String) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(30.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DevicePicker(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.music_from),
                icon = Icons.Rounded.LibraryMusic,
                devices = state.servers,
                selectedId = state.selectedServerId,
                rememberedDevice = state.rememberedServer,
                rememberedLabel = stringResource(R.string.last_used_library),
                isSearching = state.isSearching,
                emptyLabel = stringResource(R.string.no_library_found),
                onSelected = onSelectServer,
            )
            DevicePicker(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.play_to),
                icon = Icons.Rounded.Speaker,
                devices = state.renderers,
                selectedId = state.selectedRendererId,
                rememberedDevice = state.rememberedRenderer,
                rememberedLabel = stringResource(R.string.last_used_player),
                isSearching = state.isSearching,
                emptyLabel = stringResource(R.string.choose_player),
                onSelected = onSelectRenderer,
            )
        }
    }
}

@Composable
private fun DevicePicker(
    label: String,
    icon: ImageVector,
    devices: List<DlnaDevice>,
    selectedId: String?,
    rememberedDevice: DlnaDevice?,
    rememberedLabel: String,
    isSearching: Boolean,
    emptyLabel: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = devices.firstOrNull { it.id == selectedId }
    val rememberedSelection = rememberedDevice?.takeIf { it.id == selectedId }
    val selectedName = when {
        selected?.id == LsMusicViewModel.LOCAL_RENDERER_ID -> stringResource(R.string.local_player_name)
        !selected?.name.isNullOrBlank() -> selected?.name
        rememberedSelection?.id == LsMusicViewModel.LOCAL_RENDERER_ID -> stringResource(R.string.local_player_name)
        !rememberedSelection?.name.isNullOrBlank() -> rememberedSelection?.name
        rememberedSelection != null -> rememberedLabel
        else -> emptyLabel
    }
    val connectionStatus = when {
        selected != null || rememberedSelection == null -> null
        isSearching -> stringResource(R.string.connecting)
        else -> stringResource(R.string.currently_unavailable)
    }
    Box(modifier) {
        Surface(
            modifier = Modifier.fillMaxWidth().clickable(enabled = devices.isNotEmpty()) { expanded = true },
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = .7f),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(label, style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        listOfNotNull(selectedName, connectionStatus).joinToString(" · "),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Icon(Icons.Rounded.ExpandMore, null, Modifier.size(18.dp))
                }
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            devices.forEach { device ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(if (device.id == LsMusicViewModel.LOCAL_RENDERER_ID) stringResource(R.string.local_player_name) else device.name)
                            val detail = if (device.id == LsMusicViewModel.LOCAL_RENDERER_ID) {
                                stringResource(R.string.local_player_model)
                            } else {
                                listOf(device.manufacturer, device.model).filter { it.isNotBlank() }.joinToString(" · ")
                            }
                            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelected(device.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun Breadcrumbs(
    path: List<BrowseLocation>,
    onNavigateTo: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        path.forEachIndexed { index, location ->
            if (index > 0) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(18.dp))
            AssistChip(
                onClick = { onNavigateTo(index) },
                label = { Text(location.title ?: stringResource(R.string.library_root)) },
            )
        }
    }
}

@Composable
private fun MediaGridCard(
    entry: MediaEntry,
    gallerySize: GallerySize,
    artworkRequestSizePx: Int,
    onOpen: () -> Unit,
    onQueue: () -> Unit,
) {
    val cardCornerRadius = when (gallerySize) {
        GallerySize.COMPACT -> 16.dp
        GallerySize.STANDARD -> 21.dp
        GallerySize.LARGE -> 26.dp
    }
    val artworkCornerRadius = when (gallerySize) {
        GallerySize.COMPACT -> if (entry.isContainer) 10.dp else 9.dp
        GallerySize.STANDARD -> if (entry.isContainer) 14.dp else 12.dp
        GallerySize.LARGE -> if (entry.isContainer) 18.dp else 16.dp
    }
    val titleStyle = when (gallerySize) {
        GallerySize.COMPACT -> MaterialTheme.typography.bodySmall
        GallerySize.STANDARD -> MaterialTheme.typography.bodyMedium
        GallerySize.LARGE -> MaterialTheme.typography.titleMedium
    }
    val detailStyle = when (gallerySize) {
        GallerySize.COMPACT -> MaterialTheme.typography.labelSmall.copy(
            fontSize = MaterialTheme.typography.labelSmall.fontSize * .9f,
        )
        GallerySize.STANDARD -> MaterialTheme.typography.labelSmall
        GallerySize.LARGE -> MaterialTheme.typography.bodySmall
    }
    val titleMinHeight = when (gallerySize) {
        GallerySize.COMPACT -> 32.dp
        GallerySize.STANDARD -> 40.dp
        GallerySize.LARGE -> 48.dp
    }
    val horizontalPadding = when (gallerySize) {
        GallerySize.COMPACT -> 9.dp
        GallerySize.STANDARD -> 12.dp
        GallerySize.LARGE -> 14.dp
    }
    val verticalPadding = when (gallerySize) {
        GallerySize.COMPACT -> 8.dp
        GallerySize.STANDARD -> 10.dp
        GallerySize.LARGE -> 12.dp
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        ArtworkTile(
            entry = entry,
            size = null,
            requestSizePx = artworkRequestSizePx,
            retryTransientFailures = true,
            cornerRadius = artworkCornerRadius,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                start = horizontalPadding,
                top = verticalPadding,
                end = if (entry.isContainer) horizontalPadding else 8.dp,
                bottom = verticalPadding,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    entry.title,
                    modifier = Modifier.heightIn(min = titleMinHeight),
                    style = titleStyle,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    when {
                        entry.isAlbum -> albumDetails(entry)
                        entry.isContainer && entry.childCount != null ->
                            pluralStringResource(R.plurals.directory_item_count, entry.childCount, entry.childCount)
                        entry.creator.isNotBlank() -> entry.creator
                        entry.album.isNotBlank() -> entry.album
                        entry.isContainer -> stringResource(R.string.folder)
                        else -> entry.duration ?: stringResource(R.string.audio)
                    },
                    style = detailStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!entry.isContainer) {
                IconButton(onClick = onQueue) { Icon(Icons.Rounded.Add, stringResource(R.string.add_to_queue)) }
            }
        }
    }
}

@Composable
private fun MediaEntryRow(
    entry: MediaEntry,
    emphasized: Boolean,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onQueue: () -> Unit,
) {
    val background = if (emphasized) MaterialTheme.colorScheme.tertiaryContainer
    else MaterialTheme.colorScheme.surfaceContainer
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        color = background,
        shape = RoundedCornerShape(if (emphasized) 28.dp else 20.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ArtworkTile(entry, if (emphasized) 62.dp else 54.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val details = when {
                    entry.isAlbum -> albumDetails(entry)
                    entry.isContainer && entry.childCount != null ->
                        pluralStringResource(R.plurals.directory_item_count, entry.childCount, entry.childCount)
                    entry.creator.isNotBlank() && !entry.duration.isNullOrBlank() -> "${entry.creator} · ${entry.duration}"
                    entry.creator.isNotBlank() -> entry.creator
                    !entry.duration.isNullOrBlank() -> entry.duration
                    entry.isContainer -> stringResource(R.string.folder)
                    else -> entry.mimeType?.substringAfter('/')?.uppercase() ?: stringResource(R.string.audio)
                }
                Text(details, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            if (entry.isContainer) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.open))
            } else {
                IconButton(onClick = onQueue) { Icon(Icons.Rounded.Add, stringResource(R.string.add_to_queue)) }
                FilledTonalIconButton(onClick = onPlay) { Icon(Icons.Rounded.PlayArrow, stringResource(R.string.play_now)) }
            }
        }
    }
}

@Composable
private fun albumDetails(entry: MediaEntry): String = listOfNotNull(
    entry.albumArtist.takeIf { it.isNotBlank() },
    entry.year?.toString(),
    entry.childCount?.let { pluralStringResource(R.plurals.track_count, it, it) },
).joinToString(" · ").ifBlank { stringResource(R.string.album) }

@Immutable
private data class QueueDisplayItem(
    val key: String,
    val stateIndex: Int,
    val entry: MediaEntry,
)

private class QueueDisplayKeyGenerator {
    private var nextKey = 0L

    fun next(entry: MediaEntry): String = "queue:${nextKey++}:${entry.id}"
}

private fun reconcileQueueDisplayItems(
    currentItems: List<QueueDisplayItem>,
    queue: List<MediaEntry>,
    keyGenerator: QueueDisplayKeyGenerator,
): List<QueueDisplayItem> {
    val unmatchedItems = currentItems.toMutableList()
    return queue.mapIndexed { index, entry ->
        val matchIndex = unmatchedItems.indexOfFirst { it.entry.id == entry.id }
        if (matchIndex >= 0) {
            unmatchedItems.removeAt(matchIndex).copy(stateIndex = index, entry = entry)
        } else {
            QueueDisplayItem(
                key = keyGenerator.next(entry),
                stateIndex = index,
                entry = entry,
            )
        }
    }
}

private data class QueueDragHandleInfo(
    val itemKey: String,
    val boundsInRoot: Rect,
    val pinnableContainer: PinnableContainer?,
)

private suspend fun PointerInputScope.detectQueueReorderGestures(
    handleAt: (Offset) -> QueueDragHandleInfo?,
    reorderState: LazyListReorderState,
    onDragStart: () -> Unit,
    onDragEnd: (String) -> Unit,
    onDragCancel: (String) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val handle = handleAt(down.position) ?: return@awaitEachGesture
        val longPress = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
        longPress.consume()
        reorderState.startDragging(handle.itemKey, handle.pinnableContainer)
        if (!reorderState.isDragging(handle.itemKey)) return@awaitEachGesture
        onDragStart()

        try {
            val completed = drag(longPress.id) { change ->
                val distance = change.position.y - change.previousPosition.y
                change.consume()
                reorderState.dragBy(distance)
            }
            if (completed) currentEvent.changes.forEach { it.consume() }
            if (completed) onDragEnd(handle.itemKey) else onDragCancel(handle.itemKey)
        } finally {
            reorderState.stopDragging()
        }
    }
}

@Composable
private fun QueueScreen(
    state: LsMusicUiState,
    onPlay: (MediaEntry) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onClear: () -> Unit,
    bottomContentPadding: Dp,
) {
    if (state.queue.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp, 24.dp, 20.dp, bottomContentPadding),
        ) {
            Column {
                Text(stringResource(R.string.queue_up_next), style = MaterialTheme.typography.headlineLarge)
                Text(pluralStringResource(R.plurals.queue_summary, 0, 0), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                EmptyPanel(Icons.AutoMirrored.Rounded.PlaylistPlay, stringResource(R.string.queue_empty))
            }
        }
        return
    }
    val queueDisplayKeyGenerator = remember { QueueDisplayKeyGenerator() }
    var displayedQueue by remember {
        mutableStateOf(
            state.queue.mapIndexed { index, entry ->
                QueueDisplayItem(
                    key = queueDisplayKeyGenerator.next(entry),
                    stateIndex = index,
                    entry = entry,
                )
            },
        )
    }
    val listState = rememberLazyListState()
    val reorderState = remember(listState) {
        LazyListReorderState(listState = listState, itemIndexOffset = 1)
    }
    val dragHandles = remember { mutableMapOf<String, QueueDragHandleInfo>() }
    val listPositionInRoot = remember { mutableStateOf(Offset.Zero) }
    var suppressItemClicksUntilMs by remember { mutableLongStateOf(0L) }
    val currentDisplayedQueue by rememberUpdatedState(displayedQueue)
    val currentOnMove by rememberUpdatedState(onMove)
    reorderState.onMove = { fromIndex, toIndex ->
        displayedQueue = moveListItem(displayedQueue, fromIndex, toIndex)
    }
    val suppressQueueItemClicks = {
        suppressItemClicksUntilMs =
            SystemClock.elapsedRealtime() + QueuePostDragClickSuppressionMs
    }
    val commitQueueDrag: (String) -> Unit = { itemKey ->
        val queue = currentDisplayedQueue
        val destination = queue.indexOfFirst { it.key == itemKey }
        val draggedItem = queue.getOrNull(destination)
        if (draggedItem != null && destination != draggedItem.stateIndex) {
            currentOnMove(draggedItem.stateIndex, destination)
        }
        suppressQueueItemClicks()
    }
    LaunchedEffect(state.queue) {
        displayedQueue = reconcileQueueDisplayItems(
            currentItems = displayedQueue,
            queue = state.queue,
            keyGenerator = queueDisplayKeyGenerator,
        )
    }
    val density = LocalDensity.current
    val reorderEdgeSizePx = with(density) { ReorderEdgeScrollSize.toPx() }
    val reorderMaximumScrollPx = with(density) { ReorderMaximumScrollPerFrame.toPx() }

    LaunchedEffect(reorderState.draggedItemKey, reorderEdgeSizePx, reorderMaximumScrollPx) {
        while (reorderState.draggedItemKey != null) {
            withFrameNanos { }
            reorderState.scrollAtEdge(reorderEdgeSizePx, reorderMaximumScrollPx)
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { listPositionInRoot.value = it.positionInRoot() }
            .pointerInput(reorderState, dragHandles, listPositionInRoot) {
                detectQueueReorderGestures(
                    handleAt = { localPosition ->
                        val rootPosition = localPosition + listPositionInRoot.value
                        dragHandles.values.firstOrNull { it.boundsInRoot.contains(rootPosition) }
                    },
                    reorderState = reorderState,
                    onDragStart = suppressQueueItemClicks,
                    onDragEnd = commitQueueDrag,
                    onDragCancel = commitQueueDrag,
                )
            },
        contentPadding = PaddingValues(20.dp, 24.dp, 20.dp, bottomContentPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        userScrollEnabled = reorderState.draggedItemKey == null,
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.queue_up_next), style = MaterialTheme.typography.headlineLarge)
                    Text(pluralStringResource(R.plurals.queue_summary, state.queue.size, state.queue.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.queue.isNotEmpty()) FilledTonalButton(onClick = onClear) {
                    Icon(Icons.Rounded.ClearAll, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.clear))
                }
            }
        }
        itemsIndexed(displayedQueue, key = { _, item -> item.key }) { index, queueItem ->
            val item = queueItem.entry
            val dragged = reorderState.isDragging(queueItem.key)
            val pinnableContainer = LocalPinnableContainer.current
            DisposableEffect(queueItem.key) {
                onDispose { dragHandles.remove(queueItem.key) }
            }
            val playing = item.id == state.currentTrack?.id
            val scale by animateFloatAsState(if (dragged) 1.025f else 1f, label = "queue-drag-scale")
            val elevation by animateDpAsState(if (dragged) 6.dp else 0.dp, label = "queue-drag-elevation")
            val normalContainerColor = if (playing) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
            val containerColor by animateColorAsState(
                if (dragged) MaterialTheme.colorScheme.secondaryContainer else normalContainerColor,
                label = "queue-drag-color",
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
                shape = RoundedCornerShape(if (playing) 28.dp else 18.dp),
                color = containerColor,
                shadowElevation = elevation,
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                if (SystemClock.elapsedRealtime() >= suppressItemClicksUntilMs) {
                                    onPlay(item)
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(30.dp), contentAlignment = Alignment.Center) {
                            if (playing) {
                                Icon(
                                    Icons.Rounded.GraphicEq,
                                    stringResource(R.string.playing),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            } else {
                                Text("${index + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        ArtworkTile(item, 48.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            Text(item.creator.ifBlank { item.duration ?: stringResource(R.string.audio) }, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Icon(
                        Icons.Rounded.DragHandle,
                        stringResource(R.string.reorder_queue, item.title),
                        modifier = Modifier
                            .size(44.dp)
                            .onGloballyPositioned { coordinates ->
                                dragHandles[queueItem.key] = QueueDragHandleInfo(
                                    itemKey = queueItem.key,
                                    boundsInRoot = coordinates.boundsInRoot(),
                                    pinnableContainer = pinnableContainer,
                                )
                            }
                            .padding(10.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IconButton(onClick = { onRemove(queueItem.stateIndex) }) {
                        Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.remove))
                    }
                }
            }
        }
    }
}

internal fun shouldShowLyricsBesideArtwork(widthDp: Float, heightDp: Float): Boolean {
    if (widthDp <= 0f || heightDp <= 0f) return false
    return widthDp >= LYRICS_SIDE_BY_SIDE_MIN_WIDTH_DP &&
        heightDp >= LYRICS_SIDE_BY_SIDE_MIN_HEIGHT_DP &&
        widthDp / heightDp >= LYRICS_SIDE_BY_SIDE_MIN_ASPECT_RATIO
}

private const val LYRICS_SIDE_BY_SIDE_MIN_WIDTH_DP = 720f
private const val LYRICS_SIDE_BY_SIDE_MIN_HEIGHT_DP = 560f
private const val LYRICS_SIDE_BY_SIDE_MIN_ASPECT_RATIO = 1.25f

@Composable
internal fun NowPlayingScreen(
    state: LsMusicUiState,
    modifier: Modifier = Modifier,
    onTogglePlayback: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onSeek: (Long) -> Unit,
    onLoadLyrics: () -> Unit,
    onRetryLyrics: () -> Unit,
) {
    val track = state.currentTrack
    if (track == null) {
        Column(
            modifier = modifier.fillMaxSize().padding(24.dp, 28.dp, 24.dp, 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.nav_now_playing),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineLarge,
            )
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                EmptyPanel(Icons.Rounded.Album, stringResource(R.string.no_current_track))
            }
        }
        return
    }
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.preferences.lyricsEnabled) {
        if (!state.preferences.lyricsEnabled) showLyrics = false
    }
    LaunchedEffect(
        showLyrics,
        state.playbackGeneration,
        state.currentTrack?.id,
        state.preferences.lyricsEnabled,
        state.preferences.lyricsProviderOrder,
    ) {
        if (showLyrics && state.preferences.lyricsEnabled) onLoadLyrics()
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val compact = maxHeight < 620.dp
        val wide = maxWidth >= 720.dp
        val canShowSideBySide = shouldShowLyricsBesideArtwork(maxWidth.value, maxHeight.value)
        val verticalPadding = if (compact) 16.dp else 28.dp
        val horizontalPadding = if (wide) 40.dp else 24.dp
        val trackTitleStyle = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall
        val artistStyle = if (compact) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium
        val albumStyle = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium
        val primaryControlSize = if (compact) 68.dp else 82.dp
        val secondaryControlSize = if (compact) 50.dp else 58.dp
        val controlIconSize = if (compact) 28.dp else 30.dp
        val primaryIconSize = if (compact) 36.dp else 42.dp

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontalPadding, verticalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.nav_now_playing),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineLarge,
            )
            Spacer(Modifier.height(if (compact) 8.dp else 16.dp))
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                if (showLyrics && canShowSideBySide) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BoxWithConstraints(
                            modifier = Modifier.weight(7f).fillMaxHeight(),
                            contentAlignment = Alignment.Center,
                        ) {
                            val artworkSize = minOf(maxWidth, maxHeight * .96f, 440.dp)
                            val artworkContentDescription = if (state.preferences.lyricsEnabled) {
                                stringResource(R.string.album_cover_open_lyrics, track.title)
                            } else {
                                stringResource(R.string.album_cover, track.title)
                            }
                            Box(
                                modifier = Modifier
                                    .semantics {
                                        contentDescription = artworkContentDescription
                                    }
                                    .then(
                                        if (state.preferences.lyricsEnabled) {
                                            Modifier.clickable(
                                                onClickLabel = stringResource(R.string.open_lyrics),
                                            ) { showLyrics = true }
                                        } else {
                                            Modifier
                                        },
                                    ),
                            ) { HeroArtwork(track, artworkSize) }
                        }
                        LyricsPanel(
                            loadState = state.lyricsLoadState,
                            positionMs = state.positionMs,
                            durationMs = state.durationMs,
                            isPlaying = state.playbackState == RemotePlaybackState.PLAYING,
                            translationMode = state.preferences.lyricsTranslationMode,
                            sourceVisible = state.preferences.lyricsSourceVisible,
                            effectsEnabled = state.preferences.lyricsEffectsEnabled,
                            fontSizeSp = state.preferences.lyricsFontSizeSp,
                            onRetry = onRetryLyrics,
                            onClose = { showLyrics = false },
                            modifier = Modifier.weight(8f).fillMaxHeight(),
                        )
                    }
                } else if (showLyrics) {
                    LyricsPanel(
                        loadState = state.lyricsLoadState,
                        positionMs = state.positionMs,
                        durationMs = state.durationMs,
                        isPlaying = state.playbackState == RemotePlaybackState.PLAYING,
                        translationMode = state.preferences.lyricsTranslationMode,
                        sourceVisible = state.preferences.lyricsSourceVisible,
                        effectsEnabled = state.preferences.lyricsEffectsEnabled,
                        fontSizeSp = state.preferences.lyricsFontSizeSp,
                        onRetry = onRetryLyrics,
                        onClose = { showLyrics = false },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    val maximumArtwork = if (wide) 440.dp else 360.dp
                    val artworkSize = minOf(
                        maxWidth * if (wide) .68f else .84f,
                        maxHeight * .96f,
                        maximumArtwork,
                    )
                    val artworkContentDescription = if (state.preferences.lyricsEnabled) {
                        stringResource(R.string.album_cover_open_lyrics, track.title)
                    } else {
                        stringResource(R.string.album_cover, track.title)
                    }
                    Box(
                        modifier = Modifier
                            .semantics {
                                contentDescription = artworkContentDescription
                            }
                            .then(
                                if (state.preferences.lyricsEnabled) {
                                    Modifier.clickable(
                                        onClickLabel = stringResource(R.string.open_lyrics),
                                    ) { showLyrics = true }
                                } else {
                                    Modifier
                                },
                            ),
                    ) { HeroArtwork(track, artworkSize) }
                }
            }
            Spacer(Modifier.height(if (compact) 8.dp else 16.dp))
            Text(
                track.title,
                modifier = Modifier.fillMaxWidth(),
                style = trackTitleStyle,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                track.creator.ifBlank { stringResource(R.string.unknown_artist) },
                modifier = Modifier.fillMaxWidth(),
                style = artistStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                track.album.ifBlank { stringResource(R.string.unknown_album) },
                modifier = Modifier.fillMaxWidth(),
                style = albumStyle,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(if (compact) 8.dp else 16.dp))
            PlaybackSlider(
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                isPlaying = state.playbackState == RemotePlaybackState.PLAYING,
                onSeek = onSeek,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatPlaybackTime(state.positionMs), style = MaterialTheme.typography.labelMedium)
                Text(
                    if (state.durationMs > 0L) formatPlaybackTime(state.durationMs) else track.duration ?: "--:--",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Spacer(Modifier.height(if (compact) 6.dp else 12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 14.dp),
            ) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        if (state.playbackOrder.shuffleEnabled) {
                            Icons.Rounded.Shuffle
                        } else {
                            Icons.Rounded.FormatListNumbered
                        },
                        if (state.playbackOrder.shuffleEnabled) {
                            stringResource(R.string.shuffle_on_accessibility)
                        } else {
                            stringResource(R.string.shuffle_off_accessibility)
                        },
                        tint = if (state.playbackOrder.shuffleEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                FilledTonalIconButton(
                    onClick = onPrevious,
                    enabled = state.currentQueueIndex > 0,
                    modifier = Modifier.size(secondaryControlSize),
                ) {
                    Icon(Icons.Rounded.SkipPrevious, stringResource(R.string.previous_track), Modifier.size(controlIconSize))
                }
                FilledIconButton(onClick = onTogglePlayback, modifier = Modifier.size(primaryControlSize)) {
                    AnimatedContent(state.playbackState, label = "play pause") { playback ->
                        Icon(
                            if (playback == RemotePlaybackState.PLAYING) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            if (playback == RemotePlaybackState.PLAYING) stringResource(R.string.pause) else stringResource(R.string.play),
                            Modifier.size(primaryIconSize),
                        )
                    }
                }
                FilledTonalIconButton(
                    onClick = onNext,
                    enabled = canSelectNextTrack(state.queue, state.currentQueueIndex, state.playbackOrder),
                    modifier = Modifier.size(secondaryControlSize),
                ) { Icon(Icons.Rounded.SkipNext, stringResource(R.string.next_track), Modifier.size(controlIconSize)) }
                IconButton(onClick = onCycleRepeat) {
                    Icon(
                        when (state.playbackOrder.repeatMode) {
                            RepeatMode.NONE -> Icons.Rounded.SyncDisabled
                            RepeatMode.ONE -> Icons.Rounded.RepeatOne
                            RepeatMode.ALL -> Icons.Rounded.Repeat
                        },
                        when (state.playbackOrder.repeatMode) {
                            RepeatMode.NONE -> stringResource(R.string.repeat_off_accessibility)
                            RepeatMode.ONE -> stringResource(R.string.repeat_one_accessibility)
                            RepeatMode.ALL -> stringResource(R.string.repeat_all_accessibility)
                        },
                        tint = if (state.playbackOrder.repeatMode == RepeatMode.NONE) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaybackSlider(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
) {
    var draggedFraction by remember { mutableStateOf<Float?>(null) }
    val liveFraction = if (durationMs > 0L) {
        positionMs.toFloat().div(durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    Slider(
        value = draggedFraction ?: liveFraction,
        onValueChange = { draggedFraction = it },
        onValueChangeFinished = {
            draggedFraction?.let { onSeek((it * durationMs).toLong()) }
            draggedFraction = null
        },
        enabled = durationMs > 0L,
        modifier = Modifier.fillMaxWidth(),
        thumb = {
            Surface(
                modifier = Modifier.size(width = 4.dp, height = 28.dp),
                shape = RoundedCornerShape(2.dp),
                color = if (durationMs > 0L) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = .38f)
                },
            ) {}
        },
        track = { sliderState ->
            LinearWavyProgressIndicator(
                progress = { sliderState.value },
                modifier = Modifier.fillMaxWidth().height(12.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                stopSize = 0.dp,
                amplitude = { if (isPlaying) .65f else 0f },
                wavelength = 64.dp,
                waveSpeed = 28.dp,
            )
        },
    )
}

private fun formatPlaybackTime(valueMs: Long): String {
    val totalSeconds = valueMs.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = totalSeconds % 3_600L / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

@Composable
private fun MiniPlayer(
    state: LsMusicUiState,
    onOpen: () -> Unit,
    onTogglePlayback: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = state.currentTrack ?: return
    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 8.dp,
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ArtworkTile(track, 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(
                    state.renderers.firstOrNull { it.id == state.selectedRendererId }?.let {
                        if (it.id == LsMusicViewModel.LOCAL_RENDERER_ID) stringResource(R.string.local_player_name) else it.name
                    } ?: stringResource(R.string.not_selected_player),
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = onTogglePlayback) {
                Icon(if (state.playbackState == RemotePlaybackState.PLAYING) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, stringResource(R.string.play_or_pause))
            }
            IconButton(
                onClick = onNext,
                enabled = canSelectNextTrack(state.queue, state.currentQueueIndex, state.playbackOrder),
            ) {
                Icon(Icons.Rounded.SkipNext, stringResource(R.string.next_track))
            }
        }
    }
}

@Composable
private fun ArtworkTile(
    entry: MediaEntry,
    size: androidx.compose.ui.unit.Dp?,
    modifier: Modifier = Modifier,
    imageIdentity: Any = mediaEntryKey(entry),
    requestSizePx: Int? = null,
    useCachedAlbumThumbnailAsPlaceholder: Boolean = false,
    preferThumbnailSource: Boolean = true,
    placeholderArtworkUri: String? = null,
    retryTransientFailures: Boolean = false,
    filterQuality: FilterQuality = FilterQuality.Low,
    cornerRadius: Dp? = null,
) {
    val context = LocalContext.current
    val colors = remember(entry.title) {
        artworkPalettes[(entry.title.hashCode() and Int.MAX_VALUE) % artworkPalettes.size]
    }
    val placeholderBrush = remember(colors) { Brush.linearGradient(colors) }
    val selectedArtworkUri = if (preferThumbnailSource) entry.thumbnailArtworkUri else entry.artworkUri
    var artworkRequestGeneration by remember(
        imageIdentity,
        selectedArtworkUri,
        requestSizePx,
    ) { mutableIntStateOf(0) }
    var artworkFailureCount by remember(
        imageIdentity,
        selectedArtworkUri,
        requestSizePx,
    ) { mutableIntStateOf(0) }
    var lastFailedGeneration by remember(
        imageIdentity,
        selectedArtworkUri,
        requestSizePx,
    ) { mutableStateOf<Int?>(null) }
    LaunchedEffect(retryTransientFailures, artworkFailureCount) {
        if (!retryTransientFailures) return@LaunchedEffect
        val retryDelayMillis = albumArtworkRetryDelayMillis(artworkFailureCount)
            ?: return@LaunchedEffect
        delay(retryDelayMillis)
        artworkRequestGeneration += 1
    }
    val artworkModel = remember(
        context,
        selectedArtworkUri,
        requestSizePx,
        useCachedAlbumThumbnailAsPlaceholder,
        placeholderArtworkUri,
    ) {
        selectedArtworkUri?.takeIf(String::isNotBlank)?.let { uri ->
            when {
                useCachedAlbumThumbnailAsPlaceholder -> ImageRequest.Builder(context)
                    .data(uri)
                    .placeholderMemoryCacheKey(
                        albumArtworkThumbnailMemoryCacheKey(placeholderArtworkUri ?: uri),
                    )
                    .apply { if (requestSizePx != null) size(requestSizePx) }
                    .scale(Scale.FILL)
                    .precision(Precision.INEXACT)
                    .crossfade(180)
                    .build()
                requestSizePx != null -> albumArtworkRequest(context, uri, requestSizePx)
                else -> uri
            }
        }
    }
    val tileModifier = if (size == null) modifier else modifier.size(size)
    val iconSize = (size ?: 72.dp) * .48f
    val resolvedCornerRadius = cornerRadius ?: if (entry.isContainer) 18.dp else 16.dp
    Box(
        modifier = tileModifier.clip(RoundedCornerShape(resolvedCornerRadius))
            .background(placeholderBrush),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            when {
                entry.isAlbum -> Icons.Rounded.Album
                entry.isContainer -> Icons.Rounded.Folder
                else -> Icons.Rounded.MusicNote
            },
            null,
            tint = Color.White,
            modifier = Modifier.size(iconSize),
        )
        if (artworkModel != null) {
            key(
                imageIdentity,
                selectedArtworkUri,
                requestSizePx,
                useCachedAlbumThumbnailAsPlaceholder,
                artworkRequestGeneration,
            ) {
                AsyncImage(
                    model = artworkModel,
                    contentDescription = stringResource(R.string.album_cover, entry.title),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    filterQuality = filterQuality,
                    onSuccess = {
                        artworkFailureCount = 0
                        lastFailedGeneration = null
                    },
                    onError = {
                        if (
                            retryTransientFailures &&
                            lastFailedGeneration != artworkRequestGeneration
                        ) {
                            lastFailedGeneration = artworkRequestGeneration
                            artworkFailureCount += 1
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun HeroArtwork(track: MediaEntry, size: androidx.compose.ui.unit.Dp) {
    val colors = if (track.title.hashCode() % 2 == 0) listOf(Color(0xFF6147D7), Color(0xFFFF78A9), Color(0xFFFFC857))
    else listOf(Color(0xFF167D8D), Color(0xFF6957DE), Color(0xFFEC6B9D))
    Box(
        modifier = Modifier.size(size)
            .clip(RoundedCornerShape(28.dp)).background(Brush.radialGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        if (!track.artworkUri.isNullOrBlank()) {
            key(track.id, track.artworkUri) {
                AsyncImage(
                    model = track.artworkUri,
                    contentDescription = stringResource(R.string.album_cover, track.title),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        } else {
            Surface(shape = CircleShape, color = Color.Black.copy(alpha = .76f), modifier = Modifier.fillMaxSize(.58f)) {
                Box(contentAlignment = Alignment.Center) {
                    Surface(shape = CircleShape, color = Color.White.copy(alpha = .85f), modifier = Modifier.size(34.dp)) {}
                }
            }
        }
    }
}

@Composable
private fun AlbumMark(size: androidx.compose.ui.unit.Dp) {
    Surface(
        modifier = Modifier.size(size),
        shape = RoundedCornerShape(size * .34f),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun LoadingPanel(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(Modifier.padding(26.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
            Spacer(Modifier.width(16.dp))
            Text(message)
        }
    }
}

@Composable
private fun EmptyPanel(
    icon: ImageVector,
    title: String,
    body: String? = null,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val cardSize = minOf(maxWidth, maxHeight, LargeSquareContentMaxSize)
        Surface(
            modifier = Modifier.size(cardSize),
            shape = RoundedCornerShape(34.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(
                Modifier.padding(horizontal = 28.dp, vertical = 42.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(
                        icon,
                        null,
                        Modifier.padding(18.dp).size(36.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                if (!body.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(body, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (action != null) {
                    Spacer(Modifier.height(22.dp))
                    Button(onClick = onAction) { Text(action) }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun LibraryPreview() {
    val server = DlnaDevice("server", "客厅媒体库", "Synology", "Audio Station", DlnaDeviceKind.MEDIA_SERVER)
    val renderer = DlnaDevice("renderer", "书房音箱", "WiiM", "WiiM Pro", DlnaDeviceKind.MEDIA_RENDERER)
    val tracks = listOf(
        MediaEntry("albums", "0", "最近添加的唱片", isContainer = true, childCount = 28),
        MediaEntry("1", "0", "A Walk Through the City", creator = "Luna Park", duration = "04:12", resourceUri = "http://example/1.flac", mimeType = "audio/flac", isContainer = false),
        MediaEntry("2", "0", "Soft Focus", creator = "Noon Atlas", duration = "03:48", resourceUri = "http://example/2.flac", mimeType = "audio/flac", isContainer = false),
    )
    LsMusicTheme(dynamicColor = false) {
        LsMusicContent(
            state = LsMusicUiState(
                servers = listOf(server),
                renderers = listOf(renderer),
                selectedServerId = server.id,
                selectedRendererId = renderer.id,
                entries = tracks,
                queue = tracks.filterNot { it.isContainer },
                currentQueueIndex = 0,
                playbackState = RemotePlaybackState.PLAYING,
                isSearching = false,
                browseLoadStatus = BrowseLoadStatus.LOADED,
            ),
            snackbar = remember { SnackbarHostState() },
            onDestination = {},
            onRefresh = {},
            onSelectServer = {},
            onSelectRenderer = {},
            onOpen = {},
            onNavigateTo = {},
            onPlay = {},
            onQueue = {},
            onPlayAll = {},
            onShufflePlay = {},
            onQueueAll = {},
            onAlbumSort = {},
            onSaveBrowseViewState = { _, _ -> },
            onResolveAlbumArtwork = { _, _ -> },
            onTogglePlayback = {},
            onPrevious = {},
            onNext = {},
            onCycleRepeat = {},
            onToggleShuffle = {},
            onSeek = {},
            onRemoveQueue = {},
            onMoveQueue = { _, _ -> },
            onClearQueue = {},
            onGallerySize = {},
            onDefaultGridLayout = {},
            onThemeMode = {},
            onDynamicColor = {},
            onPresetPalette = {},
            onLoadLyrics = {},
            onRetryLyrics = {},
            onLyricsEnabled = {},
            onLyricsProviderOrder = {},
            onLyricsTranslationMode = {},
            onLyricsSourceVisible = {},
            onLyricsEffectsEnabled = {},
            onLyricsFontSizeSp = {},
            onClearLyricsCache = {},
            onListenBrainzEnabled = {},
            onListenBrainzToken = {},
            onListenBrainzMinimumSeconds = {},
            onListenBrainzMinimumPercent = {},
            onRetryPendingListens = {},
            onRemovePendingListen = {},
            onClearPendingListens = {},
        )
    }
}
