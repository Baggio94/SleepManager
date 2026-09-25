package com.med.sleepmanager.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMaintenancePolicyTest {
    @Test
    fun completionRequiresAtLeastOneSelectedProvider() {
        assertFalse(
            SyncMaintenancePolicy.completionReady(
                selectedProviderCount = 0,
                allSelectedProvidersCompletionAware = true
            )
        )
    }

    @Test
    fun oneNonCompletionAwareProviderBlocksMaintenance() {
        assertFalse(
            SyncMaintenancePolicy.completionReady(
                selectedProviderCount = 2,
                allSelectedProvidersCompletionAware = false
            )
        )
    }

    @Test
    fun completionReadyWhenEverySelectedProviderSupportsIt() {
        assertTrue(
            SyncMaintenancePolicy.completionReady(
                selectedProviderCount = 2,
                allSelectedProvidersCompletionAware = true
            )
        )
    }

    @Test
    fun periodicRequiresManagerFeatureAndCompletionCapability() {
        assertTrue(
            SyncMaintenancePolicy.periodicCanRun(
                managerEnabled = true,
                periodicEnabled = true,
                selectedProviderCount = 1,
                allSelectedProvidersCompletionAware = true
            )
        )
        assertFalse(
            SyncMaintenancePolicy.periodicCanRun(
                managerEnabled = true,
                periodicEnabled = false,
                selectedProviderCount = 1,
                allSelectedProvidersCompletionAware = true
            )
        )
        assertFalse(
            SyncMaintenancePolicy.periodicCanRun(
                managerEnabled = true,
                periodicEnabled = true,
                selectedProviderCount = 1,
                allSelectedProvidersCompletionAware = false
            )
        )
    }

    @Test
    fun screenOffKeepsPreSleepAndPeriodicMaintenanceButCancelsWakeMaintenance() {
        assertFalse(
            SyncMaintenancePolicy.shouldCancelMaintenanceOnScreenOff(
                SyncMaintenanceTrigger.BEFORE_SLEEP
            )
        )
        assertFalse(
            SyncMaintenancePolicy.shouldCancelMaintenanceOnScreenOff(
                SyncMaintenanceTrigger.PERIODIC_SLEEP
            )
        )
        assertTrue(
            SyncMaintenancePolicy.shouldCancelMaintenanceOnScreenOff(
                SyncMaintenanceTrigger.AFTER_WAKE
            )
        )
    }

    @Test
    fun periodicAlarmRetriesOnlyForSuppressedThorFalseWake() {
        assertEquals(
            PeriodicAlarmDeviceDecision.RUN,
            SyncMaintenancePolicy.periodicAlarmDeviceDecision(
                interactive = false,
                thorClosedLidWakeSuppressed = false
            )
        )
        assertEquals(
            PeriodicAlarmDeviceDecision.RETRY_AFTER_THOR_FALSE_WAKE,
            SyncMaintenancePolicy.periodicAlarmDeviceDecision(
                interactive = true,
                thorClosedLidWakeSuppressed = true
            )
        )
        assertEquals(
            PeriodicAlarmDeviceDecision.CANCEL_AWAKE,
            SyncMaintenancePolicy.periodicAlarmDeviceDecision(
                interactive = true,
                thorClosedLidWakeSuppressed = false
            )
        )
    }

    @Test
    fun transitionRequiresManagerFeatureAndCompletionCapability() {
        assertTrue(
            SyncMaintenancePolicy.transitionCanRun(
                managerEnabled = true,
                transitionEnabled = true,
                selectedProviderCount = 1,
                allSelectedProvidersCompletionAware = true
            )
        )
        assertFalse(
            SyncMaintenancePolicy.transitionCanRun(
                managerEnabled = false,
                transitionEnabled = true,
                selectedProviderCount = 1,
                allSelectedProvidersCompletionAware = true
            )
        )
        assertFalse(
            SyncMaintenancePolicy.transitionCanRun(
                managerEnabled = true,
                transitionEnabled = true,
                selectedProviderCount = 0,
                allSelectedProvidersCompletionAware = true
            )
        )
    }
}
