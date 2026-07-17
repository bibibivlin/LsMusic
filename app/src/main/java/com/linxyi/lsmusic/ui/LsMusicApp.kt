@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.linxyi.lsmusic.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
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
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speaker
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linxyi.lsmusic.dlna.DlnaDevice
import com.linxyi.lsmusic.dlna.MediaEntry
import com.linxyi.lsmusic.dlna.RemotePlaybackState
import com.linxyi.lsmusic.dlna.DlnaDeviceKind
import com.linxyi.lsmusic.ui.theme.LsMusicTheme
import coil3.compose.AsyncImage

private data class DestinationItem(
    val destination: AppDestination,
    val label: String,
    val icon: ImageVector,
)

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
            onQueueAll = viewModel::addAllToQueue,
            onTogglePlayback = viewModel::togglePlayback,
            onPrevious = viewModel::previous,
            onNext = viewModel::next,
            onSeek = viewModel::seekTo,
            onRemoveQueue = viewModel::removeFromQueue,
            onMoveQueue = viewModel::moveQueueItem,
            onClearQueue = viewModel::clearQueue,
            onGallerySize = viewModel::setGallerySize,
            onDefaultGridLayout = viewModel::setDefaultGridLayout,
            onThemeMode = viewModel::setThemeMode,
            onDynamicColor = viewModel::setDynamicColor,
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
    onQueueAll: (List<MediaEntry>) -> Unit,
    onTogglePlayback: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onRemoveQueue: (Int) -> Unit,
    onMoveQueue: (Int, Int) -> Unit,
    onClearQueue: () -> Unit,
    onGallerySize: (GallerySize) -> Unit,
    onDefaultGridLayout: (Boolean) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
) {
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
                        AnimatedVisibility(
                            visible = state.currentTrack != null && state.destination != AppDestination.NOW_PLAYING,
                            enter = fadeIn() + scaleIn(initialScale = .96f),
                            exit = fadeOut() + scaleOut(targetScale = .96f),
                        ) {
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
                AnimatedContent(
                    targetState = state.destination,
                    label = "main destination",
                    modifier = Modifier.fillMaxSize().padding(padding),
                ) { destination ->
                    when (destination) {
                        AppDestination.LIBRARY -> LibraryScreen(
                            state,
                            onOpen,
                            onNavigateTo,
                            onPlay,
                            onQueue,
                            onPlayAll,
                            onQueueAll,
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
    NavigationRail(Modifier.fillMaxHeight().width(92.dp)) {
        Spacer(Modifier.height(24.dp))
        AlbumMark(54.dp)
        Spacer(Modifier.height(28.dp))
        destinations.forEach { item ->
            NavigationRailItem(
                selected = selected == item.destination,
                onClick = { onDestination(item.destination) },
                icon = { Icon(item.icon, null) },
                label = { Text(item.label) },
            )
        }
    }
}

@Composable
private fun LibraryScreen(
    state: LsMusicUiState,
    onOpen: (MediaEntry) -> Unit,
    onNavigateTo: (Int) -> Unit,
    onPlay: (MediaEntry) -> Unit,
    onQueue: (MediaEntry) -> Unit,
    onPlayAll: (List<MediaEntry>) -> Unit,
    onQueueAll: (List<MediaEntry>) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var useGrid by rememberSaveable(state.preferences.useGridByDefault) {
        mutableStateOf(state.preferences.useGridByDefault)
    }
    val visibleEntries = remember(state.entries, query) {
        if (query.isBlank()) state.entries
        else state.entries.filter {
            it.title.contains(query, ignoreCase = true) || it.creator.contains(query, ignoreCase = true)
        }
    }
    val isTrackCollection = state.entries.isNotEmpty() && state.entries.all { !it.isContainer }

    if (isTrackCollection && !state.isBrowsing) {
        TrackCollectionScreen(
            state = state,
            onNavigateTo = onNavigateTo,
            onPlay = onPlay,
            onQueue = onQueue,
            onPlayAll = onPlayAll,
            onQueueAll = onQueueAll,
        )
        return
    }

    LazyVerticalGrid(
        columns = if (useGrid) GridCells.Adaptive(state.preferences.gallerySize.minCellSize.dp) else GridCells.Fixed(1),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("L's Music", style = MaterialTheme.typography.headlineLarge)
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
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
        item(span = { GridItemSpan(maxLineSpan) }) {
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

        when {
            state.isSearching && state.servers.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                LoadingPanel("正在扫描局域网中的 DLNA 设备…")
            }
            state.servers.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyPanel(
                    icon = Icons.Rounded.Devices,
                    title = "还没发现媒体库",
                    body = "在设置中选择或重新扫描局域网内的 DLNA 媒体库。",
                    action = "打开设备设置",
                    onAction = onOpenSettings,
                )
            }
            state.isBrowsing -> item(span = { GridItemSpan(maxLineSpan) }) { LoadingPanel("正在读取音乐目录…") }
            visibleEntries.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyPanel(
                    icon = Icons.Rounded.MusicNote,
                    title = if (query.isBlank()) "这个目录是空的" else "没有匹配的音乐",
                    body = if (query.isBlank()) "返回上一级看看其他唱片或播放列表。" else "换一个关键词试试。",
                )
            }
            else -> gridItemsIndexed(visibleEntries, key = { _, it -> "${it.parentId}:${it.id}" }) { index, entry ->
                if (useGrid) {
                    MediaGridCard(
                        entry = entry,
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

@Composable
private fun TrackCollectionScreen(
    state: LsMusicUiState,
    onNavigateTo: (Int) -> Unit,
    onPlay: (MediaEntry) -> Unit,
    onQueue: (MediaEntry) -> Unit,
    onPlayAll: (List<MediaEntry>) -> Unit,
    onQueueAll: (List<MediaEntry>) -> Unit,
) {
    val representativeTrack = state.entries.firstOrNull()
    val title = state.path.lastOrNull()?.title ?: representativeTrack?.album.orEmpty()
    val artists = state.entries.map { it.creator }.filter { it.isNotBlank() }.distinct().take(2).joinToString(" · ")
    LazyColumn(
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
                representativeTrack?.let { ArtworkTile(it, 200.dp) }
                Spacer(Modifier.height(16.dp))
                Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                if (artists.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(artists, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(8.dp))
                Text("${state.entries.size} 首歌曲", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = { onPlayAll(state.entries) }) {
                        Icon(Icons.Rounded.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text("播放全部")
                    }
                    OutlinedButton(onClick = { onQueueAll(state.entries) }) {
                        Icon(Icons.Rounded.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("加入队列")
                    }
                }
            }
        }
        itemsIndexed(state.entries, key = { index, item -> "collection:$index:${item.id}" }) { index, track ->
                TrackCollectionRow(
                    track = track,
                    number = index + 1,
                    isPlaying = state.currentTrack?.id == track.id && state.playbackState == RemotePlaybackState.PLAYING,
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
                emptyLabel = "未发现媒体库",
                onSelected = onSelectServer,
            )
            DevicePicker(
                modifier = Modifier.weight(1f),
                label = "播放到",
                icon = Icons.Rounded.Speaker,
                devices = state.renderers,
                selectedId = state.selectedRendererId,
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
    emptyLabel: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = devices.firstOrNull { it.id == selectedId }
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
                        selected?.name ?: emptyLabel,
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
    onOpen: () -> Unit,
    onQueue: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Box {
            ArtworkTile(
                entry = entry,
                size = null,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    entry.title,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
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
) {
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
            Text(
                "L's Music 仅浏览和播放音乐媒体。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp, end = 4.dp),
            )
        }
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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp, 28.dp, 24.dp, 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Text(
                "正在播放",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineLarge,
            )
            Spacer(Modifier.height(34.dp))
        }
        item {
                HeroArtwork(track)
                Spacer(Modifier.height(30.dp))
                Text(
                    track.title,
                    style = MaterialTheme.typography.displaySmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    listOf(track.creator, track.album).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "来自 DLNA 媒体库" },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(26.dp))
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
                Spacer(Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    FilledTonalIconButton(onClick = onPrevious, enabled = state.currentQueueIndex > 0, modifier = Modifier.size(58.dp)) {
                        Icon(Icons.Rounded.SkipPrevious, "上一首", Modifier.size(30.dp))
                    }
                    FilledIconButton(onClick = onTogglePlayback, modifier = Modifier.size(82.dp)) {
                        AnimatedContent(state.playbackState, label = "play pause") { playback ->
                            Icon(
                                if (playback == RemotePlaybackState.PLAYING) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                if (playback == RemotePlaybackState.PLAYING) "暂停" else "播放",
                                Modifier.size(42.dp),
                            )
                        }
                    }
                    FilledTonalIconButton(
                        onClick = onNext,
                        enabled = state.currentQueueIndex < state.queue.lastIndex,
                        modifier = Modifier.size(58.dp),
                    ) { Icon(Icons.Rounded.SkipNext, "下一首", Modifier.size(30.dp)) }
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
            IconButton(onClick = onNext, enabled = state.currentQueueIndex < state.queue.lastIndex) {
                Icon(Icons.Rounded.SkipNext, "下一首")
            }
        }
    }
}

@Composable
private fun ArtworkTile(
    entry: MediaEntry,
    size: androidx.compose.ui.unit.Dp?,
    modifier: Modifier = Modifier,
) {
    val palettes = listOf(
        listOf(Color(0xFF7454E8), Color(0xFFE263A9)),
        listOf(Color(0xFF1A9A8A), Color(0xFF8CC85A)),
        listOf(Color(0xFFE27A45), Color(0xFFF0B85A)),
        listOf(Color(0xFF376DCC), Color(0xFF6B54E8)),
    )
    val colors = palettes[(entry.title.hashCode() and Int.MAX_VALUE) % palettes.size]
    val tileModifier = if (size == null) modifier else modifier.size(size)
    val iconSize = (size ?: 72.dp) * .48f
    Box(
        modifier = tileModifier.clip(RoundedCornerShape(if (entry.isContainer) 18.dp else 16.dp))
            .background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (entry.isContainer) Icons.Rounded.Folder else Icons.Rounded.MusicNote,
            null,
            tint = Color.White,
            modifier = Modifier.size(iconSize),
        )
        if (!entry.artworkUri.isNullOrBlank()) {
            AsyncImage(
                model = entry.artworkUri,
                contentDescription = "${entry.title} 封面",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun HeroArtwork(track: MediaEntry) {
    val colors = if (track.title.hashCode() % 2 == 0) listOf(Color(0xFF6147D7), Color(0xFFFF78A9), Color(0xFFFFC857))
    else listOf(Color(0xFF167D8D), Color(0xFF6957DE), Color(0xFFEC6B9D))
    Box(
        modifier = Modifier.fillMaxWidth(.78f).widthIn(max = 420.dp).aspectRatio(1f)
            .clip(RoundedCornerShape(56.dp)).background(Brush.radialGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        if (!track.artworkUri.isNullOrBlank()) {
            AsyncImage(
                model = track.artworkUri,
                contentDescription = "${track.title} 封面",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
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
            onQueueAll = {},
            onTogglePlayback = {},
            onPrevious = {},
            onNext = {},
            onSeek = {},
            onRemoveQueue = {},
            onMoveQueue = { _, _ -> },
            onClearQueue = {},
            onGallerySize = {},
            onDefaultGridLayout = {},
            onThemeMode = {},
            onDynamicColor = {},
        )
    }
}
