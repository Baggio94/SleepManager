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
        private const val RECHECK_INTERVAL_MS = 500L
    }

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private var completed = false
    private var registered = false

    private val timeoutRunnable = Runnable {
        complete(Result.TIMEOUT)
    }

    private val recheckRunnable = object : Runnable {
        override fun run() {
            if (completed) return

            val validated =
                runCatching {
                    val cm = connectivityManager ?: return@runCatching false
                    val active = cm.activeNetwork ?: return@runCatching false
                    val capabilities =
                        cm.getNetworkCapabilities(active)
                            ?: return@runCatching false
                    isValidated(capabilities)
                }.getOrDefault(false)

            if (validated) {
                Log.i(TAG, "Validated network detected by bounded recheck")
                complete(Result.VALIDATED)
            } else if (!completed) {
                handler.postDelayed(this, RECHECK_INTERVAL_MS)
            }
        }
    }

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val cm = connectivityManager ?: return
            val capabilities =
                runCatching {
                    cm.getNetworkCapabilities(network)
                }.getOrNull()

            if (capabilities != null && isValidated(capabilities)) {
                complete(Result.VALIDATED)
            }
        }
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

        handler.postDelayed(recheckRunnable, RECHECK_INTERVAL_MS)
        handler.postDelayed(timeoutRunnable, timeoutMs)
        Log.i(
            TAG,
            "Waiting for validated network (timeout=" + timeoutMs +
                "ms, fallbackRecheck=" + RECHECK_INTERVAL_MS + "ms)"
        )
    }

    fun cancel() {
        if (completed) return
        completed = true
        handler.removeCallbacks(timeoutRunnable)
        handler.removeCallbacks(recheckRunnable)
        unregister()
        Log.i(TAG, "Network-ready wait cancelled")
    }

    private fun complete(result: Result) {
        if (completed) return
        completed = true
        handler.removeCallbacks(timeoutRunnable)
        handler.removeCallbacks(recheckRunnable)
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
