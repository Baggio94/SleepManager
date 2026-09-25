package com.med.sleepmanager.update

import android.app.job.JobParameters
import android.app.job.JobService

class UpdateCheckJobService : JobService() {
    @Volatile
    private var workerThread: Thread? = null

    @Volatile
    private var stoppedBySystem = false

    override fun onStartJob(params: JobParameters): Boolean {
        stoppedBySystem = false

        val worker =
            Thread(
                {
                    try {
                        UpdateChecker.check(
                            context = this,
                            force = false,
                            notify = true
                        )
                    } finally {
                        workerThread = null
                        if (!stoppedBySystem) {
                            jobFinished(params, false)
                        }
                    }
                },
                "SleepManagerUpdateJob"
            )

        workerThread = worker
        worker.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        stoppedBySystem = true
        workerThread?.interrupt()
        workerThread = null
        return true
    }
}
