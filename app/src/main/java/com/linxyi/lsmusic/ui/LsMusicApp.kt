@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.linxyi.lsmusic.ui

import android.os.SystemClock
import android.widget.ImageView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.BackEventCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.layout.PinnableContainer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.viewinterop.AndroidView
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
import coil3.compose.asPainter
import coil3.memory.MemoryCache
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

private const val LIBRARY_GRID_HEADER_COUNT = 3
private const val LIBRARY_TOP_ITEM_INDEX = 0
private const val LIBRARY_SEARCH_ITEM_INDEX = 1
private data class LibrarySearchScrollRequest(val index: Int, val offset: Int)
private val LibraryFastScrollerTopInset = LibrarySearchControlHeight + 32.dp
private val LibraryFastScrollerTouchWidth = 48.dp
private val LibraryFastScrollerMinimumThumbHeight = 48.dp
private val ReorderEdgeScrollSize = 72.dp
private val ReorderMaximumScrollPerFrame = 20.dp
internal val ReorderVisualPadding = 8.dp
private const val ReorderPlacementAnimationDurationMillis = 110
private const val QueuePostDragClickSuppressionMs = 450L
private const val ALBUM_DETAIL_HEADER_COUNT = 1
private val AlbumDetailTwoPaneMinWidth = 480.dp
private const val ALBUM_DETAIL_TWO_PANE_MIN_ASPECT_RATIO = 1.35f
private val AlbumDetailLargeTwoPaneMinWidth = 840.dp
private const val ALBUM_DETAIL_LARGE_TWO_PANE_MIN_ASPECT_RATIO = 1.2f
private val AlbumDetailFloatingActionSize = 64.dp
private val AlbumDetailSecondaryActionSize = 56.dp
private val AlbumDetailFloatingActionSpacing = 12.dp
private val AlbumDetailCompactActionSize = 48.dp
private val AlbumDetailCompactPlayActionSize = 56.dp
private val AlbumDetailCompactActionSpacing = 8.dp
private val AlbumDetailFloatingActionEndMargin = 20.dp
private val AlbumDetailFloatingActionGap = 16.dp
private val AlbumDetailTrackSpacing = 14.dp
private val AlbumDetailTrackMinimumHeight = 68.dp
private val AlbumDetailFloatingActionHeight =
    AlbumDetailFloatingActionSize + AlbumDetailSecondaryActionSize * 2 + AlbumDetailFloatingActionSpacing * 2
private const val ALBUM_ART_PREFETCH_SCREENS = 2
private const val MAX_ACTIVE_ART_PREFETCHES = 4
private const val ALBUM_ART_PREFETCH_RESUME_DELAY_MS = 150L
private val LargeSquareContentMaxSize = 360.dp
private val DefaultScreenBottomPadding = 32.dp
private val MiniPlayerHeight = 68.dp
private val MiniPlayerBottomSpacing = 12.dp
private val MiniPlayerContentInset = MiniPlayerHeight + MiniPlayerBottomSpacing
private val MiniPlayerMaxWidth = 720.dp
private const val MINI_PLAYER_MOTION_DURATION_MILLIS = 400
private val MiniPlayerMotionEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private const val ALBUM_ARTWORK_RESIZE_SETTLE_MILLIS = 150L

private class MiniPlayerSnapshot {
    var state: LsMusicUiState? = null
}

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

private fun albumArtworkRequest(
    context: android.content.Context,
    artworkUri: String,
    sizePx: Int,
    placeholderMemoryCacheKey: String? = null,
): ImageRequest = ImageRequest.Builder(context)
    .data(artworkUri)
    .memoryCacheKey(albumArtworkThumbnailMemoryCacheKey(artworkUri))
    .placeholderMemoryCacheKey(placeholderMemoryCacheKey)
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
internal fun AppBackHandler(
    state: LsMusicUiState,
    onDestination: (AppDestination) -> Unit,
    onNavigateTo: (Int) -> Unit,
    backProgress: Animatable<Float, AnimationVector1D>,
    onBackEdge: (Int) -> Unit,
) {
    val playerReturnDestination = state.miniPlayerReturnDestination
        .takeIf { state.destination == AppDestination.NOW_PLAYING }
    PredictiveBackHandler(
        enabled = state.exitStatus != ExitStatus.IDLE || playerReturnDestination != null ||
            state.destination.settingsParent != null ||
            (state.destination == AppDestination.LIBRARY && state.path.size > 1),
    ) { events ->
        try {
            events.collect { event ->
                onBackEdge(event.swipeEdge)
                if (state.exitStatus == ExitStatus.IDLE) backProgress.snapTo(event.progress)
            }
            if (state.exitStatus == ExitStatus.IDLE) {
                when {
                    playerReturnDestination != null -> onDestination(playerReturnDestination)
                    state.destination.settingsParent != null -> onDestination(requireNotNull(state.destination.settingsParent))
                    else -> onNavigateTo(state.path.lastIndex - 1)
                }
            }
        } finally {
            // A cancelled gesture must restore the page without changing navigation.
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                backProgress.animateTo(0f, tween(180))
            }
        }
    }
}

@Composable
fun LsMusicApp(viewModel: LsMusicViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val darkTheme = resolvesToDarkTheme(
        mode = state.preferences.themeMode,
        systemUsesDarkTheme = isSystemInDarkTheme(),
    )

    LsMusicTheme(
        darkTheme = darkTheme,
        dynamicColor = state.preferences.useDynamicColor,
        presetPalette = state.preferences.presetPalette,
    ) {
        SystemBarAppearance()
        val snackbar = remember { SnackbarHostState() }
        val resources = LocalResources.current
        val localeKey = resources.configuration.locales.toLanguageTags()

        LaunchedEffect(state.error, localeKey) {
            state.error?.let {
                snackbar.showSnackbar(it.resolve(resources))
                viewModel.consumeError()
            }
        }

        val backProgress = remember { Animatable(0f) }
        var backEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
        AppBackHandler(
            state = state,
            onDestination = viewModel::setDestination,
            onNavigateTo = viewModel::navigateTo,
            backProgress = backProgress,
            onBackEdge = { backEdge = it },
        )

        SleepTimerPermissionEffect(
            state.sleepTimerPermissionRequest,
            onResult = viewModel::onSleepTimerPermissionResult,
            onDismiss = viewModel::dismissSleepTimerPermission,
        )
        ExitProgressDialog(state.exitStatus, state.exitError, viewModel::exitApp)
        val dataBackupActions = rememberDataBackupActions(
            state.dataBackup, viewModel::exportData, viewModel::inspectDataImport,
            viewModel::importData, viewModel::dismissDataBackup,
            viewModel::retryDataRecovery,
        )
        key(state.dataImportGeneration) {
            LsMusicContent(
                state = state,
                snackbar = snackbar,
                backProgress = { backProgress.value },
                backEdge = { backEdge },
                onDestination = viewModel::setDestination,
                onOpenNowPlayingFromMiniPlayer = viewModel::openNowPlayingFromMiniPlayer,
                onRefresh = viewModel::refreshDevices,
                onSelectServer = viewModel::selectServer,
                onSelectRenderer = viewModel::selectRenderer,
                onOpen = viewModel::open,
                onNavigateTo = viewModel::navigateTo,
                onPlay = viewModel::playNow,
                onPlayQueueItem = viewModel::playQueueItem,
                onEnqueueWhilePlaying = viewModel::setEnqueueWhilePlaying,
                onMiniPlayerEnabled = viewModel::setMiniPlayerEnabled,
                onClearQueueOnPlay = viewModel::setClearQueueOnPlay,
                onStartSleepTimer = viewModel::requestSleepTimer,
                onCancelSleepTimer = viewModel::cancelSleepTimer,
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
                onExportData = dataBackupActions.export,
                onImportData = dataBackupActions.import,
            )
        }
    }
}

@Composable
internal fun LsMusicContent(
    state: LsMusicUiState,
    snackbar: SnackbarHostState,
    onDestination: (AppDestination) -> Unit,
    onOpenNowPlayingFromMiniPlayer: () -> Unit,
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
    onPlayQueueItem: (String) -> Unit = {},
    onEnqueueWhilePlaying: (Boolean) -> Unit = {},
    onMiniPlayerEnabled: (Boolean) -> Unit = {},
    onClearQueueOnPlay: (Boolean) -> Unit = {},
    onStartSleepTimer: (Int, Boolean) -> Unit = { _, _ -> },
    onCancelSleepTimer: () -> Unit = {},
    onExportData: () -> Unit = {},
    onImportData: () -> Unit = {},
    backProgress: () -> Float = { 0f },
    backEdge: () -> Int = { BackEventCompat.EDGE_LEFT },
) {
    val destinationStateHolder = rememberSaveableStateHolder()
    val libraryState = remember(
        state.entries,
        state.albumSort,
        state.path,
        state.browsePageKey,
        state.browseViewState,
        state.preferences,
        state.gallerySize,
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
            preferences = state.preferences.copy(gallerySize = state.gallerySize),
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
        val showMiniPlayer = state.preferences.miniPlayerEnabled && state.currentTrack != null && state.destination != AppDestination.NOW_PLAYING
        val snackbarBottomInset by animateDpAsState(
            targetValue = if (showMiniPlayer) MiniPlayerContentInset else 0.dp,
            animationSpec = tween(MINI_PLAYER_MOTION_DURATION_MILLIS, easing = MiniPlayerMotionEasing),
            label = "mini-player-snackbar-inset",
        )
        Row(Modifier.fillMaxSize()) {
            if (expanded) {
                AppNavigationRail(state.destination, onDestination)
            }
            Scaffold(
                modifier = Modifier.weight(1f),
                snackbarHost = {
                    SnackbarHost(
                        hostState = snackbar,
                        modifier = Modifier.padding(bottom = snackbarBottomInset),
                    )
                },
                bottomBar = {
                    if (!expanded) AppNavigationBar(state.destination, onDestination)
                },
            ) { padding ->
                val topInset = padding.calculateTopPadding()
                val layoutDirection = LocalLayoutDirection.current
                Box(Modifier.fillMaxSize().padding(
                    start = padding.calculateStartPadding(layoutDirection),
                    end = padding.calculateEndPadding(layoutDirection),
                    bottom = padding.calculateBottomPadding(),
                ).graphicsLayer {
                    // Read gesture frames in the layer, without recomposing the app or its pages.
                    val progress = backProgress()
                    scaleX = 1f - 0.08f * progress
                    scaleY = scaleX
                    translationX = (if (backEdge() == BackEventCompat.EDGE_LEFT) 1 else -1) *
                        24.dp.toPx() * progress
                    shape = RoundedCornerShape((28 * progress).dp)
                    clip = progress > 0f
                }) {
                    PlayerPageTransition(
                        destination = state.destination,
                        track = state.currentTrack,
                        miniPlayerEnabled = state.preferences.miniPlayerEnabled,
                        topInset = topInset,
                    ) { destination ->
                        // The outgoing page keeps its own inset and mini player until the flight ends.
                        val pageShowsMiniPlayer = state.preferences.miniPlayerEnabled && state.currentTrack != null &&
                            destination != AppDestination.NOW_PLAYING
                        val miniPlayerTransition = updateTransition(pageShowsMiniPlayer, label = "mini-player-visibility")
                        val miniPlayerInset by miniPlayerTransition.animateDp(
                            transitionSpec = {
                                tween(MINI_PLAYER_MOTION_DURATION_MILLIS, easing = MiniPlayerMotionEasing)
                            },
                            label = "mini-player-content-inset",
                        ) { visible -> if (visible) MiniPlayerContentInset else 0.dp }
                        val bottomContentPadding = DefaultScreenBottomPadding + miniPlayerInset
                        val miniPlayerSnapshot = remember { MiniPlayerSnapshot() }
                        SideEffect {
                            if (state.currentTrack != null) {
                                miniPlayerSnapshot.state = state
                            } else if (!miniPlayerTransition.currentState && !miniPlayerTransition.targetState) {
                                miniPlayerSnapshot.state = null
                            }
                        }
                        destinationStateHolder.SaveableStateProvider(destination.navigationDestination.name) {
                            when (destination) {
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
                                    onPlayQueueItem,
                                    onRemoveQueue,
                                    onMoveQueue,
                                    onClearQueue,
                                    bottomContentPadding,
                                )
                                AppDestination.NOW_PLAYING -> NowPlayingScreen(
                                    state = state,
                                    modifier = Modifier.padding(top = topInset),
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
                                AppDestination.SETTINGS_PLAYBACK,
                                AppDestination.SETTINGS_LYRICS,
                                AppDestination.SETTINGS_NETWORK,
                                AppDestination.SETTINGS_ABOUT,
                                AppDestination.PENDING_LISTENS -> SettingsPageTransition(destination) { page ->
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
                                            onEnqueueWhilePlaying = onEnqueueWhilePlaying,
                                            onMiniPlayerEnabled = onMiniPlayerEnabled,
                                            onClearQueueOnPlay = onClearQueueOnPlay,
                                            onStartSleepTimer = onStartSleepTimer,
                                            onCancelSleepTimer = onCancelSleepTimer,
                                            onExportData = onExportData,
                                            onImportData = onImportData,
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
                        Box(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.BottomCenter) {
                            miniPlayerTransition.AnimatedVisibility(
                                visible = { it },
                                enter = fadeIn(tween(MINI_PLAYER_MOTION_DURATION_MILLIS, easing = MiniPlayerMotionEasing)) +
                                    slideInVertically(tween(MINI_PLAYER_MOTION_DURATION_MILLIS, easing = MiniPlayerMotionEasing)) { it },
                                exit = fadeOut(tween(MINI_PLAYER_MOTION_DURATION_MILLIS, easing = MiniPlayerMotionEasing)) +
                                    slideOutVertically(tween(MINI_PLAYER_MOTION_DURATION_MILLIS, easing = MiniPlayerMotionEasing)) { it },
                            ) {
                                val playerState = if (state.currentTrack != null) state else miniPlayerSnapshot.state
                                if (playerState != null) {
                                    MiniPlayer(
                                        state = playerState,
                                        onOpen = onOpenNowPlayingFromMiniPlayer,
                                        onTogglePlayback = onTogglePlayback,
                                        onNext = onNext,
                                        enabled = pageShowsMiniPlayer,
                                        modifier = Modifier
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
        }
    }
}

@Composable
private fun AppNavigationBar(selected: AppDestination, onDestination: (AppDestination) -> Unit) {
    val selectedNavigationDestination = selected.navigationDestination
    Box(
        Modifier.fillMaxWidth()
            .testTag("app-navigation-bar")
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .navigationBarsPadding(),
    ) {
        NavigationBar(
            modifier = Modifier.fillMaxWidth(),
            containerColor = Color.Transparent,
        ) {
            destinations.forEach { item ->
                NavigationBarItem(
                    modifier = Modifier.testTag("navigation-${item.destination.name}"),
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
internal fun SystemBarAppearance() {
    val view = LocalView.current
    val colorScheme = MaterialTheme.colorScheme
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        window.isNavigationBarContrastEnforced = false
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightNavigationBars = colorScheme.surfaceContainer.luminance() > .5f
            isAppearanceLightStatusBars = useDarkStatusBarIcons(colorScheme.background)
        }
    }
}

@Composable
private fun AppNavigationRail(selected: AppDestination, onDestination: (AppDestination) -> Unit) {
    val selectedNavigationDestination = selected.navigationDestination
    NavigationRail(Modifier.fillMaxHeight().width(132.dp)) {
        Spacer(Modifier.height(24.dp))
        AppIcon(54.dp)
        Spacer(Modifier.height(28.dp))
        destinations.forEach { item ->
            NavigationRailItem(
                modifier = Modifier.width(116.dp).testTag("navigation-${item.destination.name}"),
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
internal fun LibraryScreen(
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
    LibraryPageTransition(state) { page ->
        val pageKey = page.pageKey
        when (page.pageKind) {
            LibraryPageKind.ALBUM -> AlbumDetailScreen(
                state = page,
                pageKey = pageKey,
                initialViewState = page.browseViewState,
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
                path = page.path,
                onNavigateTo = onNavigateTo,
            )
            LibraryPageKind.DIRECTORY -> LibraryDirectoryScreen(
                state = page,
                pageKey = pageKey,
                initialViewState = page.browseViewState,
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
            .padding(start = 20.dp, top = LocalContentTopInset.current + 24.dp, end = 20.dp, bottom = 32.dp),
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
    val topInset = LocalContentTopInset.current
    var query by remember { mutableStateOf(initialViewState.query) }
    var useGrid by remember {
        mutableStateOf(initialViewState.useGrid ?: state.preferences.useGridByDefault)
    }
    var unfilteredPosition by remember { mutableStateOf(initialViewState.unfilteredPosition) }
    var floatingSearchExpanded by remember { mutableStateOf(false) }
    var pendingSearchScroll by remember { mutableStateOf<LibrarySearchScrollRequest?>(null) }
    var keepInlineSearchHidden by remember { mutableStateOf(false) }
    LaunchedEffect(floatingSearchExpanded) {
        if (floatingSearchExpanded) {
            keepInlineSearchHidden = true
        } else if (keepInlineSearchHidden) {
            // Let the floating text disappear before revealing its in-content counterpart.
            delay(220)
            keepInlineSearchHidden = false
        }
    }
    val hideInlineSearch = floatingSearchExpanded || keepInlineSearchHidden
    val inlineSearchAlpha by animateFloatAsState(
        targetValue = if (hideInlineSearch) 0f else 1f,
        animationSpec = tween(durationMillis = 120, easing = MiniPlayerMotionEasing),
        label = "library-inline-search-alpha",
    )
    val isAlbumCollection = remember(state.entries) {
        state.entries.isNotEmpty() &&
            state.entries.all { it.isContainer } &&
            state.entries.any { it.isAlbum }
    }
    val orderedEntries = remember(state.entries, state.albumSort, isAlbumCollection) {
        if (isAlbumCollection) state.entries.sortedAlbums(state.albumSort) else state.entries
    }
    val visibleEntries = remember(orderedEntries, query) {
        if (query.isBlank()) orderedEntries else orderedEntries.filter { it.matchesLibraryQuery(query) }
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
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val floatingResultInsetPx = with(density) { (topInset + LibraryFloatingSearchResultInset).roundToPx() }
    val coroutineScope = rememberCoroutineScope()
    var isFastScrollerDragging by remember { mutableStateOf(false) }
    val showFloatingSearchButton by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > LIBRARY_SEARCH_ITEM_INDEX
        }
    }
    val currentQuery by rememberUpdatedState(query)
    val currentUseGrid by rememberUpdatedState(useGrid)
    val currentUnfilteredPosition by rememberUpdatedState(unfilteredPosition)
    val isCurrentPage = LocalLibraryPageMotion.current?.isCurrentPage != false
    val isCurrentDestination = LocalPlayerPageMotion.current?.isCurrentPage != false
    val contentStatus = resolveLibraryContentStatus(
        browseLoadStatus = state.browseLoadStatus,
        isSearching = state.isSearching,
        hasSelectedServer = state.selectedServerId != null,
        selectedServerAvailable = state.servers.any { it.id == state.selectedServerId },
        visibleEntriesEmpty = visibleEntries.isEmpty(),
    )

    fun currentBrowsePosition(): BrowseScrollPosition {
        val anchorKey = gridState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == gridState.firstVisibleItemIndex }
            ?.key as? String
        return BrowseScrollPosition(
            anchorEntryKey = anchorKey?.takeIf { it.startsWith(MEDIA_ENTRY_KEY_PREFIX) },
            fallbackItemIndex = gridState.firstVisibleItemIndex,
            scrollOffset = gridState.firstVisibleItemScrollOffset,
        )
    }

    fun updateQuery(nextQuery: String) {
        if (nextQuery == query) return
        if (query.isBlank() && nextQuery.isNotBlank()) {
            unfilteredPosition = currentBrowsePosition()
        }
        if (query.isNotBlank() && nextQuery.isBlank()) {
            pendingSearchScroll = unfilteredPosition?.let { position ->
                LibrarySearchScrollRequest(
                    index = browseItemIndex(orderedEntries, position, LIBRARY_GRID_HEADER_COUNT),
                    offset = position.scrollOffset,
                )
            }
            unfilteredPosition = null
        } else if (nextQuery.isNotBlank() && floatingSearchExpanded) {
            val hasMatches = orderedEntries.any { it.matchesLibraryQuery(nextQuery) }
            pendingSearchScroll = LibrarySearchScrollRequest(
                index = if (hasMatches) LIBRARY_GRID_HEADER_COUNT else LIBRARY_TOP_ITEM_INDEX,
                offset = if (hasMatches) -floatingResultInsetPx else 0,
            )
        }
        query = nextQuery
    }

    fun closeFloatingSearch() {
        floatingSearchExpanded = false
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    BackHandler(enabled = floatingSearchExpanded && isCurrentPage && isCurrentDestination) {
        closeFloatingSearch()
    }

    fun saveViewState() {
        val position = currentBrowsePosition()
        onSaveBrowseViewState(
            pageKey,
            BrowseViewState(
                query = currentQuery,
                useGrid = currentUseGrid,
                anchorEntryKey = position.anchorEntryKey,
                fallbackItemIndex = position.fallbackItemIndex,
                scrollOffset = position.scrollOffset,
                unfilteredPosition = currentUnfilteredPosition,
            ),
        )
    }
    val openEntry: (MediaEntry) -> Unit = { entry ->
        // Save before navigating: AnimatedContent keeps this page alive during the cover flight.
        saveViewState()
        if (floatingSearchExpanded) closeFloatingSearch()
        onOpen(entry)
    }
    // Publish layout choices before the next tap so the destination starts with the source corners.
    // Scroll frames remain local; the viewport is saved when navigation actually happens.
    LaunchedEffect(query, useGrid) {
        if (isCurrentPage) saveViewState()
    }
    DisposableEffect(pageKey, gridState, isCurrentPage) {
        onDispose {
            if (isCurrentPage) saveViewState()
        }
    }

    BoxWithConstraints(modifier) {
        val gridSpacing = 12.dp
        val pinnedSearchTopPx = with(density) { (topInset + LibraryFloatingSearchTop).roundToPx() }
        val floatingSearchTopPx = remember(gridState, pinnedSearchTopPx) {
            derivedStateOf {
                val layoutInfo = gridState.layoutInfo
                val inlineTop = layoutInfo.visibleItemsInfo
                    .firstOrNull { it.index == LIBRARY_SEARCH_ITEM_INDEX }
                    ?.let { it.offset.y + layoutInfo.beforeContentPadding }
                maxOf(pinnedSearchTopPx, inlineTop ?: pinnedSearchTopPx)
            }
        }
        val availableWidth = (maxWidth - 40.dp).coerceAtLeast(1.dp)
        val minimumCellWidth = state.preferences.gallerySize.minCellSize.dp
        val columnCount = if (useGrid) {
            ((availableWidth + gridSpacing) / (minimumCellWidth + gridSpacing)).toInt().coerceAtLeast(1)
        } else {
            1
        }
        val artworkWidth = (availableWidth - gridSpacing * (columnCount - 1)) / columnCount
        val artworkRequestSizePx = with(density) { artworkWidth.roundToPx() }

        AlbumArtworkPrefetchEffect(
            pageKey = pageKey,
            entries = visibleEntries,
            gridState = gridState,
            requestSizePx = artworkRequestSizePx,
            enabled = useGrid && isAlbumCollection && !state.isBrowsing && visibleEntries.isNotEmpty(),
            paused = isFastScrollerDragging || !isCurrentPage,
        )

        LazyVerticalGrid(
            columns = if (useGrid) GridCells.Adaptive(minimumCellWidth) else GridCells.Fixed(1),
            state = gridState,
            modifier = Modifier.fillMaxSize().protectTopEdge(gridState).testTag("library-directory-entries"),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = topInset + 24.dp,
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
                Box(Modifier.fillMaxWidth().height(LibrarySearchControlHeight).testTag("library-inline-search-slot")) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = ::updateQuery,
                        enabled = !hideInlineSearch,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(LibrarySearchControlHeight)
                            .focusRequester(searchFocusRequester)
                            .graphicsLayer { alpha = inlineSearchAlpha }
                            .then(if (hideInlineSearch) Modifier.clearAndSetSemantics { } else Modifier)
                            .testTag("library-inline-search-input"),
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = {
                            if (query.isNotBlank()) IconButton(onClick = { updateQuery("") }) {
                                Icon(Icons.Rounded.Close, stringResource(R.string.clear_search))
                            }
                        },
                        placeholder = { Text(stringResource(R.string.search_current_directory)) },
                    )
                }
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

            if (contentStatus == LibraryContentStatus.CONTENT) gridItemsIndexed(
                items = visibleEntries,
                key = { _, it -> mediaEntryKey(it) },
                contentType = { _, _ -> if (useGrid) "media-grid-card" else "media-list-row" },
            ) { index, entry ->
                if (useGrid) {
                    MediaGridCard(
                        entry = entry,
                        gallerySize = state.preferences.gallerySize,
                        artworkRequestSizePx = artworkRequestSizePx,
                        onOpen = { openEntry(entry) },
                        onQueue = { onQueue(entry) },
                        albumTransitionKey = if (entry.isContainer) BrowsePageKey(pageKey.serverId, entry.id) else null,
                    )
                } else {
                    MediaEntryRow(
                        entry = entry,
                        emphasized = index % 5 == 0,
                        onOpen = { openEntry(entry) },
                        onPlay = { onPlay(entry) },
                        onQueue = { onQueue(entry) },
                        albumTransitionKey = if (entry.isContainer) BrowsePageKey(pageKey.serverId, entry.id) else null,
                    )
                }
            }
        }

        SideEffect {
            // Apply the request after the filtered item provider has been updated, but before
            // its next layout. Otherwise the old, shorter result set can clamp the restore index.
            pendingSearchScroll?.let { request ->
                gridState.requestScrollToItem(request.index, request.offset)
                pendingSearchScroll = null
            }
        }

        if (contentStatus != LibraryContentStatus.CONTENT) {
            CenteredStatusLayout(
                title = stringResource(R.string.app_name),
                modifier = Modifier.padding(top = topInset),
                showTitle = false,
            ) {
                when (contentStatus) {
                    LibraryContentStatus.LOADING -> {
                        LoadingPanel(stringResource(R.string.loading_music_library))
                    }
                    LibraryContentStatus.NO_SERVER -> {
                        EmptyPanel(
                            icon = Icons.Rounded.Devices,
                            title = stringResource(R.string.library_not_found_title),
                            body = stringResource(R.string.library_not_found_body),
                            action = stringResource(R.string.open_device_settings),
                            onAction = onOpenSettings,
                        )
                    }
                    LibraryContentStatus.SERVER_UNAVAILABLE -> {
                        EmptyPanel(
                            icon = Icons.Rounded.Devices,
                            title = stringResource(R.string.library_unavailable_title),
                            body = stringResource(R.string.library_unavailable_body),
                            action = stringResource(R.string.open_device_settings),
                            onAction = onOpenSettings,
                        )
                    }
                    LibraryContentStatus.LOAD_FAILED -> {
                        EmptyPanel(
                            icon = Icons.Rounded.MusicNote,
                            title = stringResource(R.string.library_load_failed_title),
                            body = stringResource(R.string.library_load_failed_body),
                            action = stringResource(R.string.open_device_settings),
                            onAction = onOpenSettings,
                        )
                    }
                    LibraryContentStatus.EMPTY -> {
                        EmptyPanel(
                            icon = Icons.Rounded.MusicNote,
                            title = stringResource(if (query.isBlank()) R.string.empty_directory_title else R.string.no_matching_music_title),
                            body = stringResource(if (query.isBlank()) R.string.empty_directory_body else R.string.no_matching_music_body),
                        )
                    }
                    LibraryContentStatus.CONTENT -> Unit
                }
            }
        }

        LibraryFastScrollerOverlay(
            gridState = gridState,
            viewportHeight = maxHeight,
            gridSpacing = gridSpacing,
            bottomContentPadding = bottomContentPadding,
            topContentInset = topInset,
            onDragStateChanged = { isFastScrollerDragging = it },
        )

        LibraryFloatingSearch(
            expanded = floatingSearchExpanded,
            showButton = showFloatingSearchButton,
            expandedWidth = (maxWidth - 40.dp).coerceAtLeast(LibrarySearchControlHeight),
            query = query,
            onQueryChange = ::updateQuery,
            onOpen = {
                val first = gridState.firstVisibleItemIndex
                val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: first
                if (isLibrarySearchNearTop(first, last)) {
                    coroutineScope.launch {
                        if (gridState.animateSearchReturnToTop(density)) {
                            searchFocusRequester.requestFocus()
                        }
                    }
                } else {
                    floatingSearchExpanded = true
                }
            },
            onClose = ::closeFloatingSearch,
            onImeSearch = { keyboardController?.hide() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 20.dp)
                .offset {
                    // Follow the original field as it comes into view, then stick to the
                    // floating position once that field scrolls past the top edge.
                    IntOffset(0, floatingSearchTopPx.value)
                }
                .libraryFloatingSearchOverlay(),
        )
    }
}

/** Header offsets change on every scroll pixel; observe them outside the directory's composition. */
@Composable
private fun BoxScope.LibraryFastScrollerOverlay(
    gridState: LazyGridState,
    viewportHeight: Dp,
    gridSpacing: Dp,
    bottomContentPadding: Dp,
    topContentInset: Dp,
    onDragStateChanged: (Boolean) -> Unit,
) {
    val density = LocalDensity.current
    val minimumTopInsetPx = with(density) { (topContentInset + LibraryFastScrollerTopInset).roundToPx() }
    val gridSpacingPx = with(density) { gridSpacing.roundToPx() }
    val showFastScroller by remember(gridState) {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            layoutInfo.totalItemsCount > layoutInfo.visibleItemsInfo.size &&
                (gridState.canScrollBackward || gridState.canScrollForward)
        }
    }
    val topInsetPx by remember(gridState, minimumTopInsetPx, gridSpacingPx) {
        derivedStateOf {
            val lastHeader = gridState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == LIBRARY_GRID_HEADER_COUNT - 1 }
            when {
                lastHeader != null -> maxOf(
                    minimumTopInsetPx,
                    lastHeader.offset.y + gridState.layoutInfo.beforeContentPadding + lastHeader.size.height + gridSpacingPx,
                )
                gridState.firstVisibleItemIndex >= LIBRARY_GRID_HEADER_COUNT -> minimumTopInsetPx
                else -> null
            }
        }
    }
    val topInset = topInsetPx?.let { with(density) { it.toDp() } } ?: return
    if (viewportHeight - topInset - bottomContentPadding < LibraryFastScrollerMinimumThumbHeight) return

    AnimatedVisibility(
        visible = showFastScroller,
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .padding(top = topInset, bottom = bottomContentPadding)
            .width(LibraryFastScrollerTouchWidth),
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        LibraryFastScroller(
            gridState = gridState,
            onDragStateChanged = onDragStateChanged,
            modifier = Modifier.fillMaxSize(),
        )
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
internal fun AlbumDetailScreen(
    state: LibraryUiState,
    modifier: Modifier = Modifier,
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
    val topInset = LocalContentTopInset.current
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
    var showFullScreenArtwork by rememberSaveable(pageKey) { mutableStateOf(false) }
    if (showFullScreenArtwork) {
        headerArtworkEntry?.artworkUri?.let { uri ->
            FullScreenArtwork(uri = uri, title = title, onDismiss = { showFullScreenArtwork = false })
        }
    }
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
    val summaryScrollState = rememberScrollState()

    val isCurrentPage = LocalLibraryPageMotion.current?.isCurrentPage != false
    val trackReveal = animateFloatAsState(
        targetValue = if (state.browseLoadStatus == BrowseLoadStatus.LOADED) 1f else 0f,
        animationSpec = tween(240),
        label = "album-loaded-tracks",
    )
    DisposableEffect(pageKey, listState, isCurrentPage) {
        onDispose {
            if (isCurrentPage) {
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
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        // Measure the actual content area after navigation and system insets.
        val usableHeight = (maxHeight - topInset).coerceAtLeast(0.dp)
        val twoPane = maxWidth >= AlbumDetailTwoPaneMinWidth && (
            maxWidth >= usableHeight * ALBUM_DETAIL_TWO_PANE_MIN_ASPECT_RATIO ||
                (maxWidth >= AlbumDetailLargeTwoPaneMinWidth &&
                    maxWidth >= usableHeight * ALBUM_DETAIL_LARGE_TWO_PANE_MIN_ASPECT_RATIO)
            )
        val summaryAvailableHeight = (usableHeight - 24.dp - bottomContentPadding).coerceAtLeast(0.dp)
        val compactActions = twoPane && summaryAvailableHeight <
            AlbumDetailFloatingActionHeight + AlbumDetailFloatingActionGap + AlbumDetailTrackMinimumHeight
        val compactActionProgress by animateFloatAsState(
            targetValue = if (compactActions) 1f else 0f,
            animationSpec = tween(MINI_PLAYER_MOTION_DURATION_MILLIS, easing = MiniPlayerMotionEasing),
            label = "album-compact-actions",
        )
        val floatingActionHeight = lerp(
            AlbumDetailFloatingActionHeight,
            AlbumDetailCompactPlayActionSize,
            compactActionProgress,
        )
        val compactActionInset = (AlbumDetailCompactPlayActionSize + AlbumDetailFloatingActionGap) * compactActionProgress
        val sideActionInset = (AlbumDetailFloatingActionSize + AlbumDetailFloatingActionGap) * (1f - compactActionProgress)
        val albumSummary: @Composable () -> Unit = {
            AlbumDetailSummary(
                entry = headerArtworkEntry,
                pageKey = pageKey,
                title = title,
                artists = artists,
                trackCount = trackCount,
                entries = state.entries,
                browseLoadStatus = state.browseLoadStatus,
                placeholderArtworkUri = currentLocation?.let { location ->
                    selectThumbnailArtworkUri(location.artworkCandidates, location.artworkUri)
                } ?: representativeTrack?.thumbnailArtworkUri,
                availableHeight = summaryAvailableHeight,
                twoPane = twoPane,
                onResolveAlbumArtwork = onResolveAlbumArtwork,
                onOpenArtwork = { showFullScreenArtwork = true },
            )
        }
        Row(Modifier.fillMaxSize()) {
            if (twoPane) {
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(0.4f)
                        .fillMaxHeight()
                        .protectTopEdge(summaryScrollState, 24.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(summaryScrollState)
                            .heightIn(min = maxHeight)
                            .padding(start = 20.dp, top = topInset + 24.dp, end = 12.dp, bottom = bottomContentPadding),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        albumSummary()
                    }
                }
            }
            val trackList: @Composable (Boolean, Modifier) -> Unit = { centerTracks, listModifier ->
                val overflowProgress by animateFloatAsState(
                    targetValue = if (centerTracks) 0f else 1f,
                    animationSpec = tween(MINI_PLAYER_MOTION_DURATION_MILLIS, easing = MiniPlayerMotionEasing),
                    label = "album-track-overflow",
                )
                LazyColumn(
                    state = listState,
                    modifier = listModifier.protectTopEdge(listState).testTag("album-detail-tracks"),
                    contentPadding = PaddingValues(
                        start = if (twoPane) 12.dp else 20.dp,
                        // The zero-height header still contributes one item spacing.
                        top = topInset + if (twoPane) 24.dp - AlbumDetailTrackSpacing * (1f - overflowProgress) else 24.dp,
                        end = 20.dp + if (twoPane) sideActionInset * (1f - overflowProgress) else 0.dp,
                        // The last row can move completely above the floating actions.
                        bottom = bottomContentPadding + if (twoPane) {
                            lerp(compactActionInset, floatingActionHeight + AlbumDetailFloatingActionGap, overflowProgress)
                        } else {
                            0.dp
                        },
                    ),
                    verticalArrangement = if (centerTracks) {
                        Arrangement.spacedBy(AlbumDetailTrackSpacing, Alignment.CenterVertically)
                    } else {
                        Arrangement.spacedBy(AlbumDetailTrackSpacing)
                    },
                ) {
                    // Keep one header in both layouts so saved track indices remain valid.
                    item(key = "album-detail-header", contentType = "header") {
                        if (!twoPane) {
                            Column {
                                albumSummary()
                                Spacer(Modifier.height(14.dp))
                                AlbumPlaybackActions(
                                    tracks = tracks,
                                    enabled = !state.isBrowsing && trackCount > 0,
                                    onPlayAll = onPlayAll,
                                    onShufflePlay = onShufflePlay,
                                    onQueueAll = onQueueAll,
                                )
                            }
                        }
                    }
                    if (!state.isBrowsing && trackCount == 0) {
                        item(key = "album-detail-empty", contentType = "empty") {
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
                        contentType = { _, _ -> "track" },
                    ) { index, track ->
                        TrackCollectionRow(
                            track = track,
                            number = index + 1,
                            isPlaying = state.currentTrackId == track.id && state.playbackState == RemotePlaybackState.PLAYING,
                            onPlay = { onPlay(track) },
                            onQueue = { onQueue(track) },
                            modifier = Modifier
                                .albumDetailEnter(delayMillis = 150 + index.coerceAtMost(4) * 20)
                                .graphicsLayer {
                                    alpha = trackReveal.value
                                    translationY = (1f - trackReveal.value) * 10.dp.toPx()
                                },
                        )
                    }
                }
            }
            if (twoPane) {
                SubcomposeLayout(Modifier.weight(0.6f).fillMaxHeight()) { constraints ->
                    val availableHeight = (constraints.maxHeight - topInset.roundToPx() - 24.dp.roundToPx() -
                        bottomContentPadding.roundToPx() - compactActionInset.roundToPx()).coerceAtLeast(0)
                    // Each row contains a 24 dp icon and 10 dp of vertical padding on both sides.
                    // Skip the measurement entirely when even these minimum heights cannot fit.
                    val minimumRowHeight = 24.dp.roundToPx() + 2 * 10.dp.roundToPx()
                    val minimumContentHeight = trackCount.toLong() * minimumRowHeight +
                        (trackCount - 1).coerceAtLeast(0).toLong() * AlbumDetailTrackSpacing.roundToPx()
                    val centerTracks = if (trackCount > 0 && minimumContentHeight <= availableHeight) {
                        val reservedWidth = 12.dp.roundToPx() + 20.dp.roundToPx() + sideActionInset.roundToPx()
                        val trackWidth = (constraints.maxWidth - reservedWidth).coerceAtLeast(0)
                        // Measure only a potentially short list before composing the visible list, so
                        // restored scroll positions and large-font row heights are handled on the first frame.
                        val measuredTracks = subcompose("track-measurement") {
                            Column(
                                modifier = Modifier.clearAndSetSemantics { },
                                verticalArrangement = Arrangement.spacedBy(AlbumDetailTrackSpacing),
                            ) {
                                tracks.forEachIndexed { index, track ->
                                    TrackCollectionRow(
                                        track = track,
                                        number = index + 1,
                                        isPlaying = state.currentTrackId == track.id &&
                                            state.playbackState == RemotePlaybackState.PLAYING,
                                        onPlay = {},
                                        onQueue = {},
                                    )
                                }
                            }
                        }.single().measure(Constraints.fixedWidth(trackWidth))
                        measuredTracks.height <= availableHeight
                    } else {
                        false
                    }
                    val list = subcompose("track-list") {
                        trackList(centerTracks, Modifier.fillMaxSize())
                    }.single().measure(constraints)
                    layout(list.width, list.height) { list.placeRelative(0, 0) }
                }
            } else {
                trackList(false, Modifier.weight(1f).fillMaxHeight())
            }
        }
        if (twoPane) {
            AlbumFloatingPlaybackActions(
                tracks = tracks,
                enabled = !state.isBrowsing && trackCount > 0,
                onPlayAll = onPlayAll,
                onShufflePlay = onShufflePlay,
                onQueueAll = onQueueAll,
                compactProgress = compactActionProgress,
                modifier = Modifier.align(Alignment.BottomEnd)
                    .padding(end = AlbumDetailFloatingActionEndMargin, bottom = bottomContentPadding),
            )
        }
    }
}

@Composable
private fun AlbumDetailSummary(
    entry: MediaEntry?,
    pageKey: BrowsePageKey,
    title: String,
    artists: String,
    trackCount: Int,
    entries: List<MediaEntry>,
    browseLoadStatus: BrowseLoadStatus,
    placeholderArtworkUri: String?,
    availableHeight: Dp,
    twoPane: Boolean,
    onResolveAlbumArtwork: (BrowsePageKey, Int) -> Unit,
    onOpenArtwork: () -> Unit,
) {
    val openArtworkLabel = stringResource(R.string.open_full_screen_artwork)
    val motion = LocalLibraryPageMotion.current
    val artworkCanResolve = motion == null || (motion.isCurrentPage && motion.isSettled)
    val textMeasurer = rememberTextMeasurer()
    val titleStyle = MaterialTheme.typography.headlineSmall
    val artistStyle = MaterialTheme.typography.bodyLarge
    val countStyle = MaterialTheme.typography.labelLarge
    val trackCountLabel = if (browseLoadStatus == BrowseLoadStatus.LOADING) {
        stringResource(R.string.loading_music_content)
    } else {
        pluralStringResource(R.plurals.track_count, trackCount, trackCount)
    }
    SubcomposeLayout(
        modifier = Modifier.fillMaxWidth().testTag("album-detail-summary"),
    ) { constraints ->
        val textConstraints = Constraints(maxWidth = constraints.maxWidth)
        // Interpolate the compact spacing so a changing player inset cannot make the cover jump.
        val expandedSpacing = ((availableHeight - 300.dp) / 40.dp).coerceIn(0f, 1f)
        val artworkGap = lerp(8.dp, 16.dp, expandedSpacing).roundToPx()
        val artistGap = lerp(4.dp, 6.dp, expandedSpacing).roundToPx()
        val countGap = lerp(4.dp, 8.dp, expandedSpacing).roundToPx()
        val availableHeightPx = availableHeight.roundToPx().coerceAtLeast(0)
        val minimumCoverSize = if (twoPane && entry != null) {
            minOf(32.dp.roundToPx(), availableHeightPx / 4)
        } else {
            0
        }
        fun TextStyle.scaledForSummary(scale: Float): TextStyle = if (scale == 1f) this else copy(
            fontSize = fontSize * scale,
            lineHeight = lineHeight * scale,
        )
        val textScale = if (twoPane && entry != null) {
            val textBudget = availableHeightPx - artworkGap - minimumCoverSize
            // Keep the full title and metadata visible if shrinking the artwork alone is insufficient.
            floatArrayOf(1f, .9f, .8f, .7f, .6f).firstOrNull { scale ->
                val titleHeight = textMeasurer.measure(
                    title,
                    style = titleStyle.scaledForSummary(scale),
                    constraints = textConstraints,
                ).size.height
                val artistHeight = if (artists.isNotBlank()) {
                    artistGap + textMeasurer.measure(
                        artists,
                        style = artistStyle.scaledForSummary(scale),
                        constraints = textConstraints,
                    ).size.height
                } else {
                    0
                }
                val countHeight = textMeasurer.measure(
                    trackCountLabel,
                    style = countStyle.scaledForSummary(scale),
                    constraints = textConstraints,
                ).size.height
                titleHeight + artistHeight + countGap + countHeight <= textBudget
            } ?: .6f
        } else {
            1f
        }
        val titleText = subcompose("title") {
            Text(
                title,
                modifier = Modifier.fillMaxWidth().testTag("album-detail-title").albumDetailEnter(),
                style = titleStyle.scaledForSummary(textScale),
                textAlign = TextAlign.Center,
            )
        }.single().measure(textConstraints)
        val artistText = if (artists.isNotBlank()) {
            subcompose("artist") {
                Text(
                    artists,
                    modifier = Modifier.fillMaxWidth().testTag("album-detail-artist").albumDetailEnter(),
                    style = artistStyle.scaledForSummary(textScale),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }.single().measure(textConstraints)
        } else {
            null
        }
        val countText = subcompose("count") {
            Text(
                trackCountLabel,
                modifier = Modifier.fillMaxWidth().testTag("album-detail-count").albumDetailEnter(),
                style = countStyle.scaledForSummary(textScale),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }.single().measure(textConstraints)

        val textHeight = titleText.height + (artistText?.let { artistGap + it.height } ?: 0) +
            countGap + countText.height
        // In a scrolling single-pane page, the artist and count may continue below the viewport.
        // The cover and title should still fit above the mini player on the first frame.
        val reservedTextHeight = if (twoPane ||
            availableHeightPx >= artworkGap + textHeight + 48.dp.roundToPx()
        ) {
            textHeight
        } else {
            titleText.height
        }
        val artworkSizePx = if (entry != null) {
            val fallbackCoverSize = if (twoPane && textHeight + artworkGap >= availableHeightPx) {
                minOf(16.dp.roundToPx(), availableHeightPx / 4)
            } else {
                0
            }
            minOf(
                constraints.maxWidth,
                LargeSquareContentMaxSize.roundToPx(),
                (availableHeightPx - artworkGap - reservedTextHeight).coerceAtLeast(fallbackCoverSize),
            )
        } else {
            0
        }
        val artwork = if (entry != null && artworkSizePx > 0) {
            subcompose("artwork") {
                // Keep one image request while the available height is changing, then resolve
                // the final size once. This also prevents a new decode on every animation frame.
                var requestSizePx by remember(pageKey) { mutableIntStateOf(artworkSizePx) }
                LaunchedEffect(artworkSizePx) {
                    delay(ALBUM_ARTWORK_RESIZE_SETTLE_MILLIS)
                    requestSizePx = artworkSizePx
                }
                LaunchedEffect(pageKey, requestSizePx, entries, browseLoadStatus, artworkCanResolve) {
                    // Keep the original thumbnail through the flight, then resolve the large cover.
                    if (artworkCanResolve && browseLoadStatus == BrowseLoadStatus.LOADED) {
                        onResolveAlbumArtwork(pageKey, requestSizePx)
                    }
                }
                ArtworkTile(
                    entry = entry,
                    modifier = Modifier.testTag("album-detail-cover").pointerInput(entry.artworkUri) {
                        detectTapGestures(onDoubleTap = {
                            if (!entry.artworkUri.isNullOrBlank()) onOpenArtwork()
                        })
                    }.semantics {
                        onClick(label = openArtworkLabel) {
                            if (!entry.artworkUri.isNullOrBlank()) {
                                onOpenArtwork()
                                true
                            } else {
                                false
                            }
                        }
                    },
                    size = artworkSizePx.toDp(),
                    imageIdentity = pageKey,
                    requestSizePx = requestSizePx,
                    useCachedAlbumThumbnailAsPlaceholder = true,
                    preferThumbnailSource = false,
                    placeholderArtworkUri = placeholderArtworkUri,
                    filterQuality = FilterQuality.High,
                    albumTransitionKey = pageKey,
                    useCachedThumbnailDuringFlight = motion?.navigation?.opening == true && motion.isCurrentPage,
                )
            }.single().measure(Constraints.fixed(artworkSizePx, artworkSizePx))
        } else {
            null
        }
        val contentHeight = artworkSizePx + artworkGap + textHeight
        layout(constraints.maxWidth, contentHeight.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            artwork?.placeRelative((constraints.maxWidth - artwork.width) / 2, 0)
            var y = artworkSizePx + artworkGap
            titleText.placeRelative(0, y)
            y += titleText.height
            artistText?.let {
                y += artistGap
                it.placeRelative(0, y)
                y += it.height
            }
            countText.placeRelative(0, y + countGap)
        }
    }
}

@Composable
private fun AlbumPlaybackActions(
    tracks: List<MediaEntry>,
    enabled: Boolean,
    onPlayAll: (List<MediaEntry>) -> Unit,
    onShufflePlay: (List<MediaEntry>) -> Unit,
    onQueueAll: (List<MediaEntry>) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag("album-detail-actions").albumDetailEnter(130),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledTonalButton(
            onClick = { onPlayAll(tracks) },
            enabled = enabled,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 6.dp),
        ) {
            Icon(Icons.Rounded.PlayArrow, null)
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.play_all))
        }
        OutlinedButton(
            onClick = { onShufflePlay(tracks) },
            enabled = enabled,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 6.dp),
        ) {
            Icon(Icons.Rounded.Shuffle, null)
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.shuffle_play))
        }
        OutlinedButton(
            onClick = { onQueueAll(tracks) },
            enabled = enabled,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 6.dp),
        ) {
            Icon(Icons.Rounded.Add, null)
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.add_to_queue))
        }
    }
}

@Composable
private fun AlbumFloatingPlaybackActions(
    tracks: List<MediaEntry>,
    enabled: Boolean,
    onPlayAll: (List<MediaEntry>) -> Unit,
    onShufflePlay: (List<MediaEntry>) -> Unit,
    onQueueAll: (List<MediaEntry>) -> Unit,
    compactProgress: Float,
    modifier: Modifier = Modifier,
) {
    val secondarySize = lerp(AlbumDetailSecondaryActionSize, AlbumDetailCompactActionSize, compactProgress)
    val playSize = lerp(AlbumDetailFloatingActionSize, AlbumDetailCompactPlayActionSize, compactProgress)
    val buttons: @Composable () -> Unit = {
        FilledTonalIconButton(
            onClick = { onShufflePlay(tracks) },
            enabled = enabled,
            modifier = Modifier.size(secondarySize)
                .shadow(if (enabled) 6.dp else 0.dp, CircleShape).testTag("album-action-shuffle"),
        ) {
            Icon(Icons.Rounded.Shuffle, stringResource(R.string.shuffle_play))
        }
        FilledTonalIconButton(
            onClick = { onQueueAll(tracks) },
            enabled = enabled,
            modifier = Modifier.size(secondarySize)
                .shadow(if (enabled) 6.dp else 0.dp, CircleShape).testTag("album-action-queue-all"),
        ) {
            Icon(Icons.Rounded.Add, stringResource(R.string.add_to_queue))
        }
        FilledIconButton(
            onClick = { onPlayAll(tracks) },
            enabled = enabled,
            modifier = Modifier.size(playSize)
                .shadow(if (enabled) 6.dp else 0.dp, CircleShape)
                .testTag("album-action-play-all"),
        ) {
            Icon(Icons.Rounded.PlayArrow, stringResource(R.string.play_all), Modifier.size(28.dp))
        }
    }
    val actionModifier = modifier.testTag("album-detail-floating-actions").albumDetailEnter(130)
    Layout(content = buttons, modifier = actionModifier) { measurables, constraints ->
        val secondary = secondarySize.roundToPx()
        val play = playSize.roundToPx()
        val verticalGap = AlbumDetailFloatingActionSpacing.roundToPx()
        val horizontalGap = AlbumDetailCompactActionSpacing.roundToPx()
        val sizes = listOf(secondary, secondary, play)
        val placeables = measurables.mapIndexed { index, measurable ->
            measurable.measure(Constraints.fixed(sizes[index], sizes[index]))
        }
        val verticalHeight = secondary * 2 + play + verticalGap * 2
        val horizontalWidth = secondary * 2 + play + horizontalGap * 2
        fun interpolate(start: Int, end: Int): Int = (start + (end - start) * compactProgress).roundToInt()
        val width = constraints.constrainWidth(interpolate(play, horizontalWidth))
        val height = constraints.constrainHeight(interpolate(verticalHeight, play))
        layout(width, height) {
            placeables.forEachIndexed { index, placeable ->
                val verticalX = (play - sizes[index]) / 2
                val verticalY = index * (secondary + verticalGap)
                val horizontalX = index * (secondary + horizontalGap)
                val horizontalY = (play - sizes[index]) / 2
                placeable.placeRelative(
                    interpolate(verticalX, horizontalX),
                    interpolate(verticalY, horizontalY),
                )
            }
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
    modifier: Modifier = Modifier,
) {
    val supportingContentColor = if (isPlaying) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onPlay),
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
    albumTransitionKey: BrowsePageKey? = null,
) {
    val pageMotion = LocalLibraryPageMotion.current
    val cardCornerRadius = when (gallerySize) {
        GallerySize.COMPACT -> 16.dp
        GallerySize.STANDARD -> 21.dp
        GallerySize.LARGE -> 26.dp
    }
    val artworkCornerRadius = if (entry.isContainer) AlbumArtworkCornerRadius else when (gallerySize) {
        GallerySize.COMPACT -> 9.dp
        GallerySize.STANDARD -> 12.dp
        GallerySize.LARGE -> 16.dp
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
        modifier = Modifier.fillMaxWidth().testTag("library-card:${entry.id}").then(
            if (entry.isContainer) Modifier.pressableNavigationClick(
                enabled = pageMotion?.isCurrentPage != false,
                onRelease = { scale -> albumTransitionKey?.let { pageMotion?.handoff?.capture(it, scale) } },
                onAbandon = { pageMotion?.handoff?.clear() },
                onClick = onOpen,
            ) else Modifier.clickable(onClick = onOpen),
        ),
        // The card must not clip the cover to a different radius at the start or end of a flight.
        shape = if (entry.isContainer) RoundedCornerShape(
            topStart = AlbumArtworkCornerRadius,
            topEnd = AlbumArtworkCornerRadius,
            bottomStart = cardCornerRadius,
            bottomEnd = cardCornerRadius,
        ) else RoundedCornerShape(cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        ArtworkTile(
            entry = entry,
            size = null,
            requestSizePx = artworkRequestSizePx,
            retryTransientFailures = true,
            cornerRadius = artworkCornerRadius,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).testTag("library-cover:${entry.id}"),
            albumTransitionKey = albumTransitionKey,
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
    albumTransitionKey: BrowsePageKey? = null,
) {
    val pageMotion = LocalLibraryPageMotion.current
    val background = if (emphasized) MaterialTheme.colorScheme.tertiaryContainer
    else MaterialTheme.colorScheme.surfaceContainer
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("library-card:${entry.id}").then(
            if (entry.isContainer) Modifier.pressableNavigationClick(
                enabled = pageMotion?.isCurrentPage != false,
                onRelease = { scale -> albumTransitionKey?.let { pageMotion?.handoff?.capture(it, scale) } },
                onAbandon = { pageMotion?.handoff?.clear() },
                onClick = onOpen,
            ) else Modifier.clickable(onClick = onOpen),
        ),
        color = background,
        shape = RoundedCornerShape(if (emphasized) 28.dp else 20.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val artworkSize = if (emphasized) 62.dp else 54.dp
            ArtworkTile(
                entry = entry,
                size = artworkSize,
                requestSizePx = with(LocalDensity.current) { artworkSize.roundToPx() },
                modifier = Modifier.testTag("library-cover:${entry.id}"),
                albumTransitionKey = albumTransitionKey,
            )
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

private fun queueDisplayItems(queue: List<QueueItem>): List<QueueDisplayItem> =
    queue.mapIndexed { index, item -> QueueDisplayItem(item.queueId, index, item.track) }

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
    onPlay: (String) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onClear: () -> Unit,
    bottomContentPadding: Dp,
) {
    val topInset = LocalContentTopInset.current
    if (state.queue.isEmpty()) {
        CenteredStatusLayout(
            title = stringResource(R.string.queue_up_next),
            modifier = Modifier.padding(top = topInset),
            supportingContent = {
                Text(pluralStringResource(R.plurals.queue_summary, 0, 0), color = MaterialTheme.colorScheme.onSurfaceVariant)
            },
        ) {
            EmptyPanel(Icons.AutoMirrored.Rounded.PlaylistPlay, stringResource(R.string.queue_empty))
        }
        return
    }
    var displayedQueue by remember { mutableStateOf(queueDisplayItems(state.queue)) }
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
        displayedQueue = queueDisplayItems(state.queue)
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
            .protectTopEdge(listState)
            .testTag("queue-list")
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
        contentPadding = PaddingValues(20.dp, topInset + 24.dp, 20.dp, bottomContentPadding),
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
            val playing = queueItem.key == state.currentQueueItem?.queueId
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
                    .testTag("queue-item-${queueItem.key}")
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
                                    onPlay(queueItem.key)
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
private const val LYRICS_PANEL_TRANSITION_DURATION_MS = 320

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
        CenteredStatusLayout(
            title = stringResource(R.string.nav_now_playing),
            modifier = modifier,
        ) {
            EmptyPanel(Icons.Rounded.Album, stringResource(R.string.no_current_track))
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
                modifier = Modifier.fillMaxWidth().playerControlsEnter(40),
                style = MaterialTheme.typography.headlineLarge,
            )
            Spacer(Modifier.height(if (compact) 8.dp else 16.dp))
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                // A restored portrait lyrics panel must not remove the incoming shared cover.
                // Reveal it with the existing lyrics transition once the cover has landed.
                val lyricsVisible = showLyrics && state.preferences.lyricsEnabled &&
                    LocalPlayerPageMotion.current?.enteringFromMiniPlayer != true
                val transition = updateTransition(lyricsVisible, label = "artwork-lyrics")
                val artworkSize = minOf(
                    maxWidth * if (wide) .68f else .84f,
                    maxHeight * .96f,
                    if (wide) 440.dp else 360.dp,
                )
                val splitArtworkWidth = (maxWidth - 24.dp).coerceAtLeast(0.dp) * (7f / 15f)
                val splitArtworkSize = minOf(splitArtworkWidth, maxHeight * .96f, 440.dp)
                val artworkOffset by transition.animateDp(
                    transitionSpec = { tween(LYRICS_PANEL_TRANSITION_DURATION_MS, easing = FastOutSlowInEasing) },
                    label = "artwork-offset",
                ) { visible ->
                    if (visible && canShowSideBySide) (splitArtworkWidth - maxWidth) / 2f else 0.dp
                }
                val artworkScale by transition.animateFloat(
                    transitionSpec = { tween(LYRICS_PANEL_TRANSITION_DURATION_MS, easing = FastOutSlowInEasing) },
                    label = "artwork-scale",
                ) { visible ->
                    if (visible && canShowSideBySide && artworkSize > 0.dp) splitArtworkSize / artworkSize else 1f
                }
                val fadeDuration = LYRICS_PANEL_TRANSITION_DURATION_MS
                transition.AnimatedVisibility(
                    visible = { visible -> !visible || canShowSideBySide },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset { IntOffset(artworkOffset.roundToPx(), 0) }
                        .zIndex(if (canShowSideBySide) 1f else 0f),
                    enter = fadeIn(tween(fadeDuration)) + scaleIn(tween(fadeDuration), initialScale = .96f),
                    exit = fadeOut(tween(fadeDuration)) + scaleOut(tween(fadeDuration), targetScale = .96f),
                ) {
                    val artworkContentDescription = when {
                        !state.preferences.lyricsEnabled -> stringResource(R.string.album_cover, track.title)
                        lyricsVisible -> stringResource(R.string.album_cover_close_lyrics, track.title)
                        else -> stringResource(R.string.album_cover_open_lyrics, track.title)
                    }
                    // Keep one artwork and a stable image size while its position and scale animate.
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = artworkScale
                                scaleY = artworkScale
                            }
                            .semantics { contentDescription = artworkContentDescription }
                            .then(
                                if (state.preferences.lyricsEnabled) {
                                    Modifier.clickable(
                                        onClickLabel = stringResource(
                                            if (lyricsVisible) R.string.close_lyrics else R.string.open_lyrics,
                                        ),
                                    ) { showLyrics = !showLyrics }
                                } else {
                                    Modifier
                                },
                            ),
                    ) { PlaybackArtwork(track, artworkSize, expanded = true) }
                }
                transition.AnimatedVisibility(
                    visible = { it },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(if (canShowSideBySide) (maxWidth - 24.dp) * (8f / 15f) else maxWidth),
                    enter = fadeIn(tween(fadeDuration)) + if (canShowSideBySide) {
                        slideInHorizontally(tween(fadeDuration, easing = FastOutSlowInEasing)) { it / 12 }
                    } else {
                        scaleIn(tween(fadeDuration), initialScale = .96f)
                    },
                    exit = fadeOut(tween(fadeDuration)) + if (canShowSideBySide) {
                        slideOutHorizontally(tween(fadeDuration, easing = FastOutSlowInEasing)) { it / 12 }
                    } else {
                        scaleOut(tween(fadeDuration), targetScale = .96f)
                    },
                ) {
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
                }
            }
            Spacer(Modifier.height(if (compact) 8.dp else 16.dp))
            Text(
                track.title,
                modifier = Modifier.fillMaxWidth().playerControlsEnter(),
                style = trackTitleStyle,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                track.creator.ifBlank { stringResource(R.string.unknown_artist) },
                modifier = Modifier.fillMaxWidth().playerControlsEnter(120),
                style = artistStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                track.album.ifBlank { stringResource(R.string.unknown_album) },
                modifier = Modifier.fillMaxWidth().playerControlsEnter(140),
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
                modifier = Modifier.playerControlsEnter(160),
            )
            Row(Modifier.fillMaxWidth().playerControlsEnter(160), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatPlaybackTime(state.positionMs), style = MaterialTheme.typography.labelMedium)
                Text(
                    if (state.durationMs > 0L) formatPlaybackTime(state.durationMs) else track.duration ?: "--:--",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Spacer(Modifier.height(if (compact) 6.dp else 12.dp))
            Row(
                modifier = Modifier.playerControlsEnter(180),
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
    modifier: Modifier = Modifier,
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
        modifier = modifier.fillMaxWidth(),
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
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val track = state.currentTrack ?: return
    val pageMotion = LocalPlayerPageMotion.current
    Surface(
        modifier = modifier.fillMaxWidth().testTag("mini-player").pressableNavigationClick(
            enabled = enabled && pageMotion?.isCurrentPage != false,
            onRelease = { scale -> pageMotion?.captureArtworkPress(track, scale) },
            onAbandon = { pageMotion?.handoff?.clear() },
            onClick = onOpen,
        ),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 8.dp,
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PlaybackArtwork(track, 48.dp, expanded = false)
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
            IconButton(onClick = onTogglePlayback, enabled = enabled) {
                Icon(if (state.playbackState == RemotePlaybackState.PLAYING) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, stringResource(R.string.play_or_pause))
            }
            IconButton(
                onClick = onNext,
                enabled = enabled && canSelectNextTrack(state.queue, state.currentQueueIndex, state.playbackOrder),
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
    albumTransitionKey: BrowsePageKey? = null,
    useCachedThumbnailDuringFlight: Boolean = false,
) {
    val context = LocalContext.current
    val motion = LocalLibraryPageMotion.current
    val returningPlaceholderKey = motion?.returningArtworkUri
        ?.takeIf { motion.navigation?.albumKey == albumTransitionKey }
    val colors = remember(entry.title) {
        artworkPalettes[(entry.title.hashCode() and Int.MAX_VALUE) % artworkPalettes.size]
    }
    val placeholderBrush = remember(colors) { Brush.linearGradient(colors) }
    val selectedArtworkUri = if (preferThumbnailSource) entry.thumbnailArtworkUri else entry.artworkUri
    val flightThumbnail = remember(context, imageIdentity, selectedArtworkUri, placeholderArtworkUri, useCachedThumbnailDuringFlight) {
        if (useCachedThumbnailDuringFlight) {
            (placeholderArtworkUri ?: selectedArtworkUri)?.takeIf(String::isNotBlank)?.let { uri ->
                SingletonImageLoader.get(context).memoryCache
                    ?.get(MemoryCache.Key(albumArtworkThumbnailMemoryCacheKey(uri)))
                    ?.image?.asPainter(context, FilterQuality.Low)
            }
        } else null
    }
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
        useCachedThumbnailDuringFlight,
        placeholderArtworkUri,
        returningPlaceholderKey,
    ) {
        selectedArtworkUri?.takeIf { !useCachedThumbnailDuringFlight && it.isNotBlank() }?.let { uri ->
            when {
                useCachedAlbumThumbnailAsPlaceholder -> ImageRequest.Builder(context)
                    .data(uri)
                    .placeholderMemoryCacheKey(
                        albumArtworkThumbnailMemoryCacheKey(placeholderArtworkUri ?: uri),
                    )
                    .apply { if (requestSizePx != null) size(requestSizePx) }
                    .scale(Scale.FILL)
                    .precision(Precision.INEXACT)
                    // The cached thumbnail is already visible; dissolving it breaks the shared cover.
                    .crossfade(false)
                    .build()
                requestSizePx != null -> albumArtworkRequest(
                    context, uri, requestSizePx,
                    // Reuse this album's large cover if its small thumbnail was evicted while browsing tracks.
                    placeholderMemoryCacheKey = returningPlaceholderKey,
                )
                else -> uri
            }
        }
    }
    val resolvedCornerRadius = cornerRadius ?: if (entry.isContainer) AlbumArtworkCornerRadius else 16.dp
    // Size modifiers must follow the shared element so the animated bounds reach the artwork.
    val sharedModifier = Modifier.sharedAlbumArtwork(albumTransitionKey, resolvedCornerRadius).then(modifier)
    val tileModifier = if (size == null) sharedModifier else sharedModifier.size(size)
    Box(
        modifier = tileModifier.background(placeholderBrush),
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
            modifier = Modifier.fillMaxSize(.48f),
        )
        if (useCachedThumbnailDuringFlight) {
            flightThumbnail?.let { painter ->
                Image(
                    painter = painter,
                    contentDescription = stringResource(R.string.album_cover, entry.title),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        } else if (artworkModel != null) {
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
internal fun AppIcon(size: Dp) {
    AndroidView(
        factory = { context ->
            ImageView(context).apply { setImageResource(R.mipmap.ic_launcher) }
        },
        modifier = Modifier.size(size).clip(RoundedCornerShape(percent = 28)),
    )
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

// The status center depends only on the large title and the Scaffold's navigation inset.
// Search, breadcrumbs and queue counts remain independent of this shared coordinate space.
@Composable
internal fun CenteredStatusLayout(
    title: String,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
    supportingContent: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxSize().padding(start = 20.dp, top = 24.dp, end = 20.dp)) {
        // Font fallback can give Chinese and Latin titles different measured heights.
        // A shared scalable title slot keeps those metrics out of the status position.
        Box(Modifier.fillMaxWidth().height(with(LocalDensity.current) {
            MaterialTheme.typography.headlineLarge.lineHeight.toDp() + 8.dp
        })) {
            if (showTitle) Text(title, style = MaterialTheme.typography.headlineLarge)
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
            supportingContent()
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
            modifier = Modifier.size(cardSize).testTag("empty-status-card"),
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
                queue = tracks.filterNot { it.isContainer }.map(QueueItem::create),
                currentQueueIndex = 0,
                playbackState = RemotePlaybackState.PLAYING,
                isSearching = false,
                browseLoadStatus = BrowseLoadStatus.LOADED,
            ),
            snackbar = remember { SnackbarHostState() },
            onDestination = {},
            onOpenNowPlayingFromMiniPlayer = {},
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
