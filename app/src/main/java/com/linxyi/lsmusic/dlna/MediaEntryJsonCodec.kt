package com.linxyi.lsmusic.dlna

import org.json.JSONArray
import org.json.JSONObject

/** Shared, lossless metadata representation for queues and durable listens. */
internal object MediaEntryJsonCodec {
    fun encode(track: MediaEntry) = JSONObject().apply {
        put("id", track.id)
        put("parentId", track.parentId)
        put("title", track.title)
        put("creator", track.creator)
        put("album", track.album)
        put("albumArtist", track.albumArtist)
        put("year", track.year)
        put("genre", track.genre)
        put("trackNumber", track.trackNumber)
        put("artworkUri", track.artworkUri)
        put("artworkCandidates", JSONArray().apply {
            track.artworkCandidates.forEach { put(JSONObject().put("uri", it.uri).put("profileId", it.profileId)) }
        })
        put("resourceUri", track.resourceUri)
        put("resourceSize", track.resourceSize)
        put("duration", track.duration)
        put("mimeType", track.mimeType)
        put("protocolInfo", track.protocolInfo)
        put("didlMetadata", track.didlMetadata)
        put("recordingMbid", track.recordingMbid)
        put("releaseMbid", track.releaseMbid)
        put("releaseGroupMbid", track.releaseGroupMbid)
        put("trackMbid", track.trackMbid)
        put("artistMbids", JSONArray(track.artistMbids))
        put("isContainer", track.isContainer)
        put("isAlbum", track.isAlbum)
        put("childCount", track.childCount)
    }

    fun decode(root: JSONObject) = MediaEntry(
        id = root.getString("id"),
        parentId = root.getString("parentId"),
        title = root.getString("title"),
        creator = root.optString("creator"),
        album = root.optString("album"),
        albumArtist = root.optString("albumArtist"),
        year = root.intOrNull("year"),
        genre = root.optString("genre"),
        trackNumber = root.intOrNull("trackNumber"),
        artworkUri = root.stringOrNull("artworkUri"),
        artworkCandidates = (root.optJSONArray("artworkCandidates") ?: JSONArray()).let { array ->
            List(array.length()) { index ->
                val candidate = array.getJSONObject(index)
                ArtworkCandidate(candidate.getString("uri"), candidate.stringOrNull("profileId"))
            }
        },
        resourceUri = root.stringOrNull("resourceUri"),
        resourceSize = root.longOrNull("resourceSize"),
        duration = root.stringOrNull("duration"),
        mimeType = root.stringOrNull("mimeType"),
        protocolInfo = root.stringOrNull("protocolInfo"),
        didlMetadata = root.stringOrNull("didlMetadata"),
        recordingMbid = root.stringOrNull("recordingMbid"),
        releaseMbid = root.stringOrNull("releaseMbid"),
        releaseGroupMbid = root.stringOrNull("releaseGroupMbid"),
        trackMbid = root.stringOrNull("trackMbid"),
        artistMbids = (root.optJSONArray("artistMbids") ?: JSONArray()).strings(),
        isContainer = root.optBoolean("isContainer", false),
        isAlbum = root.optBoolean("isAlbum", false),
        childCount = root.intOrNull("childCount"),
    )

    private fun JSONArray.strings() = List(length()) { get(it) as String }
    private fun JSONObject.stringOrNull(key: String) = if (isNull(key)) null else getString(key)
    private fun JSONObject.intOrNull(key: String) = if (isNull(key)) null else getInt(key)
    private fun JSONObject.longOrNull(key: String) = if (isNull(key)) null else getLong(key)
}
