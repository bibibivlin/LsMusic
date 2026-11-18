package com.linxyi.lsmusic.dlna

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.TetheringInterface
import android.net.TetheringManager
import android.os.Build
import android.os.Handler
import androidx.annotation.RequiresApi
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException

/** These callbacks run on the controller's main handler. */
internal class DlnaNetworkMonitor(
    context: Context,
    handler: Handler,
    onChange: (rebind: Boolean) -> Unit,
) : AutoCloseable {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val lanCallback = object : ConnectivityManager.NetworkCallback() {
        private val links = mutableMapOf<Network, Pair<String?, Set<String>>>()

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            val addresses = linkProperties.linkAddresses
                .filter { it.address is Inet4Address }
                .mapTo(mutableSetOf()) { it.toString() }
            if (addresses.isEmpty()) {
                if (links.remove(network) != null) onChange(true)
            } else {
                val link = linkProperties.interfaceName to addresses
                if (links.put(network, link) != link) onChange(true)
            }
        }

        override fun onLost(network: Network) {
            if (links.remove(network) != null) onChange(true)
        }
    }
    private val upstreamCallback = object : ConnectivityManager.NetworkCallback() {
        private var lastUpstream: Pair<Network, Boolean>? = null

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val upstream = network to networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            if (upstream == lastUpstream) return
            lastUpstream = upstream
            // Some phones keep the hotspot interface visible even when its clients reconnect.
            // An upstream change prompts a search without tearing down a working LAN route.
            onChange(false)
        }

        override fun onLost(network: Network) {
            if (lastUpstream?.first != network) return
            lastUpstream = null
            onChange(false)
        }
    }
    private val tetheringRegistration: AutoCloseable?

    init {
        val lanRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build()
        connectivity?.registerNetworkCallback(lanRequest, lanCallback, handler)
        connectivity?.registerDefaultNetworkCallback(upstreamCallback, handler)
        tetheringRegistration = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            watchTethering(context, handler, onChange)
        } else {
            null
        }
    }

    override fun close() {
        connectivity?.unregisterNetworkCallback(lanCallback)
        connectivity?.unregisterNetworkCallback(upstreamCallback)
        tetheringRegistration?.close()
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun watchTethering(context: Context, handler: Handler, onChange: (Boolean) -> Unit): AutoCloseable? {
        val manager = context.getSystemService(TetheringManager::class.java) ?: return null
        val callback = object : TetheringManager.TetheringEventCallback {
            private var lastInterfaces = emptySet<TetheringInterface>()

            override fun onTetheredInterfacesChanged(interfaces: Set<TetheringInterface>) {
                if (lastInterfaces == interfaces) return
                lastInterfaces = interfaces.toSet()
                onChange(true)
            }
        }
        try {
            manager.registerTetheringEventCallback({ task -> handler.post(task) }, callback)
        } catch (_: SecurityException) {
            return null
        }
        return AutoCloseable { manager.unregisterTetheringEventCallback(callback) }
    }
}

internal data class DlnaLinkAddress(val interfaceIndex: Int, val address: Inet4Address)

internal fun dlnaLinkAddresses(): Set<DlnaLinkAddress>? = try {
    buildSet {
        for (iface in NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()) {
            if (!iface.isUp || iface.isLoopback || iface.isPointToPoint || !iface.supportsMulticast()) continue
            for (address in iface.inetAddresses) {
                if (address is Inet4Address) add(DlnaLinkAddress(iface.index, address))
            }
        }
    }
} catch (_: SocketException) {
    // Interfaces may disappear while Android is enumerating them. Try at the next scan.
    null
}
