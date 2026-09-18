package com.med.sleepmanager

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ThorClosedLidProtectionService : Service() {

    companion object {
        private const val CHANNEL_ID = "thor_closed_lid_test"
        private const val NOTIFICATION_ID = 9041
        private const val CLOSE_GUARD_DELAY_MS = 1500L
        private const val SCREEN_ON_RECHECK_DELAY_MS = 700L
    }

    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var running = false

    @Volatile
    private var lidClosed = false

    private var monitorStream: FileInputStream? = null
    private var monitorThread: Thread? = null

    private val powerManager by lazy {
        getSystemService(Context.POWER_SERVICE) as PowerManager
    }

    private val devicePolicyManager by lazy {
        getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }

    private val adminComponent by lazy {
        ComponentName(this, ThorDeviceAdminReceiver::class.java)
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    ThorProtectionPrefs.append(
                        this@ThorClosedLidProtectionService,
                        "SCREEN_ON received (lidClosed=$lidClosed)"
                    )
                    handler.postDelayed(
                        { enforceIfStillClosed("SCREEN_ON recheck") },
                        SCREEN_ON_RECHECK_DELAY_MS
                    )
                }

                Intent.ACTION_SCREEN_OFF -> {
                    ThorProtectionPrefs.append(
                        this@ThorClosedLidProtectionService,
                        "SCREEN_OFF received"
                    )
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenReceiver, filter)

        running = true
        ThorProtectionPrefs.setRunning(this, true)
        ThorProtectionPrefs.append(this, "Protection service started")
        startLidMonitor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running) {
            running = true
            ThorProtectionPrefs.setRunning(this, true)
            startLidMonitor()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        ThorProtectionPrefs.setRunning(this, false)

        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Throwable) {
        }

        try {
            monitorStream?.close()
        } catch (_: Throwable) {
        }

        monitorStream = null
        monitorThread = null
        handler.removeCallbacksAndMessages(null)

        ThorProtectionPrefs.append(this, "Protection service stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = Binder()

    private fun startLidMonitor() {
        if (monitorThread?.isAlive == true) return

        monitorThread = Thread {
            try {
                val stream = FileInputStream("/dev/input/event1")
                monitorStream = stream

                val is64Bit = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()
                val eventSize = if (is64Bit) 24 else 16
                val payloadOffset = if (is64Bit) 16 else 8
                val buffer = ByteArray(eventSize)

                ThorProtectionPrefs.append(this, "Hall monitor opened /dev/input/event1")

                while (running) {
                    var offset = 0
                    while (offset < eventSize && running) {
                        val count = stream.read(buffer, offset, eventSize - offset)
                        if (count < 0) throw IllegalStateException("Unexpected EOF")
                        offset += count
                    }
                    if (!running) break

                    val byteBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)
                    byteBuffer.position(payloadOffset)

                    val type = byteBuffer.short.toInt() and 0xffff
                    val code = byteBuffer.short.toInt() and 0xffff
                    val value = byteBuffer.int

                    // Linux input-event constants: EV_SW=0x05, SW_LID=0x00.
                    if (type == 0x05 && code == 0x00) {
                        when (value) {
                            1 -> onLidClosed()
                            0 -> onLidOpened()
                        }
                    }
                }
            } catch (t: Throwable) {
                if (running) {
                    ThorProtectionPrefs.append(
                        this,
                        "Hall monitor ERROR: ${t.javaClass.simpleName}: ${t.message ?: "no message"}"
                    )
                }
            } finally {
                try {
                    monitorStream?.close()
                } catch (_: Throwable) {
                }
                monitorStream = null
            }
        }.apply {
            name = "ThorClosedLidMonitor"
            isDaemon = true
            start()
        }
    }

    private fun onLidClosed() {
        lidClosed = true
        ThorProtectionPrefs.append(this, "SW_LID → CLOSED")

        handler.postDelayed(
            { enforceIfStillClosed("close guard") },
            CLOSE_GUARD_DELAY_MS
        )
    }

    private fun onLidOpened() {
        lidClosed = false
        ThorProtectionPrefs.append(this, "SW_LID → OPEN")
    }

    private fun enforceIfStillClosed(reason: String) {
        if (!running || !lidClosed) {
            ThorProtectionPrefs.append(this, "$reason: skipped (lid open)")
            return
        }

        val interactive = powerManager.isInteractive
        ThorProtectionPrefs.append(
            this,
            "$reason: lid=CLOSED interactive=$interactive"
        )

        if (!interactive) {
            return
        }

        if (!devicePolicyManager.isAdminActive(adminComponent)) {
            ThorProtectionPrefs.append(this, "Cannot sleep: Device Admin not active")
            return
        }

        try {
            ThorProtectionPrefs.append(this, "PROTECTION → lockNow()")
            devicePolicyManager.lockNow()
        } catch (t: Throwable) {
            ThorProtectionPrefs.append(
                this,
                "lockNow ERROR: ${t.javaClass.simpleName}: ${t.message ?: "no message"}"
            )
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Thor closed-lid test",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("Thor closed-lid protection test")
            .setContentText("Monitoring the lid and accidental wake-ups")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .build()
    }
}
