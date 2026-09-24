package com.med.sleepmanager.sync

import com.med.sleepmanager.integration.BasicSyncController
import org.junit.Assert.assertEquals
import org.junit.Test

class BasicSyncCompletionStateTest {
    private fun counters(
        foldersIdle: Int = 1,
        foldersScanning: Int = 0,
        foldersSyncing: Int = 0,
        foldersCleaning: Int = 0,
        foldersErrored: Int = 0,
        foldersStarting: Int = 0,
        devicesConnected: Int = 1,
        devicesSyncing: Int = 0,
        devicesPending: Int = 0
    ) = BasicSyncController.SyncCounters(
        foldersIdle = foldersIdle,
        foldersScanning = foldersScanning,
        foldersSyncing = foldersSyncing,
        foldersCleaning = foldersCleaning,
        foldersErrored = foldersErrored,
        foldersStarting = foldersStarting,
        devicesConnected = devicesConnected,
        devicesSyncing = devicesSyncing,
        devicesPending = devicesPending
    )

    private fun state(
        runState: BasicSyncController.RunState =
            BasicSyncController.RunState.RUNNING,
        blockedReasons: Set<String> = emptySet(),
        counters: BasicSyncController.SyncCounters? = counters()
    ) = BasicSyncController.RemoteState(
        mode = BasicSyncController.Mode.MANUAL_MODE_STARTED,
        runState = runState,
        blockedReasons = blockedReasons,
        syncCounters = counters
    )

    @Test
    fun activeFolderWorkIsSyncing() {
        val result = basicSyncCompletionState(
            state(counters = counters(foldersScanning = 1))
        )

        assertEquals(SyncCompletionState.SYNCING, result)
    }

    @Test
    fun pendingDeviceWorkIsSyncing() {
        val result = basicSyncCompletionState(
            state(counters = counters(devicesPending = 1))
        )

        assertEquals(SyncCompletionState.SYNCING, result)
    }

    @Test
    fun idleFoldersWithConnectedDeviceAreSynced() {
        val result = basicSyncCompletionState(state())

        assertEquals(SyncCompletionState.SYNCED, result)
    }

    @Test
    fun noConnectedDeviceIsUnknown() {
        val result = basicSyncCompletionState(
            state(counters = counters(devicesConnected = 0))
        )

        assertEquals(SyncCompletionState.UNKNOWN, result)
    }

    @Test
    fun folderErrorIsUnknown() {
        val result = basicSyncCompletionState(
            state(counters = counters(foldersErrored = 1))
        )

        assertEquals(SyncCompletionState.UNKNOWN, result)
    }

    @Test
    fun blockedStateIsUnknown() {
        val result = basicSyncCompletionState(
            state(blockedReasons = setOf("DISCONNECTED"))
        )

        assertEquals(SyncCompletionState.UNKNOWN, result)
    }

    @Test
    fun nonRunningStateIsUnknown() {
        val result = basicSyncCompletionState(
            state(runState = BasicSyncController.RunState.STOPPING)
        )

        assertEquals(SyncCompletionState.UNKNOWN, result)
    }

    @Test
    fun missingCountersAreUnknown() {
        val result = basicSyncCompletionState(
            state(counters = null)
        )

        assertEquals(SyncCompletionState.UNKNOWN, result)
    }
}
