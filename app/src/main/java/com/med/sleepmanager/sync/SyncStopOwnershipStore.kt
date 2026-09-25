package com.med.sleepmanager.sync

import android.content.Context
import android.util.Log
import com.med.sleepmanager.integration.BasicSyncController
import com.med.sleepmanager.integration.connector.BasicSyncConnector

internal enum class OwnedBasicSyncRestoreResult {
    NOT_OWNED,
    RESTORED,
    RELINQUISHED,
    PENDING,
    FAILED
}

internal fun basicSyncRestoreTokenForState(
    state: BasicSyncController.RemoteState
): String =
    when (state.mode) {
        BasicSyncController.Mode.AUTO_MODE ->
            BasicSyncConnector.TOKEN_AUTO_MODE

        BasicSyncController.Mode.MANUAL_MODE_STARTED ->
            BasicSyncConnector.TOKEN_MANUAL_MODE_STARTED

        BasicSyncController.Mode.MANUAL_MODE_STOPPED ->
            BasicSyncConnector.TOKEN_MANUAL_MODE_STOPPED
    }

internal fun basicSyncStillInManagedStopState(
    state: BasicSyncController.RemoteState
): Boolean =
    state.mode == BasicSyncController.Mode.MANUAL_MODE_STOPPED &&
        state.runState in setOf(
            BasicSyncController.RunState.NOT_RUNNING,
            BasicSyncController.RunState.PAUSED,
            BasicSyncController.RunState.STOPPING
        )

/**
 * Persists the BasicSync state that existed before Sync then stop first took
 * ownership. The saved state is restored only while BasicSync is still in the
 * MANUAL_MODE_STOPPED state produced by SleepManager. If the user changes the
 * mode manually, ownership is relinquished instead of overwriting that choice.
 */
object SyncStopOwnershipStore {
    private const val TAG = "SleepManagerSyncOwner"
    private const val PREFS = "sync_stop_ownership"
    private const val KEY_BASIC_SYNC_RESTORE_TOKEN = "basic_sync_restore_token"
    private const val KEY_BASIC_SYNC_CAPTURED_AT = "basic_sync_captured_at"

    private fun prefs(context: Context) =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasBasicSyncOwnership(context: Context): Boolean =
        prefs(context).contains(KEY_BASIC_SYNC_RESTORE_TOKEN)

    fun captureBasicSyncIfNeeded(context: Context): Boolean {
        if (hasBasicSyncOwnership(context)) return true

        val state =
            BasicSyncController.lastObservedState()
                ?: run {
                    BasicSyncController.requestStateBroadcast(context)
                    Log.i(TAG, "BasicSync ownership capture waiting for fresh state")
                    return false
                }

        val token = basicSyncRestoreTokenForState(state)
        prefs(context).edit()
            .putString(KEY_BASIC_SYNC_RESTORE_TOKEN, token)
            .putLong(KEY_BASIC_SYNC_CAPTURED_AT, System.currentTimeMillis())
            .commit()

        Log.i(
            TAG,
            "Captured BasicSync original state: mode=${state.mode} runState=${state.runState}"
        )
        return true
    }

    internal fun restoreBasicSyncIfOwned(context: Context): OwnedBasicSyncRestoreResult {
        val token =
            prefs(context).getString(KEY_BASIC_SYNC_RESTORE_TOKEN, null)
                ?: return OwnedBasicSyncRestoreResult.NOT_OWNED

        if (!BasicSyncController.isInstalled(context)) {
            clearBasicSync(context)
            Log.i(TAG, "BasicSync removed; ownership cleared")
            return OwnedBasicSyncRestoreResult.RELINQUISHED
        }

        val state =
            BasicSyncController.lastObservedState()
                ?: run {
                    BasicSyncController.requestStateBroadcast(context)
                    return OwnedBasicSyncRestoreResult.PENDING
                }

        if (!basicSyncStillInManagedStopState(state)) {
            clearBasicSync(context)
            Log.i(
                TAG,
                "BasicSync state changed outside SleepManager; ownership relinquished: " +
                    "mode=${state.mode} runState=${state.runState}"
            )
            return OwnedBasicSyncRestoreResult.RELINQUISHED
        }

        if (token == BasicSyncConnector.TOKEN_MANUAL_MODE_STOPPED) {
            clearBasicSync(context)
            Log.i(TAG, "BasicSync was originally manually stopped; ownership cleared")
            return OwnedBasicSyncRestoreResult.RESTORED
        }

        val result = BasicSyncConnector.wake(context, token)
        return if (result.success) {
            clearBasicSync(context)
            Log.i(
                TAG,
                "Restored BasicSync original state: " +
                    BasicSyncConnector.restoreTargetName(token)
            )
            OwnedBasicSyncRestoreResult.RESTORED
        } else {
            Log.w(
                TAG,
                "BasicSync original state restore failed: ${result.detail}"
            )
            OwnedBasicSyncRestoreResult.FAILED
        }
    }

    fun clearBasicSync(context: Context) {
        prefs(context).edit()
            .remove(KEY_BASIC_SYNC_RESTORE_TOKEN)
            .remove(KEY_BASIC_SYNC_CAPTURED_AT)
            .commit()
    }
}
