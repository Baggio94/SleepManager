package com.med.sleepmanager.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.med.sleepmanager.data.AppPreferences

class SleepDelayReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != SleepManagerService.ACTION_SLEEP_DELAY_ELAPSED) {
            return
        }

        Log.i("SleepManager", "Custom sleep delay alarm received")

        if (!AppPreferences.isEnabled(context)) {
            return
        }

        val serviceIntent =
            Intent(context, SleepManagerService::class.java)
                .setAction(SleepManagerService.ACTION_SLEEP_DELAY_ELAPSED)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
