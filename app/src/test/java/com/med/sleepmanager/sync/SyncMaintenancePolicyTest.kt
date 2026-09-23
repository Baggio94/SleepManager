package com.med.sleepmanager.sync

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
