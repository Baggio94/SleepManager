package com.med.sleepmanager.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.med.sleepmanager.MainActivity
import com.med.sleepmanager.data.AppPreferences
import com.med.sleepmanager.data.SleepCycleStore
import com.med.sleepmanager.integration.HelperController
import com.med.sleepmanager.integration.connector.SyncthingConnector
import com.med.sleepmanager.network.NetworkReadyGate
import com.med.sleepmanager.protection.ThorDeviceAdminReceiver
import com.med.sleepmanager.protection.ThorLidMonitor

class SleepManagerService : Service() {
    companion object {
        private const val TAG = "SleepManager"
        private const val CHANNEL_ID = "sleep_manager"
        private const val NOTIFICATION_ID = 5217
        private const val NETWORK_READY_TIMEOUT_MS = 15000L
        private const val SYNCTHING_STOP_GRACE_MS = 1000L
        private const val SLEEP_TRANSITION_WAKELOCK_TIMEOUT_MS = 3000L
        private const val THOR_CLOSE_GUARD_DELAY_MS = 1500L
        private const val THOR_SCREEN_ON_RECHECK_DELAY_MS = 500L
        private const val THOR_LOCK_COOLDOWN_MS = 900L
        const val ACTION_DISABLE_AND_RESTORE =
            "com.med.sleepmanager.action.DISABLE_AND_RESTORE"

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
    private var sleepActionsApplied = false

    private var pendingSleepWifi = false
    private var pendingSleepBluetooth = false
    private var pendingSleepSyncthing = false
    private var sleepGracePending = false
    private var sleepTransitionWakeLock: PowerManager.WakeLock? = null
    private var networkReadyGate: NetworkReadyGate? = null
    private var pendingNetworkRestoreToken: String? = null
    private var disableRestoreRequested = false

    @Volatile
    private var thorLidClosed = false

    private var thorLidMonitor: ThorLidMonitor? = null
    private var lastThorLockAt = 0L

    private val thorAdminComponent by lazy {
        ComponentName(this, ThorDeviceAdminReceiver::class.java)
    }

    private val sleepGraceRunnable = Runnable {
        sleepGracePending = false
        Log.i(TAG, "Sleep grace elapsed -> applying sleep actions")
        performFreshSleepActions()
    }

    private val sleepRadioRunnable = Runnable {
        val wifi = pendingSleepWifi
        val bluetooth = pendingSleepBluetooth
        val syncthing = pendingSleepSyncthing

        pendingSleepWifi = false
        pendingSleepBluetooth = false
        pendingSleepSyncthing = false

        Log.i(TAG, "Syncthing STOP grace elapsed -> applying radio sleep")
        applySleepConnectivity(wifi, bluetooth, syncthing)
    }

    private val thorCloseGuardRunnable = Runnable {
        maybeReturnThorToSleep("close guard")
    }

    private val thorScreenOnRecheckRunnable = Runnable {
        maybeReturnThorToSleep("closed-lid wake")
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
            val restoreSuccess = intent.getBooleanExtra(
                HelperController.EXTRA_RESTORE_SUCCESS,
                true
            )

            when (phase) {
                HelperController.PHASE_SLEEP -> {
                    if (SleepCycleStore.isActive(this@SleepManagerService)) {
                        SleepCycleStore.markHelperSleepRequested(this@SleepManagerService)
                    }
                    releaseSleepTransitionWakeLock()
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

                    val restoreToken = pendingNetworkRestoreToken

                    if (!restoreSuccess) {
                        Log.w(TAG, "Helper restore failed; preserving sleep transaction")
                        AppPreferences.recordEvent(
                            this@SleepManagerService,
                            if (disableRestoreRequested) {
                                "Disable → Helper restore pending"
                            } else {
                                "Wake → Helper restore pending"
                            }
                        )
                        finishDisableRestoreIfRequested(forceStop = true)
                        return
                    }

                    if (SleepCycleStore.isActive(this@SleepManagerService)) {
                        SleepCycleStore.markHelperRestored(this@SleepManagerService)
                    }
                    pendingNetworkRestoreToken = null

                    if (
                        restoreToken != null &&
                        SleepCycleStore.hasConnectorChange(
                            this@SleepManagerService,
                            SyncthingConnector.id
                        )
                    ) {
                        Log.i(
                            TAG,
                            "Helper wake completed -> starting network-ready wait"
                        )
                        waitForNetworkAndRestoreSyncthing(restoreToken)
                    } else {
                        if (
                            !disableRestoreRequested &&
                            !SleepCycleStore.hasConnectorChange(
                                this@SleepManagerService,
                                SyncthingConnector.id
                            )
                        ) {
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

                        SleepCycleStore.completeIfRestored(this@SleepManagerService)
                        finishDisableRestoreIfRequested()
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
        refreshThorLidMonitor()
        Log.i(TAG, "Service started")
        applyCurrentScreenState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISABLE_AND_RESTORE) {
            beginDisableAndRestore()
            return START_NOT_STICKY
        }

        if (!AppPreferences.isEnabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!receiverRegistered) registerScreenReceiver()
        if (!helperResultReceiverRegistered) registerHelperResultReceiver()
        refreshThorLidMonitor()

        return START_STICKY
    }

    private fun beginDisableAndRestore() {
        disableRestoreRequested = true

        cancelNetworkReadyWait()
        handler.removeCallbacks(sleepGraceRunnable)
        sleepGracePending = false
        handler.removeCallbacks(sleepRadioRunnable)
        pendingSleepWifi = false
        pendingSleepBluetooth = false
        pendingSleepSyncthing = false
        releaseSleepTransitionWakeLock()

        val cycle = SleepCycleStore.current(this)
        val helperRestoreNeeded =
            cycle.active &&
                cycle.helperExpected &&
                !cycle.helperRestored
        val syncthingChange =
            SleepCycleStore.connectorChange(this, SyncthingConnector.id)

        pendingNetworkRestoreToken =
            if (helperRestoreNeeded && syncthingChange != null) {
                syncthingChange.restoreToken
            } else {
                null
            }

        if (helperRestoreNeeded) {
            val sent = HelperController.restoreNow(this)
            if (sent) {
                Log.i(TAG, "Disable requested -> waiting for Helper restore result")
                return
            }

            Log.w(TAG, "Disable requested -> Helper restore could not be sent")
            AppPreferences.recordEvent(this, "Disable → Helper restore pending")
            finishDisableRestoreIfRequested(forceStop = true)
            return
        }

        if (syncthingChange != null) {
            waitForNetworkAndRestoreSyncthing(syncthingChange.restoreToken)
            return
        }

        SleepCycleStore.completeIfRestored(this)
        finishDisableRestoreIfRequested()
    }

    private fun finishDisableRestoreIfRequested(forceStop: Boolean = false) {
        if (!disableRestoreRequested) return

        if (!forceStop && SleepCycleStore.isActive(this)) {
            return
        }

        disableRestoreRequested = false
        pendingNetworkRestoreToken = null

        if (!forceStop) {
            AppPreferences.recordEvent(this, "SleepManager disabled")
        }

        stopSelf()
    }

    private fun onScreenOff() {
        cancelNetworkReadyWait()
        pendingNetworkRestoreToken = null
        handler.removeCallbacks(thorScreenOnRecheckRunnable)

        val existingCycle = SleepCycleStore.current(this)
        if (sleepActionsApplied || existingCycle.active) {
            sleepActionsApplied = true

            if (
                existingCycle.active &&
                existingCycle.helperExpected &&
                !existingCycle.helperSleepRequested
            ) {
                val syncthingStopped =
                    SleepCycleStore.hasConnectorChange(this, SyncthingConnector.id)
                val elapsed = System.currentTimeMillis() - existingCycle.startedAt
                val remainingGrace =
                    if (syncthingStopped) {
                        (SYNCTHING_STOP_GRACE_MS - elapsed).coerceAtLeast(0L)
                    } else {
                        0L
                    }

                pendingSleepWifi = existingCycle.wifiManaged
                pendingSleepBluetooth = existingCycle.bluetoothManaged
                pendingSleepSyncthing = syncthingStopped

                if (remainingGrace > 0L) {
                    acquireSleepTransitionWakeLock()
                    handler.postDelayed(sleepRadioRunnable, remainingGrace)
                    Log.i(
                        TAG,
                        "Recovered pending sleep transaction; radio sleep in " +
                            remainingGrace + "ms"
                    )
                } else {
                    Log.i(TAG, "Recovered pending sleep transaction; applying radio sleep now")
                    applySleepConnectivity(
                        wifi = existingCycle.wifiManaged,
                        bluetooth = existingCycle.bluetoothManaged,
                        syncthing = syncthingStopped
                    )
                }
            } else {
                Log.i(TAG, "Screen OFF -> active sleep transaction already exists; skipping duplicate")
            }
            return
        }
        if (sleepGracePending) {
            Log.i(TAG, "Screen OFF -> sleep grace already pending")
            return
        }

        val sleepGraceMs = AppPreferences.sleepGraceMs(this)
        if (sleepGraceMs > 0L) {
            sleepGracePending = true
            acquireSleepTransitionWakeLock(
                sleepGraceMs + SLEEP_TRANSITION_WAKELOCK_TIMEOUT_MS
            )
            handler.postDelayed(sleepGraceRunnable, sleepGraceMs)
            Log.i(TAG, "Screen OFF -> sleep grace scheduled for ${sleepGraceMs}ms")
            return
        }

        performFreshSleepActions()
    }

    private fun performFreshSleepActions() {
        sleepActionsApplied = true

        handler.removeCallbacks(sleepRadioRunnable)
        releaseSleepTransitionWakeLock()

        val wifi = AppPreferences.manageWifi(this)
        val bluetooth = AppPreferences.manageBluetooth(this)
        val syncthing = AppPreferences.manageSyncthing(this)
        val radiosManaged = wifi || bluetooth
        val helperAvailable = radiosManaged && HelperController.isInstalled(this)

        val cycle = SleepCycleStore.begin(
            context = this,
            helperExpected = helperAvailable,
            wifiManaged = wifi,
            bluetoothManaged = bluetooth
        )
        Log.i(
            TAG,
            "Screen OFF -> cycle=${cycle.cycleId} wifi=$wifi bluetooth=$bluetooth syncthing=$syncthing"
        )

        val syncthingResult = if (syncthing) {
            SyncthingConnector.sleep(this)
        } else {
            null
        }

        if (syncthingResult?.changed == true) {
            SleepCycleStore.recordConnectorChange(
                this,
                SyncthingConnector.id,
                syncthingResult.restoreToken
            )
        }

        val stopSent = syncthingResult?.changed == true

        if (stopSent && helperAvailable) {
            pendingSleepWifi = wifi
            pendingSleepBluetooth = bluetooth
            pendingSleepSyncthing = true

            acquireSleepTransitionWakeLock()
            handler.postDelayed(sleepRadioRunnable, SYNCTHING_STOP_GRACE_MS)

            Log.i(
                TAG,
                "Syncthing STOP grace scheduled for ${SYNCTHING_STOP_GRACE_MS}ms before radio sleep"
            )
        } else {
            applySleepConnectivity(
                wifi = wifi,
                bluetooth = bluetooth,
                syncthing = stopSent
            )
        }

        if (!helperAvailable && !stopSent) {
            SleepCycleStore.clear(this)
        }
    }

    private fun applySleepConnectivity(
        wifi: Boolean,
        bluetooth: Boolean,
        syncthing: Boolean
    ) {
        val helperSent = if (wifi || bluetooth) {
            HelperController.sendSleep(this, wifi, bluetooth)
        } else {
            false
        }

        if (helperSent) {
            SleepCycleStore.markHelperSleepRequested(this)
        } else {
            if (SleepCycleStore.isActive(this)) {
                SleepCycleStore.markHelperRestored(this)
            }
            releaseSleepTransitionWakeLock()
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

    private fun acquireSleepTransitionWakeLock(
        timeoutMs: Long = SLEEP_TRANSITION_WAKELOCK_TIMEOUT_MS
    ) {
        releaseSleepTransitionWakeLock()

        val powerManager =
            getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return

        sleepTransitionWakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$packageName:syncthing-stop-grace"
        ).apply {
            setReferenceCounted(false)
            acquire(timeoutMs)
        }

        Log.i(TAG, "Sleep transition wakelock acquired")
    }

    private fun releaseSleepTransitionWakeLock() {
        val wakeLock = sleepTransitionWakeLock ?: return

        if (wakeLock.isHeld) {
            runCatching { wakeLock.release() }
        }
        sleepTransitionWakeLock = null
    }

    private fun onScreenOn() {
        cancelNetworkReadyWait()

        if (AppPreferences.manageThorProtection(this) && thorLidClosed) {
            Log.i(TAG, "Screen ON while Thor lid is closed -> suppressing wake restore")
            handler.removeCallbacks(thorScreenOnRecheckRunnable)
            handler.postDelayed(
                thorScreenOnRecheckRunnable,
                THOR_SCREEN_ON_RECHECK_DELAY_MS
            )
            return
        }

        if (sleepGracePending) {
            handler.removeCallbacks(sleepGraceRunnable)
            sleepGracePending = false
            releaseSleepTransitionWakeLock()
            Log.i(TAG, "Sleep grace cancelled by wake")
        }

        handler.removeCallbacks(sleepRadioRunnable)
        pendingSleepWifi = false
        pendingSleepBluetooth = false
        pendingSleepSyncthing = false
        releaseSleepTransitionWakeLock()

        sleepActionsApplied = false

        val cycle = SleepCycleStore.current(this)
        val helperRestoreNeeded =
            cycle.active &&
                cycle.helperExpected &&
                !cycle.helperRestored

        Log.i(
            TAG,
            "Screen ON -> restoring cycle=${cycle.cycleId} active=${cycle.active} " +
                "helperRestoreNeeded=$helperRestoreNeeded"
        )

        lastWakeWifiManaged = false
        lastWakeWifiChanged = false
        lastWakeBluetoothManaged = false
        lastWakeBluetoothChanged = false

        val syncthingChange =
            SleepCycleStore.connectorChange(this, SyncthingConnector.id)

        // Restoration must follow the persisted sleep transaction, not today's
        // UI switches. The user may change options while the device is asleep.
        // If Helper changed a radio for this cycle, let Helper restore that
        // exact previous state before evaluating network readiness.
        pendingNetworkRestoreToken =
            if (helperRestoreNeeded && syncthingChange != null) {
                syncthingChange.restoreToken
            } else {
                null
            }

        val helperSent = if (helperRestoreNeeded) {
            HelperController.sendWake(this)
        } else {
            false
        }

        if (!helperSent) {
            lastWakeWifiManaged = false
            lastWakeBluetoothManaged = false
            pendingNetworkRestoreToken = null

            if (helperRestoreNeeded) {
                Log.w(
                    TAG,
                    "Helper restore still pending; preserving sleep transaction"
                )
                AppPreferences.recordEvent(
                    this,
                    "Wake → Helper restore pending"
                )
            }

            if (syncthingChange != null) {
                waitForNetworkAndRestoreSyncthing(syncthingChange.restoreToken)
            } else {
                if (!helperRestoreNeeded) {
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
                SleepCycleStore.completeIfRestored(this)
            }
        } else if (syncthingChange != null) {
            Log.i(TAG, "Syncthing restore waiting for Helper wake result")
        } else {
            SleepCycleStore.completeIfRestored(this)
        }
    }

    private fun waitForNetworkAndRestoreSyncthing(restoreToken: String?) {
        cancelNetworkReadyWait()

        networkReadyGate = NetworkReadyGate(
            context = this,
            handler = handler,
            timeoutMs = NETWORK_READY_TIMEOUT_MS
        ) { result ->
            networkReadyGate = null

            val powerManager =
                getSystemService(Context.POWER_SERVICE) as? PowerManager
            val realWake =
                powerManager?.isInteractive == true &&
                    (!AppPreferences.manageThorProtection(this) || !thorLidClosed)

            if (!realWake) {
                Log.i(
                    TAG,
                    "Network ready result=$result while device is not in a real wake; restore deferred"
                )
            } else {
                Log.i(TAG, "Network ready result=$result -> restoring Syncthing")

                val wakeResult = SyncthingConnector.wake(this, restoreToken)

                if (wakeResult.success) {
                    SleepCycleStore.clearConnectorChange(this, SyncthingConnector.id)
                } else {
                    Log.w(
                        TAG,
                        "Syncthing restore failed; preserving pending connector transaction"
                    )
                }

                AppPreferences.recordEvent(
                    this,
                    if (disableRestoreRequested) {
                        if (wakeResult.success) {
                            "SleepManager disabled"
                        } else {
                            "Disable → Syncthing restore pending"
                        }
                    } else if (wakeResult.success) {
                        buildWakeSummary(
                            wifiManaged = lastWakeWifiManaged,
                            wifiChanged = lastWakeWifiChanged,
                            bluetoothManaged = lastWakeBluetoothManaged,
                            bluetoothChanged = lastWakeBluetoothChanged,
                            syncthing = true
                        )
                    } else {
                        "Wake → Syncthing restore pending"
                    }
                )

                SleepCycleStore.completeIfRestored(this)
                finishDisableRestoreIfRequested(forceStop = !wakeResult.success)
            }
        }.also { it.start() }
    }

    private fun cancelNetworkReadyWait() {
        networkReadyGate?.cancel()
        networkReadyGate = null
    }

    private fun refreshThorLidMonitor() {
        if (!AppPreferences.manageThorProtection(this)) {
            stopThorLidMonitor()
            return
        }

        if (thorLidMonitor != null) return

        val monitor = ThorLidMonitor(
            onClosed = {
                handler.post {
                    thorLidClosed = true
                    handler.removeCallbacks(thorCloseGuardRunnable)
                    handler.postDelayed(
                        thorCloseGuardRunnable,
                        THOR_CLOSE_GUARD_DELAY_MS
                    )
                    Log.i(TAG, "Thor SW_LID -> CLOSED")
                }
            },
            onOpened = {
                handler.post {
                    thorLidClosed = false
                    handler.removeCallbacks(thorCloseGuardRunnable)
                    handler.removeCallbacks(thorScreenOnRecheckRunnable)
                    Log.i(TAG, "Thor SW_LID -> OPEN")
                }
            },
            onError = { error ->
                Log.e(TAG, "Thor lid monitor failed", error)
                handler.post {
                    AppPreferences.recordEvent(
                        this,
                        "AYN Thor protection unavailable"
                    )
                    stopThorLidMonitor()
                }
            }
        )

        if (monitor.start()) {
            thorLidMonitor = monitor
            Log.i(TAG, "Thor lid monitor started on ${ThorLidMonitor.findHallDevicePath()}")
        } else {
            Log.w(TAG, "No hall_switch input device found; Thor protection unavailable")
        }
    }

    private fun stopThorLidMonitor() {
        handler.removeCallbacks(thorCloseGuardRunnable)
        handler.removeCallbacks(thorScreenOnRecheckRunnable)
        thorLidClosed = false
        thorLidMonitor?.stop()
        thorLidMonitor = null
    }

    private fun maybeReturnThorToSleep(reason: String) {
        if (!AppPreferences.manageThorProtection(this) || !thorLidClosed) return

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        if (!powerManager.isInteractive) return

        val now = SystemClock.elapsedRealtime()
        val sinceLastLock = now - lastThorLockAt
        if (sinceLastLock < THOR_LOCK_COOLDOWN_MS) {
            // AYN Thor can briefly bounce awake again after lockNow(). Do not
            // fire duplicate calls immediately, but always schedule a retry
            // after the cooldown so a second closed-lid wake cannot remain awake.
            handler.removeCallbacks(thorScreenOnRecheckRunnable)
            handler.postDelayed(
                thorScreenOnRecheckRunnable,
                THOR_LOCK_COOLDOWN_MS - sinceLastLock + 100L
            )
            return
        }

        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return
        if (!dpm.isAdminActive(thorAdminComponent)) {
            Log.w(TAG, "Thor protection skipped: Device Admin not active")
            return
        }

        try {
            lastThorLockAt = now
            cancelNetworkReadyWait()
            pendingNetworkRestoreToken = null
            Log.i(TAG, "Thor protection -> lockNow() ($reason)")
            AppPreferences.recordEvent(
                this,
                "Protection → AYN Thor returned to sleep"
            )
            dpm.lockNow()

            // Verify again after the transition. If the Thor is asleep this is
            // a no-op; if its controller caused another bounce while still
            // closed, the same guarded path puts it back to sleep.
            handler.removeCallbacks(thorScreenOnRecheckRunnable)
            handler.postDelayed(
                thorScreenOnRecheckRunnable,
                THOR_LOCK_COOLDOWN_MS + 150L
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Unable to return Thor to sleep", t)
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
        cancelNetworkReadyWait()
        pendingNetworkRestoreToken = null
        disableRestoreRequested = false
        handler.removeCallbacks(sleepGraceRunnable)
        sleepGracePending = false
        handler.removeCallbacks(sleepRadioRunnable)
        handler.removeCallbacks(thorCloseGuardRunnable)
        handler.removeCallbacks(thorScreenOnRecheckRunnable)
        pendingSleepWifi = false
        pendingSleepBluetooth = false
        pendingSleepSyncthing = false
        releaseSleepTransitionWakeLock()
        stopThorLidMonitor()

        if (receiverRegistered) {
            try {
                unregisterReceiver(screenReceiver)
            } catch (_: IllegalArgumentException) {
            }
            receiverRegistered = false
        }

        if (helperResultReceiverRegistered) {
            try {
                unregisterReceiver(helperResultReceiver)
            } catch (_: IllegalArgumentException) {
            }
            helperResultReceiverRegistered = false
        }

        running = false
        Log.i(TAG, "Service stopped; active sleep transaction preserved")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
