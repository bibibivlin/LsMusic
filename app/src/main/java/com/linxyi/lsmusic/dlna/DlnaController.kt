package com.linxyi.lsmusic.dlna

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.linxyi.lsmusic.listenbrainz.MusicBrainzMetadataParser
import org.jupnp.android.AndroidUpnpService
import org.jupnp.controlpoint.ActionCallback
import org.jupnp.model.action.ActionInvocation
import org.jupnp.model.message.UpnpResponse
import org.jupnp.model.meta.RemoteDevice
import org.jupnp.model.types.UDAServiceType
import org.jupnp.registry.DefaultRegistryListener
import org.jupnp.registry.Registry
import org.jupnp.support.contentdirectory.callback.Browse
import org.jupnp.support.contentdirectory.DIDLParser
import org.jupnp.support.model.BrowseFlag
import org.jupnp.support.model.DIDLContent
import org.jupnp.support.model.DIDLObject
import org.jupnp.support.model.PersonWithRole
import java.net.URI
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class DlnaController(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val devices = ConcurrentHashMap<String, RemoteDevice>()
    private val commandExecutor = Executors.newSingleThreadExecutor()
    private val _snapshot = MutableStateFlow(DlnaSnapshot(isSearching = true))
    val snapshot: StateFlow<DlnaSnapshot> = _snapshot.asStateFlow()

    private var service: AndroidUpnpService? = null
    private var bound = false

    private val registryListener = object : DefaultRegistryListener() {
        override fun remoteDeviceAdded(registry: Registry, device: RemoteDevice) = addDevice(device)

        override fun remoteDeviceRemoved(registry: Registry, device: RemoteDevice) {
            devices.remove(device.identity.udn.identifierString)
            publishDevices()
        }

        override fun remoteDeviceDiscoveryFailed(
            registry: Registry,
            device: RemoteDevice,
            ex: Exception,
        ) {
            val isRelevantMusicDevice =
                device.findService(CONTENT_DIRECTORY) != null || device.findService(AV_TRANSPORT) != null
            if (isRelevantMusicDevice) {
                _snapshot.value = _snapshot.value.copy(error = "发现设备失败：${ex.localizedMessage}")
            } else {
                Log.i(TAG, "Ignoring non-media UPnP discovery failure: ${device.displayString}", ex)
            }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val upnp = binder as? AndroidUpnpService
            if (upnp == null) {
                reportServiceError("DLNA 服务返回了无法识别的连接")
                return
            }
            val registry = upnp.registry
            val controlPoint = upnp.controlPoint
            if (registry == null || controlPoint == null) {
                reportServiceError("DLNA 核心服务初始化失败，请确认 Wi‑Fi 已连接后重试")
                return
            }
            service = upnp
            registry.addListener(registryListener)
            registry.remoteDevices.forEach(::addDevice)
            refresh()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            _snapshot.value = _snapshot.value.copy(
                isSearching = false,
                error = "DLNA 服务连接已断开",
            )
        }
    }

    init {
        bound = appContext.bindService(
            Intent(appContext, LsUpnpService::class.java),
            connection,
            Context.BIND_AUTO_CREATE,
        )
        if (!bound) {
            _snapshot.value = DlnaSnapshot(error = "无法启动 DLNA 控制服务")
        }
    }

    fun refresh() {
        _snapshot.value = _snapshot.value.copy(isSearching = true, error = null)
        service?.controlPoint?.search()
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({ publishDevices(searchCompleted = true) }, SEARCH_WINDOW_MS)
    }

    fun browse(
        serverId: String,
        objectId: String,
        onResult: (List<MediaEntry>) -> Unit,
        onError: (String) -> Unit,
    ) {
        val upnp = service ?: return onError("DLNA 服务尚未就绪")
        val server = devices[serverId]
        val contentDirectory = server?.findService(CONTENT_DIRECTORY)
            ?: return onError("所选媒体库不再可用")
        fun resolveServerUri(value: Any?): String? {
            if (value == null) return null
            return runCatching {
                server.identity.descriptorURL.toURI().resolve(value.toString()).toString()
            }.getOrElse { value.toString() }
        }

        upnp.controlPoint.execute(object : Browse(
            contentDirectory,
            objectId,
            BrowseFlag.DIRECT_CHILDREN,
            "*",
            0,
            null,
        ) {
            override fun received(actionInvocation: ActionInvocation<*>, didl: DIDLContent) {
                val rawDidl = actionInvocation.getOutput("Result")?.value?.toString()
                val folders = didl.containers.map { container ->
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
                        artworkUri = resolveServerUri(
                            container.getFirstPropertyValue(DIDLObject.Property.UPNP.ALBUM_ART_URI::class.java),
                        ),
                        isContainer = true,
                        isAlbum = mediaClass.startsWith("object.container.album"),
                        childCount = container.childCount,
                    )
                }
                val tracks = didl.items.mapNotNull { item ->
                    val resource = item.resources.firstOrNull {
                        it.protocolInfo?.contentFormat?.startsWith("audio/", ignoreCase = true) == true
                    } ?: item.resources.firstOrNull()
                    val selectedResource = resource ?: return@mapNotNull null
                    val uri = resolveServerUri(selectedResource.value) ?: return@mapNotNull null
                    val artist = item.creator.orEmpty().ifBlank {
                        (item.getFirstPropertyValue(
                            DIDLObject.Property.UPNP.ARTIST::class.java,
                        ) as? PersonWithRole)?.name.orEmpty()
                    }
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
                        artworkUri = resolveServerUri(
                            item.getFirstPropertyValue(DIDLObject.Property.UPNP.ALBUM_ART_URI::class.java),
                        ),
                        resourceUri = uri,
                        duration = selectedResource.duration,
                        mimeType = selectedResource.protocolInfo?.contentFormat,
                        protocolInfo = selectedResource.protocolInfo?.toString(),
                        didlMetadata = originalDidlMetadata,
                        recordingMbid = musicBrainzIds.recordingMbid,
                        releaseMbid = musicBrainzIds.releaseMbid,
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
            ) = onError(defaultMsg)
        })
    }

    fun setTrack(
        rendererId: String,
        track: MediaEntry,
        playImmediately: Boolean,
        onComplete: () -> Unit = {},
        onError: (String) -> Unit = {},
    ) {
        val uri = track.resourceUri ?: return onError("曲目没有可播放的资源地址")
        val metadata = track.didlMetadata?.takeIf { it.isNotBlank() } ?: createDidlMetadata(track, uri)
        setTrackUri(rendererId, uri, metadata, playImmediately, onComplete, onError)
    }

    private fun setTrackUri(
        rendererId: String,
        uri: String,
        metadata: String,
        playImmediately: Boolean,
        onComplete: () -> Unit,
        onError: (String) -> Unit,
        retriesRemaining: Int = 1,
    ) {
        val renderer = devices[rendererId]
        val avTransport = renderer?.findService(AV_TRANSPORT)
        if (renderer != null && avTransport != null && isAkConnectRenderer(renderer)) {
            setAkConnectTrackUri(
                rendererId = rendererId,
                controlUri = avTransport.controlURI,
                descriptorUri = renderer.identity.descriptorURL.toURI(),
                uri = uri,
                metadata = metadata,
                playImmediately = playImmediately,
                onComplete = onComplete,
                onError = onError,
                retriesRemaining = retriesRemaining,
            )
            return
        }
        Log.i(TAG, "Sending SetAVTransportURI to $rendererId: uri=$uri")
        execute(rendererId, "AVTransport", "SetAVTransportURI", mapOf(
            "InstanceID" to 0,
            "CurrentURI" to uri,
            "CurrentURIMetaData" to metadata,
        ), onSuccess = {
            if (playImmediately) {
                // Some renderers (including portable players) need a short transition from
                // STOPPED to READY after SetAVTransportURI before they accept Play.
                mainHandler.postDelayed({ play(rendererId, onComplete, onError) }, PLAY_AFTER_SET_DELAY_MS)
            } else {
                onComplete()
            }
        }, onError = { error ->
            if (retriesRemaining > 0) {
                Log.i(TAG, "SetAVTransportURI retrying on $rendererId after: $error")
                mainHandler.postDelayed(
                    {
                        setTrackUri(
                            rendererId,
                            uri,
                            metadata,
                            playImmediately,
                            onComplete,
                            onError,
                            retriesRemaining - 1,
                        )
                    },
                    SET_URI_RETRY_DELAY_MS,
                )
            } else {
                onError(error)
            }
        })
    }

    /**
     * AK Connect accepts the server DIDL but rejects the SOAP body serialized by JUPnP with a
     * generic HTTP 500. Its own control point accepts the conventional SOAP 1.1 representation.
     */
    private fun setAkConnectTrackUri(
        rendererId: String,
        controlUri: URI,
        descriptorUri: URI,
        uri: String,
        metadata: String,
        playImmediately: Boolean,
        onComplete: () -> Unit,
        onError: (String) -> Unit,
        retriesRemaining: Int,
    ) {
        val endpoint = if (controlUri.isAbsolute) controlUri else descriptorUri.resolve(controlUri)
        commandExecutor.execute {
            val result = runCatching {
                val payload = akConnectSetUriEnvelope(uri, metadata).toByteArray(Charsets.UTF_8)
                val connection = (URL(endpoint.toString()).openConnection() as HttpURLConnection)
                try {
                    connection.requestMethod = "POST"
                    connection.connectTimeout = HTTP_TIMEOUT_MS
                    connection.readTimeout = HTTP_TIMEOUT_MS
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
                    connection.setRequestProperty(
                        "SOAPACTION",
                        "\"urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI\"",
                    )
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
                } finally {
                    connection.disconnect()
                }
            }
            result.onSuccess {
                Log.i(TAG, "SetAVTransportURI accepted by AK Connect renderer $rendererId")
                if (playImmediately) {
                    mainHandler.postDelayed({ play(rendererId, onComplete, onError) }, PLAY_AFTER_SET_DELAY_MS)
                } else {
                    onComplete()
                }
            }.onFailure { failure ->
                val error = "SetAVTransportURI 失败：${failure.localizedMessage ?: "无法连接播放设备"}"
                Log.w(TAG, "Raw SetAVTransportURI failed on $rendererId: $error", failure)
                if (retriesRemaining > 0) {
                    mainHandler.postDelayed(
                        {
                            setTrackUri(
                                rendererId,
                                uri,
                                metadata,
                                playImmediately,
                                onComplete,
                                onError,
                                retriesRemaining - 1,
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

    fun play(rendererId: String, onComplete: () -> Unit = {}, onError: (String) -> Unit = {}) {
        if (executeAkConnectTransportAction(
                rendererId,
                "Play",
                mapOf("InstanceID" to "0", "Speed" to "1"),
                onComplete,
                onError,
            )
        ) return
        execute(rendererId, "AVTransport", "Play", mapOf("InstanceID" to 0, "Speed" to "1"), onComplete, onError)
    }

    fun pause(rendererId: String, onComplete: () -> Unit = {}, onError: (String) -> Unit = {}) {
        if (executeAkConnectTransportAction(rendererId, "Pause", mapOf("InstanceID" to "0"), onComplete, onError)) return
        execute(rendererId, "AVTransport", "Pause", mapOf("InstanceID" to 0), onComplete, onError)
    }

    fun stop(rendererId: String, onComplete: () -> Unit = {}, onError: (String) -> Unit = {}) {
        if (executeAkConnectTransportAction(rendererId, "Stop", mapOf("InstanceID" to "0"), onComplete, onError)) return
        execute(rendererId, "AVTransport", "Stop", mapOf("InstanceID" to 0), onComplete, onError)
    }

    fun seek(rendererId: String, target: String, onError: (String) -> Unit = {}) {
        if (executeAkConnectTransportAction(
                rendererId,
                "Seek",
                mapOf("InstanceID" to "0", "Unit" to "REL_TIME", "Target" to target),
                onError = onError,
            )
        ) return
        execute(
            rendererId,
            "AVTransport",
            "Seek",
            mapOf("InstanceID" to 0, "Unit" to "REL_TIME", "Target" to target),
            onError = onError,
        )
    }

    /** Returns true when the command is handed to the AK Connect SOAP compatibility path. */
    private fun executeAkConnectTransportAction(
        rendererId: String,
        actionName: String,
        inputs: Map<String, String>,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {},
    ): Boolean {
        val renderer = devices[rendererId] ?: return false
        val avTransport = renderer.findService(AV_TRANSPORT) ?: return false
        if (!isAkConnectRenderer(renderer)) return false
        val controlUri = avTransport.controlURI
        val endpoint = if (controlUri.isAbsolute) controlUri else renderer.identity.descriptorURL.toURI().resolve(controlUri)
        commandExecutor.execute {
            runCatching {
                val payload = akConnectActionEnvelope(actionName, inputs).toByteArray(Charsets.UTF_8)
                val connection = (URL(endpoint.toString()).openConnection() as HttpURLConnection)
                try {
                    connection.requestMethod = "POST"
                    connection.connectTimeout = HTTP_TIMEOUT_MS
                    connection.readTimeout = HTTP_TIMEOUT_MS
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
                    connection.setRequestProperty(
                        "SOAPACTION",
                        "\"urn:schemas-upnp-org:service:AVTransport:1#$actionName\"",
                    )
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
                } finally {
                    connection.disconnect()
                }
            }.onSuccess {
                Log.i(TAG, "$actionName accepted by AK Connect renderer $rendererId")
                onSuccess()
            }.onFailure { failure ->
                val error = "$actionName 失败：${failure.localizedMessage ?: "无法连接播放设备"}"
                Log.w(TAG, "Raw $actionName failed on $rendererId: $error", failure)
                onError(error)
            }
        }
        return true
    }

    fun getPositionInfo(
        rendererId: String,
        onResult: (position: String?, duration: String?) -> Unit,
        onError: (String) -> Unit = {},
    ) {
        val renderer = devices[rendererId]
        val avTransport = renderer?.findService(AV_TRANSPORT)
        if (renderer != null && avTransport != null && isAkConnectRenderer(renderer)) {
            getAkConnectPositionInfo(
                rendererId = rendererId,
                controlUri = avTransport.controlURI,
                descriptorUri = renderer.identity.descriptorURL.toURI(),
                onResult = onResult,
                onError = onError,
            )
            return
        }
        val upnp = service ?: return onError("DLNA 服务尚未就绪")
        val remoteService = renderer?.findService(AV_TRANSPORT)
            ?: return onError("播放设备不支持 AVTransport")
        val action = remoteService.getAction("GetPositionInfo")
            ?: return onError("播放设备不支持进度查询")
        val invocation = ActionInvocation(action)
        invocation.setInput("InstanceID", 0)
        upnp.controlPoint.execute(object : ActionCallback(invocation) {
            override fun success(invocation: ActionInvocation<*>) {
                onResult(
                    invocation.getOutput("RelTime")?.value?.toString(),
                    invocation.getOutput("TrackDuration")?.value?.toString(),
                )
            }

            override fun failure(
                invocation: ActionInvocation<*>,
                operation: UpnpResponse?,
                defaultMsg: String,
            ) = onError(defaultMsg)
        })
    }

    fun getTransportInfo(
        rendererId: String,
        onResult: (state: String?) -> Unit,
        onError: (String) -> Unit = {},
    ) {
        val renderer = devices[rendererId]
        val avTransport = renderer?.findService(AV_TRANSPORT)
        if (renderer != null && avTransport != null && isAkConnectRenderer(renderer)) {
            getAkConnectTransportInfo(
                rendererId = rendererId,
                controlUri = avTransport.controlURI,
                descriptorUri = renderer.identity.descriptorURL.toURI(),
                onResult = onResult,
                onError = onError,
            )
            return
        }
        val upnp = service ?: return onError("DLNA 服务尚未就绪")
        val remoteService = renderer?.findService(AV_TRANSPORT)
            ?: return onError("播放设备不支持 AVTransport")
        val action = remoteService.getAction("GetTransportInfo")
            ?: return onError("播放设备不支持状态查询")
        val invocation = ActionInvocation(action)
        invocation.setInput("InstanceID", 0)
        upnp.controlPoint.execute(object : ActionCallback(invocation) {
            override fun success(invocation: ActionInvocation<*>) {
                onResult(invocation.getOutput("CurrentTransportState")?.value?.toString())
            }

            override fun failure(
                invocation: ActionInvocation<*>,
                operation: UpnpResponse?,
                defaultMsg: String,
            ) = onError(defaultMsg)
        })
    }

    private fun getAkConnectPositionInfo(
        rendererId: String,
        controlUri: URI,
        descriptorUri: URI,
        onResult: (position: String?, duration: String?) -> Unit,
        onError: (String) -> Unit,
    ) {
        val endpoint = if (controlUri.isAbsolute) controlUri else descriptorUri.resolve(controlUri)
        commandExecutor.execute {
            runCatching {
                val payload = akConnectActionEnvelope("GetPositionInfo", mapOf("InstanceID" to "0"))
                    .toByteArray(Charsets.UTF_8)
                val connection = (URL(endpoint.toString()).openConnection() as HttpURLConnection)
                try {
                    connection.requestMethod = "POST"
                    connection.connectTimeout = HTTP_TIMEOUT_MS
                    connection.readTimeout = HTTP_TIMEOUT_MS
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
                    connection.setRequestProperty(
                        "SOAPACTION",
                        "\"urn:schemas-upnp-org:service:AVTransport:1#GetPositionInfo\"",
                    )
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
                    val position = soapValue(response, "RelTime")
                    val duration = soapValue(response, "TrackDuration")
                    if (position == null && duration == null) {
                        error("播放设备未返回进度数据")
                    }
                    position to duration
                } finally {
                    connection.disconnect()
                }
            }.onSuccess { (position, duration) ->
                onResult(position, duration)
            }.onFailure { failure ->
                val error = "进度查询失败：${failure.localizedMessage ?: "无法连接播放设备"}"
                Log.w(TAG, "Raw GetPositionInfo failed on $rendererId: $error", failure)
                onError(error)
            }
        }
    }

    private fun getAkConnectTransportInfo(
        rendererId: String,
        controlUri: URI,
        descriptorUri: URI,
        onResult: (state: String?) -> Unit,
        onError: (String) -> Unit,
    ) {
        val endpoint = if (controlUri.isAbsolute) controlUri else descriptorUri.resolve(controlUri)
        commandExecutor.execute {
            runCatching {
                val payload = akConnectActionEnvelope("GetTransportInfo", mapOf("InstanceID" to "0"))
                    .toByteArray(Charsets.UTF_8)
                val connection = (URL(endpoint.toString()).openConnection() as HttpURLConnection)
                try {
                    connection.requestMethod = "POST"
                    connection.connectTimeout = HTTP_TIMEOUT_MS
                    connection.readTimeout = HTTP_TIMEOUT_MS
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
                    connection.setRequestProperty(
                        "SOAPACTION",
                        "\"urn:schemas-upnp-org:service:AVTransport:1#GetTransportInfo\"",
                    )
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
                    soapValue(response, "CurrentTransportState")
                } finally {
                    connection.disconnect()
                }
            }.onSuccess(onResult).onFailure { failure ->
                val error = "状态查询失败：${failure.localizedMessage ?: "无法连接播放设备"}"
                Log.w(TAG, "Raw GetTransportInfo failed on $rendererId: $error", failure)
                onError(error)
            }
        }
    }

    private fun execute(
        rendererId: String,
        serviceName: String,
        actionName: String,
        inputs: Map<String, Any>,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {},
    ) {
        val upnp = service ?: return onError("DLNA 服务尚未就绪")
        val remoteService = devices[rendererId]?.findService(UDAServiceType(serviceName))
            ?: return onError("播放设备不支持 $serviceName")
        val action = remoteService.getAction(actionName)
            ?: return onError("播放设备不支持 $actionName")
        val invocation = ActionInvocation(action)
        try {
            inputs.forEach { (name, value) -> invocation.setInput(name, value) }
        } catch (error: Exception) {
            return onError(error.localizedMessage ?: "无法创建播放命令")
        }
        upnp.controlPoint.execute(object : ActionCallback(invocation) {
            override fun success(invocation: ActionInvocation<*>) = onSuccess()

            override fun failure(
                invocation: ActionInvocation<*>,
                operation: UpnpResponse?,
                defaultMsg: String,
            ) {
                Log.w(TAG, "$actionName failed on $rendererId: $defaultMsg")
                onError("$actionName 失败：$defaultMsg")
            }
        })
    }

    private fun addDevice(device: RemoteDevice) {
        val isServer = device.findService(CONTENT_DIRECTORY) != null
        val isRenderer = device.findService(AV_TRANSPORT) != null
        if (!isServer && !isRenderer) return
        devices[device.identity.udn.identifierString] = device
        Log.i(
            TAG,
            "Discovered ${device.details.friendlyName}: mediaServer=$isServer, mediaRenderer=$isRenderer",
        )
        publishDevices()
    }

    private fun reportServiceError(message: String) {
        service = null
        _snapshot.value = _snapshot.value.copy(isSearching = false, error = message)
    }

    private fun publishDevices(searchCompleted: Boolean = false) {
        val servers = mutableListOf<DlnaDevice>()
        val renderers = mutableListOf<DlnaDevice>()
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
            if (device.findService(CONTENT_DIRECTORY) != null) servers += model(DlnaDeviceKind.MEDIA_SERVER)
            if (device.findService(AV_TRANSPORT) != null) renderers += model(DlnaDeviceKind.MEDIA_RENDERER)
        }
        _snapshot.value = _snapshot.value.copy(
            servers = servers.distinctBy { it.id }.sortedBy { it.name.lowercase() },
            renderers = renderers.distinctBy { it.id }.sortedBy { it.name.lowercase() },
            isSearching = if (searchCompleted) false else _snapshot.value.isSearching,
        )
    }

    override fun close() {
        mainHandler.removeCallbacksAndMessages(null)
        commandExecutor.shutdownNow()
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
                <dc:title>${xmlEscape(track.title.ifBlank { "未知曲目" })}</dc:title>$creator$album
                <upnp:class>object.item.audioItem.musicTrack</upnp:class>
                <res protocolInfo="${xmlEscape(protocolInfo)}"$duration>${xmlEscape(uri)}</res>
              </item>
            </DIDL-Lite>
        """.trimIndent()
    }

    /**
     * Astell&Kern renderers validate more than the standard minimum DIDL fields. Preserve the
     * server-provided XML item verbatim so vendor extensions and the exact res attributes survive.
     */
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

    private fun akConnectSetUriEnvelope(uri: String, metadata: String): String = """
        <?xml version="1.0" encoding="utf-8"?>
        <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
          <s:Body>
            <u:SetAVTransportURI xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
              <InstanceID>0</InstanceID>
              <CurrentURI>${xmlEscape(uri)}</CurrentURI>
              <CurrentURIMetaData>${xmlEscape(metadata)}</CurrentURIMetaData>
            </u:SetAVTransportURI>
          </s:Body>
        </s:Envelope>
    """.trimIndent()

    private fun akConnectActionEnvelope(actionName: String, inputs: Map<String, String>): String {
        val fields = inputs.entries.joinToString(separator = "") { (name, value) ->
            "<$name>${xmlEscape(value)}</$name>"
        }
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
              <s:Body>
                <u:$actionName xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">$fields</u:$actionName>
              </s:Body>
            </s:Envelope>
        """.trimIndent()
    }

    private fun soapValue(response: String, name: String): String? = Regex(
        "<(?:[A-Za-z][\\w.-]*:)?$name>(.*?)</(?:[A-Za-z][\\w.-]*:)?$name>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    ).find(response)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }

    private fun isAkConnectRenderer(device: RemoteDevice): Boolean {
        val manufacturer = device.details.manufacturerDetails?.manufacturer.orEmpty()
        val model = device.details.modelDetails?.modelName.orEmpty()
        return manufacturer.contains("iriver", ignoreCase = true) ||
            manufacturer.contains("astell", ignoreCase = true) ||
            model.contains("AK Connect", ignoreCase = true)
    }

    companion object {
        private val CONTENT_DIRECTORY = UDAServiceType("ContentDirectory")
        private val AV_TRANSPORT = UDAServiceType("AVTransport")
        private const val SEARCH_WINDOW_MS = 3_500L
        private const val SET_URI_RETRY_DELAY_MS = 800L
        private const val PLAY_AFTER_SET_DELAY_MS = 450L
        private const val HTTP_TIMEOUT_MS = 10_000
        private const val TAG = "LsMusic/DLNA"
    }
}
