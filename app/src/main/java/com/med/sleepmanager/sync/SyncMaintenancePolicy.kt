package com.med.sleepmanager.sync

object SyncMaintenancePolicy {
    fun completionReady(
        selectedProviderCount: Int,
        allSelectedProvidersCompletionAware: Boolean
    ): Boolean =
        selectedProviderCount > 0 &&
            allSelectedProvidersCompletionAware

    fun periodicCanRun(
        managerEnabled: Boolean,
        periodicEnabled: Boolean,
        selectedProviderCount: Int,
        allSelectedProvidersCompletionAware: Boolean
    ): Boolean =
        managerEnabled &&
            periodicEnabled &&
            completionReady(
                selectedProviderCount,
                allSelectedProvidersCompletionAware
            )

    fun transitionCanRun(
        managerEnabled: Boolean,
        transitionEnabled: Boolean,
        selectedProviderCount: Int,
        allSelectedProvidersCompletionAware: Boolean
    ): Boolean =
        managerEnabled &&
            transitionEnabled &&
            completionReady(
                selectedProviderCount,
                allSelectedProvidersCompletionAware
            )
}
