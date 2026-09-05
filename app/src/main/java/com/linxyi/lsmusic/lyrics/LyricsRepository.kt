package com.linxyi.lsmusic.lyrics

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface LyricsRepositoryResult {
    data class Found(val document: LyricsDocument) : LyricsRepositoryResult
    data object NotFound : LyricsRepositoryResult
    data class Failure(val message: String) : LyricsRepositoryResult
}

internal class LyricsRepository(
    providers: List<LyricsProvider>,
    private val cache: LyricsDiskCache,
) {
    private val providersById = providers.associateBy(LyricsProvider::id)

    suspend fun load(
        query: LyricsQuery,
        order: List<LyricsProviderId>,
        forceRefresh: Boolean = false,
    ): LyricsRepositoryResult = withContext(Dispatchers.IO) {
        val failures = mutableListOf<String>()
        for (providerId in normalizedProviderOrder(order)) {
            val provider = providersById[providerId] ?: continue
            if (!forceRefresh) {
                when (val entry = cache.get(query, providerId)) {
                    is LyricsCacheEntry.Document -> return@withContext LyricsRepositoryResult.Found(entry.value)
                    LyricsCacheEntry.NotFound -> continue
                    null -> Unit
                }
            }
            try {
                val candidate = LyricsCandidateMatcher.best(query, provider.search(query))
                if (candidate == null) {
                    cache.put(query, providerId, null)
                    continue
                }
                val document = provider.fetch(candidate)?.takeIf(LyricsDocument::hasVisibleText)
                if (document == null) {
                    cache.put(query, providerId, null)
                    continue
                }
                cache.put(query, providerId, document)
                return@withContext LyricsRepositoryResult.Found(document)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                failures += "${providerId.name}: ${error.localizedMessage ?: "request failed"}"
            }
        }
        if (failures.isEmpty()) {
            LyricsRepositoryResult.NotFound
        } else {
            LyricsRepositoryResult.Failure(failures.joinToString("; "))
        }
    }

    suspend fun clearCache() = cache.clear()

    suspend fun cacheSizeBytes(): Long = cache.sizeBytes()
}

fun normalizedProviderOrder(value: List<LyricsProviderId>): List<LyricsProviderId> = buildList {
    value.forEach { if (it !in this) add(it) }
    LyricsProviderId.entries.forEach { if (it !in this) add(it) }
}
