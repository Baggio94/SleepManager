package com.med.sleepmanager.sync

import com.med.sleepmanager.integration.BasicSyncController
import com.med.sleepmanager.integration.connector.BasicSyncConnector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncStopOwnershipPolicyTest {

    @Test
    fun capturesAutoModeAsAutoRestore() {
        val state =
            BasicSyncController.RemoteState(
                mode = BasicSyncController.Mode.AUTO_MODE,
                runState = BasicSyncController.RunState.RUNNING
            )

        assertEquals(
            BasicSyncConnector.TOKEN_AUTO_MODE,
            basicSyncRestoreTokenForState(state)
        )
    }

    @Test
    fun capturesManualStartedAsStartRestore() {
        val state =
            BasicSyncController.RemoteState(
                mode = BasicSyncController.Mode.MANUAL_MODE_STARTED,
                runState = BasicSyncController.RunState.RUNNING
            )

        assertEquals(
            BasicSyncConnector.TOKEN_MANUAL_MODE_STARTED,
            basicSyncRestoreTokenForState(state)
        )
    }

    @Test
    fun capturesManualStoppedWithoutInventingAStart() {
        val state =
            BasicSyncController.RemoteState(
                mode = BasicSyncController.Mode.MANUAL_MODE_STOPPED,
                runState = BasicSyncController.RunState.NOT_RUNNING
            )

        assertEquals(
            BasicSyncConnector.TOKEN_MANUAL_MODE_STOPPED,
            basicSyncRestoreTokenForState(state)
        )
    }

    @Test
    fun stoppedStateRemainsOwned() {
        assertTrue(
            basicSyncStillInManagedStopState(
                BasicSyncController.RemoteState(
                    mode = BasicSyncController.Mode.MANUAL_MODE_STOPPED,
                    runState = BasicSyncController.RunState.NOT_RUNNING
                )
            )
        )

        assertTrue(
            basicSyncStillInManagedStopState(
                BasicSyncController.RemoteState(
                    mode = BasicSyncController.Mode.MANUAL_MODE_STOPPED,
                    runState = BasicSyncController.RunState.PAUSED
                )
            )
        )

        assertTrue(
            basicSyncStillInManagedStopState(
                BasicSyncController.RemoteState(
                    mode = BasicSyncController.Mode.MANUAL_MODE_STOPPED,
                    runState = BasicSyncController.RunState.STOPPING
                )
            )
        )
    }

    @Test
    fun userStateChangeRelinquishesOwnership() {
        assertFalse(
            basicSyncStillInManagedStopState(
                BasicSyncController.RemoteState(
                    mode = BasicSyncController.Mode.AUTO_MODE,
                    runState = BasicSyncController.RunState.RUNNING
                )
            )
        )

        assertFalse(
            basicSyncStillInManagedStopState(
                BasicSyncController.RemoteState(
                    mode = BasicSyncController.Mode.MANUAL_MODE_STARTED,
                    runState = BasicSyncController.RunState.RUNNING
                )
            )
        )
    }
}
