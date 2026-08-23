package com.linxyi.lsmusic

import com.linxyi.lsmusic.artwork.AlbumArtworkRepository
import com.linxyi.lsmusic.artwork.AlbumArtworkSource
import com.linxyi.lsmusic.artwork.ArtworkBoundsReader
import com.linxyi.lsmusic.artwork.ArtworkDimensions
import com.linxyi.lsmusic.artwork.EmbeddedArtwork
import com.linxyi.lsmusic.artwork.EmbeddedArtworkDiskCache
import com.linxyi.lsmusic.artwork.EmbeddedArtworkSource
import com.linxyi.lsmusic.artwork.ServerArtworkLoader
import com.linxyi.lsmusic.dlna.ArtworkCandidate
import com.linxyi.lsmusic.dlna.MediaEntry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AlbumArtworkRepositoryTest {
    private val cacheDirectory = Files.createTempDirectory("album-artwork-test").toFile()

    @After
    fun tearDown() {
        cacheDirectory.deleteRecursively()
    }

    @Test
    fun resolver_doesNotReadAudioWhenServerArtworkMeetsTarget() = runBlocking {
        var embeddedReads = 0
        val repository = repository(
            serverSizes = mapOf("large" to ArtworkDimensions(1200, 1200)),
            embeddedSource = EmbeddedArtworkSource {
                embeddedReads++
                EmbeddedArtwork(byteArrayOf(1), "image/jpeg", 3)
            },
        )

        val result = repository.resolve(
            serverId = "server",
            artworkCandidates = listOf(ArtworkCandidate("large", "JPEG_LRG")),
            tracks = listOf(track("1")),
            targetSizePx = 1080,
        )

        assertEquals(AlbumArtworkSource.SERVER, result?.source)
        assertEquals(0, embeddedReads)
    }

    @Test
    fun resolver_scansAtMostThreeTracksAndUsesEmbeddedArtwork() = runBlocking {
        val reads = mutableListOf<String>()
        val repository = repository(
            serverSizes = mapOf("thumbnail" to ArtworkDimensions(160, 160)),
            embeddedSource = EmbeddedArtworkSource { track ->
                reads += track.id
                if (track.id == "3") EmbeddedArtwork(byteArrayOf(3), "image/jpeg", 3) else null
            },
        )

        val result = repository.resolve(
            serverId = "server",
            artworkCandidates = listOf(ArtworkCandidate("thumbnail", "JPEG_TN")),
            tracks = listOf(track("1"), track("2"), track("3"), track("4")),
            targetSizePx = 1080,
        )

        assertEquals(listOf("1", "2", "3"), reads)
        assertEquals(AlbumArtworkSource.EMBEDDED, result?.source)
        assertEquals(1500, result?.dimensions?.shortEdge)
    }

    @Test
    fun resolver_reusesExtractedArtworkFromDiskCache() = runBlocking {
        var embeddedReads = 0
        val source = EmbeddedArtworkSource {
            embeddedReads++
            EmbeddedArtwork(byteArrayOf(7), "image/jpeg", 3)
        }
        val first = repository(emptyMap(), source)
        val second = repository(emptyMap(), source)

        assertNotNull(first.resolve("server", emptyList(), listOf(track("1")), 1080))
        assertNotNull(second.resolve("server", emptyList(), listOf(track("1")), 1080))
        assertEquals(1, embeddedReads)
    }

    @Test
    fun resolver_boundsServerCandidateAttemptsBeforeEmbeddedFallback() = runBlocking {
        var serverLoads = 0
        val repository = AlbumArtworkRepository(
            serverArtworkLoader = ServerArtworkLoader { _, _ ->
                serverLoads++
                ArtworkDimensions(160, 160)
            },
            embeddedArtworkSource = EmbeddedArtworkSource {
                EmbeddedArtwork(byteArrayOf(5), "image/jpeg", 3)
            },
            embeddedArtworkCache = EmbeddedArtworkDiskCache(cacheDirectory),
            boundsReader = ArtworkBoundsReader { ArtworkDimensions(1500, 1500) },
        )

        val result = repository.resolve(
            serverId = "server",
            artworkCandidates = (1..20).map { ArtworkCandidate("thumbnail-$it", "JPEG_TN") },
            tracks = listOf(track("1")),
            targetSizePx = 1080,
        )

        assertEquals(8, serverLoads)
        assertEquals(AlbumArtworkSource.EMBEDDED, result?.source)
    }

    @Test
    fun diskCache_expiresAndTrimsLeastRecentlyUsedEntries() {
        var now = 1_000L
        val cache = EmbeddedArtworkDiskCache(
            directory = cacheDirectory,
            maxBytes = 3,
            maxAgeMs = 100,
            now = { now },
        )
        assertNotNull(cache.put("one", byteArrayOf(1, 1)))
        now++
        assertNotNull(cache.put("two", byteArrayOf(2, 2)))
        assertTrue(cache.get("one") == null)
        assertNotNull(cache.get("two"))

        now += 101
        assertTrue(cache.get("two") == null)
    }

    private fun repository(
        serverSizes: Map<String, ArtworkDimensions>,
        embeddedSource: EmbeddedArtworkSource,
    ) = AlbumArtworkRepository(
        serverArtworkLoader = ServerArtworkLoader { uri, _ -> serverSizes[uri] },
        embeddedArtworkSource = embeddedSource,
        embeddedArtworkCache = EmbeddedArtworkDiskCache(cacheDirectory),
        boundsReader = ArtworkBoundsReader { data ->
            val marker = when (data) {
                is ByteArray -> data.firstOrNull()
                is File -> data.readBytes().firstOrNull()
                else -> null
            }
            marker?.let { ArtworkDimensions(1500, 1500) }
        },
    )

    private fun track(id: String) = MediaEntry(
        id = id,
        parentId = "album",
        title = "Track $id",
        resourceUri = "http://server/$id.flac",
        resourceSize = 10_000L,
        isContainer = false,
    )
}
