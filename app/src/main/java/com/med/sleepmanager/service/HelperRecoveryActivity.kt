package com.med.sleepmanager.service

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import com.med.sleepmanager.data.SleepCycleStore
import com.med.sleepmanager.integration.HelperController

/**
 * Brief, transparent notification entry point for a user-approved recovery.
 *
 * An actual user tap can launch the Helper activation activity even when
 * Android has put the Helper package in FLAG_STOPPED. The existing Helper
 * is unchanged; the SleepManager foreground service owns all WAKE retries.
 */
class HelperRecoveryActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var retryRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

        val requestedCycleId =
            intent.getLongExtra(HelperController.EXTRA_CYCLE_ID, 0L)
        val cycle = SleepCycleStore.current(this)
        if (
            !cycle.active ||
            cycle.cycleId != requestedCycleId ||
            !cycle.helperExpected ||
            !cycle.helperSleepRequested ||
            cycle.helperRestored
        ) {
            Log.i(TAG, "Ignoring stale Helper recovery notification")
            finish()
            return
        }

        if (HelperController.packageStoppedState(this) == true) {
            runCatching {
                startActivity(HelperController.buildActivationIntent(this))
                Log.i(TAG, "User requested Helper activation")
            }.onFailure { error ->
                Log.w(TAG, "Unable to activate Helper from notification", error)
            }
        }

        // Keep the tap-triggered Activity briefly alive while Android removes
        // FLAG_STOPPED, then ask the existing foreground service to resend WAKE.
        val retry = Runnable {
            val current = SleepCycleStore.current(this)
            if (
                current.active &&
                current.cycleId == requestedCycleId &&
                current.helperExpected &&
                current.helperSleepRequested &&
                !current.helperRestored
            ) {
                val request = Intent(this, SleepManagerService::class.java)
                    .setAction(SleepManagerService.ACTION_HELPER_USER_RETRY)
                    .putExtra(HelperController.EXTRA_CYCLE_ID, requestedCycleId)
                runCatching { startService(request) }
                    .onFailure { error ->
                        Log.w(TAG, "Unable to request Helper recovery retry", error)
                    }
            }
            retryRunnable = null
            finish()
        }
        retryRunnable = retry
        handler.postDelayed(retry, 750L)
    }

    override fun onDestroy() {
        retryRunnable?.let(handler::removeCallbacks)
        retryRunnable = null
        super.onDestroy()
    }

    private companion object {
        const val TAG = "SleepManager"
    }
}
