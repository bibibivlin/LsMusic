package com.linxyi.lsmusic.dlna

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.Color
import android.os.IBinder
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.SingletonImageLoader
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jupnp.UpnpService
import org.jupnp.model.message.StreamRequestMessage
import org.jupnp.model.message.UpnpRequest
import org.jupnp.model.meta.Action
import org.jupnp.model.meta.ActionArgument
import org.jupnp.model.meta.ActionArgument.Direction.IN
import org.jupnp.model.meta.ActionArgument.Direction.OUT
import org.jupnp.model.meta.DeviceDetails
import org.jupnp.model.meta.RemoteDevice
import org.jupnp.model.meta.RemoteDeviceIdentity
import org.jupnp.model.meta.RemoteService
import org.jupnp.model.meta.StateVariable
import org.jupnp.model.meta.StateVariableTypeDetails
import org.jupnp.model.types.StringDatatype
import org.jupnp.model.types.UDADeviceType
import org.jupnp.model.types.UDAServiceId
import org.jupnp.model.types.UDAServiceType
import org.jupnp.model.types.UDN
import org.jupnp.model.types.UnsignedIntegerFourBytesDatatype
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URI
import java.net.URL
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class DlnaRecoveryTest {
    @Test
    fun soapEndpointRepliesImmediatelyAfterRepeatedConnectionFailures() {
        LoopbackHttpServer().use { server ->
            val configuration = LanUpnpConfiguration()
            val client = configuration.createStreamClient()
            val uri = URI(server.url)
            try {
                repeat(6) {
                    assertNull(client.sendRequest(StreamRequestMessage(UpnpRequest.Method.GET, uri)))
                }
                val failedRequests = server.requestCount.get()
                server.response = HttpResponse(200, "text/plain", "ready".toByteArray())

                val recovered = client.sendRequest(StreamRequestMessage(UpnpRequest.Method.GET, uri))

                assertEquals(200, recovered?.operation?.statusCode)
                assertEquals(failedRequests + 1, server.requestCount.get())
            } finally {
                client.stop()
                configuration.shutdown()
            }
        }
    }

    @Test
    fun artworkCanLoadAtTheSameUrlAfterAnHttpError() = runBlocking {
        LoopbackHttpServer().use { server ->
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val loader = SingletonImageLoader.get(context)
            val request = ImageRequest.Builder(context).data(server.url).size(2).build()
            server.response = HttpResponse(404, "text/plain", byteArrayOf())
            assertTrue(loader.execute(request) is ErrorResult)

            val image = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
            val png = ByteArrayOutputStream().use { output ->
                image.eraseColor(Color.BLUE)
                image.compress(Bitmap.CompressFormat.PNG, 100, output)
                image.recycle()
                output.toByteArray()
            }
            server.response = HttpResponse(200, "image/png", png)

            assertTrue(loader.execute(request) is SuccessResult)
            assertEquals(2, server.requestCount.get())
            server.response = null
            assertTrue(loader.execute(request) is SuccessResult)
            assertEquals(2, server.requestCount.get())
        }
    }

    @Test
    fun browseUsesTheNewControlPortAdvertisedByTheSameServer() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val connectedService = CompletableDeferred<UpnpService>()
        val binding = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                connectedService.complete((binder as LsUpnpService.LocalBinder).upnp)
            }

            override fun onServiceDisconnected(name: ComponentName) = Unit
        }
        val controller = withContext(Dispatchers.Main) { DlnaController(context) }
        val bound = context.bindService(Intent(context, LsUpnpService::class.java), binding, Context.BIND_AUTO_CREATE)
        try {
            assertTrue(bound)
            val registry = withTimeout(10_000L) { connectedService.await().registry }
            val udn = UDN.valueOf("uuid:${UUID.randomUUID()}")
            LoopbackHttpServer().use { first ->
                LoopbackHttpServer().use { second ->
                    first.response = browseResponse("Original album")
                    second.response = browseResponse("Recovered album")
                    registry.addDevice(mediaServer(udn, first.url))
                    withTimeout(10_000L) { controller.snapshot.first { it.serverLocations[udn.identifierString] == first.url } }
                    assertEquals("Original album", controller.browse(udn.identifierString, "0").single().title)

                    first.response = null
                    val replacement = mediaServer(udn, second.url)
                    assertFalse(registry.update(replacement.identity))
                    registry.addDevice(replacement)
                    withTimeout(10_000L) { controller.snapshot.first { it.serverLocations[udn.identifierString] == second.url } }

                    assertEquals("Recovered album", controller.browse(udn.identifierString, "0").single().title)
                }
            }
        } finally {
            if (bound) context.unbindService(binding)
            withContext(Dispatchers.Main) { controller.close() }
        }
    }

    private fun mediaServer(udn: UDN, location: String): RemoteDevice {
        val arguments = arrayOf(
            ActionArgument<RemoteService>("ObjectID", "Text", IN),
            ActionArgument("BrowseFlag", "Text", IN),
            ActionArgument("Filter", "Text", IN),
            ActionArgument("StartingIndex", "Count", IN),
            ActionArgument("RequestedCount", "Count", IN),
            ActionArgument("SortCriteria", "Text", IN),
            ActionArgument("Result", "Text", OUT),
            ActionArgument("NumberReturned", "Count", OUT),
            ActionArgument("TotalMatches", "Count", OUT),
            ActionArgument("UpdateID", "Count", OUT),
        )
        val service = RemoteService(
            UDAServiceType("ContentDirectory"),
            UDAServiceId("ContentDirectory"),
            URI("/content.xml"),
            URI("/content"),
            URI("/events"),
            arrayOf(Action("Browse", arguments)),
            arrayOf(
                StateVariable("Text", StateVariableTypeDetails(StringDatatype())),
                StateVariable("Count", StateVariableTypeDetails(UnsignedIntegerFourBytesDatatype())),
            ),
        )
        return RemoteDevice(
            RemoteDeviceIdentity(udn, 900, URL(location), null, null),
            UDADeviceType("MediaServer", 1),
            DeviceDetails("Test media server"),
            service,
        )
    }

    private fun browseResponse(title: String): HttpResponse {
        val didl = """
            <DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/"
                xmlns:dc="http://purl.org/dc/elements/1.1/"
                xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
              <container id="album" parentID="0" restricted="1">
                <dc:title>$title</dc:title><upnp:class>object.container.album.musicAlbum</upnp:class>
              </container>
            </DIDL-Lite>
        """.trimIndent().replace("<", "&lt;").replace(">", "&gt;")
        val body = """
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
              <s:Body>
                <u:BrowseResponse xmlns:u="urn:schemas-upnp-org:service:ContentDirectory:1">
                  <Result>$didl</Result><NumberReturned>1</NumberReturned><TotalMatches>1</TotalMatches><UpdateID>0</UpdateID>
                </u:BrowseResponse>
              </s:Body>
            </s:Envelope>
        """.trimIndent()
        return HttpResponse(200, "text/xml; charset=utf-8", body.toByteArray(Charsets.UTF_8))
    }

    private data class HttpResponse(val status: Int, val contentType: String, val body: ByteArray)

    private class LoopbackHttpServer : AutoCloseable {
        private val listener = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val url = "http://127.0.0.1:${listener.localPort}/${UUID.randomUUID()}"
        val requestCount = AtomicInteger()
        @Volatile var response: HttpResponse? = null

        private val worker = thread(name = "DLNA test HTTP", isDaemon = true) {
            while (!listener.isClosed) {
                val client = try {
                    listener.accept()
                } catch (_: SocketException) {
                    return@thread
                }
                client.use(::respond)
            }
        }

        private fun respond(client: Socket) {
            client.soTimeout = 3_000
            val reader = client.getInputStream().bufferedReader(Charsets.US_ASCII)
            if (reader.readLine() == null) return
            var contentLength = 0
            while (true) {
                val header = reader.readLine().orEmpty()
                if (header.isEmpty()) break
                if (header.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = header.substringAfter(':').trim().toInt()
                }
            }
            repeat(contentLength) { if (reader.read() == -1) return }
            requestCount.incrementAndGet()
            val answer = response ?: return
            val statusText = if (answer.status == 200) "OK" else "Not Found"
            client.getOutputStream().apply {
                write((
                    "HTTP/1.1 ${answer.status} $statusText\r\n" +
                        "Content-Type: ${answer.contentType}\r\n" +
                        "Content-Length: ${answer.body.size}\r\n" +
                        "Connection: close\r\n\r\n"
                    ).toByteArray(Charsets.US_ASCII))
                write(answer.body)
                flush()
            }
        }

        override fun close() {
            listener.close()
            worker.join(3_000)
        }
    }
}
