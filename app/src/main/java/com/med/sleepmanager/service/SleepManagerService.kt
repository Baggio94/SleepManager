package com.med.sleepmanager.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import com.med.sleepmanager.MainActivity
import com.med.sleepmanager.data.AppPreferences
import com.med.sleepmanager.integration.HelperController
import com.med.sleepmanager.integration.SyncthingController

class SleepManagerService : Service() {
    companion object {
        private const val TAG = "SleepManager"
        private const val CHANNEL_ID = "sleep_manager"
        private const val NOTIFICATION_ID = 5217
        private const val FOLLOW_DELAY_MS = 2500L

        @Volatile
        var running: Boolean = false
            private set
    }

    private val handler = Handler(Looper.getMainLooper())
    private var receiverRegistered = false
    private var helperResultReceiverRegistered = false

    private var lastWakeWifiManaged = false
    private var lastWakeWifiChanged = false
    private var lastWakeBluetoothManaged = false
    private var lastWakeBluetoothChanged = false

    private val followRunnable = Runnable {
        val syncthing = AppPreferences.manageSyncthing(this)
        if (syncthing) {
            SyncthingController.sendFollow(this)
        }

        AppPreferences.recordEvent(
            this,
            buildWakeSummary(
                wifiManaged = lastWakeWifiManaged,
                wifiChanged = lastWakeWifiChanged,
                bluetoothManaged = lastWakeBluetoothManaged,
                bluetoothChanged = lastWakeBluetoothChanged,
                syncthing = syncthing
            )
        )
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> onScreenOff()
                Intent.ACTION_SCREEN_ON -> onScreenOn()
            }
        }
    }

    private val helperResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != HelperController.ACTION_RESULT) return

            val phase = intent.getStringExtra(HelperController.EXTRA_PHASE) ?: return
            val wifiManaged = intent.getBooleanExtra(HelperController.EXTRA_WIFI_MANAGED, false)
            val wifiChanged = intent.getBooleanExtra(HelperController.EXTRA_WIFI_CHANGED, false)
            val bluetoothManaged = intent.getBooleanExtra(HelperController.EXTRA_BLUETOOTH_MANAGED, false)
            val bluetoothChanged = intent.getBooleanExtra(HelperController.EXTRA_BLUETOOTH_CHANGED, false)

            when (phase) {
                HelperController.PHASE_SLEEP -> {
                    AppPreferences.recordEvent(
                        this@SleepManagerService,
                        buildSleepSummary(
                            wifiManaged = wifiManaged,
                            wifiChanged = wifiChanged,
                            bluetoothManaged = bluetoothManaged,
                            bluetoothChanged = bluetoothChanged,
                            syncthing = AppPreferences.manageSyncthing(this@SleepManagerService)
                        )
                    )
                }

                HelperController.PHASE_WAKE -> {
                    lastWakeWifiManaged = wifiManaged
                    lastWakeWifiChanged = wifiChanged
                    lastWakeBluetoothManaged = bluetoothManaged
                    lastWakeBluetoothChanged = bluetoothChanged

                    if (!AppPreferences.manageSyncthing(this@SleepManagerService)) {
                        AppPreferences.recordEvent(
                            this@SleepManagerService,
                            buildWakeSummary(
                                wifiManaged = wifiManaged,
                                wifiChanged = wifiChanged,
                                bluetoothManaged = bluetoothManaged,
                                bluetoothChanged = bluetoothChanged,
                                syncthing = false
                            )
                        )
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        running = true
        createNotificationChannel()
        startForegroundCompat()
        registerScreenReceiver()
        registerHelperResultReceiver()
        Log.i(TAG, "Service started")
        applyCurrentScreenState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!AppPreferences.isEnabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!receiverRegistered) registerScreenReceiver()
        if (!helperResultReceiverRegistered) registerHelperResultReceiver()
        return START_STICKY
    }

    private fun onScreenOff() {
        handler.removeCallbacks(followRunnable)

        val wifi = AppPreferences.manageWifi(this)
        val bluetooth = AppPreferences.manageBluetooth(this)
        val syncthing = AppPreferences.manageSyncthing(this)

        Log.i(TAG, "Screen OFF -> wifi=$wifi bluetooth=$bluetooth syncthing=$syncthing")

        if (syncthing) SyncthingController.sendStop(this)

        val helperSent = if (wifi || bluetooth) {
            HelperController.sendSleep(this, wifi, bluetooth)
        } else {
            false
        }

        if (!helperSent) {
            AppPreferences.recordEvent(
                this,
                buildSleepSummary(
                    wifiManaged = false,
                    wifiChanged = false,
                    bluetoothManaged = false,
                    bluetoothChanged = false,
                    syncthing = syncthing
                )
            )
        }
    }

    private fun onScreenOn() {
        handler.removeCallbacks(followRunnable)

        val wifiManaged = AppPreferences.manageWifi(this)
        val bluetoothManaged = AppPreferences.manageBluetooth(this)
        val connectivity = wifiManaged || bluetoothManaged
        Log.i(TAG, "Screen ON -> restoring connectivity")

        lastWakeWifiManaged = false
        lastWakeWifiChanged = false
        lastWakeBluetoothManaged = false
        lastWakeBluetoothChanged = false

        val helperSent = if (connectivity) {
            HelperController.sendWake(this)
        } else {
            false
        }

        if (!helperSent) {
            lastWakeWifiManaged = false
            lastWakeBluetoothManaged = false
        }

        if (AppPreferences.manageSyncthing(this)) {
            val delay = if (connectivity) FOLLOW_DELAY_MS else 800L
            handler.postDelayed(followRunnable, delay)
            Log.i(TAG, "Syncthing resumed scheduled in ${delay}ms")
        } else if (!connectivity) {
            AppPreferences.recordEvent(
                this,
                buildWakeSummary(
                    wifiManaged = false,
                    wifiChanged = false,
                    bluetoothManaged = false,
                    bluetoothChanged = false,
                    syncthing = false
                )
            )
        }
    }

    private fun buildSleepSummary(
        wifiManaged: Boolean,
        wifiChanged: Boolean,
        bluetoothManaged: Boolean,
        bluetoothChanged: Boolean,
        syncthing: Boolean
    ): String {
        val actions = buildList {
            if (wifiManaged) add(if (wifiChanged) "Wi‑Fi off" else "Wi‑Fi unchanged")
            if (bluetoothManaged) add(if (bluetoothChanged) "Bluetooth off" else "Bluetooth unchanged")
            if (syncthing) add("Syncthing paused")
        }
        return if (actions.isEmpty()) "Sleep" else "Sleep → " + actions.joinToString(" · ")
    }

    private fun buildWakeSummary(
        wifiManaged: Boolean,
        wifiChanged: Boolean,
        bluetoothManaged: Boolean,
        bluetoothChanged: Boolean,
        syncthing: Boolean
    ): String {
        val actions = buildList {
            if (wifiManaged) add(if (wifiChanged) "Wi‑Fi restored to previous state" else "Wi‑Fi unchanged")
            if (bluetoothManaged) add(if (bluetoothChanged) "Bluetooth restored to previous state" else "Bluetooth unchanged")
            if (syncthing) add("Syncthing resumed")
        }
        return if (actions.isEmpty()) "Wake" else "Wake → " + actions.joinToString(" · ")
    }

    private fun applyCurrentScreenState() {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (pm?.isInteractive == false) onScreenOff() else onScreenOn()
    }

    private fun registerHelperResultReceiver() {
        if (helperResultReceiverRegistered) return

        val filter = IntentFilter(HelperController.ACTION_RESULT)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                helperResultReceiver,
                filter,
                HelperController.PERMISSION,
                null,
                Context.RECEIVER_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(helperResultReceiver, filter, HelperController.PERMISSION, null)
        }
        helperResultReceiverRegistered = true
    }

    private fun registerScreenReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(screenReceiver, filter)
        }
        receiverRegistered = true
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SleepManager",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Keeps sleep and wake automation active"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
                ?.createNotificationChannel(channel)
        }
    }

    private fun startForegroundCompat() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this).setPriority(Notification.PRIORITY_MIN)
        }
        return builder
            .setContentTitle("SleepManager is on")
            .setContentText("Sleep / wake automation is running")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    override fun onDestroy() {
        handler.removeCallbacks(followRunnable)
        if (receiverRegistered) {
            try { unregisterReceiver(screenReceiver) } catch (_: IllegalArgumentException) {}
            receiverRegistered = false
        }

        HelperController.restoreNow(this)
        if (AppPreferences.manageSyncthing(this)) SyncthingController.sendFollow(this)

        running = false
        Log.i(TAG, "Service stopped; restore requested")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
