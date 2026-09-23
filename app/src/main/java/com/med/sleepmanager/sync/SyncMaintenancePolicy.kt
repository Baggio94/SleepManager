package com.med.sleepmanager.sync

enum class PeriodicAlarmDeviceDecision {
    RUN,
    RETRY_AFTER_THOR_FALSE_WAKE,
    CANCEL_AWAKE
}

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

    fun shouldCancelMaintenanceOnScreenOff(
        activeTrigger: SyncMaintenanceTrigger?
    ): Boolean =
        activeTrigger == SyncMaintenanceTrigger.AFTER_WAKE

    fun periodicAlarmDeviceDecision(
        interactive: Boolean,
        thorClosedLidWakeSuppressed: Boolean
    ): PeriodicAlarmDeviceDecision =
        when {
            !interactive -> PeriodicAlarmDeviceDecision.RUN
            thorClosedLidWakeSuppressed ->
                PeriodicAlarmDeviceDecision.RETRY_AFTER_THOR_FALSE_WAKE
            else -> PeriodicAlarmDeviceDecision.CANCEL_AWAKE
        }
}
