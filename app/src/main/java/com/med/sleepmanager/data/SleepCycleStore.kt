package com.med.sleepmanager.data

import android.content.Context

object SleepCycleStore {
    private const val PREFS = "sleep_cycle_state"

    private const val KEY_ACTIVE = "active"
    private const val KEY_CYCLE_ID = "cycle_id"
    private const val KEY_STARTED_AT = "started_at"
    private const val KEY_HELPER_EXPECTED = "helper_expected"
    private const val KEY_HELPER_RESTORED = "helper_restored"

    data class ConnectorChange(
        val connectorId: String,
        val restoreToken: String?
    )

    data class Snapshot(
        val active: Boolean,
        val cycleId: Long,
        val startedAt: Long,
        val helperExpected: Boolean,
        val helperRestored: Boolean
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun begin(context: Context, helperExpected: Boolean): Snapshot {
        val now = System.currentTimeMillis()
        prefs(context).edit()
            .clear()
            .putBoolean(KEY_ACTIVE, true)
            .putLong(KEY_CYCLE_ID, now)
            .putLong(KEY_STARTED_AT, now)
            .putBoolean(KEY_HELPER_EXPECTED, helperExpected)
            .putBoolean(KEY_HELPER_RESTORED, !helperExpected)
            .commit()

        return current(context)
    }

    fun current(context: Context): Snapshot {
        val p = prefs(context)
        return Snapshot(
            active = p.getBoolean(KEY_ACTIVE, false),
            cycleId = p.getLong(KEY_CYCLE_ID, 0L),
            startedAt = p.getLong(KEY_STARTED_AT, 0L),
            helperExpected = p.getBoolean(KEY_HELPER_EXPECTED, false),
            helperRestored = p.getBoolean(KEY_HELPER_RESTORED, true)
        )
    }

    fun isActive(context: Context): Boolean = current(context).active

    fun markHelperRestored(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_HELPER_RESTORED, true)
            .commit()
    }

    fun recordConnectorChange(
        context: Context,
        connectorId: String,
        restoreToken: String?
    ) {
        prefs(context).edit()
            .putBoolean(connectorChangedKey(connectorId), true)
            .apply {
                if (restoreToken == null) remove(connectorTokenKey(connectorId))
                else putString(connectorTokenKey(connectorId), restoreToken)
            }
            .commit()
    }

    fun connectorChange(context: Context, connectorId: String): ConnectorChange? {
        val p = prefs(context)
        if (!p.getBoolean(connectorChangedKey(connectorId), false)) return null
        return ConnectorChange(
            connectorId = connectorId,
            restoreToken = p.getString(connectorTokenKey(connectorId), null)
        )
    }

    fun clearConnectorChange(context: Context, connectorId: String) {
        prefs(context).edit()
            .remove(connectorChangedKey(connectorId))
            .remove(connectorTokenKey(connectorId))
            .commit()
    }

    fun hasConnectorChange(context: Context, connectorId: String): Boolean =
        prefs(context).getBoolean(connectorChangedKey(connectorId), false)

    fun completeIfRestored(context: Context): Boolean {
        val snapshot = current(context)
        if (!snapshot.active) return true

        val helperDone = !snapshot.helperExpected || snapshot.helperRestored
        val syncthingDone = !hasConnectorChange(context, "syncthing")

        if (helperDone && syncthingDone) {
            clear(context)
            return true
        }
        return false
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().commit()
    }

    private fun connectorChangedKey(id: String) = "connector.$id.changed"
    private fun connectorTokenKey(id: String) = "connector.$id.token"
}
