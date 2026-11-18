package com.linxyi.lsmusic.dlna

import android.app.Service
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import org.jupnp.UpnpService
import org.jupnp.UpnpServiceImpl
import org.jupnp.android.AndroidUpnpServiceConfiguration
import org.jupnp.model.ServerClientTokens
import org.jupnp.model.meta.RemoteDeviceIdentity
import org.jupnp.protocol.ProtocolFactory
import org.jupnp.registry.Registry
import org.jupnp.registry.RegistryImpl
import org.jupnp.transport.Router
import org.jupnp.transport.RouterImpl
import org.jupnp.transport.impl.DatagramIOConfigurationImpl
import org.jupnp.transport.impl.DatagramIOImpl
import org.jupnp.transport.impl.NetworkAddressFactoryImpl
import org.jupnp.transport.impl.jetty.JettyStreamClientImpl
import org.jupnp.transport.impl.jetty.StreamClientConfigurationImpl
import org.jupnp.transport.spi.DatagramProcessor
import org.jupnp.transport.spi.NetworkAddressFactory
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException

class LsUpnpService : Service() {
    internal class LocalBinder(val upnp: UpnpService) : Binder()

    private val upnp = object : UpnpServiceImpl(LanUpnpConfiguration()) {
        // AndroidRouter follows the default internet transport and may sleep in its broadcast
        // receiver. The controller rebinds this router from LAN and tethering events instead.
        override fun createRouter(protocolFactory: ProtocolFactory, registry: Registry): Router =
            RouterImpl(configuration, protocolFactory)

        override fun createRegistry(protocolFactory: ProtocolFactory): Registry = object : RegistryImpl(this) {
            @Synchronized
            override fun update(identity: RemoteDeviceIdentity): Boolean {
                val device = getRemoteDevice(identity.udn, false)
                val oldLocation = device?.identity?.descriptorURL?.toExternalForm()
                val location = identity.descriptorURL?.toExternalForm()
                // A renderer can advertise a new port after reconnecting with the same IP and UDN.
                if (device != null && oldLocation != null && location != null && oldLocation != location) {
                    removeDevice(device.root)
                    return false
                }
                return super.update(identity)
            }
        }

        override fun shutdown() = shutdown(true)
    }
    private var multicastLock: WifiManager.MulticastLock? = null

    override fun onCreate() {
        super.onCreate()
        multicastLock = applicationContext.getSystemService(WifiManager::class.java)
            ?.createMulticastLock("LsMusic/SSDP")
            ?.apply { acquire() }
        upnp.startup()
    }

    override fun onBind(intent: Intent): IBinder = LocalBinder(upnp)

    override fun onDestroy() {
        upnp.shutdown()
        multicastLock?.release()
        super.onDestroy()
    }
}

internal class LanUpnpConfiguration : AndroidUpnpServiceConfiguration() {
    override fun createNetworkAddressFactory(streamListenPort: Int, multicastResponsePort: Int) =
        object : NetworkAddressFactoryImpl(streamListenPort, multicastResponsePort) {
            override fun requiresNetworkInterface() = false

            override fun isUsableNetworkInterface(iface: NetworkInterface) =
                iface.supportsMulticast() && super.isUsableNetworkInterface(iface)
        }

    override fun createDatagramIO(networkAddressFactory: NetworkAddressFactory) =
        object : DatagramIOImpl(DatagramIOConfigurationImpl()) {
            override fun init(bindAddress: InetAddress, bindPort: Int, router: Router, processor: DatagramProcessor) {
                super.init(bindAddress, bindPort, router, processor)
                try {
                    // On a hotspot host the default route can be cellular. Send SSDP from
                    // the interface corresponding to this socket's local address.
                    NetworkInterface.getByInetAddress(bindAddress)?.let { socket.networkInterface = it }
                } catch (error: SocketException) {
                    Log.d("LsMusic/SSDP", "The LAN interface changed while binding SSDP", error)
                }
            }
        }

    override fun createStreamClient() = object : JettyStreamClientImpl(
        // jUPnP otherwise suppresses a URL for ten minutes after repeated failures. Every
        // AVTransport action on a renderer shares a URL, including recovery and status reads.
        object : StreamClientConfigurationImpl(syncProtocolExecutorService, 10, 5, 0, 0) {
            override fun getUserAgentValue(majorVersion: Int, minorVersion: Int): String =
                ServerClientTokens(majorVersion, minorVersion).apply {
                    osName = "Android"
                    osVersion = Build.VERSION.RELEASE
                }.toString()
        },
    ) {
        init {
            httpClient.connectTimeout = 2_500L
        }
    }
}
