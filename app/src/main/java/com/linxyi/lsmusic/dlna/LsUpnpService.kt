package com.linxyi.lsmusic.dlna

import org.jupnp.android.AndroidUpnpServiceImpl

/**
 * jUPnP 3.0.4 creates [org.jupnp.UpnpServiceImpl] in its Android service but
 * does not invoke startup(). Starting it here ensures the registry and control
 * point exist before Android publishes the service binder to the application.
 */
class LsUpnpService : AndroidUpnpServiceImpl() {
    override fun onCreate() {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info")
        System.setProperty("org.slf4j.simpleLogger.log.org.jupnp", "info")
        System.setProperty("org.slf4j.simpleLogger.showDateTime", "false")
        System.setProperty("org.slf4j.simpleLogger.showThreadName", "true")

        // AndroidRouter already creates SSDP sockets for each usable network interface
        // and acquires the Wi-Fi multicast lock. Binding the whole process to Wi-Fi here
        // would also pin unrelated DNS and HTTPS traffic (including ListenBrainz) to that
        // network, bypassing the active VPN or leaving the process on a stale Wi-Fi route.
        super.onCreate()
        upnpService.startup()
    }
}
