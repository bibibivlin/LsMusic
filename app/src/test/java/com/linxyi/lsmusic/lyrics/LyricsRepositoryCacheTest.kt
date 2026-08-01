package com.linxyi.lsmusic.lyrics

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LyricsRepositoryCacheTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val query = LyricsQuery("Song", "Artist", "Album", 180_000L)
    private val candidate = LyricsCandidate(
        id = "1",
        title = "Song",
        artists = listOf("Artist"),
        album = "Album",
        durationMs = 180_000L,
    )

    @Test
    fun repositoryUsesPriorityFallsBackAndReportsPartialFailure() = runBlocking {
        val failing = FakeProvider(LyricsProviderId.NETEASE, failure = IllegalStateException("offline"))
        val successful = FakeProvider(LyricsProviderId.QQ, candidates = listOf(candidate), document = document(LyricsProviderId.QQ))
        val repository = LyricsRepository(
            listOf(failing, successful),
            LyricsDiskCache(temporaryFolder.newFolder()),
        )

        val loaded = repository.load(query, listOf(LyricsProviderId.NETEASE, LyricsProviderId.QQ))
        assertTrue(loaded is LyricsRepositoryResult.Found)
        assertEquals(1, failing.searchCount)
        assertEquals(1, successful.searchCount)

        val allMissing = LyricsRepository(
            listOf(failing, FakeProvider(LyricsProviderId.QQ)),
            LyricsDiskCache(temporaryFolder.newFolder()),
        ).load(query, LyricsProviderId.entries)
        assertTrue(allMissing is LyricsRepositoryResult.Failure)
    }

    @Test
    fun repositoryCachesSuccessAndForceRefreshBypassesIt() = runBlocking {
        val provider = FakeProvider(
            LyricsProviderId.NETEASE,
            candidates = listOf(candidate),
            document = document(LyricsProviderId.NETEASE),
        )
        val repository = LyricsRepository(listOf(provider), LyricsDiskCache(temporaryFolder.newFolder()))

        repository.load(query, listOf(LyricsProviderId.NETEASE))
        repository.load(query, listOf(LyricsProviderId.NETEASE))
        assertEquals(1, provider.searchCount)
        repository.load(query, listOf(LyricsProviderId.NETEASE), forceRefresh = true)
        assertEquals(2, provider.searchCount)
    }

    @Test
    fun repositoryStopsAfterPreferredProviderLoadsLyrics() = runBlocking {
        val qq = FakeProvider(
            LyricsProviderId.QQ,
            candidates = listOf(candidate),
            document = document(LyricsProviderId.QQ),
        )
        val netEase = FakeProvider(
            LyricsProviderId.NETEASE,
            candidates = listOf(candidate),
            document = document(LyricsProviderId.NETEASE),
        )
        val repository = LyricsRepository(
            listOf(netEase, qq),
            LyricsDiskCache(temporaryFolder.newFolder()),
        )

        val loaded = repository.load(query, listOf(LyricsProviderId.QQ, LyricsProviderId.NETEASE))

        assertEquals(LyricsProviderId.QQ, (loaded as LyricsRepositoryResult.Found).document.provider)
        assertEquals(1, qq.searchCount)
        assertEquals(0, netEase.searchCount)
    }

    @Test
    fun cacheExpiresNegativeAfterOneDayAndPositiveAfterThirtyDays() = runBlocking {
        var now = 1_000_000L
        val cache = LyricsDiskCache(temporaryFolder.newFolder(), clock = { now })
        cache.put(query, LyricsProviderId.NETEASE, null)
        assertEquals(LyricsCacheEntry.NotFound, cache.get(query, LyricsProviderId.NETEASE))
        now += 24L * 60L * 60L * 1_000L + 1L
        assertNull(cache.get(query, LyricsProviderId.NETEASE))

        cache.put(query, LyricsProviderId.NETEASE, document(LyricsProviderId.NETEASE))
        assertTrue(cache.get(query, LyricsProviderId.NETEASE) is LyricsCacheEntry.Document)
        now += 30L * 24L * 60L * 60L * 1_000L + 1L
        assertNull(cache.get(query, LyricsProviderId.NETEASE))
    }

    @Test
    fun cacheRecoversFromCorruptionPrunesAndClears() = runBlocking {
        val pruneDirectory = temporaryFolder.newFolder()
        val cache = LyricsDiskCache(pruneDirectory, maximumBytes = 900L)
        cache.put(query, LyricsProviderId.NETEASE, document(LyricsProviderId.NETEASE, "a".repeat(700)))
        cache.put(query.copy(title = "Second"), LyricsProviderId.NETEASE, document(LyricsProviderId.NETEASE, "b".repeat(700)))
        assertTrue(cache.sizeBytes() <= 900L)

        val corruptDirectory = temporaryFolder.newFolder()
        val corruptCache = LyricsDiskCache(corruptDirectory)
        corruptCache.put(query, LyricsProviderId.NETEASE, document(LyricsProviderId.NETEASE))
        val corruptFile = requireNotNull(corruptDirectory.listFiles()?.singleOrNull())
        corruptFile.writeText("not a cache entry")
        assertNull(corruptCache.get(query, LyricsProviderId.NETEASE))
        assertFalse(corruptFile.exists())

        cache.clear()
        assertEquals(0L, cache.sizeBytes())
    }

    @Test
    fun providerOrderRemovesDuplicatesAndRestoresMissingSources() {
        assertEquals(
            listOf(LyricsProviderId.QQ, LyricsProviderId.NETEASE),
            normalizedProviderOrder(listOf(LyricsProviderId.QQ, LyricsProviderId.QQ)),
        )
    }

    private fun document(provider: LyricsProviderId, text: String = "line") = LyricsDocument(
        provider,
        listOf(LyricsLine("1", 0L, original = text)),
    )

    private class FakeProvider(
        override val id: LyricsProviderId,
        private val candidates: List<LyricsCandidate> = emptyList(),
        private val document: LyricsDocument? = null,
        private val failure: Throwable? = null,
    ) : LyricsProvider {
        var searchCount = 0

        override suspend fun search(query: LyricsQuery): List<LyricsCandidate> {
            searchCount++
            failure?.let { throw it }
            return candidates
        }

        override suspend fun fetch(candidate: LyricsCandidate): LyricsDocument? = document
    }
}
