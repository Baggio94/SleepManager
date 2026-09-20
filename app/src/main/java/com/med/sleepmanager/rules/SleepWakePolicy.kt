package com.med.sleepmanager.rules

object SleepWakePolicy {
    data class WakeDecision(
        val suppressWake: Boolean,
        val cancelSleepDelay: Boolean,
        val restoreNormalWake: Boolean,
        val requestThorLock: Boolean
    )

    fun onScreenOn(
        thorProtectionEnabled: Boolean,
        lidClosed: Boolean,
        sleepDelayPending: Boolean
    ): WakeDecision {
        val suppress = thorProtectionEnabled && lidClosed
        return WakeDecision(
            suppressWake = suppress,
            cancelSleepDelay = !suppress && sleepDelayPending,
            restoreNormalWake = !suppress,
            requestThorLock = suppress
        )
    }
}
