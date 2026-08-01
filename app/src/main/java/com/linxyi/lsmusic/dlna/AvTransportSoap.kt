package com.linxyi.lsmusic.dlna

internal object AvTransportSoap {
    private const val SOAP_ENVELOPE_NAMESPACE = "http://schemas.xmlsoap.org/soap/envelope/"
    private const val SOAP_ENCODING_STYLE = "http://schemas.xmlsoap.org/soap/encoding/"
    private const val TRANSITION_NOT_AVAILABLE_ERROR_CODE = 701
    private val upnpErrorCodePattern = Regex(
        "<(?:[A-Za-z][\\w.-]*:)?errorCode>\\s*(\\d+)\\s*</(?:[A-Za-z][\\w.-]*:)?errorCode>",
        RegexOption.IGNORE_CASE,
    )

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
    ).find(response)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }

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
