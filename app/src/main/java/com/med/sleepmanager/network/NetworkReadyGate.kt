package com.med.sleepmanager.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.util.Log

class NetworkReadyGate(
    context: Context,
    private val handler: Handler,
    private val timeoutMs: Long,
    private val onComplete: (Result) -> Unit
) {
    enum class Result {
        VALIDATED,
        TIMEOUT,
        CALLBACK_UNAVAILABLE
    }

    companion object {
        private const val TAG = "SleepManager"
    }

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private var completed = false
    private var registered = false

    private val timeoutRunnable = Runnable {
        complete(Result.TIMEOUT)
    }

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            if (isValidated(networkCapabilities)) {
                complete(Result.VALIDATED)
            }
        }
    }

    fun start() {
        val cm = connectivityManager
        if (cm == null) {
            complete(Result.CALLBACK_UNAVAILABLE)
            return
        }

        try {
            cm.registerDefaultNetworkCallback(callback, handler)
            registered = true
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to register network-ready callback", t)
            complete(Result.CALLBACK_UNAVAILABLE)
            return
        }

        val currentCapabilities = runCatching {
            cm.activeNetwork?.let(cm::getNetworkCapabilities)
        }.getOrNull()

        if (currentCapabilities != null && isValidated(currentCapabilities)) {
            complete(Result.VALIDATED)
            return
        }

        handler.postDelayed(timeoutRunnable, timeoutMs)
        Log.i(TAG, "Waiting for validated network (timeout=" + timeoutMs + "ms)")
    }

    fun cancel() {
        if (completed) return
        completed = true
        handler.removeCallbacks(timeoutRunnable)
        unregister()
        Log.i(TAG, "Network-ready wait cancelled")
    }

    private fun complete(result: Result) {
        if (completed) return
        completed = true
        handler.removeCallbacks(timeoutRunnable)
        unregister()
        onComplete(result)
    }

    private fun unregister() {
        if (!registered) return
        registered = false
        runCatching {
            connectivityManager?.unregisterNetworkCallback(callback)
        }
    }

    private fun isValidated(capabilities: NetworkCapabilities): Boolean =
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
