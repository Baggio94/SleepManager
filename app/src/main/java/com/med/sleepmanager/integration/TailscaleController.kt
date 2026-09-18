package com.med.sleepmanager.integration

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

object TailscaleController {
    const val PACKAGE = "com.tailscale.ipn"
    const val ACTION_CONNECT = "com.tailscale.ipn.CONNECT_VPN"
    const val ACTION_DISCONNECT = "com.tailscale.ipn.DISCONNECT_VPN"

    fun isInstalled(context: Context): Boolean =
        try {
            context.packageManager.getPackageInfo(PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    fun versionName(context: Context): String? =
        runCatching {
            context.packageManager.getPackageInfo(PACKAGE, 0).versionName
        }.getOrNull()

    fun open(context: Context): Boolean {
        val launchIntent =
            context.packageManager.getLaunchIntentForPackage(PACKAGE)
                ?: return false

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(launchIntent)
            true
        }.getOrDefault(false)
    }

    fun isConnected(context: Context): Boolean {
        if (!isInstalled(context)) return false

        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false

        return runCatching {
            @Suppress("DEPRECATION")
            connectivityManager.allNetworks.any { network ->
                val capabilities =
                    connectivityManager.getNetworkCapabilities(network)
                        ?: return@any false

                if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    return@any false
                }

                val linkProperties =
                    connectivityManager.getLinkProperties(network)
                        ?: return@any false

                linkProperties.linkAddresses.any { linkAddress ->
                    isTailscaleAddress(linkAddress.address)
                }
            }
        }.getOrDefault(false)
    }

    fun hasAnyVpnTransport(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false

        return runCatching {
            @Suppress("DEPRECATION")
            connectivityManager.allNetworks.any { network ->
                connectivityManager
                    .getNetworkCapabilities(network)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            }
        }.getOrDefault(false)
    }

    fun sendDisconnect(context: Context): Boolean =
        sendControlBroadcast(context, ACTION_DISCONNECT, "DISCONNECT")

    fun sendConnect(context: Context): Boolean =
        sendControlBroadcast(context, ACTION_CONNECT, "CONNECT")

    private fun isTailscaleAddress(address: InetAddress): Boolean {
        val bytes = address.address

        if (address is Inet4Address && bytes.size == 4) {
            val first = bytes[0].toInt() and 0xff
            val second = bytes[1].toInt() and 0xff
            return first == 100 && second in 64..127
        }

        if (address is Inet6Address && bytes.size == 16) {
            return (bytes[0].toInt() and 0xff) == 0xfd &&
                (bytes[1].toInt() and 0xff) == 0x7a &&
                (bytes[2].toInt() and 0xff) == 0x11 &&
                (bytes[3].toInt() and 0xff) == 0x5c &&
                (bytes[4].toInt() and 0xff) == 0xa1 &&
                (bytes[5].toInt() and 0xff) == 0xe0
        }

        return false
    }

    private fun sendControlBroadcast(
        context: Context,
        action: String,
        label: String
    ): Boolean {
        if (!isInstalled(context)) return false

        return runCatching {
            context.sendBroadcast(
                Intent(action)
                    .setPackage(PACKAGE)
                    .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            )
            Log.i("SleepManager", "Sent Tailscale $label")
            true
        }.getOrElse {
            Log.e("SleepManager", "Unable to send Tailscale $label", it)
            false
        }
    }
}
