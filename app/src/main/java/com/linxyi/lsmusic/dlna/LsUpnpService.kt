package com.linxyi.lsmusic.dlna

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import org.jupnp.android.AndroidUpnpServiceImpl

/**
 * jUPnP 3.0.4 creates [org.jupnp.UpnpServiceImpl] in its Android service but
 * does not invoke startup(). Starting it here ensures the registry and control
 * point exist before Android publishes the service binder to the application.
 */
class LsUpnpService : AndroidUpnpServiceImpl() {
    private var connectivityManager: ConnectivityManager? = null
    private var wifiNetwork: Network? = null

    override fun onCreate() {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info")
        System.setProperty("org.slf4j.simpleLogger.log.org.jupnp", "info")
        System.setProperty("org.slf4j.simpleLogger.showDateTime", "false")
        System.setProperty("org.slf4j.simpleLogger.showThreadName", "true")

        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        wifiNetwork = connectivityManager?.allNetworks?.firstOrNull { network ->
            connectivityManager?.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
        val boundToWifi = wifiNetwork?.let { connectivityManager?.bindProcessToNetwork(it) } == true
        Log.i(TAG, "DLNA process routing bound to Wi-Fi: $boundToWifi, network=$wifiNetwork")

        try {
            super.onCreate()
            upnpService.startup()
        } catch (error: Throwable) {
            releaseWifiRouting()
            throw error
        }
    }

    override fun onDestroy() {
        try {
            super.onDestroy()
        } finally {
            releaseWifiRouting()
        }
    }

    private fun releaseWifiRouting() {
        if (wifiNetwork != null) {
            connectivityManager?.bindProcessToNetwork(null)
            Log.i(TAG, "DLNA process routing restored to the system default")
        }
        wifiNetwork = null
    }

    companion object {
        private const val TAG = "LsMusic/DLNA"
    }
}
