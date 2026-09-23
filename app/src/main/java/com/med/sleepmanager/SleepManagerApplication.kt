package com.med.sleepmanager

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.med.sleepmanager.data.AppPreferences
import com.med.sleepmanager.network.NetworkReadyGate
import com.med.sleepmanager.update.UpdateChecker

class SleepManagerApplication : Application(), DefaultLifecycleObserver {
    private val handler = Handler(Looper.getMainLooper())
    private var networkWait: NetworkReadyGate? = null

    override fun onCreate() {
        super<Application>.onCreate()
        // Process lifecycle ignores activity recreation (rotation, resizing) and
        // service-only starts. It fires once for a real return to the foreground.
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        networkWait?.cancel()
        networkWait = null
        if (!AppPreferences.automaticUpdateChecks(this)) return

        // Wi-Fi may still be reconnecting after wake. Listen briefly rather than
        // polling, turning radios on, holding a wake lock or scheduling a retry.
        val wait = NetworkReadyGate(this, handler, 15_000L) { result ->
            networkWait = null
            if (
                result == NetworkReadyGate.Result.VALIDATED &&
                owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            ) {
                UpdateChecker.checkOnForegroundAsync(this, notify = true)
            }
        }
        networkWait = wait
        wait.start()
    }

    override fun onStop(owner: LifecycleOwner) {
        networkWait?.cancel()
        networkWait = null
    }
}
