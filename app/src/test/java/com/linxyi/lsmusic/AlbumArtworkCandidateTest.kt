package com.linxyi.lsmusic

import com.linxyi.lsmusic.dlna.ArtworkCandidate
import com.linxyi.lsmusic.dlna.rankArtworkCandidates
import com.linxyi.lsmusic.dlna.selectThumbnailArtworkUri
import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumArtworkCandidateTest {
    @Test
    fun ranking_prefersLargeAndUnprofiledArtworkOverThumbnails() {
        val ranked = rankArtworkCandidates(
            listOf(
                ArtworkCandidate("http://server/thumbnail.jpg", "JPEG_TN"),
                ArtworkCandidate("http://server/small.jpg", "JPEG_SM"),
                ArtworkCandidate("http://server/original.jpg"),
                ArtworkCandidate("http://server/large.jpg", "JPEG_LRG"),
                ArtworkCandidate("http://server/medium.jpg", "JPEG_MED"),
            ),
        )

        assertEquals(
            listOf("large.jpg", "original.jpg", "medium.jpg", "small.jpg", "thumbnail.jpg"),
            ranked.map { it.uri.substringAfterLast('/') },
        )
    }

    @Test
    fun ranking_deduplicatesResolvedUrisAndKeepsFirstMetadata() {
        val ranked = rankArtworkCandidates(
            listOf(
                ArtworkCandidate("http://server/cover.jpg", "JPEG_TN"),
                ArtworkCandidate("http://server/cover.jpg", "JPEG_LRG"),
                ArtworkCandidate("http://server/vendor.jpg", "VENDOR_UNKNOWN"),
            ),
        )

        assertEquals(2, ranked.size)
        assertEquals("JPEG_LRG", ranked.first().profileId)
        assertEquals("http://server/vendor.jpg", ranked.last().uri)
    }

    @Test
    fun thumbnailSelection_keepsGridOnThumbnailProfile() {
        val candidates = rankArtworkCandidates(
            listOf(
                ArtworkCandidate("http://server/thumb.jpg", "JPEG_TN"),
                ArtworkCandidate("http://server/large.jpg", "JPEG_LRG"),
            ),
        )

        assertEquals("http://server/large.jpg", candidates.first().uri)
        assertEquals("http://server/thumb.jpg", selectThumbnailArtworkUri(candidates))
    }
}
