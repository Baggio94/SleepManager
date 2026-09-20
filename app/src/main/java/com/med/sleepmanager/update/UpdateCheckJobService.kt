package com.med.sleepmanager.update

import android.app.job.JobParameters
import android.app.job.JobService

class UpdateCheckJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            try {
                UpdateChecker.check(
                    context = this,
                    force = false,
                    notify = true
                )
            } finally {
                jobFinished(params, false)
            }
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true
}
