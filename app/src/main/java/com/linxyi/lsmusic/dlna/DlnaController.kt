package com.linxyi.lsmusic.dlna

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.linxyi.lsmusic.listenbrainz.MusicBrainzMetadataParser
import com.linxyi.lsmusic.R
import com.linxyi.lsmusic.ui.UiText
import org.jupnp.UpnpService
import org.jupnp.controlpoint.ActionCallback
import org.jupnp.model.action.ActionInvocation
import org.jupnp.model.message.UpnpResponse
import org.jupnp.model.meta.RemoteDevice
import org.jupnp.model.meta.RemoteService
import org.jupnp.model.types.UDAServiceType
import org.jupnp.registry.DefaultRegistryListener
import org.jupnp.registry.Registry
import org.jupnp.support.contentdirectory.callback.Browse
import org.jupnp.support.contentdirectory.DIDLParser
import org.jupnp.support.model.BrowseFlag
import org.jupnp.support.model.DIDLContent
import org.jupnp.support.model.DIDLObject
import org.jupnp.transport.RouterException
import java.io.IOException
import java.net.URI
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DlnaController(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val devices = ConcurrentHashMap<String, RemoteDevice>()
    private val rawSoapRendererIds: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val commandExecutor = Executors.newSingleThreadExecutor()
    private val shutdownExecutor = Executors.newSingleThreadExecutor()
    private val _snapshot = MutableStateFlow(DlnaSnapshot(isSearching = true))
    val snapshot: StateFlow<DlnaSnapshot> = _snapshot.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @Volatile private var service: UpnpService? = null
    private var bound = false
    @Volatile private var stopping = false
    @Volatile private var closed = false
    private var discoveryJob: Job? = null
    private var boundLinks: Set<DlnaLinkAddress>? = null
    private var rebindRequested = false
    private var recoveryRequested = false
    private var recoverySignalPending = false
    private var lastFailureSearchMs = -FAILURE_SEARCH_INTERVAL_MS
    private val failedDevices = mutableSetOf<String>()

    private val registryListener = object : DefaultRegistryListener() {
        override fun remoteDeviceAdded(registry: Registry, device: RemoteDevice) {
            scope.launch {
                if (service?.registry !== registry || stopping ||
                    registry.getRemoteDevice(device.identity.udn, true) !== device
                ) return@launch
                if (addDevice(device)) {
                    publishDevices()
                    deviceResponded(device)
                }
            }
        }

        override fun remoteDeviceUpdated(registry: Registry, device: RemoteDevice) {
            // A discovery reply from the same UDN and address can refresh its lease
            // without adding a new device to the registry.
            if (service?.registry === registry && !stopping) deviceResponded(device)
        }

        override fun remoteDeviceRemoved(registry: Registry, device: RemoteDevice) {
            scope.launch {
                if (service?.registry !== registry || stopping) return@launch
                val id = device.identity.udn.identifierString
                if (devices[id] !== device) return@launch
                devices.remove(id)
                startDiscovery(recovery = true)
                publishDevices()
            }
        }

        override fun remoteDeviceDiscoveryFailed(
            registry: Registry,
            device: RemoteDevice,
            ex: Exception,
        ) {
            val isRelevantMusicDevice =
                device.findService(CONTENT_DIRECTORY) != null || device.findService(AV_TRANSPORT) != null
            if (isRelevantMusicDevice) {
                scope.launch {
                    if (service?.registry !== registry || stopping) return@launch
                    _snapshot.update {
                        it.copy(error = UiText.Resource(R.string.error_dlna_discovery_failed, listOf(ex.localizedMessage.orEmpty())))
                    }
                }
            } else {
                Log.i(TAG, "Ignoring non-media UPnP discovery failure: ${device.displayString}", ex)
            }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            if (stopping || closed) return
            val upnp = (binder as? LsUpnpService.LocalBinder)?.upnp
            if (upnp == null) {
                reportServiceError(UiText.Resource(R.string.error_dlna_service_unrecognized))
                return
            }
            val registry = upnp.registry
            val controlPoint = upnp.controlPoint
            if (registry == null || controlPoint == null) {
                reportServiceError(UiText.Resource(R.string.error_dlna_service_init))
                return
            }
            service = upnp
            registry.addListener(registryListener)
            registry.remoteDevices.forEach(::addDevice)
            publishDevices()
            startDiscovery()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            reportServiceError(UiText.Resource(R.string.error_dlna_service_disconnected))
        }
    }

    private val networkMonitor = DlnaNetworkMonitor(appContext, mainHandler) { rebind ->
        recoverySignalPending = true
        startDiscovery(recovery = true, rebind = rebind)
    }

    init {
        bound = appContext.bindService(
            Intent(appContext, LsUpnpService::class.java),
            connection,
            Context.BIND_AUTO_CREATE,
        )
        if (!bound) {
            _snapshot.value = DlnaSnapshot(error = UiText.Resource(R.string.error_dlna_control_start))
        }
    }

    fun refresh() {
        scope.launch { startDiscovery() }
    }

    private fun startDiscovery(recovery: Boolean = false, rebind: Boolean = false) {
        if (stopping || closed) return
        rebindRequested = rebindRequested || rebind
        recoveryRequested = recoveryRequested || recovery
        val upnp = service ?: return
        if (discoveryJob?.isActive == true) return
        _snapshot.update { it.copy(isSearching = true, error = null) }
        discoveryJob = scope.launch {
            val intervals = if (recoveryRequested) RECOVERY_SEARCH_INTERVALS_MS else SEARCH_INTERVALS_MS
            var retryRebind = false
            for (interval in intervals) {
                delay(interval)
                val forceRebind = rebindRequested || retryRebind
                val reportRecovery = recoverySignalPending
                rebindRequested = false
                recoveryRequested = false
                recoverySignalPending = false
                val searched = withContext(Dispatchers.IO) { searchOnAvailableLinks(upnp, forceRebind) }
                retryRebind = forceRebind && !searched
                if (reportRecovery) {
                    if (searched) publishRecovery() else recoverySignalPending = true
                }
            }
            delay(SEARCH_RESPONSE_WINDOW_MS)
            discoveryJob = null
            if (rebindRequested || recoveryRequested) {
                startDiscovery()
            } else {
                _snapshot.update { it.copy(isSearching = false) }
            }
        }
    }

    private fun searchOnAvailableLinks(upnp: UpnpService, forceRebind: Boolean): Boolean {
        return try {
            val currentLinks = dlnaLinkAddresses()
            val router = upnp.router
            if (forceRebind || !router.isEnabled || (boundLinks != null && currentLinks != null && boundLinks != currentLinks)) {
                router.disable()
                router.enable()
            }
            boundLinks = currentLinks ?: boundLinks
            if (!router.isEnabled) return false
            upnp.controlPoint.search()
            true
        } catch (error: RouterException) {
            Log.w(TAG, "Unable to rebind SSDP to the local network", error)
            false
        }
    }

    private fun connectionFailed(deviceId: String, device: RemoteDevice? = null) {
        scope.launch {
            if (stopping || closed || (device != null && devices[deviceId] !== device)) return@launch
            val firstFailure = failedDevices.add(deviceId)
            val now = SystemClock.elapsedRealtime()
            if (now - lastFailureSearchMs < FAILURE_SEARCH_INTERVAL_MS) return@launch
            lastFailureSearchMs = now
            startDiscovery(recovery = true, rebind = firstFailure && device != null)
        }
    }

    private fun deviceResponded(device: RemoteDevice) {
        scope.launch {
            val id = device.identity.udn.identifierString
            if (devices[id] === device && failedDevices.remove(id)) publishRecovery()
        }
    }

    private fun publishRecovery() {
        _snapshot.update { it.copy(recoveryGeneration = it.recoveryGeneration + 1) }
    }

    private fun unavailable(deviceId: String, error: UiText): DlnaRequestException {
        connectionFailed(deviceId)
        return DlnaRequestException(error, connectionLost = true)
    }

    internal suspend fun browse(
        serverId: String,
        objectId: String,
    ): List<MediaEntry> {
        val upnp = service ?: throw unavailable(serverId, UiText.Resource(R.string.error_dlna_not_ready))
        val server = devices[serverId]
            ?: throw unavailable(serverId, UiText.Resource(R.string.error_library_unavailable))
        val contentDirectory = server.findService(CONTENT_DIRECTORY)
            ?: throw DlnaRequestException(UiText.Resource(R.string.error_library_unavailable), connectionLost = false)
        if (contentDirectory.getAction("Browse") == null) {
            throw DlnaRequestException(
                UiText.Resource(R.string.error_unsupported_action, listOf("Browse")),
                connectionLost = false,
            )
        }
        val entries = try {
            suspendCancellableCoroutine<List<MediaEntry>> { continuation ->
                val request = upnp.controlPoint.execute(
                    browseChildren(
                        server, contentDirectory, objectId,
                        onResult = continuation::resume,
                        onError = { operation, detail ->
                            if (continuation.isActive) {
                                continuation.resumeWithException(actionFailure("Browse", operation, detail))
                            }
                        },
                    ),
                )
                continuation.invokeOnCancellation { request.cancel(true) }
            }
        } catch (failure: DlnaRequestException) {
            if (failure.connectionLost) connectionFailed(serverId, server)
            throw failure
        }
        if (devices[serverId] !== server) {
            throw unavailable(serverId, UiText.Resource(R.string.error_library_unavailable))
        }
        deviceResponded(server)
        return entries
    }

    private fun browseChildren(
        server: RemoteDevice,
        contentDirectory: RemoteService,
        objectId: String,
        onResult: (List<MediaEntry>) -> Unit,
        onError: (UpnpResponse?, String) -> Unit,
    ): Browse {
        fun resolveServerUri(value: Any?): String? {
            if (value == null) return null
            return runCatching {
                server.identity.descriptorURL.toURI().resolve(value.toString()).toString()
            }.getOrElse { value.toString() }
        }
        fun artworkCandidates(value: DIDLObject): List<ArtworkCandidate> = rankArtworkCandidates(
            value.getProperties(DIDLObject.Property.UPNP.ALBUM_ART_URI::class.java).mapNotNull { property ->
                val uri = resolveServerUri(property.value) ?: return@mapNotNull null
                val profileId = property.getAttribute("profileID")?.value?.value
                ArtworkCandidate(uri = uri, profileId = profileId)
            },
        )

        return object : Browse(
            contentDirectory,
            objectId,
            BrowseFlag.DIRECT_CHILDREN,
            "*",
            0,
            null,
        ) {
            override fun run() {
                try {
                    super.run()
                } catch (error: RuntimeException) {
                    // Several missing/malformed Browse output arguments can throw before the
                    // library calls its failure callback.
                    onError(null, error.localizedMessage.orEmpty())
                }
            }

            override fun received(actionInvocation: ActionInvocation<*>, didl: DIDLContent) {
                val rawDidl = actionInvocation.getOutput("Result")?.value?.toString()
                val folders = didl.containers.map { container ->
                    val artworkCandidates = artworkCandidates(container)
                    val artists = container.getPropertyValues(DIDLObject.Property.UPNP.ARTIST::class.java)
                    val albumArtist = artists.firstOrNull {
                        it.role.equals("AlbumArtist", ignoreCase = true)
                    }?.name.orEmpty().ifBlank {
                        artists.firstOrNull()?.name.orEmpty().ifBlank { container.creator.orEmpty() }
                    }
                    val mediaClass = container.clazz?.value.orEmpty()
                    MediaEntry(
                        id = container.id,
                        parentId = container.parentID,
                        title = container.title.orEmpty(),
                        creator = container.creator.orEmpty(),
                        albumArtist = albumArtist,
                        year = parseAlbumYear(
                            container.getFirstPropertyValue(DIDLObject.Property.DC.DATE::class.java),
                        ),
                        artworkUri = artworkCandidates.firstOrNull()?.uri,
                        artworkCandidates = artworkCandidates,
                        isContainer = true,
                        isAlbum = mediaClass.startsWith("object.container.album"),
                        childCount = container.childCount,
                    )
                }
                val tracks = didl.items.mapNotNull { item ->
                    val artworkCandidates = artworkCandidates(item)
                    val resource = item.resources.firstOrNull {
                        it.protocolInfo?.contentFormat?.startsWith("audio/", ignoreCase = true) == true
                    } ?: item.resources.firstOrNull()
                    val selectedResource = resource ?: return@mapNotNull null
                    val uri = resolveServerUri(selectedResource.value) ?: return@mapNotNull null
                    val artists = item.getPropertyValues(DIDLObject.Property.UPNP.ARTIST::class.java)
                    val artist = artists.firstOrNull { it.role.equals("Artist", ignoreCase = true) }
                        ?.name.orEmpty().ifBlank {
                            artists.firstOrNull { it.role.isNullOrBlank() }?.name.orEmpty()
                        }.ifBlank {
                            artists.firstOrNull {
                                !it.role.equals("Composer", ignoreCase = true) &&
                                    !it.role.equals("AlbumArtist", ignoreCase = true)
                            }?.name.orEmpty()
                        }.ifBlank {
                            artists.firstOrNull { it.role.equals("AlbumArtist", ignoreCase = true) }
                                ?.name.orEmpty()
                        }.ifBlank { item.creator.orEmpty() }
                    val originalDidlMetadata = extractOriginalItemMetadata(rawDidl, item.id)
                        ?: runCatching { DIDLParser().generate(DIDLContent().apply { addItem(item) }) }
                            .onFailure { Log.w(TAG, "Unable to preserve source DIDL metadata for ${item.id}", it) }
                            .getOrNull()
                    val musicBrainzIds = MusicBrainzMetadataParser.parse(originalDidlMetadata)
                    MediaEntry(
                        id = item.id,
                        parentId = item.parentID,
                        title = item.title.orEmpty(),
                        creator = artist,
                        album = item.getFirstPropertyValue(DIDLObject.Property.UPNP.ALBUM::class.java).orEmpty(),
                        genre = item.getFirstPropertyValue(DIDLObject.Property.UPNP.GENRE::class.java).orEmpty(),
                        trackNumber = item.getFirstPropertyValue(
                            DIDLObject.Property.UPNP.ORIGINAL_TRACK_NUMBER::class.java,
                        )?.toString()?.toIntOrNull(),
                        artworkUri = artworkCandidates.firstOrNull()?.uri,
                        artworkCandidates = artworkCandidates,
                        resourceUri = uri,
                        resourceSize = selectedResource.size,
                        duration = selectedResource.duration,
                        mimeType = selectedResource.protocolInfo?.contentFormat,
                        protocolInfo = selectedResource.protocolInfo?.toString(),
                        didlMetadata = originalDidlMetadata,
                        recordingMbid = musicBrainzIds.recordingMbid,
                        releaseMbid = musicBrainzIds.releaseMbid,
                        releaseGroupMbid = musicBrainzIds.releaseGroupMbid,
                        trackMbid = musicBrainzIds.trackMbid,
                        artistMbids = musicBrainzIds.artistMbids,
                        isContainer = false,
                    )
                }
                // A null SortCriteria asks the ContentDirectory to use its own default order.
                // Preserve that order so the UI can offer it alongside client-side album sorting.
                onResult(folders + tracks)
            }

            override fun updateStatus(status: Status) = Unit

            override fun failure(
                invocation: ActionInvocation<*>,
                operation: UpnpResponse?,
                defaultMsg: String,
            ) = onError(operation, defaultMsg)
        }
    }

    private fun actionFailure(
        action: String,
        response: UpnpResponse?,
        detail: String,
    ): DlnaRequestException = DlnaRequestException(
        UiText.Resource(R.string.error_transport_action, listOf(action, detail)),
        connectionLost = response == null && isMissingUpnpResponse(detail),
    )

    fun setTrack(
        rendererId: String,
        track: MediaEntry,
        playImmediately: Boolean,
        onComplete: () -> Unit = {},
        onError: (UiText) -> Unit = {},
        isCurrent: () -> Boolean = { true },
    ) {
        if (stopping || closed || !isCurrent()) return
        val uri = track.resourceUri ?: return onError(UiText.Resource(R.string.error_track_resource_missing))
        val metadata = track.didlMetadata?.takeIf { it.isNotBlank() } ?: createDidlMetadata(track, uri)
        withAvailableRenderer(rendererId, onError, isCurrent) {
            setTrackUri(rendererId, uri, metadata, playImmediately, onComplete, onError, isCurrent = isCurrent)
        }
    }

    private fun withAvailableRenderer(
        rendererId: String,
        onError: (UiText) -> Unit,
        isCurrent: () -> Boolean,
        action: () -> Unit,
    ) {
        if (service != null && devices.containsKey(rendererId)) {
            action()
            return
        }
        scope.launch {
            if (!isCurrent() || stopping) return@launch
            startDiscovery(recovery = true)
            val available = withTimeoutOrNull(DEVICE_RETURN_TIMEOUT_MS) {
                snapshot.first { snapshot -> snapshot.renderers.any { it.id == rendererId } }
            } != null
            if (!isCurrent() || stopping) return@launch
            if (available) action() else onError(UiText.Resource(R.string.error_renderer_unavailable))
        }
    }

    private fun setTrackUri(
        rendererId: String,
        uri: String,
        metadata: String,
        playImmediately: Boolean,
        onComplete: () -> Unit,
        onError: (UiText) -> Unit,
        retriesRemaining: Int = 1,
        transitionRecoveryRemaining: Int = 1,
        isCurrent: () -> Boolean,
    ) {
        if (stopping || closed || !isCurrent()) return
        val renderer = devices[rendererId]
        val avTransport = renderer?.findService(AV_TRANSPORT)
        if (renderer != null && avTransport != null && shouldUseRawSoap(rendererId)) {
            setRawSoapTrackUri(
                renderer = renderer,
                avTransport = avTransport,
                uri = uri,
                metadata = metadata,
                playImmediately = playImmediately,
                onComplete = onComplete,
                onError = onError,
                retriesRemaining = retriesRemaining,
                transitionRecoveryRemaining = transitionRecoveryRemaining,
                isCurrent = isCurrent,
            )
            return
        }
        Log.i(TAG, "Sending SetAVTransportURI to $rendererId: uri=$uri")
        execute(rendererId, "AVTransport", "SetAVTransportURI", mapOf(
            "InstanceID" to 0,
            "CurrentURI" to uri,
            "CurrentURIMetaData" to metadata,
        ), isCurrent = isCurrent, onSuccess = {
            if (playImmediately) {
                // Some renderers (including portable players) need a short transition from
                // STOPPED to READY after SetAVTransportURI before they accept Play.
                mainHandler.postDelayed({ if (isCurrent()) play(rendererId, onComplete, onError, isCurrent) }, PLAY_AFTER_SET_DELAY_MS)
            } else {
                onComplete()
            }
        }, onError = setUriFailure@{ error ->
            if (!isCurrent()) return@setUriFailure
            if (renderer != null && avTransport != null && AvTransportSoap.isRequestSerializationFailure(error)) {
                Log.i(TAG, "jUPnP could not serialize SetAVTransportURI for $rendererId; using raw SOAP")
                setRawSoapTrackUri(
                    renderer = renderer,
                    avTransport = avTransport,
                    uri = uri,
                    metadata = metadata,
                    playImmediately = playImmediately,
                    onComplete = onComplete,
                    onError = onError,
                    retriesRemaining = retriesRemaining,
                    transitionRecoveryRemaining = transitionRecoveryRemaining,
                    isCurrent = isCurrent,
                )
                return@setUriFailure
            }
            if (transitionRecoveryRemaining > 0 && AvTransportSoap.isTransitionUnavailable(error)) {
                recoverSetTrackTransition(
                    rendererId = rendererId,
                    uri = uri,
                    metadata = metadata,
                    playImmediately = playImmediately,
                    onComplete = onComplete,
                    onError = onError,
                    retriesRemaining = retriesRemaining,
                    transitionRecoveryRemaining = transitionRecoveryRemaining,
                    isCurrent = isCurrent,
                )
                return@setUriFailure
            }
            if (retriesRemaining > 0) {
                Log.i(TAG, "SetAVTransportURI retrying on $rendererId after: $error")
                mainHandler.postDelayed(
                    {
                        setTrackUri(
                            rendererId = rendererId,
                            uri = uri,
                            metadata = metadata,
                            playImmediately = playImmediately,
                            onComplete = onComplete,
                            onError = onError,
                            retriesRemaining = retriesRemaining - 1,
                            transitionRecoveryRemaining = transitionRecoveryRemaining,
                            isCurrent = isCurrent,
                        )
                    },
                    SET_URI_RETRY_DELAY_MS,
                )
            } else {
                onError(UiText.Resource(R.string.error_set_transport_uri, listOf(error)))
            }
        })
    }

    /**
     * Sends a conventional SOAP 1.1 request without jUPnP's DOM serializer after the standard
     * control path reports a local request-serialization failure.
     */
    private fun setRawSoapTrackUri(
        renderer: RemoteDevice,
        avTransport: RemoteService,
        uri: String,
        metadata: String,
        playImmediately: Boolean,
        onComplete: () -> Unit,
        onError: (UiText) -> Unit,
        retriesRemaining: Int,
        transitionRecoveryRemaining: Int,
        isCurrent: () -> Boolean,
    ) {
        if (stopping || closed || !isCurrent()) return
        val rendererId = renderer.identity.udn.identifierString
        val endpoint = renderer.normalizeURI(avTransport.controlURI).toURI()
        commandExecutor.execute {
            if (!isCurrent()) return@execute
            val result = runCatching {
                sendRawSoapAction(
                    endpoint = endpoint,
                    serviceType = avTransport.serviceType.toString(),
                    actionName = "SetAVTransportURI",
                    inputs = mapOf(
                        "InstanceID" to "0",
                        "CurrentURI" to uri,
                        "CurrentURIMetaData" to metadata,
                    ),
                )
            }
            result.onSuccess {
                if (stopping || closed || !isCurrent()) return@onSuccess
                deviceResponded(renderer)
                rememberRawSoapRenderer(rendererId, "SetAVTransportURI")
                if (playImmediately) {
                    mainHandler.postDelayed({ if (isCurrent()) play(rendererId, onComplete, onError, isCurrent) }, PLAY_AFTER_SET_DELAY_MS)
                } else {
                    onComplete()
                }
            }.onFailure { failure ->
                if (stopping || closed || !isCurrent()) return@onFailure
                if (failure is IOException) connectionFailed(rendererId, renderer)
                val technicalError = failure.localizedMessage ?: "player connection failed"
                val error = UiText.Resource(R.string.error_set_transport_uri, listOf(technicalError))
                Log.w(TAG, "Raw SetAVTransportURI failed on $rendererId: $technicalError", failure)
                if (transitionRecoveryRemaining > 0 && AvTransportSoap.isTransitionUnavailable(technicalError)) {
                    recoverSetTrackTransition(
                        rendererId = rendererId,
                        uri = uri,
                        metadata = metadata,
                        playImmediately = playImmediately,
                        onComplete = onComplete,
                        onError = onError,
                        retriesRemaining = retriesRemaining,
                        transitionRecoveryRemaining = transitionRecoveryRemaining,
                        isCurrent = isCurrent,
                    )
                } else if (retriesRemaining > 0) {
                    mainHandler.postDelayed(
                        {
                            setTrackUri(
                                rendererId = rendererId,
                                uri = uri,
                                metadata = metadata,
                                playImmediately = playImmediately,
                                onComplete = onComplete,
                                onError = onError,
                                retriesRemaining = retriesRemaining - 1,
                                transitionRecoveryRemaining = transitionRecoveryRemaining,
                                isCurrent = isCurrent,
                            )
                        },
                        SET_URI_RETRY_DELAY_MS,
                    )
                } else {
                    onError(error)
                }
            }
        }
    }

    private fun recoverSetTrackTransition(
        rendererId: String,
        uri: String,
        metadata: String,
        playImmediately: Boolean,
        onComplete: () -> Unit,
        onError: (UiText) -> Unit,
        retriesRemaining: Int,
        transitionRecoveryRemaining: Int,
        isCurrent: () -> Boolean,
    ) {
        if (stopping || closed || !isCurrent()) return
        Log.i(TAG, "SetAVTransportURI is unavailable in the current state on $rendererId; stopping before retry")
        stop(
            rendererId = rendererId,
            onComplete = {
                mainHandler.postDelayed(
                    {
                        setTrackUri(
                            rendererId = rendererId,
                            uri = uri,
                            metadata = metadata,
                            playImmediately = playImmediately,
                            onComplete = onComplete,
                            onError = onError,
                            retriesRemaining = retriesRemaining,
                            transitionRecoveryRemaining = transitionRecoveryRemaining - 1,
                            isCurrent = isCurrent,
                        )
                    },
                    SET_URI_AFTER_STOP_DELAY_MS,
                )
            },
            isCurrent = isCurrent,
            onError = { stopError ->
                onError(UiText.Resource(R.string.error_stop_before_track_change, listOf(stopError)))
            },
        )
    }

    fun play(rendererId: String, onComplete: () -> Unit = {}, onError: (UiText) -> Unit = {}, isCurrent: () -> Boolean = { true }) {
        if (stopping || closed || !isCurrent()) return
        executeTransportAction(
            rendererId = rendererId,
            actionName = "Play",
            inputs = mapOf("InstanceID" to 0, "Speed" to "1"),
            isCurrent = isCurrent,
            onSuccess = onComplete,
            onError = onError,
        )
    }

    fun pause(rendererId: String, onComplete: () -> Unit = {}, onError: (UiText) -> Unit = {}, isCurrent: () -> Boolean = { true }) {
        executeTransportAction(rendererId, "Pause", mapOf("InstanceID" to 0), onComplete, onError, isCurrent)
    }

    fun stop(rendererId: String, onComplete: () -> Unit = {}, onError: (UiText) -> Unit = {}, isCurrent: () -> Boolean = { true }) {
        executeTransportAction(rendererId, "Stop", mapOf("InstanceID" to 0), onComplete, onError, isCurrent)
    }

    fun seek(
        rendererId: String,
        target: String,
        onError: (UiText) -> Unit = {},
        isCurrent: () -> Boolean = { true },
    ) {
        executeTransportAction(
            rendererId = rendererId,
            actionName = "Seek",
            inputs = mapOf("InstanceID" to 0, "Unit" to "REL_TIME", "Target" to target),
            onError = onError,
            isCurrent = isCurrent,
        )
    }

    private fun executeTransportAction(
        rendererId: String,
        actionName: String,
        inputs: Map<String, Any>,
        onSuccess: () -> Unit = {},
        onError: (UiText) -> Unit = {},
        isCurrent: () -> Boolean = { true },
    ) {
        if (closed || (stopping && actionName != "Stop") || !isCurrent()) return
        val renderer = devices[rendererId]
        if (renderer == null || service == null) {
            if (actionName == "Stop") {
                connectionFailed(rendererId)
                onError(UiText.Resource(R.string.error_renderer_unavailable))
            } else {
                withAvailableRenderer(rendererId, onError, isCurrent) {
                    executeTransportAction(rendererId, actionName, inputs, onSuccess, onError, isCurrent)
                }
            }
            return
        }
        val avTransport = renderer.findService(AV_TRANSPORT)
            ?: return onError(UiText.Resource(R.string.error_renderer_no_avtransport))
        if (shouldUseRawSoap(rendererId)) {
            executeRawSoapTransportAction(rendererId, actionName, inputs, onSuccess, onError, isCurrent)
            return
        }
        execute(
            rendererId = rendererId,
            serviceName = "AVTransport",
            actionName = actionName,
            inputs = inputs,
            isCurrent = isCurrent,
            onSuccess = onSuccess,
            onError = transportFailure@{ error ->
                if (AvTransportSoap.isRequestSerializationFailure(error)) {
                    Log.i(TAG, "jUPnP could not serialize $actionName for $rendererId; using raw SOAP")
                    executeRawSoapTransportAction(rendererId, actionName, inputs, onSuccess, onError, isCurrent)
                    return@transportFailure
                }
                onError(UiText.Resource(R.string.error_transport_action, listOf(actionName, error)))
            },
        )
    }

    private fun executeRawSoapTransportAction(
        rendererId: String,
        actionName: String,
        inputs: Map<String, Any>,
        onSuccess: () -> Unit = {},
        onError: (UiText) -> Unit = {},
        isCurrent: () -> Boolean = { true },
    ) {
        if (closed || (stopping && actionName != "Stop") || !isCurrent()) return
        val renderer = devices[rendererId] ?: return onError(UiText.Resource(R.string.error_renderer_unavailable))
        val avTransport = renderer.findService(AV_TRANSPORT)
            ?: return onError(UiText.Resource(R.string.error_renderer_no_avtransport))
        val endpoint = renderer.normalizeURI(avTransport.controlURI).toURI()
        val executor = if (stopping && actionName == "Stop") shutdownExecutor else commandExecutor
        executor.execute {
            if (!isCurrent()) return@execute
            runCatching {
                sendRawSoapAction(
                    endpoint = endpoint,
                    serviceType = avTransport.serviceType.toString(),
                    actionName = actionName,
                    inputs = inputs.mapValues { it.value.toString() },
                )
            }.onSuccess {
                if (!isCurrent()) return@onSuccess
                deviceResponded(renderer)
                rememberRawSoapRenderer(rendererId, actionName)
                onSuccess()
            }.onFailure { failure ->
                if (!isCurrent()) return@onFailure
                if (failure is IOException) connectionFailed(rendererId, renderer)
                val detail = failure.localizedMessage ?: "player connection failed"
                val error = UiText.Resource(R.string.error_transport_action, listOf(actionName, detail))
                Log.w(TAG, "Raw $actionName failed on $rendererId: $detail", failure)
                onError(error)
            }
        }
    }

    internal suspend fun readPlaybackStatus(rendererId: String): DlnaPlaybackStatus? {
        val upnp = service
        val renderer = devices[rendererId]
        if (upnp == null || renderer == null) {
            connectionFailed(rendererId)
            return null
        }
        val avTransport = renderer.findService(AV_TRANSPORT) ?: return null
        val transport = try {
            readStatusAction(upnp, renderer, avTransport, "GetTransportInfo", "CurrentTransportState")
        } catch (failure: DlnaRequestException) {
            if (failure.connectionLost) {
                connectionFailed(rendererId, renderer)
                return null
            }
            emptyMap()
        }
        val state = transport["CurrentTransportState"].orEmpty()
        // Some renderers stop answering GetPositionInfo when their media is cleared.
        val positionTimeoutMs = when (state.uppercase()) {
            "STOPPED", "NO_MEDIA_PRESENT" -> STOPPED_POSITION_TIMEOUT_MS
            else -> STATUS_ACTION_TIMEOUT_MS
        }
        val position = try {
            readStatusAction(
                upnp, renderer, avTransport, "GetPositionInfo", "RelTime", "TrackDuration", "TrackURI",
                timeoutMs = positionTimeoutMs,
            )
        } catch (failure: DlnaRequestException) {
            if (state.isBlank() && failure.connectionLost) connectionFailed(rendererId, renderer)
            emptyMap()
        }
        if (state.isBlank() && position["RelTime"] == null) return null
        if (devices[rendererId] !== renderer) return null
        deviceResponded(renderer)
        return DlnaPlaybackStatus(state, position["RelTime"], position["TrackDuration"], position["TrackURI"])
    }

    private suspend fun readStatusAction(
        upnp: UpnpService,
        renderer: RemoteDevice,
        avTransport: RemoteService,
        actionName: String,
        vararg outputNames: String,
        timeoutMs: Int = STATUS_ACTION_TIMEOUT_MS,
    ): Map<String, String?> {
        val rendererId = renderer.identity.udn.identifierString
        if (!shouldUseRawSoap(rendererId)) {
            val action = avTransport.getAction(actionName) ?: return emptyMap()
            val invocation = ActionInvocation(action).apply { setInput("InstanceID", 0) }
            val outputs = withTimeoutOrNull(timeoutMs.toLong()) {
                val response = suspendCancellableCoroutine<ActionInvocation<*>?> { continuation ->
                    val request = upnp.controlPoint.execute(object : ActionCallback(invocation) {
                        override fun success(invocation: ActionInvocation<*>) {
                            continuation.resume(invocation)
                        }

                        override fun failure(invocation: ActionInvocation<*>, operation: UpnpResponse?, defaultMsg: String) {
                            if (!continuation.isActive) return
                            if (AvTransportSoap.isRequestSerializationFailure(defaultMsg)) {
                                continuation.resume(null)
                            } else {
                                continuation.resumeWithException(actionFailure(actionName, operation, defaultMsg))
                            }
                        }
                    })
                    continuation.invokeOnCancellation { request.cancel(true) }
                }
                // A null response here asks for the existing raw SOAP compatibility path.
                response?.let { reply -> outputNames.associateWith { reply.getOutput(it)?.value?.toString() } }.orEmpty()
            }
            if (outputs == null) {
                throw DlnaRequestException(UiText.Resource(R.string.error_renderer_unavailable), connectionLost = true)
            }
            if (outputs.isNotEmpty()) return outputs
        }

        val endpoint = renderer.normalizeURI(avTransport.controlURI).toURI()
        try {
            val response = runInterruptible(Dispatchers.IO) {
                sendRawSoapAction(
                    endpoint = endpoint,
                    serviceType = avTransport.serviceType.toString(),
                    actionName = actionName,
                    inputs = mapOf("InstanceID" to "0"),
                    readTimeoutMs = timeoutMs,
                )
            }
            rememberRawSoapRenderer(rendererId, actionName)
            return outputNames.associateWith { AvTransportSoap.responseValue(response, it) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw DlnaRequestException(
                UiText.Resource(R.string.error_transport_action, listOf(actionName, error.localizedMessage.orEmpty())),
                connectionLost = error is IOException,
            )
        }
    }

    private fun sendRawSoapAction(
        endpoint: URI,
        serviceType: String,
        actionName: String,
        inputs: Map<String, String>,
        readTimeoutMs: Int = HTTP_TIMEOUT_MS,
    ): String {
        check(!closed && (!stopping || actionName == "Stop")) { "DLNA controller is stopping" }
        val payload = AvTransportSoap.envelope(serviceType, actionName, inputs).toByteArray(Charsets.UTF_8)
        val connection = URL(endpoint.toString()).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = minOf(HTTP_CONNECT_TIMEOUT_MS, readTimeoutMs)
            connection.readTimeout = readTimeoutMs
            connection.setRequestProperty("Connection", "close")
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
            connection.setRequestProperty("SOAPACTION", "\"$serviceType#$actionName\"")
            connection.setFixedLengthStreamingMode(payload.size)
            connection.outputStream.use { it.write(payload) }
            val responseCode = connection.responseCode
            val response = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            if (responseCode !in 200..299) {
                error("HTTP $responseCode${response.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()}")
            }
            response
        } finally {
            connection.disconnect()
        }
    }

    private fun shouldUseRawSoap(rendererId: String): Boolean = rendererId in rawSoapRendererIds

    private fun rememberRawSoapRenderer(rendererId: String, actionName: String) {
        if (rawSoapRendererIds.add(rendererId)) {
            Log.i(TAG, "$actionName accepted through raw SOAP; enabled compatibility for $rendererId")
        } else {
            Log.i(TAG, "$actionName accepted through raw SOAP for $rendererId")
        }
    }

    private fun execute(
        rendererId: String,
        serviceName: String,
        actionName: String,
        inputs: Map<String, Any>,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {},
        isCurrent: () -> Boolean = { true },
    ) {
        if (closed || (stopping && actionName != "Stop") || !isCurrent()) return
        val upnp = service ?: return onError(appContext.getString(R.string.error_dlna_not_ready))
        val renderer = devices[rendererId]
        if (renderer == null) {
            connectionFailed(rendererId)
            return onError(appContext.getString(R.string.error_renderer_unavailable))
        }
        val remoteService = renderer.findService(UDAServiceType(serviceName))
            ?: return onError(appContext.getString(R.string.error_unsupported_service, serviceName))
        val action = remoteService.getAction(actionName)
            ?: return onError(appContext.getString(R.string.error_unsupported_action, actionName))
        val invocation = ActionInvocation(action)
        try {
            inputs.forEach { (name, value) -> invocation.setInput(name, value) }
        } catch (error: Exception) {
            return onError(error.localizedMessage ?: appContext.getString(R.string.error_create_playback_command, error.localizedMessage.orEmpty()))
        }
        upnp.controlPoint.execute(object : ActionCallback(invocation) {
            override fun success(invocation: ActionInvocation<*>) {
                if (!closed && (!stopping || actionName == "Stop") && isCurrent()) {
                    deviceResponded(renderer)
                    onSuccess()
                }
            }

            override fun failure(
                invocation: ActionInvocation<*>,
                operation: UpnpResponse?,
                defaultMsg: String,
            ) {
                if (closed || (stopping && actionName != "Stop") || !isCurrent()) return
                if (operation == null && isMissingUpnpResponse(defaultMsg)) connectionFailed(rendererId, renderer)
                Log.w(TAG, "$actionName failed on $rendererId: $defaultMsg")
                onError(defaultMsg)
            }
        })
    }

    private fun addDevice(device: RemoteDevice): Boolean {
        val isServer = device.findService(CONTENT_DIRECTORY) != null
        val isRenderer = device.findService(AV_TRANSPORT) != null
        if (!isServer && !isRenderer) return false
        devices[device.identity.udn.identifierString] = device
        Log.i(
            TAG,
            "Discovered ${device.details.friendlyName}: mediaServer=$isServer, mediaRenderer=$isRenderer",
        )
        return true
    }

    private fun reportServiceError(message: UiText) {
        service?.registry?.removeListener(registryListener)
        service = null
        discoveryJob?.cancel()
        discoveryJob = null
        boundLinks = null
        devices.clear()
        failedDevices.clear()
        _snapshot.update {
            it.copy(servers = emptyList(), renderers = emptyList(), serverLocations = emptyMap(), isSearching = false, error = message)
        }
    }

    private fun publishDevices() {
        val servers = mutableListOf<DlnaDevice>()
        val renderers = mutableListOf<DlnaDevice>()
        val serverLocations = mutableMapOf<String, String>()
        devices.values.forEach { device ->
            val id = device.identity.udn.identifierString
            val details = device.details
            fun model(kind: DlnaDeviceKind) = DlnaDevice(
                id = id,
                name = details.friendlyName ?: device.displayString,
                manufacturer = details.manufacturerDetails?.manufacturer.orEmpty(),
                model = details.modelDetails?.modelName.orEmpty(),
                kind = kind,
            )
            if (device.findService(CONTENT_DIRECTORY) != null) {
                servers += model(DlnaDeviceKind.MEDIA_SERVER)
                serverLocations[id] = device.identity.descriptorURL.toExternalForm()
            }
            if (device.findService(AV_TRANSPORT) != null) renderers += model(DlnaDeviceKind.MEDIA_RENDERER)
        }
        _snapshot.update {
            it.copy(
                servers = servers.sortedBy { it.name.lowercase() },
                renderers = renderers.sortedBy { it.name.lowercase() },
                serverLocations = serverLocations,
            )
        }
    }

    /** Fence delayed Play/retry callbacks before issuing the last Stop. */
    fun beginShutdown() {
        stopping = true
        discoveryJob?.cancel()
        mainHandler.removeCallbacksAndMessages(null)
    }

    override fun close() {
        if (closed) return
        closed = true
        beginShutdown()
        networkMonitor.close()
        scope.cancel()
        rawSoapRendererIds.clear()
        commandExecutor.shutdownNow()
        shutdownExecutor.shutdownNow()
        service?.registry?.removeListener(registryListener)
        if (bound) appContext.unbindService(connection)
        bound = false
        service = null
    }

    private fun createDidlMetadata(track: MediaEntry, uri: String): String {
        val protocolInfo = track.protocolInfo?.takeIf { it.isNotBlank() }
            ?: "http-get:*:${track.mimeType?.takeIf { it.isNotBlank() } ?: "audio/*"}:*"
        val duration = track.duration?.takeIf { it.isNotBlank() }?.let { " duration=\"${xmlEscape(it)}\"" }.orEmpty()
        val creator = track.creator.takeIf { it.isNotBlank() }?.let { "<dc:creator>${xmlEscape(it)}</dc:creator>" }.orEmpty()
        val album = track.album.takeIf { it.isNotBlank() }?.let { "<upnp:album>${xmlEscape(it)}</upnp:album>" }.orEmpty()
        return """
            <DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/"
                xmlns:dc="http://purl.org/dc/elements/1.1/"
                xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
              <item id="${xmlEscape(track.id)}" parentID="${xmlEscape(track.parentId)}" restricted="1">
                <dc:title>${xmlEscape(track.title.ifBlank { "Unknown track" })}</dc:title>$creator$album
                <upnp:class>object.item.audioItem.musicTrack</upnp:class>
                <res protocolInfo="${xmlEscape(protocolInfo)}"$duration>${xmlEscape(uri)}</res>
              </item>
            </DIDL-Lite>
        """.trimIndent()
    }

    /** Preserves vendor extensions and exact resource attributes from the server-provided item. */
    private fun extractOriginalItemMetadata(rawDidl: String?, itemId: String): String? {
        if (rawDidl.isNullOrBlank()) return null
        val root = Regex("<DIDL-Lite\\b([^>]*)>", RegexOption.IGNORE_CASE).find(rawDidl) ?: return null
        val item = Regex(
            "<item\\b(?=[^>]*\\bid\\s*=\\s*[\\\"']${Regex.escape(itemId)}[\\\"'])[^>]*>.*?</item>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).find(rawDidl) ?: return null
        return "<DIDL-Lite${root.groupValues[1]}>${item.value}</DIDL-Lite>"
    }

    private fun xmlEscape(value: String): String = buildString(value.length) {
        value.forEach { char ->
            append(
                when (char) {
                    '&' -> "&amp;"
                    '<' -> "&lt;"
                    '>' -> "&gt;"
                    '"' -> "&quot;"
                    '\'' -> "&apos;"
                    else -> char
                },
            )
        }
    }

    companion object {
        private val CONTENT_DIRECTORY = UDAServiceType("ContentDirectory")
        private val AV_TRANSPORT = UDAServiceType("AVTransport")
        private val SEARCH_INTERVALS_MS = longArrayOf(0L, 800L, 1_600L)
        private val RECOVERY_SEARCH_INTERVALS_MS = SEARCH_INTERVALS_MS + 2_500L
        private const val SEARCH_RESPONSE_WINDOW_MS = 1_100L
        private const val FAILURE_SEARCH_INTERVAL_MS = 8_000L
        private const val DEVICE_RETURN_TIMEOUT_MS = 7_000L
        private const val SET_URI_RETRY_DELAY_MS = 800L
        private const val SET_URI_AFTER_STOP_DELAY_MS = 250L
        private const val PLAY_AFTER_SET_DELAY_MS = 450L
        private const val HTTP_CONNECT_TIMEOUT_MS = 2_500
        private const val STATUS_ACTION_TIMEOUT_MS = 3_000
        private const val STOPPED_POSITION_TIMEOUT_MS = 800
        private const val HTTP_TIMEOUT_MS = 10_000
        private const val TAG = "LsMusic/DLNA"
    }
}
