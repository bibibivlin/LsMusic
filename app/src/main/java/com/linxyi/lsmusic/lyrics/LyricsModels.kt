package com.linxyi.lsmusic.lyrics

enum class LyricsProviderId(val label: String) {
    NETEASE("网易云音乐"),
    QQ("QQ音乐"),
}

enum class LyricsTranslationMode(val label: String) {
    ORIGINAL("仅原文"),
    BILINGUAL("双语"),
    CHINESE_ONLY("仅中文"),
}

data class LyricsQuery(
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
)

data class LyricsCandidate(
    val id: String,
    val title: String,
    val artists: List<String>,
    val album: String,
    val durationMs: Long,
)

data class TimedWord(
    val text: String,
    val startMs: Long,
    val durationMs: Long,
)

data class LyricsLine(
    val stableId: String,
    val startMs: Long?,
    val durationMs: Long? = null,
    val original: String,
    val translation: String? = null,
    val words: List<TimedWord> = emptyList(),
)

data class LyricsDocument(
    val provider: LyricsProviderId,
    val lines: List<LyricsLine>,
) {
    val isSynced: Boolean
        get() = lines.any { it.startMs != null }

    val hasWordTiming: Boolean
        get() = lines.any { it.words.isNotEmpty() }

    val hasVisibleText: Boolean
        get() = lines.any { it.original.isNotBlank() || !it.translation.isNullOrBlank() }
}

sealed interface LyricsLoadState {
    data object Idle : LyricsLoadState
    data object Loading : LyricsLoadState
    data object NotFound : LyricsLoadState
    data class Loaded(val document: LyricsDocument) : LyricsLoadState
    data class Error(val message: String) : LyricsLoadState
}

interface LyricsProvider {
    val id: LyricsProviderId

    suspend fun search(query: LyricsQuery): List<LyricsCandidate>

    suspend fun fetch(candidate: LyricsCandidate): LyricsDocument?
}

fun LyricsLine.displayTexts(mode: LyricsTranslationMode): Pair<String, String?> {
    val translated = translation?.takeIf(String::isNotBlank)
    return when (mode) {
        LyricsTranslationMode.ORIGINAL -> original to null
        LyricsTranslationMode.BILINGUAL -> original to translated
        LyricsTranslationMode.CHINESE_ONLY -> (translated ?: original) to null
    }
}
