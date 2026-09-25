package com.med.sleepmanager.integration

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BasicSyncBroadcastContractTest {
    @Test
    fun parsesBasicSync319StateChangedPayload() {
        val intent = Intent("com.chiller3.basicsync.STATE_CHANGED")
            .putExtra("mode", "MANUAL_MODE_STARTED")
            .putExtra("run_state", "RUNNING")
            .putExtra("blocked_reasons", arrayOf("METERED_NETWORK"))
            .putExtra("folders_idle_count", 2)
            .putExtra("folders_scanning_count", 1)
            .putExtra("folders_syncing_count", 3)
            .putExtra("folders_cleaning_count", 4)
            .putExtra("folders_errored_count", 5)
            .putExtra("folders_starting_count", 6)
            .putExtra("devices_connected_count", 7)
            .putExtra("devices_syncing_count", 8)
            .putExtra("devices_pending_count", 9)

        val state = BasicSyncController.parseState(intent)

        assertNotNull(state)
        state!!

        assertEquals(BasicSyncController.Mode.MANUAL_MODE_STARTED, state.mode)
        assertEquals(BasicSyncController.RunState.RUNNING, state.runState)
        assertEquals(setOf("METERED_NETWORK"), state.blockedReasons)

        val counters = state.syncCounters
        assertNotNull(counters)
        counters!!

        assertEquals(2, counters.foldersIdle)
        assertEquals(1, counters.foldersScanning)
        assertEquals(3, counters.foldersSyncing)
        assertEquals(4, counters.foldersCleaning)
        assertEquals(5, counters.foldersErrored)
        assertEquals(6, counters.foldersStarting)
        assertEquals(7, counters.devicesConnected)
        assertEquals(8, counters.devicesSyncing)
        assertEquals(9, counters.devicesPending)
    }

    @Test
    fun missing319CountersRemainUnavailable() {
        val intent = Intent("com.chiller3.basicsync.STATE_CHANGED")
            .putExtra("mode", "AUTO_MODE")
            .putExtra("run_state", "RUNNING")

        val state = BasicSyncController.parseState(intent)

        assertNotNull(state)
        assertNull(state!!.syncCounters)
    }

    @Test
    fun ignoresWrongBroadcastAction() {
        val intent = Intent("com.example.WRONG")
            .putExtra("mode", "AUTO_MODE")
            .putExtra("run_state", "RUNNING")

        assertNull(BasicSyncController.parseState(intent))
    }
}
