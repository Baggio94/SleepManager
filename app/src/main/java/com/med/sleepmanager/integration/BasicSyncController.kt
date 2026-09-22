package com.med.sleepmanager.integration

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

object BasicSyncController {
    private const val TAG = "SleepManagerBasicSync"

    const val PACKAGE = "com.chiller3.basicsync"

    private const val ACTION_STOP = "$PACKAGE.STOP"
    private const val ACTION_START = "$PACKAGE.START"
    private const val ACTION_AUTO_MODE = "$PACKAGE.AUTO_MODE"
    private const val ACTION_REQUEST_STATE = "$PACKAGE.REQUEST_STATE"
    private const val ACTION_STATE_CHANGED = "$PACKAGE.STATE_CHANGED"

    private const val EXTRA_MODE = "mode"
    private const val EXTRA_RUN_STATE = "run_state"
    private const val STATE_QUERY_TIMEOUT_MS = 5000L

    enum class Mode {
        AUTO_MODE,
        MANUAL_MODE_STARTED,
        MANUAL_MODE_STOPPED
    }

    enum class RunState {
        RUNNING,
        NOT_RUNNING,
        PAUSED,
        STARTING,
        STOPPING,
        IMPORTING,
        EXPORTING
    }

    data class RemoteState(
        val mode: Mode,
        val runState: RunState
    )

    fun isInstalled(context: Context): Boolean =
        runCatching {
            context.packageManager.getPackageInfo(PACKAGE, 0)
        }.isSuccess

    fun versionName(context: Context): String? =
        runCatching {
            context.packageManager.getPackageInfo(PACKAGE, 0).versionName
        }.getOrNull()

    fun supportsStateApi(context: Context): Boolean {
        val version = versionName(context) ?: return false
        val match = Regex("""^(\d+)\.(\d+)""").find(version) ?: return false
        val major = match.groupValues[1].toIntOrNull() ?: return false
        val minor = match.groupValues[2].toIntOrNull() ?: return false
        return major > 3 || (major == 3 && minor >= 18)
    }

    fun open(context: Context): Boolean {
        val launchIntent =
            context.packageManager.getLaunchIntentForPackage(PACKAGE)
                ?: return false

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(launchIntent)
            true
        }.getOrDefault(false)
    }

    fun sendStop(context: Context): Boolean =
        sendRemoteControl(context, ACTION_STOP)

    fun sendStart(context: Context): Boolean =
        sendRemoteControl(context, ACTION_START)

    fun sendAutoMode(context: Context): Boolean =
        sendRemoteControl(context, ACTION_AUTO_MODE)

    fun requestState(
        context: Context,
        timeoutMs: Long = STATE_QUERY_TIMEOUT_MS
    ): RemoteState? {
        if (!isInstalled(context)) return null

        val appContext = context.applicationContext
        val result = AtomicReference<RemoteState?>(null)
        val latch = CountDownLatch(1)
        val receiverThread = HandlerThread("SleepManagerBasicSyncState").apply {
            start()
        }
        val receiverHandler = Handler(receiverThread.looper)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != ACTION_STATE_CHANGED) return

                val mode =
                    intent.getStringExtra(EXTRA_MODE)
                        ?.let { raw ->
                            runCatching { Mode.valueOf(raw) }.getOrNull()
                        }
                        ?: return

                val runState =
                    intent.getStringExtra(EXTRA_RUN_STATE)
                        ?.let { raw ->
                            runCatching { RunState.valueOf(raw) }.getOrNull()
                        }
                        ?: return

                Log.i(TAG, "STATE_CHANGED received: mode=$mode runState=$runState")
                result.set(RemoteState(mode, runState))
                latch.countDown()
            }
        }

        var registered = false
        return try {
            val filter = IntentFilter(ACTION_STATE_CHANGED)
            if (Build.VERSION.SDK_INT >= 33) {
                appContext.registerReceiver(
                    receiver,
                    filter,
                    null,
                    receiverHandler,
                    Context.RECEIVER_EXPORTED
                )
            } else {
                @Suppress("DEPRECATION")
                appContext.registerReceiver(
                    receiver,
                    filter,
                    null,
                    receiverHandler
                )
            }
            registered = true

            if (!sendRemoteControl(appContext, ACTION_REQUEST_STATE)) {
                Log.w(TAG, "REQUEST_STATE could not be sent")
                return null
            }

            Log.i(TAG, "REQUEST_STATE sent; waiting up to ${timeoutMs}ms")
            val received = latch.await(timeoutMs.coerceAtLeast(1L), TimeUnit.MILLISECONDS)
            if (!received) {
                Log.w(TAG, "Timed out waiting for STATE_CHANGED")
            }
            result.get()
        } catch (_: Throwable) {
            null
        } finally {
            if (registered) {
                runCatching { appContext.unregisterReceiver(receiver) }
            }
            receiverThread.quitSafely()
        }
    }

    private fun sendRemoteControl(context: Context, action: String): Boolean {
        if (!isInstalled(context)) return false

        return runCatching {
            context.sendBroadcast(
                Intent(action).setPackage(PACKAGE)
            )
            true
        }.getOrDefault(false)
    }
}
