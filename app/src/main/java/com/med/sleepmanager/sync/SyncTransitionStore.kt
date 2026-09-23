package com.med.sleepmanager.sync

import android.content.Context

/**
 * Persists whether the current managed sleep session earned a wake-sync
 * transition. A wake sync is therefore tied to a sleep session that actually
 * passed Advanced sleep conditions instead of to any later SCREEN_ON event.
 */
object SyncTransitionStore {
    private const val PREFS = "sync_transition_state"
    private const val KEY_WAKE_SYNC_ARMED = "wake_sync_armed"
    private const val KEY_ARMED_AT = "wake_sync_armed_at"

    fun armWakeSync(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_WAKE_SYNC_ARMED, true)
            .putLong(KEY_ARMED_AT, System.currentTimeMillis())
            .apply()
    }

    fun isWakeSyncArmed(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_WAKE_SYNC_ARMED, false)

    fun clear(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_WAKE_SYNC_ARMED)
            .remove(KEY_ARMED_AT)
            .apply()
    }
}
