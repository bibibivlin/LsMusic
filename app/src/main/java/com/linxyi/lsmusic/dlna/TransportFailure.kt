package com.linxyi.lsmusic.dlna

import com.linxyi.lsmusic.R
import com.linxyi.lsmusic.ui.UiText

/** Only a renderer rejection warrants changing Pause to Stop; connection failures do not. */
internal fun isPauseRejected(error: UiText): Boolean = when (error) {
    is UiText.Resource -> error.id == R.string.error_unsupported_action || error.args.any {
        when (it) {
            is UiText -> isPauseRejected(it)
            is String -> isPauseRejectionText(it)
            else -> false
        }
    }
    is UiText.Raw -> isPauseRejectionText(error.value)
    is UiText.Plural -> false
}

private val pauseRejectionCode = Regex(
    "(?:error\\s*code|UPnP(?:\\s*error)?)\\s*[:=>]?\\s*(401|501|701)\\b",
    RegexOption.IGNORE_CASE,
)

private fun isPauseRejectionText(text: String): Boolean =
    pauseRejectionCode.containsMatchIn(text) ||
        text.contains("Invalid Action", ignoreCase = true) ||
        text.contains("Transition not available", ignoreCase = true)
