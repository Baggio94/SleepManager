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
    val completionStateAvailable: Boolean

    /**
     * Force the managed client into a running state for a maintenance sync.
     * This does not imply that synchronization has completed.
     */
    fun startSync(): SyncControlResult

    /**
     * Force the managed client to stop after maintenance work.
     */
    fun stopSync(): SyncControlResult

    /**
     * Must describe synchronization completion, not merely process/runtime state.
     *
     * Until the external apps expose a reliable completion signal, production
     * providers intentionally return UNKNOWN rather than treating RUNNING as
     * SYNCING or SYNCED.
     */
    fun currentSyncState(): SyncCompletionState
}

class BasicSyncCompletionProvider(
    context: Context
) : SyncCompletionProvider {
    private val appContext = context.applicationContext

    override val id: String = "basicsync"
    override val displayName: String = "BasicSync"
    override val completionStateAvailable: Boolean = false

    override fun startSync(): SyncControlResult {
        val installed = BasicSyncController.isInstalled(appContext)
        val sent = installed && BasicSyncController.sendStart(appContext)
        return SyncControlResult(
            attempted = installed,
            success = sent,
            detail = if (sent) "START sent" else "START not sent"
        )
    }

    override fun stopSync(): SyncControlResult {
        val installed = BasicSyncController.isInstalled(appContext)
        val sent = installed && BasicSyncController.sendStop(appContext)
        return SyncControlResult(
            attempted = installed,
            success = sent,
            detail = if (sent) "STOP sent" else "STOP not sent"
        )
    }

    override fun currentSyncState(): SyncCompletionState =
        SyncCompletionState.UNKNOWN
}

class SyncthingCompletionProvider(
    context: Context
) : SyncCompletionProvider {
    private val appContext = context.applicationContext
    // Keep one target for the whole maintenance session even if the UI selection
    // changes while the operation is running.
    private val target = SyncthingController.selectedTarget(appContext)

    override val id: String = "syncthing"
    override val displayName: String = "Syncthing-Fork"
    override val completionStateAvailable: Boolean = false

    override fun startSync(): SyncControlResult {
        val packageName = target?.packageName
            ?: return SyncControlResult(
                attempted = false,
                success = false,
                detail = "No Syncthing target installed"
            )

        val sent = SyncthingController.sendStartTo(appContext, packageName)
        return SyncControlResult(
            attempted = true,
            success = sent,
            detail = if (sent) "START sent" else "START not sent"
        )
    }

    override fun stopSync(): SyncControlResult {
        val packageName = target?.packageName
            ?: return SyncControlResult(
                attempted = false,
                success = false,
                detail = "No Syncthing target installed"
            )

        val sent = SyncthingController.sendStopTo(appContext, packageName)
        return SyncControlResult(
            attempted = true,
            success = sent,
            detail = if (sent) "STOP sent" else "STOP not sent"
        )
    }

    override fun currentSyncState(): SyncCompletionState =
        SyncCompletionState.UNKNOWN
}

object ManagedSyncProviders {
    fun selected(context: Context): List<SyncCompletionProvider> = buildList {
        if (
            AppPreferences.manageSyncthing(context) &&
            SyncthingController.selectedTarget(context) != null
        ) {
            add(SyncthingCompletionProvider(context))
        }

        if (
            AppPreferences.manageBasicSync(context) &&
            BasicSyncController.isInstalled(context)
        ) {
            add(BasicSyncCompletionProvider(context))
        }
    }
}
