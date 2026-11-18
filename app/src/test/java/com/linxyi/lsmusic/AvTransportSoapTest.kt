package com.linxyi.lsmusic

import com.linxyi.lsmusic.dlna.AvTransportSoap
import com.linxyi.lsmusic.dlna.isMissingUpnpResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AvTransportSoapTest {
    @Test
    fun serializationFailure_recognizesJupnpLocalWriteError() {
        val message = "SetAVTransportURI 失败：Error: Current state of service prevents invoking that action. " +
            "Error writing request message. Can't transform message payload. " +
            "(HTTP response was: 500 Internal Server Error)"

        assertTrue(AvTransportSoap.isRequestSerializationFailure(message))
    }

    @Test
    fun serializationFailure_doesNotTreatRemoteHttpErrorAsLocalWriteError() {
        val message = "SetAVTransportURI 失败：HTTP 500: UPnPError 701 Transition not available"

        assertFalse(AvTransportSoap.isRequestSerializationFailure(message))
    }

    @Test
    fun transitionUnavailable_recognizesSoapFaultCode() {
        val message = """
            SetAVTransportURI 失败：HTTP 500:
            <s:Fault><detail><UPnPError><errorCode>701</errorCode>
            <errorDescription>Transition not available</errorDescription></UPnPError></detail></s:Fault>
        """.trimIndent()

        assertTrue(AvTransportSoap.isTransitionUnavailable(message))
    }

    @Test
    fun transitionUnavailable_recognizesJupnpDescriptionWithoutFaultXml() {
        assertTrue(AvTransportSoap.isTransitionUnavailable("Error: Transition not available"))
    }

    @Test
    fun transitionUnavailable_rejectsUnrelatedUpnpFault() {
        val message = "HTTP 500: <UPnPError><errorCode>714</errorCode><errorDescription>Illegal MIME-type</errorDescription>"

        assertFalse(AvTransportSoap.isTransitionUnavailable(message))
    }

    @Test
    fun envelope_usesDiscoveredServiceTypeAndEscapesArguments() {
        val envelope = AvTransportSoap.envelope(
            serviceType = "urn:schemas-upnp-org:service:AVTransport:2",
            actionName = "SetAVTransportURI",
            inputs = mapOf(
                "InstanceID" to "0",
                "CurrentURI" to "http://media.example/track?a=1&b=2",
                "CurrentURIMetaData" to "<DIDL-Lite title=\"A & B\"/>",
            ),
        )

        assertTrue(envelope.contains("xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:2\""))
        assertTrue(envelope.contains("<CurrentURI>http://media.example/track?a=1&amp;b=2</CurrentURI>"))
        assertTrue(
            envelope.contains(
                "<CurrentURIMetaData>&lt;DIDL-Lite title=&quot;A &amp; B&quot;/&gt;</CurrentURIMetaData>",
            ),
        )
    }

    @Test
    fun responseValue_readsPrefixedSoapOutput() {
        val response = """
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
              <s:Body>
                <u:GetTransportInfoResponse xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                  <CurrentTransportState>PLAYING</CurrentTransportState>
                </u:GetTransportInfoResponse>
              </s:Body>
            </s:Envelope>
        """.trimIndent()

        assertEquals("PLAYING", AvTransportSoap.responseValue(response, "CurrentTransportState"))
    }

    @Test
    fun responseValue_decodesTrackUriForComparisonWithNormalSoap() {
        val response = "<TrackURI>http://media.example/track?a=1&amp;b=2</TrackURI>"

        assertEquals("http://media.example/track?a=1&b=2", AvTransportSoap.responseValue(response, "TrackURI"))
    }

    @Test
    fun missingResponse_doesNotMistakeLocalParsingAndDeviceFaultsForDisconnection() {
        assertTrue(isMissingUpnpResponse("Error: "))
        assertTrue(isMissingUpnpResponse("Error: Connection error or no response received"))
        assertFalse(isMissingUpnpResponse("Error: Can't parse DIDL XML response: invalid XML"))
        assertFalse(isMissingUpnpResponse("bad control URL: invalid URL"))
        assertFalse(isMissingUpnpResponse("Error: Transition not available"))
    }
}
