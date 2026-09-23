package com.med.sleepmanager.update

internal enum class UpdateCheckTrigger { BACKGROUND, FOREGROUND, MANUAL }

internal enum class UpdateCheckStart { READY, DISABLED, NOT_DUE }

/** One shared gate for manual, foreground and scheduled checks. No timers or polling. */
internal class UpdateCheckGate {
    private var running = false

    @Synchronized
    fun tryBegin(
        trigger: UpdateCheckTrigger,
        automaticChecks: Boolean,
        networkReady: Boolean,
        now: Long,
        lastAttempt: Long,
        lastSuccess: Long
    ): UpdateCheckStart {
        if (running) return UpdateCheckStart.NOT_DUE
        if (trigger != UpdateCheckTrigger.MANUAL) {
            if (!automaticChecks) return UpdateCheckStart.DISABLED
            if (!networkReady) return UpdateCheckStart.NOT_DUE
        }

        if (trigger == UpdateCheckTrigger.BACKGROUND) {
            if (isRecent(now, lastSuccess, BACKGROUND_INTERVAL_MS)) {
                return UpdateCheckStart.NOT_DUE
            }
            // A failed request must not suppress automatic checks for another day.
            // This is a guard for subsequent triggers, not an automatic retry timer.
            if (lastAttempt > lastSuccess && isRecent(now, lastAttempt, FAILURE_BACKOFF_MS)) {
                return UpdateCheckStart.NOT_DUE
            }
        }

        running = true
        return UpdateCheckStart.READY
    }

    @Synchronized
    fun finish() {
        running = false
    }

    private fun isRecent(now: Long, timestamp: Long, interval: Long): Boolean =
        timestamp > 0L && now - timestamp in 0L until interval

    companion object {
        const val BACKGROUND_INTERVAL_MS = 24L * 60L * 60L * 1000L
        const val FAILURE_BACKOFF_MS = 5L * 60L * 1000L
    }
}
