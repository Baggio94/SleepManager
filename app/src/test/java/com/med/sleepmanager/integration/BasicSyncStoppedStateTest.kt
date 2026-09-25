package com.med.sleepmanager.integration

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BasicSyncStoppedStateTest {
    @Test
    fun manualStoppedPausedIsConfirmedStopped() {
        val state = BasicSyncController.RemoteState(
            mode = BasicSyncController.Mode.MANUAL_MODE_STOPPED,
            runState = BasicSyncController.RunState.PAUSED
        )

        assertTrue(BasicSyncController.isConfirmedStoppedState(state))
    }

    @Test
    fun manualStoppedNotRunningIsConfirmedStopped() {
        val state = BasicSyncController.RemoteState(
            mode = BasicSyncController.Mode.MANUAL_MODE_STOPPED,
            runState = BasicSyncController.RunState.NOT_RUNNING
        )

        assertTrue(BasicSyncController.isConfirmedStoppedState(state))
    }

    @Test
    fun runningManualModeIsNotConfirmedStopped() {
        val state = BasicSyncController.RemoteState(
            mode = BasicSyncController.Mode.MANUAL_MODE_STARTED,
            runState = BasicSyncController.RunState.RUNNING
        )

        assertFalse(BasicSyncController.isConfirmedStoppedState(state))
    }
}
