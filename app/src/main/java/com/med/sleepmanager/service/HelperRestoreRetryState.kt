package com.med.sleepmanager.service

internal enum class HelperRestoreRetryDecision {
    RETRY,
    EXHAUSTED,
    STALE
}

/**
 * In-memory timer state for replaying the same cycle-correlated Helper restore
 * when its acknowledgement is lost. Persistent ownership remains in
 * SleepCycleStore and the Helper; this class only bounds replay attempts.
 */
/** FLAG_STOPPED is distinct from a cached process being killed. */
internal object HelperWakeRoutingPolicy {
    fun useBroadcastFirst(packageStopped: Boolean?): Boolean = packageStopped == true
}

internal class HelperRestoreRetryState {
    var pending: Boolean = false
        private set

    var cycleId: Long = 0L
        private set

    var attempts: Int = 0
        private set

    private var initialWasBroadcast: Boolean = false

    fun begin(
        cycleId: Long,
        requestSent: Boolean,
        initialWasBroadcast: Boolean = false
    ) {
        if (!requestSent || cycleId == 0L) {
            clear()
            return
        }

        pending = true
        this.cycleId = cycleId
        this.initialWasBroadcast = initialWasBroadcast
        attempts = 1
    }

    fun decision(
        activeCycleId: Long,
        stillNeedsRestore: Boolean,
        maxAttempts: Int
    ): HelperRestoreRetryDecision {
        if (
            !pending ||
            cycleId == 0L ||
            activeCycleId != cycleId ||
            !stillNeedsRestore
        ) {
            return HelperRestoreRetryDecision.STALE
        }

        return if (attempts >= maxAttempts.coerceAtLeast(1)) {
            HelperRestoreRetryDecision.EXHAUSTED
        } else {
            HelperRestoreRetryDecision.RETRY
        }
    }

    /** Alternate transports: SERVICE → BROADCAST → SERVICE, or the reverse. */
    fun shouldUseBroadcastFallback(): Boolean =
        pending && if (initialWasBroadcast) attempts % 2 == 0 else attempts % 2 == 1

    fun recordRetrySent() {
        if (pending) attempts++
    }

    fun acknowledge(resultCycleId: Long) {
        if (pending && (resultCycleId == 0L || resultCycleId == cycleId)) {
            clear()
        }
    }

    fun clear() {
        pending = false
        cycleId = 0L
        attempts = 0
        initialWasBroadcast = false
    }
}
