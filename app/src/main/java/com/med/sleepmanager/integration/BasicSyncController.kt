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
    private const val STATE_QUERY_TIMEOUT_MS = 7000L
    private const val MIN_STATE_API_VERSION_CODE = 0x03_12_00L

    @Volatile
    private var observedState: RemoteState? = null

    private var observerContext: Context? = null
    private var observerReceiver: BroadcastReceiver? = null

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


    fun lastObservedState(): RemoteState? = observedState

    @Synchronized
    fun startStateObserver(context: Context) {
        val appContext = context.applicationContext
        if (!isInstalled(appContext) || !supportsStateApi(appContext)) {
            observedState = null
            return
        }
        if (observerReceiver != null) return

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                parseState(intent)?.let { state ->
                    observedState = state
                    Log.i(
                        TAG,
                        "STATE_CHANGED observed: mode=${state.mode} runState=${state.runState}"
                    )
                }
            }
        }

        val filter = IntentFilter(ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) {
            appContext.registerReceiver(
                receiver,
                filter,
                Context.RECEIVER_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            appContext.registerReceiver(receiver, filter)
        }

        observerContext = appContext
        observerReceiver = receiver
        sendRemoteControl(appContext, ACTION_REQUEST_STATE)
        Log.i(TAG, "BasicSync state observer started")
    }

    @Synchronized
    fun stopStateObserver() {
        val context = observerContext
        val receiver = observerReceiver
        if (context != null && receiver != null) {
            runCatching { context.unregisterReceiver(receiver) }
        }
        observerContext = null
        observerReceiver = null
    }

    private fun parseState(intent: Intent?): RemoteState? {
        if (intent?.action != ACTION_STATE_CHANGED) return null

        val mode =
            intent.getStringExtra(EXTRA_MODE)
                ?.let { raw ->
                    runCatching { Mode.valueOf(raw) }.getOrNull()
                }
                ?: return null

        val runState =
            intent.getStringExtra(EXTRA_RUN_STATE)
                ?.let { raw ->
                    runCatching { RunState.valueOf(raw) }.getOrNull()
                }
                ?: return null

        return RemoteState(mode, runState)
    }

    fun isInstalled(context: Context): Boolean =
        runCatching {
            context.packageManager.getPackageInfo(PACKAGE, 0)
        }.isSuccess

    fun versionName(context: Context): String? =
        runCatching {
            context.packageManager.getPackageInfo(PACKAGE, 0).versionName
        }.getOrNull()

    fun supportsStateApi(context: Context): Boolean =
        runCatching {
            context.packageManager
                .getPackageInfo(PACKAGE, 0)
                .longVersionCode >= MIN_STATE_API_VERSION_CODE
        }.getOrDefault(false)

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

                val state = parseState(intent) ?: return
                observedState = state
                Log.i(
                    TAG,
                    "STATE_CHANGED received: mode=${state.mode} runState=${state.runState}"
                )
                result.set(state)
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
