package com.linxyi.lsmusic.dlna

enum class DlnaDeviceKind { MEDIA_SERVER, MEDIA_RENDERER }

data class DlnaDevice(
    val id: String,
    val name: String,
    val manufacturer: String,
    val model: String,
    val kind: DlnaDeviceKind,
)

data class ArtworkCandidate(
    val uri: String,
    val profileId: String? = null,
)

internal fun rankArtworkCandidates(candidates: Iterable<ArtworkCandidate>): List<ArtworkCandidate> = candidates
    .filter { it.uri.isNotBlank() }
    .groupBy(ArtworkCandidate::uri)
    .values
    .map { sameUri -> sameUri.minBy { artworkProfileRank(it.profileId) } }
    .sortedBy { candidate -> artworkProfileRank(candidate.profileId) }

private fun artworkProfileRank(profileId: String?): Int {
    val profile = profileId?.trim()?.uppercase()
    return when {
        profile == "JPEG_LRG" || profile == "PNG_LRG" -> 0
        profile.isNullOrBlank() -> 1
        profile == "JPEG_MED" -> 2
        profile == "JPEG_SM" -> 3
        profile == "JPEG_TN" || profile == "PNG_TN" || profile.endsWith("_ICO") -> 5
        else -> 4
    }
}

internal fun selectThumbnailArtworkUri(
    candidates: List<ArtworkCandidate>,
    fallbackUri: String? = null,
): String? = candidates.minByOrNull { candidate ->
    when (candidate.profileId?.trim()?.uppercase()) {
        "JPEG_TN", "PNG_TN" -> 0
        "JPEG_SM" -> 1
        "JPEG_MED" -> 2
        null, "" -> 3
        "JPEG_LRG", "PNG_LRG" -> 4
        else -> 5
    }
}?.uri ?: fallbackUri

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
    val artworkCandidates: List<ArtworkCandidate> = artworkUri?.let {
        listOf(ArtworkCandidate(it))
    }.orEmpty(),
    val resourceUri: String? = null,
    val resourceSize: Long? = null,
    val duration: String? = null,
    val mimeType: String? = null,
    val protocolInfo: String? = null,
    val didlMetadata: String? = null,
    val recordingMbid: String? = null,
    val releaseMbid: String? = null,
    val releaseGroupMbid: String? = null,
    val trackMbid: String? = null,
    val artistMbids: List<String> = emptyList(),
    val isContainer: Boolean,
    val isAlbum: Boolean = false,
    val childCount: Int? = null,
) {
    val thumbnailArtworkUri: String?
        get() = selectThumbnailArtworkUri(artworkCandidates, artworkUri)
}

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
