package com.med.sleepmanager.data

import android.content.Context

object AppPreferences {
    private const val PREFS = "sleep_manager"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_WIFI = "wifi"
    private const val KEY_BLUETOOTH = "bluetooth"
    private const val KEY_SYNCTHING = "syncthing"
    private const val KEY_TAILSCALE = "tailscale"
    private const val KEY_THOR_PROTECTION = "thor_protection"
    private const val KEY_THOR_LID_CLOSED_LAST_KNOWN = "thor_lid_closed_last_known"
    private const val KEY_THOR_LID_STATE_KNOWN = "thor_lid_state_known"
    private const val KEY_SLEEP_GRACE_MS = "sleep_grace_ms"
    private const val KEY_CUSTOM_DELAY_ENABLED = "custom_delay_enabled"
    private const val KEY_CUSTOM_DELAY_MS = "custom_delay_ms"
    private const val KEY_BATTERY_CONDITION_ENABLED = "battery_condition_enabled"
    private const val KEY_BATTERY_BELOW_PERCENT = "battery_below_percent"
    private const val KEY_NOT_CHARGING_ONLY = "not_charging_only"
    private const val KEY_BATTERY_SAVER_MODE = "battery_saver_mode"
    private const val KEY_SCHEDULE_ENABLED = "schedule_enabled"
    private const val KEY_SCHEDULE_START_MINUTES = "schedule_start_minutes"
    private const val KEY_SCHEDULE_END_MINUTES = "schedule_end_minutes"
    private const val KEY_SETUP_COMPLETE = "setup_complete"
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

    fun manageTailscale(context: Context) =
        prefs(context).getBoolean(KEY_TAILSCALE, false)

    fun setManageTailscale(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_TAILSCALE, value).apply()

    fun manageThorProtection(context: Context) =
        prefs(context).getBoolean(KEY_THOR_PROTECTION, false)

    fun setManageThorProtection(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_THOR_PROTECTION, value).apply()

    fun lastKnownThorLidClosed(context: Context): Boolean? {
        val p = prefs(context)
        if (!p.getBoolean(KEY_THOR_LID_STATE_KNOWN, false)) return null
        return p.getBoolean(KEY_THOR_LID_CLOSED_LAST_KNOWN, false)
    }

    fun setLastKnownThorLidClosed(context: Context, closed: Boolean) =
        prefs(context).edit()
            .putBoolean(KEY_THOR_LID_CLOSED_LAST_KNOWN, closed)
            .putBoolean(KEY_THOR_LID_STATE_KNOWN, true)
            .commit()

    fun sleepGraceMs(context: Context): Long {
        val value = prefs(context).getLong(KEY_SLEEP_GRACE_MS, 0L)
        return if (value in setOf(0L, 3000L, 5000L, 10000L)) value else 0L
    }

    fun setSleepGraceMs(context: Context, value: Long) {
        val safeValue = if (value in setOf(0L, 3000L, 5000L, 10000L)) value else 0L
        prefs(context).edit().putLong(KEY_SLEEP_GRACE_MS, safeValue).apply()
    }

    fun customDelayEnabled(context: Context) =
        prefs(context).getBoolean(KEY_CUSTOM_DELAY_ENABLED, false)

    fun setCustomDelayEnabled(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_CUSTOM_DELAY_ENABLED, value).apply()

    fun customDelayMs(context: Context): Long {
        val value = prefs(context).getLong(KEY_CUSTOM_DELAY_MS, 60_000L)
        return if (value in CUSTOM_DELAY_VALUES) value else 60_000L
    }

    fun setCustomDelayMs(context: Context, value: Long) {
        val safeValue = if (value in CUSTOM_DELAY_VALUES) value else 60_000L
        prefs(context).edit().putLong(KEY_CUSTOM_DELAY_MS, safeValue).apply()
    }

    fun effectiveSleepDelayMs(context: Context): Long =
        if (customDelayEnabled(context)) customDelayMs(context) else sleepGraceMs(context)

    fun batteryConditionEnabled(context: Context) =
        prefs(context).getBoolean(KEY_BATTERY_CONDITION_ENABLED, false)

    fun setBatteryConditionEnabled(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_BATTERY_CONDITION_ENABLED, value).apply()

    fun batteryBelowPercent(context: Context): Int =
        prefs(context).getInt(KEY_BATTERY_BELOW_PERCENT, 30).coerceIn(5, 95)

    fun setBatteryBelowPercent(context: Context, value: Int) =
        prefs(context).edit().putInt(KEY_BATTERY_BELOW_PERCENT, value.coerceIn(5, 95)).apply()

    fun notChargingOnly(context: Context) =
        prefs(context).getBoolean(KEY_NOT_CHARGING_ONLY, false)

    fun setNotChargingOnly(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_NOT_CHARGING_ONLY, value).apply()

    fun batterySaverMode(context: Context): String {
        val value = prefs(context).getString(KEY_BATTERY_SAVER_MODE, BATTERY_SAVER_IGNORE)
        return if (value in BATTERY_SAVER_MODES) value!! else BATTERY_SAVER_IGNORE
    }

    fun setBatterySaverMode(context: Context, value: String) {
        val safeValue = if (value in BATTERY_SAVER_MODES) value else BATTERY_SAVER_IGNORE
        prefs(context).edit().putString(KEY_BATTERY_SAVER_MODE, safeValue).apply()
    }

    fun scheduleEnabled(context: Context) =
        prefs(context).getBoolean(KEY_SCHEDULE_ENABLED, false)

    fun setScheduleEnabled(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_SCHEDULE_ENABLED, value).apply()

    fun scheduleStartMinutes(context: Context): Int =
        prefs(context).getInt(KEY_SCHEDULE_START_MINUTES, 23 * 60).coerceIn(0, 1439)

    fun setScheduleStartMinutes(context: Context, value: Int) =
        prefs(context).edit().putInt(KEY_SCHEDULE_START_MINUTES, value.coerceIn(0, 1439)).apply()

    fun scheduleEndMinutes(context: Context): Int =
        prefs(context).getInt(KEY_SCHEDULE_END_MINUTES, 7 * 60).coerceIn(0, 1439)

    fun setScheduleEndMinutes(context: Context, value: Int) =
        prefs(context).edit().putInt(KEY_SCHEDULE_END_MINUTES, value.coerceIn(0, 1439)).apply()

    fun hasAdvancedConditions(context: Context): Boolean =
        batteryConditionEnabled(context) ||
            notChargingOnly(context) ||
            batterySaverMode(context) != BATTERY_SAVER_IGNORE ||
            scheduleEnabled(context)

    const val BATTERY_SAVER_IGNORE = "ignore"
    const val BATTERY_SAVER_ON = "on"
    const val BATTERY_SAVER_OFF = "off"

    private val CUSTOM_DELAY_VALUES =
        setOf(60_000L, 300_000L, 600_000L, 1_800_000L)
    private val BATTERY_SAVER_MODES =
        setOf(BATTERY_SAVER_IGNORE, BATTERY_SAVER_ON, BATTERY_SAVER_OFF)

    fun isSetupComplete(context: Context) =
        prefs(context).getBoolean(KEY_SETUP_COMPLETE, false)

    fun setSetupComplete(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_SETUP_COMPLETE, value).apply()

    fun getSelectedSyncthing(context: Context): String? =
        prefs(context).getString(KEY_SELECTED_SYNCTHING, null)

    fun setSelectedSyncthing(context: Context, packageName: String?) {
        val editor = prefs(context).edit()
        if (packageName.isNullOrBlank()) editor.remove(KEY_SELECTED_SYNCTHING)
        else editor.putString(KEY_SELECTED_SYNCTHING, packageName)
        editor.apply()
    }

    fun recordEvent(context: Context, event: String) {
        val now = System.currentTimeMillis()
        prefs(context).edit()
            .putString(KEY_LAST_EVENT, event)
            .putLong(KEY_LAST_EVENT_TIME, now)
            .apply()
        EventHistoryStore.record(context, event, now)
    }

    fun lastEvent(context: Context): String =
        prefs(context).getString(KEY_LAST_EVENT, "No activity yet") ?: "No activity yet"

    fun lastEventTime(context: Context): Long =
        prefs(context).getLong(KEY_LAST_EVENT_TIME, 0L)
}
