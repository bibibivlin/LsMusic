@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.linxyi.lsmusic.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linxyi.lsmusic.dlna.DlnaDevice
import com.linxyi.lsmusic.dlna.MediaEntry
import com.linxyi.lsmusic.dlna.RemotePlaybackState
import com.linxyi.lsmusic.dlna.DlnaDeviceKind
import com.linxyi.lsmusic.ui.theme.LsMusicTheme
import coil3.compose.AsyncImage
import coil3.SingletonImageLoader
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.Scale
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt

private data class DestinationItem(
    val destination: AppDestination,
    val label: String,
    val icon: ImageVector,
)

@Immutable
private data class LibraryUiState(
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
    val currentTrackId: String?,
    val playbackState: RemotePlaybackState,
) {
    val isBrowsing: Boolean
        get() = browseLoadStatus == BrowseLoadStatus.LOADING
}

private const val MEDIA_ENTRY_KEY_PREFIX = "media:"
private const val LIBRARY_GRID_HEADER_COUNT = 3
private const val TRACK_COLLECTION_HEADER_COUNT = 2
private const val ALBUM_ART_PREFETCH_SCREENS = 2
private const val MAX_ACTIVE_ART_PREFETCHES = 32

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
) {
    val context = LocalContext.current
    val imageLoader = remember(context) { SingletonImageLoader.get(context) }

    LaunchedEffect(pageKey, entries, gridState, requestSizePx, enabled) {
        if (!enabled) return@LaunchedEffect
        val requests = LinkedHashMap<String, Disposable>()
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
                val prefetchIndices = directionalPrefetchRange(
                    firstVisibleIndex = firstVisible,
                    lastVisibleIndex = lastVisible,
                    lastEntryIndex = entries.lastIndex,
                    prefetchCount = prefetchCount,
                    forward = scrollingForward,
                )
                val visibleUris = entries.subList(firstVisible, lastVisible + 1)
                    .mapNotNull { it.artworkUri?.takeIf(String::isNotBlank) }
                    .toSet()
                val prefetchUris = prefetchIndices
                    .mapNotNull { index -> entries.getOrNull(index)?.artworkUri?.takeIf(String::isNotBlank) }
                    .toSet()
                val retainedUris = visibleUris + prefetchUris

                val iterator = requests.iterator()
                while (iterator.hasNext()) {
                    val request = iterator.next()
                    if (request.value.isDisposed || request.key !in retainedUris) {
                        if (!request.value.isDisposed) request.value.dispose()
                        iterator.remove()
                    }
                }

                prefetchUris.forEach { uri ->
                    if (uri !in requests) {
                        requests[uri] = imageLoader.enqueue(albumArtworkRequest(context, uri, requestSizePx))
                    }
                }
                while (requests.size > MAX_ACTIVE_ART_PREFETCHES) {
                    val eldest = requests.entries.iterator().next()
                    if (!eldest.value.isDisposed) eldest.value.dispose()
                    requests.remove(eldest.key)
                }
            }
        } finally {
            requests.values.forEach { if (!it.isDisposed) it.dispose() }
        }
    }
}

private val destinations = listOf(
    DestinationItem(AppDestination.LIBRARY, "媒体库", Icons.Rounded.LibraryMusic),
    DestinationItem(AppDestination.QUEUE, "播放列表", Icons.AutoMirrored.Rounded.PlaylistPlay),
    DestinationItem(AppDestination.NOW_PLAYING, "正在播放", Icons.Rounded.GraphicEq),
    DestinationItem(AppDestination.SETTINGS, "设置", Icons.Rounded.Settings),
)

@Composable
fun LsMusicApp(viewModel: LsMusicViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val darkTheme = when (state.preferences.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    LsMusicTheme(darkTheme = darkTheme, dynamicColor = state.preferences.useDynamicColor) {
        SystemNavigationBarAppearance()
        val snackbar = remember { SnackbarHostState() }

        LaunchedEffect(state.error) {
            state.error?.let {
                snackbar.showSnackbar(it)
                viewModel.consumeError()
            }
        }

        BackHandler(
            enabled = state.destination == AppDestination.LIBRARY && state.path.size > 1,
        ) {
            viewModel.navigateTo(state.path.lastIndex - 1)
        }

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
            onListenBrainzEnabled = viewModel::setListenBrainzEnabled,
            onListenBrainzToken = viewModel::validateAndSaveListenBrainzToken,
            onListenBrainzMinimumSeconds = viewModel::setListenBrainzMinimumSeconds,
            onListenBrainzMinimumPercent = viewModel::setListenBrainzMinimumPercent,
        )
    }
}

@Composable
private fun LsMusicContent(
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
    onListenBrainzEnabled: (Boolean) -> Unit,
    onListenBrainzToken: (String) -> Unit,
    onListenBrainzMinimumSeconds: (Int) -> Unit,
    onListenBrainzMinimumPercent: (Int) -> Unit,
) {
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
            currentTrackId = state.currentTrack?.id,
            playbackState = state.playbackState,
        )
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = maxWidth >= 720.dp
        Row(Modifier.fillMaxSize()) {
            if (expanded) {
                AppNavigationRail(state.destination, onDestination)
            }
            Scaffold(
                modifier = Modifier.weight(1f),
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    Column {
                        // The player must leave immediately: an exit animation changes Scaffold's
                        // content height and makes the adaptive now-playing artwork resize mid-entry.
                        if (state.currentTrack != null && state.destination != AppDestination.NOW_PLAYING) {
                            MiniPlayer(
                                state = state,
                                onOpen = { onDestination(AppDestination.NOW_PLAYING) },
                                onTogglePlayback = onTogglePlayback,
                                onNext = onNext,
                            )
                        }
                        if (!expanded) AppNavigationBar(state.destination, onDestination)
                    }
                },
            ) { padding ->
                // The now-playing page measures its artwork from the final available height.
                // Avoid AnimatedContent's intermediate size constraints, which make it visibly resize
                // once while navigating from another destination on large and foldable screens.
                Box(Modifier.fillMaxSize().padding(padding)) {
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
                            onOpenSettings = { onDestination(AppDestination.SETTINGS) },
                        )
                        AppDestination.QUEUE -> QueueScreen(
                            state,
                            onPlay,
                            onRemoveQueue,
                            onMoveQueue,
                            onClearQueue,
                        )
                        AppDestination.NOW_PLAYING -> NowPlayingScreen(
                            state,
                            onTogglePlayback,
                            onPrevious,
                            onNext,
                            onCycleRepeat,
                            onToggleShuffle,
                            onSeek,
                        )
                        AppDestination.SETTINGS -> SettingsScreen(
                            state = state,
                            preferences = state.preferences,
                            onRefresh = onRefresh,
                            onSelectServer = onSelectServer,
                            onSelectRenderer = onSelectRenderer,
                            onGallerySize = onGallerySize,
                            onDefaultGridLayout = onDefaultGridLayout,
                            onThemeMode = onThemeMode,
                            onDynamicColor = onDynamicColor,
                            onListenBrainzEnabled = onListenBrainzEnabled,
                            onListenBrainzToken = onListenBrainzToken,
                            onListenBrainzMinimumSeconds = onListenBrainzMinimumSeconds,
                            onListenBrainzMinimumPercent = onListenBrainzMinimumPercent,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppNavigationBar(selected: AppDestination, onDestination: (AppDestination) -> Unit) {
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
                    selected = selected == item.destination,
                    onClick = { onDestination(item.destination) },
                    icon = { Icon(item.icon, null) },
                    label = { Text(item.label) },
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
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars =
            colorScheme.surfaceContainer.luminance() > .5f
    }
}

@Composable
private fun AppNavigationRail(selected: AppDestination, onDestination: (AppDestination) -> Unit) {
    NavigationRail(Modifier.fillMaxHeight().width(132.dp)) {
        Spacer(Modifier.height(24.dp))
        AlbumMark(54.dp)
        Spacer(Modifier.height(28.dp))
        destinations.forEach { item ->
            NavigationRailItem(
                modifier = Modifier.width(116.dp),
                selected = selected == item.destination,
                onClick = { onDestination(item.destination) },
                icon = { Icon(item.icon, null) },
                label = {
                    Text(
                        item.label,
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
                onNavigateTo = onNavigateTo,
                onPlay = onPlay,
                onQueue = onQueue,
                onPlayAll = onPlayAll,
                onShufflePlay = onShufflePlay,
                onQueueAll = onQueueAll,
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
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回上一级")
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
            LoadingPanel("正在读取音乐内容…")
        }
    }
}

@Composable
private fun LibraryDirectoryScreen(
    state: LibraryUiState,
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

    BoxWithConstraints {
        val gridSpacing = 12.dp
        val availableWidth = (maxWidth - 40.dp).coerceAtLeast(1.dp)
        val minimumCellWidth = state.preferences.gallerySize.minCellSize.dp
        val columnCount = if (useGrid) {
            ((availableWidth + gridSpacing) / (minimumCellWidth + gridSpacing)).toInt().coerceAtLeast(1)
        } else {
            1
        }
        val artworkWidth = (availableWidth - gridSpacing * (columnCount - 1)) / columnCount
        val artworkRequestSizePx = with(LocalDensity.current) { artworkWidth.roundToPx() }

        AlbumArtworkPrefetchEffect(
            pageKey = pageKey,
            entries = visibleEntries,
            gridState = gridState,
            requestSizePx = artworkRequestSizePx,
            enabled = useGrid && isAlbumCollection && !state.isBrowsing && visibleEntries.isNotEmpty(),
        )

        LazyVerticalGrid(
            columns = if (useGrid) GridCells.Adaptive(minimumCellWidth) else GridCells.Fixed(1),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(gridSpacing),
            horizontalArrangement = Arrangement.spacedBy(gridSpacing),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, contentType = "library-header") {
                Text("L's Music", style = MaterialTheme.typography.headlineLarge)
            }
            item(span = { GridItemSpan(maxLineSpan) }, contentType = "library-header") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = {
                        if (query.isNotBlank()) IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, "清空搜索")
                        }
                    },
                    placeholder = { Text("搜索当前目录") },
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }, contentType = "library-header") {
                if (isAlbumCollection) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (state.path.size > 1) {
                                FilledTonalIconButton(onClick = { onNavigateTo(state.path.lastIndex - 1) }) {
                                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回上一级")
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
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回上一级")
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        Breadcrumbs(state.path, onNavigateTo, Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        FilledTonalIconButton(onClick = { useGrid = !useGrid }) {
                            Icon(
                                if (useGrid) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                                if (useGrid) "切换到列表" else "切换到封面网格",
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
                    LoadingPanel("正在加载音乐库…")
                }
                LibraryContentStatus.NO_SERVER -> item(
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "library-status",
                ) {
                    EmptyPanel(
                        icon = Icons.Rounded.Devices,
                        title = "还没发现媒体库",
                        body = "在设置中选择或重新扫描局域网内的 DLNA 媒体库。",
                        action = "打开设备设置",
                        onAction = onOpenSettings,
                    )
                }
                LibraryContentStatus.SERVER_UNAVAILABLE -> item(
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "library-status",
                ) {
                    EmptyPanel(
                        icon = Icons.Rounded.Devices,
                        title = "上次使用的媒体库当前不可用",
                        body = "确认媒体库已开机并连接到同一局域网，或选择其他媒体库。",
                        action = "打开设备设置",
                        onAction = onOpenSettings,
                    )
                }
                LibraryContentStatus.LOAD_FAILED -> item(
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "library-status",
                ) {
                    EmptyPanel(
                        icon = Icons.Rounded.MusicNote,
                        title = "无法读取音乐目录",
                        body = "请确认媒体库连接正常，然后重新选择或扫描设备。",
                        action = "打开设备设置",
                        onAction = onOpenSettings,
                    )
                }
                LibraryContentStatus.EMPTY -> item(
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "library-status",
                ) {
                    EmptyPanel(
                        icon = Icons.Rounded.MusicNote,
                        title = if (query.isBlank()) "这个目录是空的" else "没有匹配的音乐",
                        body = if (query.isBlank()) "返回上一级看看其他唱片或播放列表。" else "换一个关键词试试。",
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
                            compact = state.preferences.gallerySize == GallerySize.COMPACT,
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
                    "$albumCount 张专辑",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "浏览与排序",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AlbumSortPicker(sort, onSort)
            Spacer(Modifier.width(6.dp))
            FilledTonalIconButton(onClick = onToggleLayout) {
                Icon(
                    if (useGrid) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                    if (useGrid) "切换到列表" else "切换到封面网格",
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
            label = { Text(selected.shortLabel) },
            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Sort, null, Modifier.size(18.dp)) },
            trailingIcon = { Icon(Icons.Rounded.ExpandMore, null, Modifier.size(18.dp)) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AlbumSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(option.menuLabel)
                            option.explanation?.let {
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

private val AlbumSort.shortLabel: String
    get() = when (this) {
        AlbumSort.SERVER_DEFAULT -> "服务器默认"
        AlbumSort.YEAR_ASCENDING -> "年份 ↑"
        AlbumSort.YEAR_DESCENDING -> "年份 ↓"
        AlbumSort.ALBUM_ARTIST -> "专辑艺术家"
        AlbumSort.TITLE -> "标题"
    }

private val AlbumSort.menuLabel: String
    get() = when (this) {
        AlbumSort.SERVER_DEFAULT -> "服务器默认排序"
        AlbumSort.YEAR_ASCENDING -> "年份：从早到晚"
        AlbumSort.YEAR_DESCENDING -> "年份：从新到旧"
        AlbumSort.ALBUM_ARTIST -> "专辑艺术家"
        AlbumSort.TITLE -> "标题"
    }

private val AlbumSort.explanation: String?
    get() = when (this) {
        AlbumSort.SERVER_DEFAULT -> "沿用媒体服务器返回的次序"
        AlbumSort.TITLE -> "数字和符号 → 英文 → 中文拼音 → 其他语言"
        else -> null
    }

@Composable
private fun AlbumDetailScreen(
    state: LibraryUiState,
    pageKey: BrowsePageKey,
    initialViewState: BrowseViewState,
    onSaveBrowseViewState: (BrowsePageKey, BrowseViewState) -> Unit,
    onNavigateTo: (Int) -> Unit,
    onPlay: (MediaEntry) -> Unit,
    onQueue: (MediaEntry) -> Unit,
    onPlayAll: (List<MediaEntry>) -> Unit,
    onShufflePlay: (List<MediaEntry>) -> Unit,
    onQueueAll: (List<MediaEntry>) -> Unit,
) {
    val tracks = remember(state.entries) { state.entries.filterNot { it.isContainer } }
    val representativeTrack = tracks.firstOrNull()
    val currentLocation = state.path.lastOrNull()
    val title = currentLocation?.title ?: representativeTrack?.album.orEmpty()
    val headerArtworkEntry = currentLocation?.let { location ->
        MediaEntry(
            id = location.id,
            parentId = state.path.getOrNull(state.path.lastIndex - 1)?.id.orEmpty(),
            title = title,
            creator = location.albumArtist.orEmpty(),
            albumArtist = location.albumArtist.orEmpty(),
            year = location.year,
            artworkUri = location.artworkUri ?: representativeTrack?.artworkUri,
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
            headerCount = TRACK_COLLECTION_HEADER_COUNT,
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
        contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.path.size > 1) {
                    FilledTonalIconButton(onClick = { onNavigateTo(state.path.lastIndex - 1) }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回上一级")
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Breadcrumbs(state.path, onNavigateTo, Modifier.weight(1f))
            }
        }
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                headerArtworkEntry?.let {
                    ArtworkTile(
                        entry = it,
                        size = 200.dp,
                        imageIdentity = pageKey,
                        useCachedAlbumThumbnailAsPlaceholder = true,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                if (artists.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(artists, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (state.isBrowsing) "正在读取曲目…" else "$trackCount 首歌曲",
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
                        Text("播放全部")
                    }
                    OutlinedButton(
                        onClick = { onShufflePlay(tracks) },
                        enabled = !state.isBrowsing && trackCount > 0,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp),
                    ) {
                        Icon(Icons.Rounded.Shuffle, null)
                        Spacer(Modifier.width(4.dp))
                        Text("随机播放")
                    }
                    OutlinedButton(
                        onClick = { onQueueAll(tracks) },
                        enabled = !state.isBrowsing && trackCount > 0,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp),
                    ) {
                        Icon(Icons.Rounded.Add, null)
                        Spacer(Modifier.width(4.dp))
                        Text("加入队列")
                    }
                }
            }
        }
        if (!state.isBrowsing && trackCount == 0) {
            item {
                EmptyPanel(
                    icon = Icons.Rounded.MusicNote,
                    title = "这张专辑没有可播放的曲目",
                    body = "媒体服务器没有返回可播放的音频内容。",
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
                if (isPlaying) Icon(Icons.Rounded.GraphicEq, "正在播放", tint = MaterialTheme.colorScheme.primary)
                else Text(number.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    track.creator.ifBlank { track.album.ifBlank { "未知艺术家" } },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                track.duration?.let { formatPlaybackTime(LsMusicViewModel.parseTimeMs(it)) } ?: "--:--",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onQueue) { Icon(Icons.Rounded.Add, "加入播放列表") }
        }
    }
}

@Composable
private fun DeviceStrip(
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
                label = "音乐来自",
                icon = Icons.Rounded.LibraryMusic,
                devices = state.servers,
                selectedId = state.selectedServerId,
                rememberedDevice = state.rememberedServer,
                rememberedLabel = "上次使用的媒体库",
                isSearching = state.isSearching,
                emptyLabel = "未发现媒体库",
                onSelected = onSelectServer,
            )
            DevicePicker(
                modifier = Modifier.weight(1f),
                label = "播放到",
                icon = Icons.Rounded.Speaker,
                devices = state.renderers,
                selectedId = state.selectedRendererId,
                rememberedDevice = state.rememberedRenderer,
                rememberedLabel = "上次使用的播放设备",
                isSearching = state.isSearching,
                emptyLabel = "选择播放器",
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
    val selectedName = selected?.name?.takeIf { it.isNotBlank() }
        ?: rememberedSelection?.name?.takeIf { it.isNotBlank() }
        ?: if (rememberedSelection != null) rememberedLabel else emptyLabel
    val connectionStatus = when {
        selected != null || rememberedSelection == null -> null
        isSearching -> "正在连接"
        else -> "当前不可用"
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
                            Text(device.name)
                            val detail = listOf(device.manufacturer, device.model).filter { it.isNotBlank() }.joinToString(" · ")
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
            AssistChip(onClick = { onNavigateTo(index) }, label = { Text(location.title) })
        }
    }
}

@Composable
private fun MediaGridCard(
    entry: MediaEntry,
    compact: Boolean,
    artworkRequestSizePx: Int,
    onOpen: () -> Unit,
    onQueue: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        ArtworkTile(
            entry = entry,
            size = null,
            requestSizePx = artworkRequestSizePx,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                start = 14.dp,
                top = 12.dp,
                end = if (entry.isContainer) 14.dp else 8.dp,
                bottom = 12.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    entry.title,
                    modifier = Modifier.heightIn(min = 48.dp),
                    style = if (compact) {
                        MaterialTheme.typography.bodyLarge
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    when {
                        entry.isAlbum -> albumDetails(entry)
                        entry.isContainer && entry.childCount != null -> "${entry.childCount} 项"
                        entry.creator.isNotBlank() -> entry.creator
                        entry.album.isNotBlank() -> entry.album
                        entry.isContainer -> "文件夹"
                        else -> entry.duration ?: "音频"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!entry.isContainer) {
                IconButton(onClick = onQueue) { Icon(Icons.Rounded.Add, "加入播放列表") }
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
                    entry.isContainer && entry.childCount != null -> "${entry.childCount} 项"
                    entry.creator.isNotBlank() && !entry.duration.isNullOrBlank() -> "${entry.creator} · ${entry.duration}"
                    entry.creator.isNotBlank() -> entry.creator
                    !entry.duration.isNullOrBlank() -> entry.duration
                    entry.isContainer -> "文件夹"
                    else -> entry.mimeType?.substringAfter('/')?.uppercase() ?: "音频"
                }
                Text(details, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            if (entry.isContainer) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "打开")
            } else {
                IconButton(onClick = onQueue) { Icon(Icons.Rounded.Add, "加入播放列表") }
                FilledTonalIconButton(onClick = onPlay) { Icon(Icons.Rounded.PlayArrow, "立即播放") }
            }
        }
    }
}

private fun albumDetails(entry: MediaEntry): String = listOfNotNull(
    entry.albumArtist.takeIf { it.isNotBlank() },
    entry.year?.toString(),
    entry.childCount?.let { "$it 项" },
).joinToString(" · ").ifBlank { "专辑" }

@Composable
private fun SettingsScreen(
    state: LsMusicUiState,
    preferences: AppPreferences,
    onRefresh: () -> Unit,
    onSelectServer: (String) -> Unit,
    onSelectRenderer: (String) -> Unit,
    onGallerySize: (GallerySize) -> Unit,
    onDefaultGridLayout: (Boolean) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onListenBrainzEnabled: (Boolean) -> Unit,
    onListenBrainzToken: (String) -> Unit,
    onListenBrainzMinimumSeconds: (Int) -> Unit,
    onListenBrainzMinimumPercent: (Int) -> Unit,
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
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 24.dp, 20.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text("设置", style = MaterialTheme.typography.headlineLarge) }
        item {
            Text("播放与设备", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
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
        item {
            Text("界面", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
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
            SwitchSettingCard(
                title = "动态配色",
                description = "使用壁纸配色；关闭后使用 L's Music 默认配色。",
                checked = preferences.useDynamicColor,
                onCheckedChange = onDynamicColor,
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
        item {
            Text("网络", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
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
            Switch(checked = checked, onCheckedChange = onCheckedChange)
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
) {
    if (state.queue.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp, 24.dp, 20.dp, 32.dp),
        ) {
            Column {
                Text("接下来播放", style = MaterialTheme.typography.headlineLarge)
                Text("0 首音乐 · 队列保存在这台手机上", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                EmptyPanel(Icons.AutoMirrored.Rounded.PlaylistPlay, "播放列表还是空的")
            }
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 24.dp, 20.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("接下来播放", style = MaterialTheme.typography.headlineLarge)
                    Text("${state.queue.size} 首音乐 · 队列保存在这台手机上", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.queue.isNotEmpty()) FilledTonalButton(onClick = onClear) {
                    Icon(Icons.Rounded.ClearAll, null)
                    Spacer(Modifier.width(8.dp))
                    Text("清空")
                }
            }
        }
        itemsIndexed(state.queue, key = { index, item -> "queue:$index:${item.id}" }) { index, item ->
            val playing = index == state.currentQueueIndex
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onPlay(item) },
                shape = RoundedCornerShape(if (playing) 28.dp else 18.dp),
                color = if (playing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(30.dp), contentAlignment = Alignment.Center) {
                        if (playing) Icon(Icons.Rounded.GraphicEq, "正在播放", tint = MaterialTheme.colorScheme.primary)
                        else Text("${index + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    ArtworkTile(item, 48.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text(item.creator.ifBlank { item.duration ?: "音频" }, style = MaterialTheme.typography.bodySmall)
                    }
                    Column {
                        IconButton(onClick = { onMove(index, -1) }, enabled = index > 0, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Rounded.KeyboardArrowUp, "上移")
                        }
                        IconButton(onClick = { onMove(index, 1) }, enabled = index < state.queue.lastIndex, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Rounded.KeyboardArrowDown, "下移")
                        }
                    }
                    IconButton(onClick = { onRemove(index) }) { Icon(Icons.Rounded.DeleteOutline, "移除") }
                }
            }
        }
    }
}

@Composable
private fun NowPlayingScreen(
    state: LsMusicUiState,
    onTogglePlayback: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    val track = state.currentTrack
    if (track == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp, 28.dp, 24.dp, 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "正在播放",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineLarge,
            )
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                EmptyPanel(Icons.Rounded.Album, "还没有播放音乐")
            }
        }
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 620.dp
        val wide = maxWidth >= 720.dp
        val verticalPadding = if (compact) 16.dp else 28.dp
        val horizontalPadding = if (wide) 40.dp else 24.dp
        val titleStyle = if (compact) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.displaySmall
        val subtitleStyle = if (compact) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleLarge
        val primaryControlSize = if (compact) 68.dp else 82.dp
        val secondaryControlSize = if (compact) 50.dp else 58.dp
        val controlIconSize = if (compact) 28.dp else 30.dp
        val primaryIconSize = if (compact) 36.dp else 42.dp

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontalPadding, verticalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "正在播放",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineLarge,
            )
            Spacer(Modifier.height(if (compact) 8.dp else 16.dp))
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                val maximumArtwork = if (wide) 440.dp else 360.dp
                val artworkSize = minOf(
                    maxWidth * if (wide) .68f else .84f,
                    maxHeight * .96f,
                    maximumArtwork,
                )
                HeroArtwork(track, artworkSize)
            }
            Spacer(Modifier.height(if (compact) 8.dp else 16.dp))
            Text(
                track.title,
                modifier = Modifier.fillMaxWidth(),
                style = titleStyle,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                listOf(track.creator, track.album).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "来自 DLNA 媒体库" },
                modifier = Modifier.fillMaxWidth(),
                style = subtitleStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(if (compact) 8.dp else 16.dp))
            PlaybackSlider(
                positionMs = state.positionMs,
                durationMs = state.durationMs,
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
                            "随机播放，点击切换到顺序播放"
                        } else {
                            "顺序播放，点击切换到随机播放"
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
                    Icon(Icons.Rounded.SkipPrevious, "上一首", Modifier.size(controlIconSize))
                }
                FilledIconButton(onClick = onTogglePlayback, modifier = Modifier.size(primaryControlSize)) {
                    AnimatedContent(state.playbackState, label = "play pause") { playback ->
                        Icon(
                            if (playback == RemotePlaybackState.PLAYING) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            if (playback == RemotePlaybackState.PLAYING) "暂停" else "播放",
                            Modifier.size(primaryIconSize),
                        )
                    }
                }
                FilledTonalIconButton(
                    onClick = onNext,
                    enabled = canSelectNextTrack(state.queue, state.currentQueueIndex, state.playbackOrder),
                    modifier = Modifier.size(secondaryControlSize),
                ) { Icon(Icons.Rounded.SkipNext, "下一首", Modifier.size(controlIconSize)) }
                IconButton(onClick = onCycleRepeat) {
                    Icon(
                        when (state.playbackOrder.repeatMode) {
                            RepeatMode.NONE -> Icons.Rounded.SyncDisabled
                            RepeatMode.ONE -> Icons.Rounded.RepeatOne
                            RepeatMode.ALL -> Icons.Rounded.Repeat
                        },
                        when (state.playbackOrder.repeatMode) {
                            RepeatMode.NONE -> "循环关闭，点击切换到单曲循环"
                            RepeatMode.ONE -> "单曲循环，点击切换到列表循环"
                            RepeatMode.ALL -> "列表循环，点击关闭循环"
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
) {
    val track = state.currentTrack ?: return
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clickable(onClick = onOpen),
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
                    state.renderers.firstOrNull { it.id == state.selectedRendererId }?.name ?: "未选择播放器",
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = onTogglePlayback) {
                Icon(if (state.playbackState == RemotePlaybackState.PLAYING) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "播放或暂停")
            }
            IconButton(
                onClick = onNext,
                enabled = canSelectNextTrack(state.queue, state.currentQueueIndex, state.playbackOrder),
            ) {
                Icon(Icons.Rounded.SkipNext, "下一首")
            }
        }
    }
}

@Composable
private fun ArtworkTile(
    entry: MediaEntry,
    size: androidx.compose.ui.unit.Dp?,
    imageIdentity: Any = mediaEntryKey(entry),
    requestSizePx: Int? = null,
    useCachedAlbumThumbnailAsPlaceholder: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = remember(entry.title) {
        artworkPalettes[(entry.title.hashCode() and Int.MAX_VALUE) % artworkPalettes.size]
    }
    val placeholderBrush = remember(colors) { Brush.linearGradient(colors) }
    val artworkModel = remember(
        context,
        entry.artworkUri,
        requestSizePx,
        useCachedAlbumThumbnailAsPlaceholder,
    ) {
        entry.artworkUri?.takeIf(String::isNotBlank)?.let { uri ->
            when {
                requestSizePx != null -> albumArtworkRequest(context, uri, requestSizePx)
                useCachedAlbumThumbnailAsPlaceholder -> ImageRequest.Builder(context)
                    .data(uri)
                    .placeholderMemoryCacheKey(albumArtworkThumbnailMemoryCacheKey(uri))
                    .scale(Scale.FILL)
                    .precision(Precision.INEXACT)
                    .build()
                else -> uri
            }
        }
    }
    val tileModifier = if (size == null) modifier else modifier.size(size)
    val iconSize = (size ?: 72.dp) * .48f
    Box(
        modifier = tileModifier.clip(RoundedCornerShape(if (entry.isContainer) 18.dp else 16.dp))
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
                entry.artworkUri,
                requestSizePx,
                useCachedAlbumThumbnailAsPlaceholder,
            ) {
                AsyncImage(
                    model = artworkModel,
                    contentDescription = "${entry.title} 封面",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.Low,
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
            .clip(RoundedCornerShape(56.dp)).background(Brush.radialGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        if (!track.artworkUri.isNullOrBlank()) {
            key(track.id, track.artworkUri) {
                AsyncImage(
                    model = track.artworkUri,
                    contentDescription = "${track.title} 封面",
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
            Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.primary)
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(34.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier.padding(horizontal = 28.dp, vertical = 42.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(icon, null, Modifier.padding(18.dp).size(36.dp), tint = MaterialTheme.colorScheme.primary)
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
            onListenBrainzEnabled = {},
            onListenBrainzToken = {},
            onListenBrainzMinimumSeconds = {},
            onListenBrainzMinimumPercent = {},
        )
    }
}
