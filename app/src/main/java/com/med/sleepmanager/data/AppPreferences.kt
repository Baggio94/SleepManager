package com.med.sleepmanager.data

import android.content.Context

object AppPreferences {
    private const val PREFS = "sleep_manager"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_WIFI = "wifi"
    private const val KEY_BLUETOOTH = "bluetooth"
    private const val KEY_SYNCTHING = "syncthing"
    private const val KEY_THOR_PROTECTION = "thor_protection"
    private const val KEY_SLEEP_GRACE_MS = "sleep_grace_ms"
    private const val KEY_SELECTED_SYNCTHING = "selected_syncthing"
    private const val KEY_LAST_EVENT = "last_event"
    private const val KEY_LAST_EVENT_TIME = "last_event_time"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context) = prefs(context).getBoolean(KEY_ENABLED, false)
    fun setEnabled(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_ENABLED, value).apply()

    fun manageWifi(context: Context) = prefs(context).getBoolean(KEY_WIFI, false)
    fun setManageWifi(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_WIFI, value).apply()

    fun manageBluetooth(context: Context) = prefs(context).getBoolean(KEY_BLUETOOTH, false)
    fun setManageBluetooth(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_BLUETOOTH, value).apply()

    fun manageSyncthing(context: Context) = prefs(context).getBoolean(KEY_SYNCTHING, false)
    fun setManageSyncthing(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_SYNCTHING, value).apply()

    fun manageThorProtection(context: Context) =
        prefs(context).getBoolean(KEY_THOR_PROTECTION, false)

    fun setManageThorProtection(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_THOR_PROTECTION, value).apply()

    fun sleepGraceMs(context: Context): Long {
        val value = prefs(context).getLong(KEY_SLEEP_GRACE_MS, 0L)
        return if (value in setOf(0L, 3000L, 5000L, 10000L)) value else 0L
    }

    fun setSleepGraceMs(context: Context, value: Long) {
        val safeValue = if (value in setOf(0L, 3000L, 5000L, 10000L)) value else 0L
        prefs(context).edit().putLong(KEY_SLEEP_GRACE_MS, safeValue).apply()
    }

    fun getSelectedSyncthing(context: Context): String? =
        prefs(context).getString(KEY_SELECTED_SYNCTHING, null)

    fun setSelectedSyncthing(context: Context, packageName: String?) {
        val editor = prefs(context).edit()
        if (packageName.isNullOrBlank()) editor.remove(KEY_SELECTED_SYNCTHING)
        else editor.putString(KEY_SELECTED_SYNCTHING, packageName)
        editor.apply()
    }

    fun recordEvent(context: Context, event: String) {
        prefs(context).edit()
            .putString(KEY_LAST_EVENT, event)
            .putLong(KEY_LAST_EVENT_TIME, System.currentTimeMillis())
            .apply()
    }

    fun lastEvent(context: Context): String =
        prefs(context).getString(KEY_LAST_EVENT, "No activity yet") ?: "No activity yet"

    fun lastEventTime(context: Context): Long =
        prefs(context).getLong(KEY_LAST_EVENT_TIME, 0L)
}
