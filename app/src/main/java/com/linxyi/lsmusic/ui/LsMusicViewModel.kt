package com.linxyi.lsmusic.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.linxyi.lsmusic.dlna.DlnaController
import com.linxyi.lsmusic.dlna.DlnaDevice
import com.linxyi.lsmusic.dlna.DlnaDeviceKind
import com.linxyi.lsmusic.dlna.MediaEntry
import com.linxyi.lsmusic.dlna.RemotePlaybackState
import com.linxyi.lsmusic.playback.LocalPlaybackService
import com.linxyi.lsmusic.playback.RemotePlaybackService
import com.linxyi.lsmusic.listenbrainz.ListenBrainzClient
import com.linxyi.lsmusic.listenbrainz.ListenBrainzPlaybackObservation
import com.linxyi.lsmusic.listenbrainz.ListenBrainzPlaybackReport
import com.linxyi.lsmusic.listenbrainz.ListenBrainzPlaybackTracker
import com.linxyi.lsmusic.listenbrainz.describeListenBrainzValidationFailure
import com.linxyi.lsmusic.listenbrainz.shouldSubmitListen
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class AppDestination { LIBRARY, QUEUE, NOW_PLAYING, SETTINGS }

enum class ListenBrainzTokenValidationStatus { IDLE, CHECKING, VALID, INVALID, ERROR }

data class ListenBrainzTokenValidationUiState(
    val status: ListenBrainzTokenValidationStatus = ListenBrainzTokenValidationStatus.IDLE,
    val message: String? = null,
    val checkedToken: String? = null,
)

data class BrowseLocation(val id: String, val title: String)

private data class RemoteMediaSessionSnapshot(
    val rendererId: String?,
    val mediaId: String?,
    val mediaUri: String?,
    val title: String?,
    val artist: String?,
    val album: String?,
    val genre: String?,
    val trackNumber: Int?,
    val artworkUri: String?,
    val isPlaying: Boolean,
    val isActive: Boolean,
    val positionMs: Long,
    val durationMs: Long,
)

private data class ListenBrainzStateSnapshot(
    val track: MediaEntry?,
    val playbackGeneration: Long,
    val playbackState: RemotePlaybackState,
    val positionMs: Long,
    val durationMs: Long,
    val preferences: AppPreferences,
)

private sealed interface ListenBrainzSubmission {
    val token: String

    data class NowPlaying(
        override val token: String,
        val track: MediaEntry,
        val durationMs: Long,
    ) : ListenBrainzSubmission

    data class Listen(
        override val token: String,
        val report: ListenBrainzPlaybackReport.Finished,
    ) : ListenBrainzSubmission
}

data class LsMusicUiState(
    val servers: List<DlnaDevice> = emptyList(),
    val renderers: List<DlnaDevice> = emptyList(),
    val selectedServerId: String? = null,
    val selectedRendererId: String? = null,
    val entries: List<MediaEntry> = emptyList(),
    val path: List<BrowseLocation> = listOf(BrowseLocation("0", "音乐库")),
    val queue: List<MediaEntry> = emptyList(),
    val currentQueueIndex: Int = -1,
    val playbackGeneration: Long = 0L,
    val playbackState: RemotePlaybackState = RemotePlaybackState.STOPPED,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val preferences: AppPreferences = AppPreferences(),
    val listenBrainzTokenValidation: ListenBrainzTokenValidationUiState = ListenBrainzTokenValidationUiState(),
    val destination: AppDestination = AppDestination.LIBRARY,
    val isSearching: Boolean = true,
    val isBrowsing: Boolean = false,
    val error: String? = null,
) {
    val currentTrack: MediaEntry?
        get() = queue.getOrNull(currentQueueIndex)
}

class LsMusicViewModel(application: Application) : AndroidViewModel(application) {
    private val dlna = DlnaController(application)
    private val preferenceStore = AppPreferencesStore(application)
    private val listenBrainzClient = ListenBrainzClient()
    private val listenBrainzPlaybackTracker = ListenBrainzPlaybackTracker()
    private val listenBrainzSubmissions = Channel<ListenBrainzSubmission>(Channel.BUFFERED)
    private val _uiState = MutableStateFlow(LsMusicUiState(preferences = preferenceStore.load()))
    val uiState: StateFlow<LsMusicUiState> = _uiState.asStateFlow()
    private val controllerFuture = MediaController.Builder(
        application,
        SessionToken(application, ComponentName(application, LocalPlaybackService::class.java)),
    ).buildAsync()
    private var localController: MediaController? = null
    private var pendingLocalPlayback: Pair<List<MediaEntry>, Int>? = null
    private var initialServerSelectionResolved = false
    private var initialRendererSelectionResolved = false
    private var userSelectedServer = false
    private var userSelectedRenderer = false
    private var remoteSessionServiceStarted = false
    private var listenBrainzTokenValidationJob: Job? = null

    private val remoteMediaCommandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != RemotePlaybackService.ACTION_REMOTE_CONTROL) return
            handleRemoteMediaCommand(
                intent.getStringExtra(RemotePlaybackService.EXTRA_COMMAND),
                intent.getLongExtra(RemotePlaybackService.EXTRA_POSITION_MS, 0L),
            )
        }
    }

    private val localPlayerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = updateLocalPlaybackState()

        override fun onPlaybackStateChanged(playbackState: Int) = updateLocalPlaybackState()

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val index = _uiState.value.queue.indexOfFirst { it.id == mediaItem?.mediaId }
            if (index >= 0) {
                _uiState.update {
                    it.copy(
                        currentQueueIndex = index,
                        playbackGeneration = if (index != it.currentQueueIndex) {
                            it.playbackGeneration + 1L
                        } else {
                            it.playbackGeneration
                        },
                        positionMs = 0L,
                    )
                }
            }
            updateLocalPlaybackState()
        }

        override fun onPlayerError(error: PlaybackException) {
            if (_uiState.value.selectedRendererId == LOCAL_RENDERER_ID) {
                _uiState.update { it.copy(playbackState = RemotePlaybackState.STOPPED) }
                showError("本机播放失败：${error.localizedMessage}")
            }
        }
    }

    init {
        ContextCompat.registerReceiver(
            application,
            remoteMediaCommandReceiver,
            IntentFilter(RemotePlaybackService.ACTION_REMOTE_CONTROL),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        controllerFuture.addListener(
            {
                runCatching { controllerFuture.get() }
                    .onSuccess { controller ->
                        localController = controller
                        controller.addListener(localPlayerListener)
                        pendingLocalPlayback?.let { (queue, index) -> playLocally(controller, queue, index) }
                        updateLocalPlaybackState()
                    }
                    .onFailure { showError("无法连接本机播放器：${it.localizedMessage}") }
            },
            ContextCompat.getMainExecutor(application),
        )

        viewModelScope.launch {
            dlna.snapshot.collect { snapshot ->
                val old = _uiState.value
                val renderers = listOf(LOCAL_RENDERER) + snapshot.renderers
                val currentServerId = old.selectedServerId?.takeIf { id -> snapshot.servers.any { it.id == id } }
                val currentRendererId = old.selectedRendererId?.takeIf { id -> renderers.any { it.id == id } }
                val shouldRestoreServer = !initialServerSelectionResolved && !userSelectedServer && !snapshot.isSearching
                val shouldRestoreRenderer = !initialRendererSelectionResolved && !userSelectedRenderer && !snapshot.isSearching
                val serverId = when {
                    shouldRestoreServer -> preferenceStore.lastServerId()
                        ?.takeIf { id -> snapshot.servers.any { it.id == id } }
                        ?: currentServerId
                        ?: snapshot.servers.firstOrNull()?.id
                    else -> currentServerId ?: snapshot.servers.firstOrNull()?.id
                }
                val rendererId = when {
                    shouldRestoreRenderer -> preferenceStore.lastRendererId()
                        ?.takeIf { id -> renderers.any { it.id == id } }
                        ?: currentRendererId
                        ?: LOCAL_RENDERER_ID
                    else -> currentRendererId ?: LOCAL_RENDERER_ID
                }
                val serverChanged = serverId != old.selectedServerId
                _uiState.update {
                    it.copy(
                        servers = snapshot.servers,
                        renderers = renderers,
                        selectedServerId = serverId,
                        selectedRendererId = rendererId,
                        entries = if (serverChanged) emptyList() else it.entries,
                        path = if (serverChanged) listOf(BrowseLocation("0", "音乐库")) else it.path,
                        isSearching = snapshot.isSearching,
                        error = snapshot.error ?: it.error,
                    )
                }
                if (!snapshot.isSearching) {
                    initialServerSelectionResolved = true
                    initialRendererSelectionResolved = true
                }
                if (serverChanged && serverId != null) browse(serverId, "0")
            }
        }

        viewModelScope.launch {
            while (isActive) {
                refreshPlaybackProgress()
                delay(if (_uiState.value.selectedRendererId == LOCAL_RENDERER_ID) 500L else 1_000L)
            }
        }

        viewModelScope.launch {
            uiState.map { state ->
                val track = state.currentTrack
                RemoteMediaSessionSnapshot(
                    rendererId = state.selectedRendererId,
                    mediaId = track?.id,
                    mediaUri = track?.resourceUri,
                    title = track?.title,
                    artist = track?.creator,
                    album = track?.album,
                    genre = track?.genre,
                    trackNumber = track?.trackNumber,
                    artworkUri = track?.artworkUri,
                    isPlaying = state.playbackState == RemotePlaybackState.PLAYING,
                    isActive = state.selectedRendererId != null &&
                        state.selectedRendererId != LOCAL_RENDERER_ID &&
                        track != null &&
                        state.playbackState != RemotePlaybackState.STOPPED,
                    positionMs = state.positionMs,
                    durationMs = state.durationMs,
                )
            }.distinctUntilChanged().collect(::publishRemoteMediaSession)
        }

        viewModelScope.launch {
            for (submission in listenBrainzSubmissions) {
                runCatching {
                    when (submission) {
                        is ListenBrainzSubmission.NowPlaying -> listenBrainzClient.submitNowPlaying(
                            submission.token,
                            submission.track,
                            submission.durationMs,
                        )
                        is ListenBrainzSubmission.Listen -> submission.report.let { report ->
                            listenBrainzClient.submitListen(
                                submission.token,
                                report.track,
                                report.startedAtEpochSeconds,
                                report.durationMs,
                                report.listenedMs,
                            )
                        }
                    }
                }.onFailure {
                    Log.w(TAG, "ListenBrainz submission failed", it)
                    showError("ListenBrainz 上报失败：${it.localizedMessage ?: "网络请求失败"}")
                }
            }
        }

        viewModelScope.launch {
            uiState.map { state ->
                ListenBrainzStateSnapshot(
                    track = state.currentTrack,
                    playbackGeneration = state.playbackGeneration,
                    playbackState = state.playbackState,
                    positionMs = state.positionMs,
                    durationMs = state.durationMs,
                    preferences = state.preferences,
                )
            }.distinctUntilChanged().collect(::trackListenBrainzPlayback)
        }
    }

    fun refreshDevices() = dlna.refresh()

    fun selectServer(id: String) {
        userSelectedServer = true
        preferenceStore.saveLastServerId(id)
        _uiState.update {
            it.copy(
                selectedServerId = id,
                entries = emptyList(),
                path = listOf(BrowseLocation("0", "音乐库")),
            )
        }
        browse(id, "0")
    }

    fun selectRenderer(id: String) {
        val state = _uiState.value
        if (state.selectedRendererId == id) return
        userSelectedRenderer = true
        preferenceStore.saveLastRendererId(id)
        if (state.playbackState != RemotePlaybackState.STOPPED) stopRenderer(state.selectedRendererId)
        _uiState.update {
            it.copy(
                selectedRendererId = id,
                playbackState = RemotePlaybackState.STOPPED,
                positionMs = 0L,
                durationMs = parseTimeMs(it.currentTrack?.duration),
                bufferedPositionMs = 0L,
            )
        }
    }

    fun open(entry: MediaEntry) {
        if (!entry.isContainer) return playNow(entry)
        val serverId = _uiState.value.selectedServerId ?: return
        _uiState.update { it.copy(path = it.path + BrowseLocation(entry.id, entry.title)) }
        browse(serverId, entry.id)
    }

    fun navigateTo(locationIndex: Int) {
        val state = _uiState.value
        val location = state.path.getOrNull(locationIndex) ?: return
        val serverId = state.selectedServerId ?: return
        _uiState.update { it.copy(path = it.path.take(locationIndex + 1)) }
        browse(serverId, location.id)
    }

    fun playNow(track: MediaEntry) {
        val rendererId = _uiState.value.selectedRendererId
            ?: return showError("请先选择播放设备")
        val oldQueue = _uiState.value.queue
        val index = oldQueue.indexOfFirst { it.id == track.id }.takeIf { it >= 0 } ?: oldQueue.size
        val queue = if (index == oldQueue.size) oldQueue + track else oldQueue
        _uiState.update {
            it.copy(
                queue = queue,
                currentQueueIndex = index,
                playbackGeneration = it.playbackGeneration + 1L,
                playbackState = RemotePlaybackState.PLAYING,
                positionMs = 0L,
                durationMs = parseTimeMs(track.duration),
                bufferedPositionMs = 0L,
            )
        }
        playOnRenderer(rendererId, track)
    }

    fun addToQueue(track: MediaEntry) {
        if (track.isContainer || track.resourceUri == null) return
        if (_uiState.value.queue.any { it.id == track.id }) return
        _uiState.update { state -> state.copy(queue = state.queue + track) }
        localController?.takeIf { it.mediaItemCount > 0 }?.addMediaItem(track.toMediaItem())
    }

    /** Replaces the controller queue and immediately starts the first playable track. */
    fun playAll(tracks: List<MediaEntry>) {
        val playable = tracks.filter { !it.isContainer && it.resourceUri != null }
        if (playable.isEmpty()) return showError("这里没有可播放的歌曲")
        val rendererId = _uiState.value.selectedRendererId ?: return showError("请先选择播放设备")
        val first = playable.first()
        _uiState.update {
            it.copy(
                queue = playable,
                currentQueueIndex = 0,
                playbackGeneration = it.playbackGeneration + 1L,
                playbackState = RemotePlaybackState.PLAYING,
                positionMs = 0L,
                durationMs = parseTimeMs(first.duration),
                bufferedPositionMs = 0L,
            )
        }
        playOnRenderer(rendererId, first)
    }

    /** Appends every playable track, preserving the existing queue and playback. */
    fun addAllToQueue(tracks: List<MediaEntry>) {
        val playable = tracks.filter { !it.isContainer && it.resourceUri != null }
        if (playable.isEmpty()) return showError("这里没有可加入的歌曲")
        _uiState.update { state -> state.copy(queue = state.queue + playable) }
        localController?.takeIf { it.mediaItemCount > 0 }
            ?.addMediaItems(playable.map { it.toMediaItem() })
    }

    fun togglePlayback() {
        val state = _uiState.value
        val rendererId = state.selectedRendererId ?: return showError("请先选择一台 DLNA 播放设备")
        val track = state.currentTrack ?: return showError("播放列表还是空的")
        if (rendererId == LOCAL_RENDERER_ID) {
            when (state.playbackState) {
                RemotePlaybackState.PLAYING -> localController?.pause()
                RemotePlaybackState.PAUSED -> localController?.play()
                RemotePlaybackState.STOPPED -> playNow(track)
            }
            return
        }
        when (state.playbackState) {
            RemotePlaybackState.PLAYING -> {
                _uiState.update { it.copy(playbackState = RemotePlaybackState.PAUSED) }
                dlna.pause(rendererId, onError = ::showError)
            }
            RemotePlaybackState.PAUSED -> {
                _uiState.update { it.copy(playbackState = RemotePlaybackState.PLAYING) }
                dlna.play(rendererId, onError = ::showError)
            }
            RemotePlaybackState.STOPPED -> playNow(track)
        }
    }

    fun next() = playAt(_uiState.value.currentQueueIndex + 1)

    fun previous() = playAt(_uiState.value.currentQueueIndex - 1)

    private fun playAt(index: Int) {
        val track = _uiState.value.queue.getOrNull(index) ?: return
        val rendererId = _uiState.value.selectedRendererId ?: return showError("请先选择播放设备")
        _uiState.update {
            it.copy(
                currentQueueIndex = index,
                playbackGeneration = it.playbackGeneration + 1L,
                playbackState = RemotePlaybackState.PLAYING,
                positionMs = 0L,
                durationMs = parseTimeMs(track.duration),
                bufferedPositionMs = 0L,
            )
        }
        playOnRenderer(rendererId, track)
    }

    fun removeFromQueue(index: Int) {
        if (_uiState.value.selectedRendererId == LOCAL_RENDERER_ID && index < (localController?.mediaItemCount ?: 0)) {
            localController?.removeMediaItem(index)
        }
        _uiState.update { state ->
            if (index !in state.queue.indices) return@update state
            val queue = state.queue.toMutableList().also { it.removeAt(index) }
            val current = when {
                queue.isEmpty() -> -1
                index < state.currentQueueIndex -> state.currentQueueIndex - 1
                index == state.currentQueueIndex -> state.currentQueueIndex.coerceAtMost(queue.lastIndex)
                else -> state.currentQueueIndex
            }
            state.copy(
                queue = queue,
                currentQueueIndex = current,
                playbackState = if (queue.isEmpty()) RemotePlaybackState.STOPPED else state.playbackState,
            )
        }
    }

    fun moveQueueItem(index: Int, direction: Int) {
        val destination = index + direction
        if (
            _uiState.value.selectedRendererId == LOCAL_RENDERER_ID &&
            index < (localController?.mediaItemCount ?: 0) &&
            destination in 0 until (localController?.mediaItemCount ?: 0)
        ) {
            localController?.moveMediaItem(index, destination)
        }
        _uiState.update { state ->
            if (index !in state.queue.indices || destination !in state.queue.indices) return@update state
            val queue = state.queue.toMutableList()
            val item = queue.removeAt(index)
            queue.add(destination, item)
            val current = when (state.currentQueueIndex) {
                index -> destination
                destination -> index
                else -> state.currentQueueIndex
            }
            state.copy(queue = queue, currentQueueIndex = current)
        }
    }

    fun clearQueue() {
        stopRenderer(_uiState.value.selectedRendererId)
        _uiState.update {
            it.copy(
                queue = emptyList(),
                currentQueueIndex = -1,
                playbackState = RemotePlaybackState.STOPPED,
                positionMs = 0L,
                durationMs = 0L,
                bufferedPositionMs = 0L,
            )
        }
    }

    fun seekTo(positionMs: Long) {
        val state = _uiState.value
        val target = positionMs.coerceIn(0L, state.durationMs.coerceAtLeast(0L))
        _uiState.update { it.copy(positionMs = target) }
        when (val rendererId = state.selectedRendererId) {
            null -> Unit
            LOCAL_RENDERER_ID -> localController?.seekTo(target)
            else -> dlna.seek(rendererId, formatDlnaTime(target), onError = ::showError)
        }
    }

    fun setDestination(destination: AppDestination) = _uiState.update { it.copy(destination = destination) }

    fun setGallerySize(size: GallerySize) = updatePreferences { it.copy(gallerySize = size) }

    fun setDefaultGridLayout(enabled: Boolean) = updatePreferences { it.copy(useGridByDefault = enabled) }

    fun setThemeMode(mode: ThemeMode) = updatePreferences { it.copy(themeMode = mode) }

    fun setDynamicColor(enabled: Boolean) = updatePreferences { it.copy(useDynamicColor = enabled) }

    fun setListenBrainzEnabled(enabled: Boolean) = updatePreferences { it.copy(listenBrainzEnabled = enabled) }

    fun validateAndSaveListenBrainzToken(token: String) {
        listenBrainzTokenValidationJob?.cancel()
        val normalized = token.trim()
        if (normalized.isEmpty()) {
            updatePreferences { it.copy(listenBrainzToken = "") }
            _uiState.update {
                it.copy(listenBrainzTokenValidation = ListenBrainzTokenValidationUiState())
            }
            return
        }

        val hadSavedToken = _uiState.value.preferences.listenBrainzToken.isNotBlank()
        _uiState.update {
            it.copy(
                listenBrainzTokenValidation = ListenBrainzTokenValidationUiState(
                    status = ListenBrainzTokenValidationStatus.CHECKING,
                    message = "正在连接 ListenBrainz 并校验令牌…",
                    checkedToken = normalized,
                ),
            )
        }
        listenBrainzTokenValidationJob = viewModelScope.launch {
            runCatching { listenBrainzClient.validateToken(normalized) }
                .onSuccess { result ->
                    if (result.valid) {
                        updatePreferences { it.copy(listenBrainzToken = normalized) }
                        _uiState.update {
                            it.copy(
                                listenBrainzTokenValidation = ListenBrainzTokenValidationUiState(
                                    status = ListenBrainzTokenValidationStatus.VALID,
                                    message = result.userName?.let { userName ->
                                        "校验成功，网络连接正常；账户：$userName"
                                    } ?: "校验成功，网络连接正常。",
                                    checkedToken = normalized,
                                ),
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                listenBrainzTokenValidation = ListenBrainzTokenValidationUiState(
                                    status = ListenBrainzTokenValidationStatus.INVALID,
                                    message = "已连接 ListenBrainz，但令牌无效" +
                                        if (hadSavedToken) "；仍保留原令牌。" else "；未保存。",
                                    checkedToken = normalized,
                                ),
                            )
                        }
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    _uiState.update {
                        it.copy(
                            listenBrainzTokenValidation = ListenBrainzTokenValidationUiState(
                                status = ListenBrainzTokenValidationStatus.ERROR,
                                message = describeListenBrainzValidationFailure(error) +
                                    if (hadSavedToken) "；仍保留原令牌。" else "；未保存。",
                                checkedToken = normalized,
                            ),
                        )
                    }
                }
        }
    }

    fun setListenBrainzMinimumSeconds(seconds: Int) = updatePreferences {
        it.copy(listenBrainzMinimumSeconds = seconds.coerceIn(30, 600))
    }

    fun setListenBrainzMinimumPercent(percent: Int) = updatePreferences {
        it.copy(listenBrainzMinimumPercent = percent.coerceIn(10, 100))
    }

    fun consumeError() = _uiState.update { it.copy(error = null) }

    private fun updatePreferences(transform: (AppPreferences) -> AppPreferences) {
        val updated = transform(_uiState.value.preferences)
        preferenceStore.save(updated)
        _uiState.update { it.copy(preferences = updated) }
    }

    private fun browse(serverId: String, objectId: String) {
        _uiState.update { it.copy(isBrowsing = true, entries = emptyList()) }
        dlna.browse(
            serverId = serverId,
            objectId = objectId,
            onResult = { entries -> _uiState.update { it.copy(entries = entries, isBrowsing = false) } },
            onError = { message -> _uiState.update { it.copy(isBrowsing = false, error = message) } },
        )
    }

    private fun showError(message: String) = _uiState.update { it.copy(error = message) }

    private fun trackListenBrainzPlayback(snapshot: ListenBrainzStateSnapshot) {
        val preferences = snapshot.preferences
        val configured = preferences.listenBrainzEnabled && preferences.listenBrainzToken.isNotBlank()
        listenBrainzPlaybackTracker.observe(
            observation = ListenBrainzPlaybackObservation(
                track = snapshot.track,
                playbackGeneration = snapshot.playbackGeneration,
                playbackState = snapshot.playbackState,
                positionMs = snapshot.positionMs,
                durationMs = snapshot.durationMs,
                reportingEnabled = configured,
            ),
            elapsedRealtimeMs = SystemClock.elapsedRealtime(),
            epochSeconds = System.currentTimeMillis() / 1_000L,
        ).forEach { report ->
            when (report) {
                is ListenBrainzPlaybackReport.NowPlaying -> listenBrainzSubmissions.trySend(
                    ListenBrainzSubmission.NowPlaying(
                        token = preferences.listenBrainzToken,
                        track = report.track,
                        durationMs = report.durationMs,
                    ),
                )
                is ListenBrainzPlaybackReport.Finished -> if (
                    shouldSubmitListen(
                        listenedMs = report.listenedMs,
                        durationMs = report.durationMs,
                        minimumSeconds = preferences.listenBrainzMinimumSeconds,
                        minimumPercent = preferences.listenBrainzMinimumPercent,
                    )
                ) {
                    listenBrainzSubmissions.trySend(
                        ListenBrainzSubmission.Listen(
                            token = preferences.listenBrainzToken,
                            report = report,
                        ),
                    )
                }
            }
        }
    }

    private fun playOnRenderer(rendererId: String, track: MediaEntry) {
        if (rendererId == LOCAL_RENDERER_ID) {
            val state = _uiState.value
            val controller = localController
            if (controller == null) {
                pendingLocalPlayback = state.queue to state.currentQueueIndex
            } else {
                playLocally(controller, state.queue, state.currentQueueIndex)
            }
        } else {
            dlna.setTrack(
                rendererId,
                track,
                playImmediately = true,
                onError = { message ->
                    _uiState.update { it.copy(playbackState = RemotePlaybackState.STOPPED) }
                    showError(message)
                },
            )
        }
    }

    private fun stopRenderer(rendererId: String?) {
        when (rendererId) {
            null -> Unit
            LOCAL_RENDERER_ID -> {
                pendingLocalPlayback = null
                localController?.stop()
                localController?.clearMediaItems()
            }
            else -> dlna.stop(rendererId, onError = ::showError)
        }
    }

    private fun handleRemoteMediaCommand(command: String?, positionMs: Long) {
        val state = _uiState.value
        val rendererId = state.selectedRendererId
            ?.takeUnless { it == LOCAL_RENDERER_ID }
            ?: return
        when (command) {
            RemotePlaybackService.COMMAND_PLAY -> when (state.playbackState) {
                RemotePlaybackState.STOPPED -> state.currentTrack?.let(::playNow)
                RemotePlaybackState.PLAYING -> Unit
                RemotePlaybackState.PAUSED -> {
                    _uiState.update { it.copy(playbackState = RemotePlaybackState.PLAYING) }
                    dlna.play(rendererId, onError = ::showError)
                }
            }
            RemotePlaybackService.COMMAND_PAUSE -> if (state.playbackState == RemotePlaybackState.PLAYING) {
                _uiState.update { it.copy(playbackState = RemotePlaybackState.PAUSED) }
                dlna.pause(rendererId, onError = ::showError)
            }
            RemotePlaybackService.COMMAND_STOP -> {
                _uiState.update { it.copy(playbackState = RemotePlaybackState.STOPPED, positionMs = 0L) }
                dlna.stop(rendererId, onError = ::showError)
            }
            RemotePlaybackService.COMMAND_NEXT -> next()
            RemotePlaybackService.COMMAND_PREVIOUS -> previous()
            RemotePlaybackService.COMMAND_SEEK -> seekTo(positionMs)
        }
    }

    private fun publishRemoteMediaSession(snapshot: RemoteMediaSessionSnapshot) {
        val app = getApplication<Application>()
        if (!snapshot.isActive) {
            if (remoteSessionServiceStarted) {
                app.startService(
                    Intent(app, RemotePlaybackService::class.java).setAction(RemotePlaybackService.ACTION_STOP_SERVICE),
                )
                remoteSessionServiceStarted = false
            }
            return
        }
        val update = Intent(app, RemotePlaybackService::class.java).apply {
            action = RemotePlaybackService.ACTION_UPDATE
            putExtra(RemotePlaybackService.EXTRA_MEDIA_ID, snapshot.mediaId)
            putExtra(RemotePlaybackService.EXTRA_MEDIA_URI, snapshot.mediaUri)
            putExtra(RemotePlaybackService.EXTRA_TITLE, snapshot.title)
            putExtra(RemotePlaybackService.EXTRA_ARTIST, snapshot.artist)
            putExtra(RemotePlaybackService.EXTRA_ALBUM, snapshot.album)
            putExtra(RemotePlaybackService.EXTRA_GENRE, snapshot.genre)
            snapshot.trackNumber?.let { putExtra(RemotePlaybackService.EXTRA_TRACK_NUMBER, it) }
            putExtra(RemotePlaybackService.EXTRA_ARTWORK_URI, snapshot.artworkUri)
            putExtra(RemotePlaybackService.EXTRA_POSITION_MS, snapshot.positionMs)
            putExtra(RemotePlaybackService.EXTRA_DURATION_MS, snapshot.durationMs)
            putExtra(RemotePlaybackService.EXTRA_PLAYING, snapshot.isPlaying)
        }
        if (remoteSessionServiceStarted) {
            app.startService(update)
        } else {
            ContextCompat.startForegroundService(app, update)
            remoteSessionServiceStarted = true
        }
    }

    private fun playLocally(controller: MediaController, queue: List<MediaEntry>, index: Int) {
        pendingLocalPlayback = null
        if (index !in queue.indices) return
        controller.setMediaItems(queue.map { it.toMediaItem() }, index, 0L)
        controller.prepare()
        controller.play()
    }

    private fun MediaEntry.toMediaItem(): MediaItem {
        val uri = requireNotNull(resourceUri) { "曲目没有可播放的资源地址" }
        return MediaItem.Builder()
            .setUri(uri)
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(creator)
                    .setAlbumTitle(album)
                    .setArtworkUri(artworkUri?.let(Uri::parse))
                    .build(),
            )
            .build()
    }

    private fun updateLocalPlaybackState() {
        val controller = localController ?: return
        if (_uiState.value.selectedRendererId != LOCAL_RENDERER_ID) return
        _uiState.update {
            it.copy(
                playbackState = when {
                    controller.isPlaying -> RemotePlaybackState.PLAYING
                    controller.playbackState == Player.STATE_IDLE ||
                        controller.playbackState == Player.STATE_ENDED -> RemotePlaybackState.STOPPED
                    else -> RemotePlaybackState.PAUSED
                },
            )
        }
        refreshLocalProgress(controller)
    }

    private fun refreshPlaybackProgress() {
        val state = _uiState.value
        val rendererId = state.selectedRendererId ?: return
        if (rendererId == LOCAL_RENDERER_ID) {
            localController?.let(::refreshLocalProgress)
        } else if (state.currentTrack != null) {
            dlna.getPositionInfo(rendererId, onResult = { position, duration ->
                _uiState.update {
                    it.copy(
                        positionMs = parseTimeMs(position),
                        durationMs = parseTimeMs(duration).takeIf { value -> value > 0L } ?: it.durationMs,
                    )
                }
            })
        }
    }

    private fun refreshLocalProgress(controller: MediaController) {
        if (_uiState.value.selectedRendererId != LOCAL_RENDERER_ID) return
        _uiState.update {
            it.copy(
                positionMs = controller.currentPosition.coerceAtLeast(0L),
                durationMs = controller.duration.takeUnless { value -> value == C.TIME_UNSET }
                    ?.coerceAtLeast(0L) ?: it.durationMs,
                bufferedPositionMs = controller.bufferedPosition.coerceAtLeast(0L),
            )
        }
    }

    override fun onCleared() {
        listenBrainzSubmissions.close()
        getApplication<Application>().unregisterReceiver(remoteMediaCommandReceiver)
        getApplication<Application>().startService(
            Intent(getApplication(), RemotePlaybackService::class.java).setAction(RemotePlaybackService.ACTION_STOP_SERVICE),
        )
        MediaController.releaseFuture(controllerFuture)
        dlna.close()
    }

    companion object {
        private const val TAG = "LsMusicViewModel"
        const val LOCAL_RENDERER_ID = "local-renderer"
        private val LOCAL_RENDERER = DlnaDevice(
            id = LOCAL_RENDERER_ID,
            name = "本机",
            manufacturer = "Android",
            model = "此设备扬声器",
            kind = DlnaDeviceKind.MEDIA_RENDERER,
        )

        fun parseTimeMs(value: String?): Long {
            if (value.isNullOrBlank() || value == "NOT_IMPLEMENTED") return 0L
            val parts = value.split(':')
            if (parts.isEmpty()) return 0L
            return runCatching {
                val seconds = parts.last().toDouble()
                val minutes = parts.getOrNull(parts.lastIndex - 1)?.toLong() ?: 0L
                val hours = parts.getOrNull(parts.lastIndex - 2)?.toLong() ?: 0L
                ((hours * 3_600L + minutes * 60L) * 1_000L + seconds * 1_000.0).toLong()
            }.getOrDefault(0L)
        }

        private fun formatDlnaTime(value: Long): String {
            val totalSeconds = value.coerceAtLeast(0L) / 1_000L
            val hours = totalSeconds / 3_600L
            val minutes = totalSeconds % 3_600L / 60L
            val seconds = totalSeconds % 60L
            return "%02d:%02d:%02d".format(hours, minutes, seconds)
        }
    }
}
