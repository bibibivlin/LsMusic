package com.linxyi.lsmusic.dlna

enum class DlnaDeviceKind { MEDIA_SERVER, MEDIA_RENDERER }

data class DlnaDevice(
    val id: String,
    val name: String,
    val manufacturer: String,
    val model: String,
    val kind: DlnaDeviceKind,
)

data class MediaEntry(
    val id: String,
    val parentId: String,
    val title: String,
    val creator: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val year: Int? = null,
    val genre: String = "",
    val trackNumber: Int? = null,
    val artworkUri: String? = null,
    val resourceUri: String? = null,
    val duration: String? = null,
    val mimeType: String? = null,
    val protocolInfo: String? = null,
    val didlMetadata: String? = null,
    val recordingMbid: String? = null,
    val releaseMbid: String? = null,
    val artistMbids: List<String> = emptyList(),
    val isContainer: Boolean,
    val isAlbum: Boolean = false,
    val childCount: Int? = null,
)

enum class RemotePlaybackState { STOPPED, PLAYING, PAUSED }

data class DlnaSnapshot(
    val servers: List<DlnaDevice> = emptyList(),
    val renderers: List<DlnaDevice> = emptyList(),
    val isSearching: Boolean = false,
    val error: String? = null,
)

private val albumYearPattern = Regex("(?<!\\d)[12]\\d{3}(?!\\d)")

internal fun parseAlbumYear(date: String?): Int? = albumYearPattern.find(date.orEmpty())
    ?.value
    ?.toIntOrNull()
    ?.takeIf { it in 1000..2999 }
