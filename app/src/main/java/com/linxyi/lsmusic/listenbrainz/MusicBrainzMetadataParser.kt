package com.linxyi.lsmusic.listenbrainz

data class MusicBrainzIds(
    val recordingMbid: String? = null,
    val releaseMbid: String? = null,
    val releaseGroupMbid: String? = null,
    val trackMbid: String? = null,
    val artistMbids: List<String> = emptyList(),
)

/** Extracts common Picard MusicBrainz tag spellings exposed as DIDL vendor metadata. */
object MusicBrainzMetadataParser {
    private val uuid = Regex(
        "(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b",
    )

    fun parse(didlMetadata: String?): MusicBrainzIds {
        if (didlMetadata.isNullOrBlank()) return MusicBrainzIds()
        return MusicBrainzIds(
            recordingMbid = valuesFor(
                didlMetadata,
                listOf("musicbrainz_recordingid", "recording_mbid"),
            ).firstOrNull(),
            releaseMbid = valuesFor(
                didlMetadata,
                listOf("musicbrainz_albumid", "musicbrainz_releaseid", "release_mbid"),
            ).firstOrNull(),
            releaseGroupMbid = valuesFor(
                didlMetadata,
                listOf("musicbrainz_releasegroupid", "release_group_mbid"),
            ).firstOrNull(),
            trackMbid = valuesFor(
                didlMetadata,
                listOf("musicbrainz_trackid", "musicbrainz_releasetrackid", "track_mbid"),
            ).firstOrNull(),
            artistMbids = valuesFor(
                didlMetadata,
                listOf("musicbrainz_artistid", "artist_mbids"),
            ),
        )
    }

    private fun valuesFor(xml: String, names: List<String>): List<String> {
        val alternatives = names.joinToString("|") { Regex.escape(it) }
        val regions = buildList {
            Regex(
                "<(?:(?:[A-Za-z][\\w.-]*):)?(?:$alternatives)\\b[^>]*>(.*?)</[^>]+>",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            ).findAll(xml).forEach { add(it.groupValues[1]) }
            Regex(
                "<[^>]+(?:name|key|id)\\s*=\\s*[\\\"'](?:$alternatives)[\\\"'][^>]*>(.*?)</[^>]+>",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            ).findAll(xml).forEach { add(it.groupValues[1]) }
            Regex(
                "(?:$alternatives)\\s*=\\s*[\\\"']([^\\\"']+)[\\\"']",
                RegexOption.IGNORE_CASE,
            ).findAll(xml).forEach { add(it.groupValues[1]) }
            Regex(
                "<[^>]+(?:name|key|id)\\s*=\\s*[\\\"'](?:$alternatives)[\\\"'][^>]+" +
                    "value\\s*=\\s*[\\\"']([^\\\"']+)[\\\"'][^>]*/?>",
                RegexOption.IGNORE_CASE,
            ).findAll(xml).forEach { add(it.groupValues[1]) }
        }
        return regions.flatMap { uuid.findAll(it).map { match -> match.value.lowercase() }.toList() }
            .distinct()
    }
}
