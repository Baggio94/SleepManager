package com.med.sleepmanager.sync

import android.content.Context
import com.med.sleepmanager.data.AppPreferences
import com.med.sleepmanager.integration.BasicSyncController
import com.med.sleepmanager.integration.SyncthingController

enum class SyncCompletionState {
    SYNCING,
    SYNCED,
    UNKNOWN
}

data class SyncControlResult(
    val attempted: Boolean,
    val success: Boolean,
    val detail: String
)

interface SyncCompletionProvider {
    val id: String
    val displayName: String

    fun isAvailable(context: Context): Boolean

    /**
     * Force the managed client into a running state for a maintenance sync.
     * This does not imply that synchronization has completed.
     */
    fun startSync(context: Context): SyncControlResult

    /**
     * Force the managed client to stop after maintenance work.
     */
    fun stopSync(context: Context): SyncControlResult

    /**
     * Must describe synchronization completion, not merely process/runtime state.
     *
     * Until the external apps expose a reliable completion signal, providers
     * intentionally return UNKNOWN rather than treating RUNNING as SYNCING or SYNCED.
     */
    fun currentSyncState(context: Context): SyncCompletionState
}

object BasicSyncCompletionProvider : SyncCompletionProvider {
    override val id: String = "basicsync"
    override val displayName: String = "BasicSync"

    override fun isAvailable(context: Context): Boolean =
        BasicSyncController.isInstalled(context)

    override fun startSync(context: Context): SyncControlResult {
        val sent = BasicSyncController.sendStart(context)
        return SyncControlResult(
            attempted = isAvailable(context),
            success = sent,
            detail = if (sent) "START sent" else "START not sent"
        )
    }

    override fun stopSync(context: Context): SyncControlResult {
        val sent = BasicSyncController.sendStop(context)
        return SyncControlResult(
            attempted = isAvailable(context),
            success = sent,
            detail = if (sent) "STOP sent" else "STOP not sent"
        )
    }

    override fun currentSyncState(context: Context): SyncCompletionState =
        SyncCompletionState.UNKNOWN
}

object SyncthingCompletionProvider : SyncCompletionProvider {
    override val id: String = "syncthing"
    override val displayName: String = "Syncthing-Fork"

    override fun isAvailable(context: Context): Boolean =
        SyncthingController.selectedTarget(context) != null

    override fun startSync(context: Context): SyncControlResult {
        val target = SyncthingController.selectedTarget(context)
            ?: return SyncControlResult(
                attempted = false,
                success = false,
                detail = "No Syncthing target installed"
            )

        val sent = SyncthingController.sendStartTo(context, target.packageName)
        return SyncControlResult(
            attempted = true,
            success = sent,
            detail = if (sent) "START sent" else "START not sent"
        )
    }

    override fun stopSync(context: Context): SyncControlResult {
        val target = SyncthingController.selectedTarget(context)
            ?: return SyncControlResult(
                attempted = false,
                success = false,
                detail = "No Syncthing target installed"
            )

        val sent = SyncthingController.sendStopTo(context, target.packageName)
        return SyncControlResult(
            attempted = true,
            success = sent,
            detail = if (sent) "STOP sent" else "STOP not sent"
        )
    }

    override fun currentSyncState(context: Context): SyncCompletionState =
        SyncCompletionState.UNKNOWN
}

object ManagedSyncProviders {
    fun selected(context: Context): List<SyncCompletionProvider> = buildList {
        if (
            AppPreferences.manageSyncthing(context) &&
            SyncthingCompletionProvider.isAvailable(context)
        ) {
            add(SyncthingCompletionProvider)
        }

        if (
            AppPreferences.manageBasicSync(context) &&
            BasicSyncCompletionProvider.isAvailable(context)
        ) {
            add(BasicSyncCompletionProvider)
        }
    }
}
