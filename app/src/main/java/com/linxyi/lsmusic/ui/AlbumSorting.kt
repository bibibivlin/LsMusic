package com.linxyi.lsmusic.ui

import com.linxyi.lsmusic.dlna.MediaEntry
import java.text.Collator
import java.util.Locale

enum class AlbumSort {
    SERVER_DEFAULT,
    YEAR_ASCENDING,
    YEAR_DESCENDING,
    ALBUM_ARTIST,
    TITLE,
}

fun List<MediaEntry>.sortedAlbums(sort: AlbumSort): List<MediaEntry> = when (sort) {
    AlbumSort.SERVER_DEFAULT -> this
    AlbumSort.YEAR_ASCENDING -> sortedWith(albumYearComparator(descending = false))
    AlbumSort.YEAR_DESCENDING -> sortedWith(albumYearComparator(descending = true))
    AlbumSort.ALBUM_ARTIST -> sortedWith { left, right ->
        compareBlankLast(left.sortAlbumArtist, right.sortAlbumArtist, ::compareAlbumText)
            .takeUnless { it == 0 }
            ?: compareAlbumText(left.title, right.title)
    }
    AlbumSort.TITLE -> sortedWith { left, right -> compareAlbumText(left.title, right.title) }
}

private val MediaEntry.sortAlbumArtist: String
    get() = albumArtist.ifBlank { creator }

private fun albumYearComparator(descending: Boolean) = Comparator<MediaEntry> { left, right ->
    val yearComparison = when {
        left.year == null && right.year == null -> 0
        left.year == null -> 1
        right.year == null -> -1
        descending -> right.year.compareTo(left.year)
        else -> left.year.compareTo(right.year)
    }
    yearComparison.takeUnless { it == 0 } ?: compareAlbumText(left.title, right.title)
}

private fun compareBlankLast(
    left: String,
    right: String,
    compare: (String, String) -> Int,
): Int = when {
    left.isBlank() && right.isBlank() -> 0
    left.isBlank() -> 1
    right.isBlank() -> -1
    else -> compare(left, right)
}

/**
 * Sorts title prefixes in the library's requested order: numbers/symbols, English,
 * Han characters (using Mandarin pinyin collation), then other scripts.
 */
fun compareAlbumText(left: String, right: String): Int {
    val normalizedLeft = left.trim()
    val normalizedRight = right.trim()
    val groupComparison = titleGroup(normalizedLeft).compareTo(titleGroup(normalizedRight))
    if (groupComparison != 0) return groupComparison

    val collator = when (titleGroup(normalizedLeft)) {
        TitleGroup.ENGLISH -> englishCollator.get()
        TitleGroup.CHINESE -> chineseCollator.get()
        else -> rootCollator.get()
    }
    val localizedComparison = requireNotNull(collator).compare(normalizedLeft, normalizedRight)
    return localizedComparison.takeUnless { it == 0 }
        ?: normalizedLeft.compareTo(normalizedRight, ignoreCase = true)
            .takeUnless { it == 0 }
        ?: normalizedLeft.compareTo(normalizedRight)
}

private enum class TitleGroup {
    NUMBER_OR_SYMBOL,
    ENGLISH,
    CHINESE,
    OTHER,
}

private fun titleGroup(value: String): TitleGroup {
    if (value.isEmpty()) return TitleGroup.OTHER
    val codePoint = value.codePointAt(0)
    return when {
        Character.isDigit(codePoint) || Character.getType(codePoint) in symbolAndPunctuationTypes -> {
            TitleGroup.NUMBER_OR_SYMBOL
        }
        codePoint in 'A'.code..'Z'.code || codePoint in 'a'.code..'z'.code -> TitleGroup.ENGLISH
        Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN -> TitleGroup.CHINESE
        else -> TitleGroup.OTHER
    }
}

private val symbolAndPunctuationTypes = setOf(
    Character.CONNECTOR_PUNCTUATION.toInt(),
    Character.DASH_PUNCTUATION.toInt(),
    Character.START_PUNCTUATION.toInt(),
    Character.END_PUNCTUATION.toInt(),
    Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
    Character.FINAL_QUOTE_PUNCTUATION.toInt(),
    Character.OTHER_PUNCTUATION.toInt(),
    Character.MATH_SYMBOL.toInt(),
    Character.CURRENCY_SYMBOL.toInt(),
    Character.MODIFIER_SYMBOL.toInt(),
    Character.OTHER_SYMBOL.toInt(),
)

private fun collator(locale: Locale) = object : ThreadLocal<Collator>() {
    override fun initialValue(): Collator = Collator.getInstance(locale).apply {
        strength = Collator.PRIMARY
    }
}

private val englishCollator = collator(Locale.ENGLISH)
private val chineseCollator = collator(Locale.SIMPLIFIED_CHINESE)
private val rootCollator = collator(Locale.ROOT)
