package com.med.sleepmanager.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.med.sleepmanager.data.AppPreferences

class PeriodicSyncReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_PERIODIC_SYNC =
            "com.med.sleepmanager.action.PERIODIC_SYNC"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_PERIODIC_SYNC) return
        if (!AppPreferences.isEnabled(context)) return

        Log.i("SleepManagerSync", "Periodic sleep sync alarm received")

        val serviceIntent =
            Intent(context, SleepManagerService::class.java)
                .setAction(SleepManagerService.ACTION_PERIODIC_SYNC)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (t: Throwable) {
            Log.e("SleepManagerSync", "Unable to start periodic sync service", t)
        }
    }
}
