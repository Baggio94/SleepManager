package com.med.sleepmanager.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

/**
 * Persists lightweight battery measurements for screen-off sleep sessions.
 *
 * A session starts on a real screen-off and ends only on a real wake. Thor
 * closed-lid false wakes never call [finishSession], so they remain part of the
 * same sleep measurement.
 */
object BatterySleepStore {
    private const val PREFS = "battery_sleep_stats"

    private const val KEY_ACTIVE = "active"
    private const val KEY_START_TIME = "start_time"
    private const val KEY_START_PERCENT = "start_percent"
    private const val KEY_START_CHARGE_UAH = "start_charge_uah"
    private const val KEY_START_CHARGING = "start_charging"
    private const val KEY_SAW_CHARGING = "saw_charging"
    private const val KEY_HISTORY = "history"

    private const val HISTORY_DAYS = 7L
    private const val MAX_HISTORY = 96
    private const val MIN_AVERAGE_DURATION_MS = 10L * 60L * 1000L

    data class BatterySnapshot(
        val percent: Int?,
        val charging: Boolean,
        val chargeCounterUah: Int?
    )

    data class SleepSession(
        val startedAt: Long,
        val endedAt: Long,
        val startPercent: Int,
        val endPercent: Int,
        val drainPercent: Int,
        val durationMs: Long,
        val chargedDuringSleep: Boolean,
        val drainMah: Double?
    ) {
        val drainPerHour: Double?
            get() {
                if (chargedDuringSleep || durationMs <= 0L) return null
                return drainPercent.toDouble() / (durationMs.toDouble() / 3_600_000.0)
            }
    }

    data class Dashboard(
        val currentPercent: Int?,
        val currentCharging: Boolean,
        val lastSession: SleepSession?,
        val averageDrainPerHour: Double?,
        val averageSessionCount: Int,
        val sessionActive: Boolean
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun currentSnapshot(context: Context): BatterySnapshot {
        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent =
            if (level >= 0 && scale > 0) {
                ((level * 100f) / scale).toInt().coerceIn(0, 100)
            } else {
                null
            }

        val status =
            batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged =
            batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val charging =
            status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL ||
                plugged != 0

        val batteryManager =
            context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val rawCounter =
            runCatching {
                batteryManager?.getIntProperty(
                    BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER
                ) ?: Int.MIN_VALUE
            }.getOrDefault(Int.MIN_VALUE)
        val chargeCounter =
            rawCounter.takeUnless { it == Int.MIN_VALUE || it < 0 }

        return BatterySnapshot(
            percent = percent,
            charging = charging,
            chargeCounterUah = chargeCounter
        )
    }

    fun beginSession(context: Context) {
        val p = prefs(context)
        if (p.getBoolean(KEY_ACTIVE, false)) return

        val snapshot = currentSnapshot(context)
        val percent = snapshot.percent ?: return

        p.edit()
            .putBoolean(KEY_ACTIVE, true)
            .putLong(KEY_START_TIME, System.currentTimeMillis())
            .putInt(KEY_START_PERCENT, percent)
            .putInt(
                KEY_START_CHARGE_UAH,
                snapshot.chargeCounterUah ?: Int.MIN_VALUE
            )
            .putBoolean(KEY_START_CHARGING, snapshot.charging)
            .putBoolean(KEY_SAW_CHARGING, snapshot.charging)
            .commit()
    }

    fun noteCharging(context: Context) {
        val p = prefs(context)
        if (!p.getBoolean(KEY_ACTIVE, false)) return
        p.edit().putBoolean(KEY_SAW_CHARGING, true).commit()
    }

    fun finishSession(context: Context): SleepSession? {
        val p = prefs(context)
        if (!p.getBoolean(KEY_ACTIVE, false)) return null

        val startedAt = p.getLong(KEY_START_TIME, 0L)
        val startPercent = p.getInt(KEY_START_PERCENT, -1)
        val startChargeUah = p.getInt(KEY_START_CHARGE_UAH, Int.MIN_VALUE)
        val startCharging = p.getBoolean(KEY_START_CHARGING, false)
        val sawCharging = p.getBoolean(KEY_SAW_CHARGING, false)

        val end = currentSnapshot(context)
        val endPercent = end.percent

        clearActiveSession(p)

        if (startedAt <= 0L || startPercent !in 0..100 || endPercent == null) {
            return null
        }

        val endedAt = System.currentTimeMillis()
        val duration = max(0L, endedAt - startedAt)
        val chargedDuringSleep = startCharging || sawCharging || end.charging
        val drainPercent = max(0, startPercent - endPercent)

        val endCounter = end.chargeCounterUah
        val drainMah =
            if (
                !chargedDuringSleep &&
                startChargeUah != Int.MIN_VALUE &&
                endCounter != null &&
                startChargeUah >= endCounter
            ) {
                (startChargeUah - endCounter) / 1000.0
            } else {
                null
            }

        val session = SleepSession(
            startedAt = startedAt,
            endedAt = endedAt,
            startPercent = startPercent,
            endPercent = endPercent,
            drainPercent = drainPercent,
            durationMs = duration,
            chargedDuringSleep = chargedDuringSleep,
            drainMah = drainMah
        )

        appendSession(context, session)
        return session
    }

    fun dashboard(context: Context): Dashboard {
        val current = currentSnapshot(context)
        val sessions = readHistory(context)
        val eligible =
            sessions.filter {
                !it.chargedDuringSleep &&
                    it.durationMs >= MIN_AVERAGE_DURATION_MS &&
                    it.endPercent <= it.startPercent
            }

        val totalDurationHours =
            eligible.sumOf { it.durationMs }.toDouble() / 3_600_000.0
        val totalDrain = eligible.sumOf { it.drainPercent }
        val average =
            if (eligible.isNotEmpty() && totalDurationHours > 0.0) {
                totalDrain / totalDurationHours
            } else {
                null
            }

        return Dashboard(
            currentPercent = current.percent,
            currentCharging = current.charging,
            lastSession = sessions.maxByOrNull { it.endedAt },
            averageDrainPerHour = average,
            averageSessionCount = eligible.size,
            sessionActive = prefs(context).getBoolean(KEY_ACTIVE, false)
        )
    }

    fun clearHistory(context: Context) {
        prefs(context).edit()
            .remove(KEY_HISTORY)
            .commit()
    }

    private fun appendSession(context: Context, session: SleepSession) {
        val now = System.currentTimeMillis()
        val cutoff = now - HISTORY_DAYS * 24L * 60L * 60L * 1000L
        val sessions =
            (readHistory(context) + session)
                .filter { it.endedAt >= cutoff }
                .sortedByDescending { it.endedAt }
                .take(MAX_HISTORY)

        val array = JSONArray()
        sessions.forEach { item ->
            array.put(
                JSONObject()
                    .put("startedAt", item.startedAt)
                    .put("endedAt", item.endedAt)
                    .put("startPercent", item.startPercent)
                    .put("endPercent", item.endPercent)
                    .put("drainPercent", item.drainPercent)
                    .put("durationMs", item.durationMs)
                    .put("chargedDuringSleep", item.chargedDuringSleep)
                    .apply {
                        item.drainMah?.let { put("drainMah", it) }
                    }
            )
        }

        prefs(context).edit()
            .putString(KEY_HISTORY, array.toString())
            .commit()
    }

    private fun readHistory(context: Context): List<SleepSession> {
        val raw = prefs(context).getString(KEY_HISTORY, null) ?: return emptyList()
        val now = System.currentTimeMillis()
        val cutoff = now - HISTORY_DAYS * 24L * 60L * 60L * 1000L

        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val endedAt = item.optLong("endedAt", 0L)
                    if (endedAt < cutoff) continue

                    add(
                        SleepSession(
                            startedAt = item.optLong("startedAt", 0L),
                            endedAt = endedAt,
                            startPercent = item.optInt("startPercent", -1),
                            endPercent = item.optInt("endPercent", -1),
                            drainPercent = item.optInt("drainPercent", 0),
                            durationMs = item.optLong("durationMs", 0L),
                            chargedDuringSleep =
                                item.optBoolean("chargedDuringSleep", false),
                            drainMah =
                                if (item.has("drainMah")) {
                                    item.optDouble("drainMah")
                                } else {
                                    null
                                }
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun clearActiveSession(
        prefs: android.content.SharedPreferences
    ) {
        prefs.edit()
            .remove(KEY_ACTIVE)
            .remove(KEY_START_TIME)
            .remove(KEY_START_PERCENT)
            .remove(KEY_START_CHARGE_UAH)
            .remove(KEY_START_CHARGING)
            .remove(KEY_SAW_CHARGING)
            .commit()
    }
}
