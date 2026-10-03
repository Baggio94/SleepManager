package com.med.sleepmanager.data

import android.content.Context

/**
 * Tiny sidecar for the pre-sleep network snapshot used by periodic sync.
 *
 * Kept separate from SleepCycleStore so its long-lived ABI remains compatible
 * with stable-main/helper matrix tests. A cycle-id mismatch always fails open.
 */
object SleepNetworkStateStore {
    private const val PREFS = "sleep_network_state"
    private const val KEY_CYCLE_ID = "cycle_id"
    private const val KEY_NETWORK_AVAILABLE_BEFORE_SLEEP =
        "network_available_before_sleep"

    private fun prefs(context: Context) =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun record(
        context: Context,
        cycleId: Long,
        networkAvailableBeforeSleep: Boolean
    ) {
        prefs(context).edit()
            .putLong(KEY_CYCLE_ID, cycleId)
            .putBoolean(
                KEY_NETWORK_AVAILABLE_BEFORE_SLEEP,
                networkAvailableBeforeSleep
            )
            .commit()
    }

    fun networkAvailableBeforeSleep(
        context: Context,
        cycleId: Long
    ): Boolean? {
        if (cycleId == 0L) return null

        val p = prefs(context)
        if (p.getLong(KEY_CYCLE_ID, 0L) != cycleId) return null
        if (!p.contains(KEY_NETWORK_AVAILABLE_BEFORE_SLEEP)) return null

        return p.getBoolean(
            KEY_NETWORK_AVAILABLE_BEFORE_SLEEP,
            true
        )
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().commit()
    }
}
