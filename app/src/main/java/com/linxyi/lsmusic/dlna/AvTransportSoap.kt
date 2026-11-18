package com.linxyi.lsmusic.dlna

internal object AvTransportSoap {
    private const val SOAP_ENVELOPE_NAMESPACE = "http://schemas.xmlsoap.org/soap/envelope/"
    private const val SOAP_ENCODING_STYLE = "http://schemas.xmlsoap.org/soap/encoding/"
    private const val TRANSITION_NOT_AVAILABLE_ERROR_CODE = 701
    private val upnpErrorCodePattern = Regex(
        "<(?:[A-Za-z][\\w.-]*:)?errorCode>\\s*(\\d+)\\s*</(?:[A-Za-z][\\w.-]*:)?errorCode>",
        RegexOption.IGNORE_CASE,
    )
    private val xmlEntity = Regex("&(amp|lt|gt|quot|apos|#[0-9]+|#x[0-9a-fA-F]+);")

    fun isRequestSerializationFailure(message: String): Boolean =
        message.contains("Error writing request message", ignoreCase = true) &&
            message.contains("Can't transform message payload", ignoreCase = true)

    fun isTransitionUnavailable(message: String): Boolean =
        upnpErrorCodePattern.find(message)?.groupValues?.getOrNull(1)?.toIntOrNull() ==
            TRANSITION_NOT_AVAILABLE_ERROR_CODE ||
            message.contains("Transition not available", ignoreCase = true)

    fun envelope(
        serviceType: String,
        actionName: String,
        inputs: Map<String, String>,
    ): String {
        val fields = inputs.entries.joinToString(separator = "") { (name, value) ->
            "<$name>${xmlEscape(value)}</$name>"
        }
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="$SOAP_ENVELOPE_NAMESPACE" s:encodingStyle="$SOAP_ENCODING_STYLE">
              <s:Body>
                <u:$actionName xmlns:u="${xmlEscape(serviceType)}">$fields</u:$actionName>
              </s:Body>
            </s:Envelope>
        """.trimIndent()
    }

    fun responseValue(response: String, name: String): String? = Regex(
        "<(?:[A-Za-z][\\w.-]*:)?$name>(.*?)</(?:[A-Za-z][\\w.-]*:)?$name>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    ).find(response)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }?.let(::decodeXmlText)

    private fun decodeXmlText(value: String): String {
        if (value.startsWith("<![CDATA[") && value.endsWith("]]>")) {
            return value.substring(9, value.length - 3)
        }
        return xmlEntity.replace(value) { match ->
            when (val entity = match.groupValues[1]) {
                "amp" -> "&"
                "lt" -> "<"
                "gt" -> ">"
                "quot" -> "\""
                "apos" -> "'"
                else -> {
                    val codePoint = if (entity.startsWith("#x")) entity.substring(2).toIntOrNull(16)
                    else entity.substring(1).toIntOrNull()
                    if (codePoint != null && Character.isValidCodePoint(codePoint)) {
                        String(Character.toChars(codePoint))
                    } else {
                        match.value
                    }
                }
            }
        }
    }

    private fun xmlEscape(value: String): String = buildString(value.length) {
        value.forEach { char ->
            append(
                when (char) {
                    '&' -> "&amp;"
                    '<' -> "&lt;"
                    '>' -> "&gt;"
                    '"' -> "&quot;"
                    '\'' -> "&apos;"
                    else -> char
                },
            )
        }
    }
}
