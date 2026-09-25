package com.med.sleepmanager.diagnostics

internal object ProcessExitReasonFormatter {
    fun reasonLabel(reason: Int): String = when (reason) {
        0 -> "UNKNOWN"
        1 -> "EXIT_SELF"
        2 -> "SIGNALED"
        3 -> "LOW_MEMORY"
        4 -> "CRASH"
        5 -> "CRASH_NATIVE"
        6 -> "ANR"
        7 -> "INITIALIZATION_FAILURE"
        8 -> "PERMISSION_CHANGE"
        9 -> "EXCESSIVE_RESOURCE_USAGE"
        10 -> "USER_REQUESTED"
        11 -> "USER_STOPPED"
        12 -> "DEPENDENCY_DIED"
        13 -> "OTHER"
        14 -> "FREEZER"
        15 -> "PACKAGE_STATE_CHANGE"
        16 -> "PACKAGE_UPDATED"
        else -> "REASON_$reason"
    }

    fun importanceLabel(importance: Int): String = when (importance) {
        100 -> "FOREGROUND"
        125 -> "FOREGROUND_SERVICE"
        200 -> "VISIBLE"
        230 -> "PERCEPTIBLE"
        300 -> "SERVICE"
        325 -> "TOP_SLEEPING"
        350 -> "CANT_SAVE_STATE"
        400 -> "CACHED"
        1000 -> "GONE"
        else -> "IMPORTANCE_$importance"
    }
}
