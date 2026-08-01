package com.linxyi.lsmusic.lyrics

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

private val lrcTimestamp = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")
private val lrcMetadata = Regex("^\\[(?:ar|al|ti|by|re|ve|length):.*]$", RegexOption.IGNORE_CASE)
private val offsetMetadata = Regex("^\\[offset:([+-]?\\d+)]$", RegexOption.IGNORE_CASE)
private val verbatimLineTimestamp = Regex("^\\[(\\d+),(\\d+)]")
private val verbatimWordTimestamp = Regex("\\((\\d+),(\\d+)(?:,\\d+)?\\)")
private val bracketedQualifier = Regex("[（(\\[].*?[）)\\]]")
private val artistSeparator = Regex("(?:\\s+(?:feat\\.?|ft\\.?|featuring)\\s+)|[&/、·・,，;；]", RegexOption.IGNORE_CASE)

fun normalizeLyricsMatchText(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
    .lowercase(Locale.ROOT)
    .replace(Regex("[^\\p{L}\\p{N}]+"), "")

private fun baseTitle(value: String): String = normalizeLyricsMatchText(value.replace(bracketedQualifier, ""))

private fun artistParts(value: String): Set<String> = value.split(artistSeparator)
    .map(::normalizeLyricsMatchText)
    .filter(String::isNotBlank)
    .toSet()

object LyricsCandidateMatcher {
    fun best(query: LyricsQuery, candidates: List<LyricsCandidate>): LyricsCandidate? = candidates
        .mapNotNull { candidate -> score(query, candidate)?.let { it to candidate } }
        .maxByOrNull { it.first }
        ?.second

    internal fun score(query: LyricsQuery, candidate: LyricsCandidate): Int? {
        val queryTitle = normalizeLyricsMatchText(query.title)
        val candidateTitle = normalizeLyricsMatchText(candidate.title)
        if (queryTitle.isBlank() || candidateTitle.isBlank()) return null
        val exactTitle = queryTitle == candidateTitle
        val baseTitleMatches = baseTitle(query.title).isNotBlank() && baseTitle(query.title) == baseTitle(candidate.title)
        if (!exactTitle && !baseTitleMatches) return null

        val durationComparable = query.durationMs > 0L && candidate.durationMs > 0L
        val durationMatches = !durationComparable || abs(query.durationMs - candidate.durationMs) <= 4_000L
        if (!durationMatches) return null

        val queryArtists = artistParts(query.artist)
        val candidateArtists = candidate.artists.flatMap(::artistParts).toSet()
        val artistMatches = queryArtists.any(candidateArtists::contains)
        val albumMatches = normalizeLyricsMatchText(query.album).let { album ->
            album.isNotBlank() && album == normalizeLyricsMatchText(candidate.album)
        }

        when {
            queryArtists.isEmpty() -> if (!exactTitle || !albumMatches || !durationComparable) return null
            !artistMatches -> return null
            !exactTitle && (!albumMatches || !durationComparable) -> return null
        }

        return (if (exactTitle) 100 else 65) +
            (if (artistMatches) 30 else 0) +
            (if (albumMatches) 15 else 0) +
            (if (durationComparable) (10 - (abs(query.durationMs - candidate.durationMs) / 500L).toInt()).coerceAtLeast(1) else 0)
    }
}

object LyricsParser {
    fun parse(
        provider: LyricsProviderId,
        original: String?,
        translation: String? = null,
        verbatim: String? = null,
    ): LyricsDocument? {
        val translationLines = parseVerbatimLines(translation.orEmpty()).ifEmpty {
            parseLrcLines(translation.orEmpty())
        }
        val timedWords = parseVerbatimLines(verbatim.orEmpty())
        val originalLines = if (timedWords.isNotEmpty()) timedWords else parseLrcLines(original.orEmpty())
        if (originalLines.isEmpty()) return null

        val merged = if (originalLines.any { it.startMs != null }) {
            originalLines.map { line ->
                val translated = line.startMs?.let { start ->
                    translationLines
                        .asSequence()
                        .filter { it.startMs != null }
                        .minByOrNull { abs(it.startMs!! - start) }
                        ?.takeIf { abs(it.startMs!! - start) <= TRANSLATION_TOLERANCE_MS }
                        ?.original
                }
                line.copy(translation = translated?.takeIf(String::isNotBlank))
            }
        } else {
            originalLines.mapIndexed { index, line ->
                line.copy(translation = translationLines.getOrNull(index)?.original?.takeIf(String::isNotBlank))
            }
        }
        return LyricsDocument(provider, merged).takeIf(LyricsDocument::hasVisibleText)
    }

    internal fun parseLrcLines(value: String): List<LyricsLine> {
        val normalized = value.replace("\r\n", "\n").replace('\r', '\n')
        val rows = normalized.lines().map(String::trim).filter(String::isNotBlank)
        val offset = rows.firstNotNullOfOrNull { offsetMetadata.matchEntire(it)?.groupValues?.get(1)?.toLongOrNull() } ?: 0L
        val timed = mutableListOf<LyricsLine>()
        val untimed = mutableListOf<String>()
        rows.forEachIndexed { rowIndex, row ->
            if (lrcMetadata.matches(row) || offsetMetadata.matches(row)) return@forEachIndexed
            val matches = lrcTimestamp.findAll(row).toList()
            val text = lrcTimestamp.replace(row, "").trim()
            if (matches.isEmpty()) {
                if (!row.startsWith("[") && row.isNotBlank()) untimed += row
            } else if (text.isNotBlank()) {
                matches.forEachIndexed { index, match ->
                    val start = parseLrcTimestamp(match.groupValues) + offset
                    timed += LyricsLine(
                        stableId = "lrc:$start:$rowIndex:$index:${text.hashCode()}",
                        startMs = start.coerceAtLeast(0L),
                        original = text,
                    )
                }
            }
        }
        if (timed.isNotEmpty()) {
            val sorted = timed.sortedBy { it.startMs }
            return sorted.mapIndexed { index, line ->
                val next = sorted.getOrNull(index + 1)?.startMs
                line.copy(durationMs = next?.minus(line.startMs ?: next)?.coerceAtLeast(0L))
            }
        }
        return untimed.mapIndexed { index, text ->
            LyricsLine("plain:$index:${text.hashCode()}", null, original = text)
        }
    }

    internal fun parseVerbatimLines(value: String): List<LyricsLine> = value
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .lines()
        .mapIndexedNotNull { index, row -> parseVerbatimLine(row.trim(), index) }
        .sortedBy { it.startMs }

    private fun parseVerbatimLine(row: String, rowIndex: Int): LyricsLine? {
        val lineMatch = verbatimLineTimestamp.find(row) ?: return null
        val lineStart = lineMatch.groupValues[1].toLongOrNull() ?: return null
        val lineDuration = lineMatch.groupValues[2].toLongOrNull() ?: return null
        val content = row.substring(lineMatch.range.last + 1)
        val markers = verbatimWordTimestamp.findAll(content).toList()
        if (markers.isEmpty()) {
            return content.trim().takeIf(String::isNotBlank)?.let {
                LyricsLine("verbatim:$lineStart:$rowIndex:${it.hashCode()}", lineStart, lineDuration, it)
            }
        }
        val words = if (content.trimStart().startsWith("(")) {
            markers.mapIndexedNotNull { index, marker ->
                val nextStart = markers.getOrNull(index + 1)?.range?.first ?: content.length
                val text = content.substring(marker.range.last + 1, nextStart)
                timedWord(marker, text)
            }
        } else {
            var previousEnd = 0
            markers.mapNotNull { marker ->
                val text = content.substring(previousEnd, marker.range.first)
                previousEnd = marker.range.last + 1
                timedWord(marker, text)
            }
        }
        val text = words.joinToString(separator = "") { it.text }.trim()
        if (text.isBlank()) return null
        return LyricsLine(
            stableId = "verbatim:$lineStart:$rowIndex:${text.hashCode()}",
            startMs = lineStart,
            durationMs = lineDuration,
            original = text,
            words = words,
        )
    }

    private fun timedWord(match: MatchResult, value: String): TimedWord? {
        val text = value.takeIf(String::isNotEmpty) ?: return null
        return TimedWord(
            text = text,
            startMs = match.groupValues[1].toLongOrNull() ?: return null,
            durationMs = match.groupValues[2].toLongOrNull() ?: return null,
        )
    }

    private fun parseLrcTimestamp(groups: List<String>): Long {
        val minutes = groups[1].toLongOrNull() ?: 0L
        val seconds = groups[2].toLongOrNull() ?: 0L
        val fraction = groups[3]
        val millis = when (fraction.length) {
            0 -> 0L
            1 -> fraction.toLongOrNull()?.times(100L) ?: 0L
            2 -> fraction.toLongOrNull()?.times(10L) ?: 0L
            else -> fraction.take(3).padEnd(3, '0').toLongOrNull() ?: 0L
        }
        return minutes * 60_000L + seconds * 1_000L + millis
    }

    private const val TRANSLATION_TOLERANCE_MS = 350L
}

fun activeLyricsLineIndex(lines: List<LyricsLine>, positionMs: Long): Int {
    var low = 0
    var high = lines.lastIndex
    var result = -1
    while (low <= high) {
        val middle = (low + high).ushr(1)
        val start = lines[middle].startMs
        if (start == null || start > positionMs) {
            high = middle - 1
        } else {
            result = middle
            low = middle + 1
        }
    }
    return result
}

fun wordSweepProgress(line: LyricsLine, positionMs: Long): Float {
    if (line.words.isEmpty()) return 1f
    val lineStart = line.startMs ?: 0L
    val totalWeight = line.words.sumOf { it.text.length.coerceAtLeast(1) }.toFloat().coerceAtLeast(1f)
    var completedWeight = 0f
    line.words.forEach { word ->
        val weight = word.text.length.coerceAtLeast(1).toFloat()
        val start = if (word.startMs < lineStart) lineStart + word.startMs else word.startMs
        val fraction = ((positionMs - start).toFloat() / word.durationMs.coerceAtLeast(1L))
            .coerceIn(0f, 1f)
        completedWeight += weight * fraction
        if (fraction < 1f) return (completedWeight / totalWeight).coerceIn(0f, 1f)
    }
    return 1f
}

fun interpolatedLyricsPosition(
    anchorPositionMs: Long,
    elapsedSinceAnchorMs: Long,
    isPlaying: Boolean,
    durationMs: Long,
): Long {
    if (!isPlaying) return anchorPositionMs.coerceAtLeast(0L)
    val maximum = durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE
    return (anchorPositionMs.coerceAtLeast(0L) + elapsedSinceAnchorMs.coerceAtLeast(0L))
        .coerceAtMost(maximum)
}
