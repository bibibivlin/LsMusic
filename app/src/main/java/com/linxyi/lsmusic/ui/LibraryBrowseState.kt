package com.linxyi.lsmusic.ui

import com.linxyi.lsmusic.dlna.MediaEntry

data class BrowsePageKey(
    val serverId: String,
    val objectId: String,
)

enum class LibraryPageKind {
    DIRECTORY,
    ALBUM,
    RESOLVING,
}

internal fun resolveLibraryPageKind(
    hint: LibraryPageKind,
    entries: List<MediaEntry>?,
): LibraryPageKind {
    if (entries == null) return hint
    if (entries.isEmpty()) {
        return if (hint == LibraryPageKind.ALBUM) LibraryPageKind.ALBUM else LibraryPageKind.DIRECTORY
    }
    if (entries.any { it.isContainer }) return LibraryPageKind.DIRECTORY
    return LibraryPageKind.ALBUM
}

data class BrowseViewState(
    val query: String = "",
    val useGrid: Boolean? = null,
    val anchorEntryKey: String? = null,
    val fallbackItemIndex: Int = 0,
    val scrollOffset: Int = 0,
)

internal fun directionalPrefetchRange(
    firstVisibleIndex: Int,
    lastVisibleIndex: Int,
    lastEntryIndex: Int,
    prefetchCount: Int,
    forward: Boolean,
): IntRange {
    if (lastEntryIndex < 0 || firstVisibleIndex > lastVisibleIndex || prefetchCount <= 0) return IntRange.EMPTY
    return if (forward) {
        val first = lastVisibleIndex + 1
        val last = (lastVisibleIndex + prefetchCount).coerceAtMost(lastEntryIndex)
        if (first <= last) first..last else IntRange.EMPTY
    } else {
        val first = (firstVisibleIndex - prefetchCount).coerceAtLeast(0)
        val last = firstVisibleIndex - 1
        if (first <= last) first..last else IntRange.EMPTY
    }
}

internal data class CachedBrowsePage(
    val entries: List<MediaEntry>? = null,
    val viewState: BrowseViewState = BrowseViewState(),
)

/**
 * Keeps ancestors available for instant back navigation and retains a bounded set of other
 * recently visited directories. A null entries value means that the directory has never loaded;
 * an empty list is a successfully loaded empty directory.
 */
internal class LibraryBrowseStore(
    private val maxUnpinnedPages: Int = DEFAULT_MAX_UNPINNED_PAGES,
) {
    private val pages = LinkedHashMap<BrowsePageKey, CachedBrowsePage>(16, 0.75f, true)
    private val latestRequests = mutableMapOf<BrowsePageKey, Long>()
    private var nextRequestGeneration = 0L

    fun page(key: BrowsePageKey): CachedBrowsePage? = pages[key]

    fun storeEntries(key: BrowsePageKey, entries: List<MediaEntry>) {
        val old = pages[key]
        pages[key] = CachedBrowsePage(entries = entries, viewState = old?.viewState ?: BrowseViewState())
    }

    fun storeViewState(key: BrowsePageKey, viewState: BrowseViewState) {
        val old = pages[key]
        pages[key] = CachedBrowsePage(entries = old?.entries, viewState = viewState)
    }

    fun beginRequest(key: BrowsePageKey): Long {
        val generation = ++nextRequestGeneration
        latestRequests[key] = generation
        return generation
    }

    fun isLatestRequest(key: BrowsePageKey, generation: Long): Boolean =
        latestRequests[key] == generation

    fun trim(pinnedKeys: Set<BrowsePageKey>) {
        var unpinnedCount = pages.keys.count { it !in pinnedKeys }
        if (unpinnedCount <= maxUnpinnedPages) return

        val iterator = pages.entries.iterator()
        while (iterator.hasNext() && unpinnedCount > maxUnpinnedPages) {
            val entry = iterator.next()
            if (entry.key !in pinnedKeys) {
                iterator.remove()
                latestRequests.remove(entry.key)
                unpinnedCount--
            }
        }
    }

    fun clear() {
        pages.clear()
        latestRequests.clear()
    }

    internal fun cachedKeys(): Set<BrowsePageKey> = pages.keys.toSet()

    private companion object {
        const val DEFAULT_MAX_UNPINNED_PAGES = 24
    }
}
