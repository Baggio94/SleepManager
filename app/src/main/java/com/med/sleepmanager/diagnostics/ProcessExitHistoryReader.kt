package com.med.sleepmanager.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.os.Build

internal data class ProcessExitRecord(
    val timestamp: Long,
    val reason: Int,
    val status: Int,
    val importance: Int,
    val pssKb: Long,
    val rssKb: Long,
    val description: String?
)

internal data class ProcessExitHistory(
    val records: List<ProcessExitRecord>,
    val lowMemoryReasonSupported: Boolean?
)

internal object ProcessExitHistoryReader {
    fun read(context: Context, limit: Int = 3): ProcessExitHistory? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null

        return runCatching {
            val activityManager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                    ?: return@runCatching ProcessExitHistory(
                        records = emptyList(),
                        lowMemoryReasonSupported = null
                    )

            val records =
                activityManager
                    .getHistoricalProcessExitReasons(
                        context.packageName,
                        0,
                        limit
                    )
                    .map { info ->
                        ProcessExitRecord(
                            timestamp = info.timestamp,
                            reason = info.reason,
                            status = info.status,
                            importance = info.importance,
                            pssKb = info.pss,
                            rssKb = info.rss,
                            description = info.description
                                ?.replace(Regex("\\s+"), " ")
                                ?.trim()
                                ?.take(240)
                                ?.takeIf { it.isNotBlank() }
                        )
                    }

            ProcessExitHistory(
                records = records,
                lowMemoryReasonSupported =
                    runCatching {
                        ActivityManager.isLowMemoryKillReportSupported()
                    }.getOrNull()
            )
        }.getOrNull()
    }
}
