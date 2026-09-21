package com.med.sleepmanager.service

import android.app.AlarmManager
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
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.Display
import com.med.sleepmanager.MainActivity
import com.med.sleepmanager.R
import com.med.sleepmanager.data.AppPreferences
import com.med.sleepmanager.data.BatterySleepStore
import com.med.sleepmanager.data.SleepCycleStore
import com.med.sleepmanager.integration.HelperController
import com.med.sleepmanager.integration.TailscaleController
import com.med.sleepmanager.integration.connector.BasicSyncConnector
import com.med.sleepmanager.integration.connector.JamesDspConnector
import com.med.sleepmanager.integration.connector.SyncthingConnector
import com.med.sleepmanager.integration.connector.TailscaleConnector
import com.med.sleepmanager.network.NetworkReadyGate
import com.med.sleepmanager.protection.ThorDeviceAdminReceiver
import com.med.sleepmanager.protection.ThorLidMonitor
import com.med.sleepmanager.protection.ThorPowerButtonMonitor
import com.med.sleepmanager.rules.SleepConditionEvaluator
import com.med.sleepmanager.rules.SleepWakePolicy

class SleepManagerService : Service() {
    companion object {
        private const val TAG = "SleepManager"
        private const val CHANNEL_ID = "sleep_manager"
        private const val NOTIFICATION_ID = 5217
        private const val NETWORK_READY_TIMEOUT_MS = 15000L
        private const val SYNCTHING_STOP_GRACE_MS = 1000L
        private const val TAILSCALE_VERIFY_INTERVAL_MS = 500L
        private const val TAILSCALE_VERIFY_MAX_ATTEMPTS = 8
        private const val TAILSCALE_WAKE_RETRY_AT_ATTEMPT = 4
        private const val TAILSCALE_WAKE_MAX_ATTEMPTS = 12
        private const val SLEEP_TRANSITION_WAKELOCK_TIMEOUT_MS = 3000L
        private const val THOR_CLOSE_GUARD_DELAY_MS = 1500L
        private const val THOR_SCREEN_ON_RECHECK_DELAY_MS = 500L
        private const val THOR_DOCK_DISCONNECT_DEBOUNCE_MS = 500L
        private const val THOR_LOCK_COOLDOWN_MS = 900L
        const val ACTION_DISABLE_AND_RESTORE =
            "com.med.sleepmanager.action.DISABLE_AND_RESTORE"
        const val ACTION_SLEEP_DELAY_ELAPSED =
            "com.med.sleepmanager.action.SLEEP_DELAY_ELAPSED"
        private const val SLEEP_DELAY_REQUEST_CODE = 5218

        @Volatile
        var running: Boolean = false
            private set
    }

    private val handler = Handler(Looper.getMainLooper())
    private var receiverRegistered = false
    private var helperResultReceiverRegistered = false

    private var lastWakeWifiManaged = false
    private var lastWakeWifiChanged = false
    private var lastWakeWifiAttempted = false
    private var lastWakeWifiToggleSuccess = true
    private var lastWakeWifiAirplaneMode = false
    private var lastWakeBluetoothManaged = false
    private var lastWakeBluetoothChanged = false
    private var sleepActionsApplied = false
    private var sleepSkippedByConditions = false

    private var pendingSleepWifi = false
    private var pendingSleepBluetooth = false
    private var pendingSleepSyncthing = false
    private var sleepGracePending = false
    private var sleepTransitionWakeLock: PowerManager.WakeLock? = null
    private var networkReadyGate: NetworkReadyGate? = null
    private var pendingNetworkRestoreAfterHelper = false
    private var tailscaleSleepVerifyAttempts = 0
    private var tailscaleWakeVerifyAttempts = 0
    private var tailscaleVerificationNeedsWakeRestore = false
    private var disableRestoreRequested = false
    private var initialScreenStateApplied = false

    @Volatile
    private var thorLidClosed = false

    private var thorLidMonitor: ThorLidMonitor? = null
    private var thorPowerButtonMonitor: ThorPowerButtonMonitor? = null
    private var thorDisplayListenerRegistered = false
    private var thorExternalDisplayConnected = false
    private var thorExternalDisplayActive = false
    private var thorClosedAwakeOverride = false
    private var thorClosedSleepIntent = false
    private var thorSleepRequestPending = false
    private var lastThorLockAt = 0L

    private val displayManager by lazy {
        getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
    }

    private val thorAdminComponent by lazy {
        ComponentName(this, ThorDeviceAdminReceiver::class.java)
    }

    private val sleepGraceRunnable = Runnable {
        sleepGracePending = false
        Log.i(TAG, "Sleep delay elapsed -> applying sleep actions")
        performFreshSleepActions()
    }

    private val sleepRadioRunnable = Runnable {
        val wifi = pendingSleepWifi
        val bluetooth = pendingSleepBluetooth
        var syncthing = pendingSleepSyncthing

        pendingSleepWifi = false
        pendingSleepBluetooth = false
        pendingSleepSyncthing = false

        if (syncthing) {
            val change =
                SleepCycleStore.connectorChange(
                    this,
                    SyncthingConnector.id
                )
            if (
                SyncthingConnector.verifyStopAfterGrace(
                    change?.restoreToken
                ) == false
            ) {
                SleepCycleStore.clearConnectorChange(
                    this,
                    SyncthingConnector.id
                )
                syncthing = false
                Log.w(
                    TAG,
                    "Syncthing still running after STOP; restore token discarded"
                )
                AppPreferences.recordEvent(
                    this,
                    "Sleep → Syncthing STOP not confirmed"
                )
            }
        }

        Log.i(TAG, "Syncthing STOP grace elapsed -> applying radio sleep")
        applySleepConnectivity(wifi, bluetooth, syncthing)
    }

    private val tailscaleSleepVerifyRunnable = Runnable {
        verifyTailscaleSleepDisconnect()
    }

    private val tailscaleWakeVerifyRunnable = Runnable {
        verifyTailscaleWakeReconnect()
    }

    private val thorCloseGuardRunnable = Runnable {
        maybeReturnThorToSleep("close guard")
    }

    private val thorScreenOnRecheckRunnable = Runnable {
        maybeReturnThorToSleep("closed-lid wake")
    }

    private val thorDockDisconnectRunnable = Runnable {
        handleThorDockDisconnect()
    }

    private val thorDisplayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            refreshThorExternalDisplayState("display added")
        }

        override fun onDisplayRemoved(displayId: Int) {
            refreshThorExternalDisplayState("display removed")
        }

        override fun onDisplayChanged(displayId: Int) {
            refreshThorExternalDisplayState("display changed")
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> onScreenOff()
                Intent.ACTION_SCREEN_ON -> onScreenOn()
                Intent.ACTION_POWER_CONNECTED -> BatterySleepStore.noteCharging(
                    this@SleepManagerService
                )
            }
        }
    }

    private val helperResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != HelperController.ACTION_RESULT) return

            val phase = intent.getStringExtra(HelperController.EXTRA_PHASE) ?: return
            val wifiManaged = intent.getBooleanExtra(HelperController.EXTRA_WIFI_MANAGED, false)
            val wifiChanged = intent.getBooleanExtra(HelperController.EXTRA_WIFI_CHANGED, false)
            val wifiAttempted =
                intent.getBooleanExtra(HelperController.EXTRA_WIFI_ATTEMPTED, false)
            val wifiAction =
                intent.getStringExtra(HelperController.EXTRA_WIFI_ACTION) ?: "NONE"
            val wifiToggleSuccess =
                intent.getBooleanExtra(HelperController.EXTRA_WIFI_TOGGLE_SUCCESS, true)
            val airplaneMode =
                intent.getBooleanExtra(HelperController.EXTRA_AIRPLANE_MODE, false)
            val bluetoothManaged = intent.getBooleanExtra(HelperController.EXTRA_BLUETOOTH_MANAGED, false)
            val bluetoothChanged = intent.getBooleanExtra(HelperController.EXTRA_BLUETOOTH_CHANGED, false)
            val restoreSuccess = intent.getBooleanExtra(
                HelperController.EXTRA_RESTORE_SUCCESS,
                true
            )
            val helperStatus =
                intent.getStringExtra(HelperController.EXTRA_STATUS)
                    ?: HelperController.STATUS_OK

            if (
                wifiManaged &&
                helperStatus != HelperController.STATUS_ALREADY_SLEEPING
            ) {
                AppPreferences.recordWifiToggleDiagnostic(
                    context = this@SleepManagerService,
                    phase = phase,
                    action = wifiAction,
                    attempted = wifiAttempted,
                    success = wifiToggleSuccess,
                    airplaneMode = airplaneMode
                )
            }

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
                            wifiAttempted = wifiAttempted,
                            wifiToggleSuccess = wifiToggleSuccess,
                            wifiAirplaneMode = airplaneMode,
                            bluetoothManaged = bluetoothManaged,
                            bluetoothChanged = bluetoothChanged,
                            syncthing = AppPreferences.manageSyncthing(this@SleepManagerService)
                        )
                    )
                }

                HelperController.PHASE_WAKE -> {
                    lastWakeWifiManaged = wifiManaged
                    lastWakeWifiChanged = wifiChanged
                    lastWakeWifiAttempted = wifiAttempted
                    lastWakeWifiToggleSuccess = wifiToggleSuccess
                    lastWakeWifiAirplaneMode = airplaneMode
                    lastWakeBluetoothManaged = bluetoothManaged
                    lastWakeBluetoothChanged = bluetoothChanged

                    if (!restoreSuccess) {
                        Log.w(
                            TAG,
                            "Helper restore failed status=$helperStatus; preserving sleep transaction"
                        )

                        val wifiRestoreFailed =
                            wifiManaged && wifiAttempted && !wifiToggleSuccess
                        val wifiFailureText =
                            if (wifiRestoreFailed) {
                                "Wi-Fi toggle failed" +
                                    if (airplaneMode) {
                                        " · Airplane mode is enabled"
                                    } else {
                                        ""
                                    }
                            } else {
                                null
                            }

                        SleepCycleStore.markRestoreProblem(
                            this@SleepManagerService,
                            when {
                                helperStatus == HelperController.STATUS_NO_ACTIVE_CYCLE ->
                                    "Compatibility Helper no longer has the pending sleep state."
                                wifiFailureText != null ->
                                    "$wifiFailureText. Wi-Fi restore is still pending."
                                else ->
                                    "Compatibility Helper could not restore Wi-Fi / Bluetooth."
                            }
                        )
                        AppPreferences.recordEvent(
                            this@SleepManagerService,
                            if (wifiFailureText != null) {
                                val prefix =
                                    if (disableRestoreRequested) "Disable" else "Wake"
                                "$prefix → $wifiFailureText"
                            } else if (disableRestoreRequested) {
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

                    val restoreNetworkConnectors = pendingNetworkRestoreAfterHelper
                    pendingNetworkRestoreAfterHelper = false

                    if (restoreNetworkConnectors && hasPendingNetworkConnectorRestore()) {
                        Log.i(
                            TAG,
                            "Helper wake completed -> starting network-ready wait"
                        )
                        waitForNetworkAndRestorePendingConnectors()
                    } else {
                        if (
                            !disableRestoreRequested &&
                            !SleepCycleStore.hasPendingConnectorChanges(
                                this@SleepManagerService
                            )
                        ) {
                            AppPreferences.recordEvent(
                                this@SleepManagerService,
                                buildWakeSummary(
                                    wifiManaged = wifiManaged,
                                    wifiChanged = wifiChanged,
                                    wifiAttempted = wifiAttempted,
                                    wifiToggleSuccess = wifiToggleSuccess,
                                    wifiAirplaneMode = airplaneMode,
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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISABLE_AND_RESTORE) {
            beginDisableAndRestore()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_SLEEP_DELAY_ELAPSED) {
            cancelSleepDelay()

            if (!AppPreferences.isEnabled(this)) {
                stopSelf()
                return START_NOT_STICKY
            }

            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (powerManager?.isInteractive == false && !SleepCycleStore.isActive(this)) {
                Log.i(TAG, "Custom sleep delay elapsed -> evaluating advanced rules")
                performFreshSleepActions()
            }
            return START_STICKY
        }

        if (!AppPreferences.isEnabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!receiverRegistered) registerScreenReceiver()
        if (!helperResultReceiverRegistered) registerHelperResultReceiver()
        refreshThorLidMonitor()

        if (!initialScreenStateApplied) {
            initialScreenStateApplied = true
            applyCurrentScreenState()
        }

        return START_STICKY
    }

    private fun beginDisableAndRestore() {
        disableRestoreRequested = true

        cancelNetworkReadyWait()
        cancelSleepDelay()
        handler.removeCallbacks(sleepRadioRunnable)
        pendingSleepWifi = false
        pendingSleepBluetooth = false
        pendingSleepSyncthing = false
        sleepSkippedByConditions = false
        releaseSleepTransitionWakeLock()

        prepareTailscaleVerificationForWake()

        var cycle = SleepCycleStore.current(this)
        if (
            cycle.active &&
            cycle.helperExpected &&
            !cycle.helperSleepRequested &&
            !cycle.helperRestored
        ) {
            SleepCycleStore.markHelperRestored(this)
            cycle = SleepCycleStore.current(this)
        }

        val helperRestoreNeeded =
            cycle.active &&
                cycle.helperExpected &&
                cycle.helperSleepRequested &&
                !cycle.helperRestored
        val networkRestoreNeeded = hasPendingNetworkConnectorRestore()

        pendingNetworkRestoreAfterHelper =
            helperRestoreNeeded && networkRestoreNeeded

        if (helperRestoreNeeded) {
            val sent = HelperController.restoreNow(this)
            if (sent) {
                Log.i(TAG, "Disable requested -> waiting for Helper restore result")
                return
            }

            pendingNetworkRestoreAfterHelper = false
            Log.w(TAG, "Disable requested -> Helper restore could not be sent")
            SleepCycleStore.markRestoreProblem(
                this,
                "Compatibility Helper is unavailable, so Wi-Fi / Bluetooth cannot be restored."
            )
            AppPreferences.recordEvent(this, "Disable → Helper restore pending")
            finishDisableRestoreIfRequested(forceStop = true)
            return
        }

        if (networkRestoreNeeded) {
            waitForNetworkAndRestorePendingConnectors()
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
        pendingNetworkRestoreAfterHelper = false

        if (!forceStop) {
            AppPreferences.recordEvent(this, "SleepManager disabled")
        }

        stopSelf()
    }

    private fun onScreenOff() {
        thorSleepRequestPending = false
        BatterySleepStore.beginSession(this)
        cancelNetworkReadyWait()
        pendingNetworkRestoreAfterHelper = false
        handler.removeCallbacks(thorScreenOnRecheckRunnable)

        val existingCycle = SleepCycleStore.current(this)
        if (sleepActionsApplied || existingCycle.active) {
            sleepActionsApplied = true

            if (isTailscaleSleepVerificationPending()) {
                if (
                    existingCycle.active &&
                    existingCycle.helperExpected &&
                    !existingCycle.helperSleepRequested
                ) {
                    pendingSleepWifi = existingCycle.wifiManaged
                    pendingSleepBluetooth = existingCycle.bluetoothManaged
                    pendingSleepSyncthing =
                        SleepCycleStore.hasConnectorChange(
                            this,
                            SyncthingConnector.id
                        )
                }

                Log.i(TAG, "Recovered pending Tailscale disconnect verification")
                scheduleTailscaleSleepVerification(resetAttempts = true)
                return
            }

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
            Log.i(TAG, "Screen OFF -> sleep delay already pending")
            return
        }

        val sleepDelayMs = AppPreferences.effectiveSleepDelayMs(this)
        if (sleepDelayMs > 0L) {
            scheduleSleepDelay(sleepDelayMs)
            Log.i(TAG, "Screen OFF -> sleep delay scheduled for ${sleepDelayMs}ms")
            return
        }

        performFreshSleepActions()
    }

    private fun performFreshSleepActions() {
        sleepActionsApplied = true

        handler.removeCallbacks(sleepRadioRunnable)
        releaseSleepTransitionWakeLock()

        val conditions = SleepConditionEvaluator.evaluate(this)
        if (!conditions.met) {
            sleepSkippedByConditions = true
            val reason = conditions.failedReasons.joinToString(" · ")
            Log.i(TAG, "Sleep actions skipped -> $reason")
            AppPreferences.recordEvent(
                this,
                "Sleep skipped → $reason"
            )
            return
        }

        sleepSkippedByConditions = false

        val wifi = AppPreferences.manageWifi(this)
        val bluetooth = AppPreferences.manageBluetooth(this)
        val syncthing = AppPreferences.manageSyncthing(this)
        val tailscale =
            AppPreferences.manageTailscale(this) &&
                TailscaleConnector.isInstalled(this)
        val jamesDsp =
            AppPreferences.manageJamesDsp(this) &&
                JamesDspConnector.isInstalled(this)
        val basicSync =
            AppPreferences.manageBasicSync(this) &&
                BasicSyncConnector.isInstalled(this)
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
            "Screen OFF -> cycle=${cycle.cycleId} wifi=$wifi bluetooth=$bluetooth " +
                "syncthing=$syncthing tailscale=$tailscale jamesDsp=$jamesDsp " +
                "basicSync=$basicSync"
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

        val tailscaleResult = if (tailscale) {
            TailscaleConnector.sleep(this)
        } else {
            null
        }

        if (
            tailscaleResult?.attempted == true &&
            tailscaleResult.restoreToken == TailscaleConnector.TOKEN_VERIFY_DISCONNECT
        ) {
            SleepCycleStore.recordConnectorChange(
                this,
                TailscaleConnector.id,
                TailscaleConnector.TOKEN_VERIFY_DISCONNECT
            )
        }

        val jamesDspResult = if (jamesDsp) {
            JamesDspConnector.sleep(this)
        } else {
            null
        }

        if (jamesDspResult?.changed == true) {
            SleepCycleStore.recordConnectorChange(
                this,
                JamesDspConnector.id,
                jamesDspResult.restoreToken
            )
        }

        val basicSyncResult = if (basicSync) {
            BasicSyncConnector.sleep(this)
        } else {
            null
        }

        if (basicSyncResult?.changed == true) {
            SleepCycleStore.recordConnectorChange(
                this,
                BasicSyncConnector.id,
                basicSyncResult.restoreToken
            )
            AppPreferences.recordEvent(
                this,
                "Sleep → BasicSync stopped"
            )
        }

        val stopSent = syncthingResult?.changed == true
        val tailscaleVerificationPending = isTailscaleSleepVerificationPending()

        if (helperAvailable && (stopSent || tailscaleVerificationPending)) {
            pendingSleepWifi = wifi
            pendingSleepBluetooth = bluetooth
            pendingSleepSyncthing = stopSent

            if (tailscaleVerificationPending) {
                Log.i(
                    TAG,
                    "Waiting for Tailscale disconnect verification before radio sleep"
                )
                scheduleTailscaleSleepVerification(resetAttempts = true)
            } else {
                acquireSleepTransitionWakeLock()
                handler.postDelayed(sleepRadioRunnable, SYNCTHING_STOP_GRACE_MS)

                Log.i(
                    TAG,
                    "Syncthing STOP grace scheduled for " +
                        "${SYNCTHING_STOP_GRACE_MS}ms before radio sleep"
                )
            }
        } else {
            if (tailscaleVerificationPending) {
                scheduleTailscaleSleepVerification(resetAttempts = true)
            }

            applySleepConnectivity(
                wifi = wifi,
                bluetooth = bluetooth,
                syncthing = stopSent
            )
        }

        if (
            !helperAvailable &&
            !SleepCycleStore.hasPendingConnectorChanges(this)
        ) {
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

    private fun isTailscaleSleepVerificationPending(): Boolean =
        SleepCycleStore.connectorChange(this, TailscaleConnector.id)
            ?.restoreToken == TailscaleConnector.TOKEN_VERIFY_DISCONNECT

    private fun prepareTailscaleVerificationForWake() {
        if (!isTailscaleSleepVerificationPending()) return

        tailscaleVerificationNeedsWakeRestore = true
        scheduleTailscaleSleepVerification(resetAttempts = true)
        Log.i(TAG, "Tailscale disconnect verification continuing during wake")
    }

    private fun scheduleTailscaleSleepVerification(resetAttempts: Boolean) {
        handler.removeCallbacks(tailscaleSleepVerifyRunnable)
        if (resetAttempts) {
            tailscaleSleepVerifyAttempts = 0
        }

        acquireSleepTransitionWakeLock(
            TAILSCALE_VERIFY_INTERVAL_MS *
                (TAILSCALE_VERIFY_MAX_ATTEMPTS + 2)
        )
        handler.postDelayed(
            tailscaleSleepVerifyRunnable,
            TAILSCALE_VERIFY_INTERVAL_MS
        )
    }

    private fun verifyTailscaleSleepDisconnect() {
        val change =
            SleepCycleStore.connectorChange(this, TailscaleConnector.id)
                ?: return releaseSleepTransitionWakeLock()

        if (change.restoreToken != TailscaleConnector.TOKEN_VERIFY_DISCONNECT) {
            releaseSleepTransitionWakeLock()
            return
        }

        if (!TailscaleController.isConnected(this)) {
            SleepCycleStore.recordConnectorChange(
                this,
                TailscaleConnector.id,
                TailscaleConnector.TOKEN_RESTORE
            )
            Log.i(TAG, "Tailscale disconnect verified")
            AppPreferences.recordEvent(
                this,
                "Sleep → Tailscale disconnected"
            )
            continueAfterTailscaleSleepVerification()
            return
        }

        tailscaleSleepVerifyAttempts++
        if (tailscaleSleepVerifyAttempts < TAILSCALE_VERIFY_MAX_ATTEMPTS) {
            handler.postDelayed(
                tailscaleSleepVerifyRunnable,
                TAILSCALE_VERIFY_INTERVAL_MS
            )
            return
        }

        SleepCycleStore.clearConnectorChange(this, TailscaleConnector.id)
        Log.i(
            TAG,
            "Tailscale disconnect not verified; no restore will be scheduled"
        )
        AppPreferences.recordEvent(
            this,
            "Sleep → Tailscale unchanged"
        )
        continueAfterTailscaleSleepVerification()
    }

    private fun continueAfterTailscaleSleepVerification() {
        handler.removeCallbacks(tailscaleSleepVerifyRunnable)
        tailscaleSleepVerifyAttempts = 0
        releaseSleepTransitionWakeLock()

        val powerManager =
            getSystemService(Context.POWER_SERVICE) as? PowerManager
        val realWake =
            powerManager?.isInteractive == true &&
                (
                    !AppPreferences.manageThorProtection(this) ||
                        !thorLidClosed ||
                        shouldBypassThorProtectionForClosedLid()
                )

        if (realWake || tailscaleVerificationNeedsWakeRestore) {
            pendingSleepWifi = false
            pendingSleepBluetooth = false
            pendingSleepSyncthing = false

            val shouldRestoreNow =
                tailscaleVerificationNeedsWakeRestore &&
                    hasPendingNetworkConnectorRestore()
            tailscaleVerificationNeedsWakeRestore = false

            if (shouldRestoreNow) {
                waitForNetworkAndRestorePendingConnectors()
            } else {
                SleepCycleStore.completeIfRestored(this)
                finishDisableRestoreIfRequested()
            }
            return
        }

        val hasPendingRadioSleep =
            pendingSleepWifi || pendingSleepBluetooth

        if (hasPendingRadioSleep) {
            val elapsed =
                System.currentTimeMillis() -
                    SleepCycleStore.current(this).startedAt
            val remainingSyncthingGrace =
                if (pendingSleepSyncthing) {
                    (SYNCTHING_STOP_GRACE_MS - elapsed).coerceAtLeast(0L)
                } else {
                    0L
                }

            if (remainingSyncthingGrace > 0L) {
                acquireSleepTransitionWakeLock()
                handler.postDelayed(
                    sleepRadioRunnable,
                    remainingSyncthingGrace
                )
            } else {
                sleepRadioRunnable.run()
            }
        } else {
            SleepCycleStore.completeIfRestored(this)
        }
    }

    private fun cancelTailscaleVerification() {
        handler.removeCallbacks(tailscaleSleepVerifyRunnable)
        handler.removeCallbacks(tailscaleWakeVerifyRunnable)
        tailscaleSleepVerifyAttempts = 0
        tailscaleWakeVerifyAttempts = 0
        tailscaleVerificationNeedsWakeRestore = false
    }

    private fun restorePendingJamesDsp() {
        val change =
            SleepCycleStore.connectorChange(this, JamesDspConnector.id)
                ?: return

        val wakeResult =
            JamesDspConnector.wake(this, change.restoreToken)

        if (wakeResult.success) {
            SleepCycleStore.clearConnectorChange(this, JamesDspConnector.id)
            if (!disableRestoreRequested) {
                AppPreferences.recordEvent(
                    this,
                    "Wake → JamesDSP restored"
                )
            }
            Log.i(TAG, "JamesDSP power ON sent")
        } else {
            SleepCycleStore.markRestoreProblem(
                this,
                "JamesDSP restore is still pending: ${wakeResult.detail}."
            )
            AppPreferences.recordEvent(
                this,
                if (disableRestoreRequested) {
                    "Disable → JamesDSP restore pending"
                } else {
                    "Wake → JamesDSP restore pending"
                }
            )
            Log.w(TAG, "JamesDSP restore failed; preserving transaction")
        }
    }

    private fun restorePendingBasicSync() {
        val change =
            SleepCycleStore.connectorChange(this, BasicSyncConnector.id)
                ?: return

        val wakeResult =
            BasicSyncConnector.wake(this, change.restoreToken)

        if (wakeResult.success) {
            SleepCycleStore.clearConnectorChange(this, BasicSyncConnector.id)
            if (!disableRestoreRequested) {
                AppPreferences.recordEvent(
                    this,
                    "Wake → BasicSync returned to auto mode"
                )
            }
            Log.i(TAG, "BasicSync AUTO_MODE sent")
        } else {
            SleepCycleStore.markRestoreProblem(
                this,
                "BasicSync restore is still pending: ${wakeResult.detail}."
            )
            AppPreferences.recordEvent(
                this,
                if (disableRestoreRequested) {
                    "Disable → BasicSync restore pending"
                } else {
                    "Wake → BasicSync restore pending"
                }
            )
            Log.w(TAG, "BasicSync restore failed; preserving transaction")
        }
    }

    private fun hasPendingNetworkConnectorRestore(): Boolean {
        val syncthingPending =
            SleepCycleStore.hasConnectorChange(this, SyncthingConnector.id)
        val tailscalePending =
            SleepCycleStore.connectorChange(this, TailscaleConnector.id)
                ?.restoreToken == TailscaleConnector.TOKEN_RESTORE
        return syncthingPending || tailscalePending
    }

    private fun scheduleTailscaleWakeVerification() {
        handler.removeCallbacks(tailscaleWakeVerifyRunnable)
        tailscaleWakeVerifyAttempts = 0
        handler.postDelayed(
            tailscaleWakeVerifyRunnable,
            TAILSCALE_VERIFY_INTERVAL_MS
        )
    }

    private fun verifyTailscaleWakeReconnect() {
        val change =
            SleepCycleStore.connectorChange(this, TailscaleConnector.id)
                ?: return

        if (change.restoreToken != TailscaleConnector.TOKEN_RESTORE) {
            return
        }

        if (TailscaleController.isConnected(this)) {
            SleepCycleStore.clearConnectorChange(this, TailscaleConnector.id)
            Log.i(TAG, "Tailscale reconnect verified")

            if (!disableRestoreRequested) {
                AppPreferences.recordEvent(
                    this,
                    "Wake → Tailscale restored"
                )
            }

            val complete = SleepCycleStore.completeIfRestored(this)
            if (disableRestoreRequested && !complete) {
                finishDisableRestoreIfRequested(forceStop = true)
            } else {
                finishDisableRestoreIfRequested()
            }
            return
        }

        tailscaleWakeVerifyAttempts++

        if (
            tailscaleWakeVerifyAttempts ==
            TAILSCALE_WAKE_RETRY_AT_ATTEMPT
        ) {
            val retrySent = TailscaleController.sendConnect(this)
            Log.i(
                TAG,
                "Tailscale reconnect still pending -> CONNECT retry sent=$retrySent"
            )
        }

        if (tailscaleWakeVerifyAttempts < TAILSCALE_WAKE_MAX_ATTEMPTS) {
            handler.postDelayed(
                tailscaleWakeVerifyRunnable,
                TAILSCALE_VERIFY_INTERVAL_MS
            )
            return
        }

        Log.w(TAG, "Tailscale reconnect not verified; restore remains pending")
        AppPreferences.recordEvent(
            this,
            if (disableRestoreRequested) {
                "Disable → Tailscale restore pending"
            } else {
                "Wake → Tailscale restore pending"
            }
        )
        finishDisableRestoreIfRequested(forceStop = disableRestoreRequested)
    }

    private fun scheduleSleepDelay(delayMs: Long) {
        cancelSleepDelay()
        sleepGracePending = true

        if (delayMs <= 10_000L) {
            acquireSleepTransitionWakeLock(
                delayMs + SLEEP_TRANSITION_WAKELOCK_TIMEOUT_MS
            )
            handler.postDelayed(sleepGraceRunnable, delayMs)
            return
        }

        val alarmManager =
            getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                ?: return

        val triggerAt =
            SystemClock.elapsedRealtime() + delayMs

        val canScheduleExact =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()

        if (canScheduleExact) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAt,
                sleepDelayPendingIntent()
            )
            Log.i(TAG, "Custom sleep delay scheduled as exact alarm")
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAt,
                sleepDelayPendingIntent()
            )
            Log.w(
                TAG,
                "Exact alarm access unavailable; custom sleep delay may be deferred"
            )
        }
    }

    private fun cancelSleepDelay() {
        handler.removeCallbacks(sleepGraceRunnable)
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        alarmManager?.cancel(sleepDelayPendingIntent())
        alarmManager?.cancel(legacySleepDelayPendingIntent())
        sleepGracePending = false
        releaseSleepTransitionWakeLock()
    }

    private fun sleepDelayPendingIntent(): PendingIntent =
        PendingIntent.getBroadcast(
            this,
            SLEEP_DELAY_REQUEST_CODE,
            Intent(this, SleepDelayReceiver::class.java)
                .setAction(ACTION_SLEEP_DELAY_ELAPSED),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun legacySleepDelayPendingIntent(): PendingIntent =
        PendingIntent.getForegroundService(
            this,
            SLEEP_DELAY_REQUEST_CODE,
            Intent(this, SleepManagerService::class.java)
                .setAction(ACTION_SLEEP_DELAY_ELAPSED),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun acquireSleepTransitionWakeLock(
        timeoutMs: Long = SLEEP_TRANSITION_WAKELOCK_TIMEOUT_MS
    ) {
        releaseSleepTransitionWakeLock()

        val powerManager =
            getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return

        sleepTransitionWakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$packageName:sleep-transition"
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

        val closedLidProtectionApplies =
            AppPreferences.manageThorProtection(this) &&
                thorLidClosed &&
                !shouldBypassThorProtectionForClosedLid()

        val wakeDecision =
            SleepWakePolicy.onScreenOn(
                thorProtectionEnabled = closedLidProtectionApplies,
                lidClosed = closedLidProtectionApplies,
                sleepDelayPending = sleepGracePending
            )

        if (wakeDecision.suppressWake) {
            Log.i(TAG, "Screen ON while Thor lid is closed -> suppressing wake restore")
            handler.removeCallbacks(thorScreenOnRecheckRunnable)
            handler.postDelayed(
                thorScreenOnRecheckRunnable,
                THOR_SCREEN_ON_RECHECK_DELAY_MS
            )
            return
        }

        BatterySleepStore.finishSession(this)

        if (wakeDecision.cancelSleepDelay) {
            cancelSleepDelay()
            Log.i(TAG, "Sleep delay cancelled by wake")
        }

        handler.removeCallbacks(sleepRadioRunnable)
        pendingSleepWifi = false
        pendingSleepBluetooth = false
        pendingSleepSyncthing = false
        releaseSleepTransitionWakeLock()

        sleepActionsApplied = false

        if (isTailscaleSleepVerificationPending()) {
            prepareTailscaleVerificationForWake()
        }

        restorePendingJamesDsp()
        restorePendingBasicSync()

        var cycle = SleepCycleStore.current(this)
        if (
            cycle.active &&
            cycle.helperExpected &&
            !cycle.helperSleepRequested &&
            !cycle.helperRestored
        ) {
            SleepCycleStore.markHelperRestored(this)
            cycle = SleepCycleStore.current(this)
        }

        val helperRestoreNeeded =
            cycle.active &&
                cycle.helperExpected &&
                cycle.helperSleepRequested &&
                !cycle.helperRestored
        val networkRestoreNeeded = hasPendingNetworkConnectorRestore()

        Log.i(
            TAG,
            "Screen ON -> restoring cycle=${cycle.cycleId} active=${cycle.active} " +
                "helperRestoreNeeded=$helperRestoreNeeded " +
                "networkRestoreNeeded=$networkRestoreNeeded"
        )

        lastWakeWifiManaged = false
        lastWakeWifiChanged = false
        lastWakeBluetoothManaged = false
        lastWakeBluetoothChanged = false

        pendingNetworkRestoreAfterHelper =
            helperRestoreNeeded && networkRestoreNeeded

        val helperSent = if (helperRestoreNeeded) {
            HelperController.sendWake(this)
        } else {
            false
        }

        if (!helperSent) {
            lastWakeWifiManaged = false
            lastWakeBluetoothManaged = false
            pendingNetworkRestoreAfterHelper = false

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

            if (networkRestoreNeeded) {
                waitForNetworkAndRestorePendingConnectors()
            } else {
                if (
                    !helperRestoreNeeded &&
                    !sleepSkippedByConditions &&
                    !SleepCycleStore.hasPendingConnectorChanges(this)
                ) {
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
                finishDisableRestoreIfRequested()
            }
        } else if (networkRestoreNeeded) {
            Log.i(TAG, "Connector restore waiting for Helper wake result")
        } else {
            SleepCycleStore.completeIfRestored(this)
            finishDisableRestoreIfRequested()
        }

        sleepSkippedByConditions = false
    }

    private fun waitForNetworkAndRestorePendingConnectors() {
        if (!hasPendingNetworkConnectorRestore()) {
            SleepCycleStore.completeIfRestored(this)
            finishDisableRestoreIfRequested()
            return
        }

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
                    (
                        !AppPreferences.manageThorProtection(this) ||
                            !thorLidClosed ||
                            shouldBypassThorProtectionForClosedLid()
                    )

            if (!realWake) {
                Log.i(
                    TAG,
                    "Network ready result=$result while device is not in a real wake; restore deferred"
                )
            } else {
                Log.i(
                    TAG,
                    "Network ready result=$result -> restoring pending connectors"
                )

                var restoreFailed = false
                var tailscaleVerificationScheduled = false

                val syncthingChange =
                    SleepCycleStore.connectorChange(
                        this,
                        SyncthingConnector.id
                    )
                if (syncthingChange != null) {
                    val wakeResult =
                        SyncthingConnector.wake(
                            this,
                            syncthingChange.restoreToken
                        )

                    if (wakeResult.success) {
                        SleepCycleStore.clearConnectorChange(
                            this,
                            SyncthingConnector.id
                        )

                        if (!disableRestoreRequested) {
                            AppPreferences.recordEvent(
                                this,
                                buildWakeSummary(
                                    wifiManaged = lastWakeWifiManaged,
                                    wifiChanged = lastWakeWifiChanged,
                                    wifiAttempted = lastWakeWifiAttempted,
                                    wifiToggleSuccess = lastWakeWifiToggleSuccess,
                                    wifiAirplaneMode = lastWakeWifiAirplaneMode,
                                    bluetoothManaged = lastWakeBluetoothManaged,
                                    bluetoothChanged = lastWakeBluetoothChanged,
                                    syncthing = true
                                )
                            )
                        }
                    } else {
                        restoreFailed = true
                        SleepCycleStore.markRestoreProblem(
                            this,
                            "Syncthing restore is still pending: ${wakeResult.detail}."
                        )
                        Log.w(
                            TAG,
                            "Syncthing restore failed; preserving pending connector transaction"
                        )
                        AppPreferences.recordEvent(
                            this,
                            if (disableRestoreRequested) {
                                "Disable → Syncthing restore pending"
                            } else {
                                "Wake → Syncthing restore pending"
                            }
                        )
                    }
                }

                val tailscaleChange =
                    SleepCycleStore.connectorChange(
                        this,
                        TailscaleConnector.id
                    )
                if (
                    tailscaleChange?.restoreToken ==
                    TailscaleConnector.TOKEN_RESTORE
                ) {
                    val wakeResult =
                        TailscaleConnector.wake(
                            this,
                            tailscaleChange.restoreToken
                        )

                    if (wakeResult.success) {
                        Log.i(
                            TAG,
                            "Tailscale CONNECT sent -> waiting for VPN verification"
                        )
                        tailscaleVerificationScheduled = true
                        scheduleTailscaleWakeVerification()
                    } else {
                        restoreFailed = true
                        SleepCycleStore.markRestoreProblem(
                            this,
                            "Tailscale restore is still pending: ${wakeResult.detail}."
                        )
                        Log.w(
                            TAG,
                            "Tailscale reconnect request failed; preserving transaction"
                        )
                        AppPreferences.recordEvent(
                            this,
                            if (disableRestoreRequested) {
                                "Disable → Tailscale restore pending"
                            } else {
                                "Wake → Tailscale restore pending"
                            }
                        )
                    }
                }

                if (!tailscaleVerificationScheduled) {
                    SleepCycleStore.completeIfRestored(this)
                    finishDisableRestoreIfRequested(
                        forceStop =
                            disableRestoreRequested && restoreFailed
                    )
                }
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

        registerThorDisplayListener()
        startThorPowerButtonMonitor()

        if (thorLidMonitor != null) return

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val currentLidState = ThorLidMonitor.readCurrentLidClosed()
        thorLidClosed =
            currentLidState
                ?: if (powerManager?.isInteractive == false) {
                    AppPreferences.lastKnownThorLidClosed(this) ?: false
                } else {
                    false
                }
        currentLidState?.let {
            AppPreferences.setLastKnownThorLidClosed(this, it)
        }

        thorExternalDisplayConnected = hasExternalDisplayConnected()
        thorExternalDisplayActive = hasActiveExternalDisplay()

        val monitor = ThorLidMonitor(
            onClosed = {
                handler.post {
                    thorLidClosed = true
                    thorClosedAwakeOverride = false
                    thorClosedSleepIntent = false
                    AppPreferences.setLastKnownThorLidClosed(
                        this@SleepManagerService,
                        true
                    )
                    handler.removeCallbacks(thorCloseGuardRunnable)
                    handler.removeCallbacks(thorScreenOnRecheckRunnable)

                    thorExternalDisplayConnected = hasExternalDisplayConnected()
                    thorExternalDisplayActive = hasActiveExternalDisplay()
                    Log.i(TAG, "Thor SW_LID -> CLOSED")

                    if (thorExternalDisplayConnected) {
                        Log.i(
                            TAG,
                            if (thorExternalDisplayActive) {
                                "Thor dock mode -> external display active; lid close ignored"
                            } else {
                                "Thor dock mode -> external display connected; lid close ignored"
                            }
                        )
                    } else {
                        handler.postDelayed(
                            thorCloseGuardRunnable,
                            THOR_CLOSE_GUARD_DELAY_MS
                        )
                    }
                }
            },
            onOpened = {
                handler.post {
                    thorLidClosed = false
                    thorClosedAwakeOverride = false
                    thorClosedSleepIntent = false
                    thorSleepRequestPending = false
                    AppPreferences.setLastKnownThorLidClosed(
                        this@SleepManagerService,
                        false
                    )
                    handler.removeCallbacks(thorCloseGuardRunnable)
                    handler.removeCallbacks(thorScreenOnRecheckRunnable)
                    handler.removeCallbacks(thorDockDisconnectRunnable)
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

    private fun registerThorDisplayListener() {
        if (thorDisplayListenerRegistered) return
        val manager = displayManager ?: return

        manager.registerDisplayListener(thorDisplayListener, handler)
        thorDisplayListenerRegistered = true
        thorExternalDisplayConnected = hasExternalDisplayConnected()
        thorExternalDisplayActive = hasActiveExternalDisplay()
        Log.i(
            TAG,
            "Thor display monitor started connected=$thorExternalDisplayConnected " +
                "active=$thorExternalDisplayActive"
        )
    }

    private fun unregisterThorDisplayListener() {
        if (!thorDisplayListenerRegistered) return
        runCatching {
            displayManager?.unregisterDisplayListener(thorDisplayListener)
        }
        thorDisplayListenerRegistered = false
        thorExternalDisplayConnected = false
        thorExternalDisplayActive = false
    }

    private fun startThorPowerButtonMonitor() {
        if (thorPowerButtonMonitor != null) return

        val monitor = ThorPowerButtonMonitor(
            onPressed = {
                handler.post { handleThorPowerButtonPressed() }
            },
            onError = { error ->
                Log.e(TAG, "Thor power button monitor failed", error)
                handler.post {
                    thorPowerButtonMonitor?.stop()
                    thorPowerButtonMonitor = null
                }
            }
        )

        if (monitor.start()) {
            thorPowerButtonMonitor = monitor
            Log.i(
                TAG,
                "Thor power button monitor started on " +
                    ThorPowerButtonMonitor.findPowerButtonDevicePath()
            )
        } else {
            Log.w(TAG, "No pmic_pwrkey input device found; closed-lid Power option unavailable")
        }
    }

    private fun isThorExternalDisplay(display: Display): Boolean {
        if (display.displayId == Display.DEFAULT_DISPLAY) return false
        val name = display.name.lowercase()
        return name.contains("dp screen") ||
            name.contains("hdmi") ||
            name.contains("external")
    }

    private fun hasExternalDisplayConnected(): Boolean =
        displayManager
            ?.displays
            ?.any(::isThorExternalDisplay) == true

    private fun hasActiveExternalDisplay(): Boolean =
        displayManager
            ?.displays
            ?.any { display ->
                isThorExternalDisplay(display) &&
                    display.state == Display.STATE_ON
            } == true

    private fun refreshThorExternalDisplayState(reason: String) {
        if (!AppPreferences.manageThorProtection(this)) return

        val connected = hasExternalDisplayConnected()
        val active = hasActiveExternalDisplay()
        val wasConnected = thorExternalDisplayConnected

        thorExternalDisplayConnected = connected
        thorExternalDisplayActive = active

        if (connected) {
            handler.removeCallbacks(thorDockDisconnectRunnable)
            if (thorLidClosed && !thorClosedSleepIntent) {
                thorClosedAwakeOverride = false
                if (active) {
                    Log.i(TAG, "Thor dock mode -> external display active ($reason)")
                }
            }
            return
        }

        if (wasConnected && thorLidClosed) {
            handler.removeCallbacks(thorDockDisconnectRunnable)
            handler.postDelayed(
                thorDockDisconnectRunnable,
                THOR_DOCK_DISCONNECT_DEBOUNCE_MS
            )
            Log.i(TAG, "Thor dock mode -> external display disconnected; debounce started")
        }
    }

    private fun handleThorDockDisconnect() {
        if (!AppPreferences.manageThorProtection(this) || !thorLidClosed) return

        if (hasExternalDisplayConnected()) {
            thorExternalDisplayConnected = true
            thorExternalDisplayActive = hasActiveExternalDisplay()
            thorClosedAwakeOverride = false
            return
        }

        thorExternalDisplayConnected = false
        thorExternalDisplayActive = false

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        if (!powerManager.isInteractive) {
            thorClosedAwakeOverride = false
            return
        }

        if (AppPreferences.thorDockDisconnectSleeps(this)) {
            thorClosedAwakeOverride = false
            thorClosedSleepIntent = true
            Log.i(TAG, "Thor dock mode -> display disconnected; requesting sleep")
            requestThorSleep("dock display disconnected")
        } else {
            thorClosedSleepIntent = false
            thorClosedAwakeOverride = true
            Log.i(TAG, "Thor dock mode -> display disconnected; keeping awake (AYN default)")
            AppPreferences.recordEvent(
                this,
                "Dock → display disconnected · kept awake"
            )
        }
    }

    private fun handleThorPowerButtonPressed() {
        if (!AppPreferences.manageThorProtection(this) || !thorLidClosed) return

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        if (!powerManager.isInteractive) return

        if (!AppPreferences.thorClosedPowerSleeps(this)) {
            Log.i(TAG, "Thor KEY_POWER while lid closed -> AYN default")
            return
        }

        thorClosedAwakeOverride = false
        thorClosedSleepIntent = true
        Log.i(TAG, "Thor KEY_POWER while lid closed -> requesting sleep")
        requestThorSleep("closed-lid power button")
    }

    private fun shouldBypassThorProtectionForClosedLid(): Boolean {
        if (!thorLidClosed || thorClosedSleepIntent) return false
        return hasExternalDisplayConnected() || thorClosedAwakeOverride
    }

    private fun stopThorLidMonitor() {
        handler.removeCallbacks(thorCloseGuardRunnable)
        handler.removeCallbacks(thorScreenOnRecheckRunnable)
        handler.removeCallbacks(thorDockDisconnectRunnable)
        thorLidClosed = false
        thorClosedAwakeOverride = false
        thorClosedSleepIntent = false
        thorSleepRequestPending = false
        thorLidMonitor?.stop()
        thorLidMonitor = null
        thorPowerButtonMonitor?.stop()
        thorPowerButtonMonitor = null
        unregisterThorDisplayListener()
    }

    private fun maybeReturnThorToSleep(reason: String) {
        if (!AppPreferences.manageThorProtection(this) || !thorLidClosed) return

        if (shouldBypassThorProtectionForClosedLid()) {
            Log.i(TAG, "Thor protection bypassed -> dock/keep-awake state ($reason)")
            return
        }

        requestThorSleep(reason)
    }

    private fun requestThorSleep(reason: String) {
        if (!AppPreferences.manageThorProtection(this) || !thorLidClosed) return

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        if (!powerManager.isInteractive) {
            thorSleepRequestPending = false
            return
        }
        if (thorSleepRequestPending) return

        val now = SystemClock.elapsedRealtime()
        val sinceLastLock = now - lastThorLockAt
        if (sinceLastLock < THOR_LOCK_COOLDOWN_MS) {
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
            thorSleepRequestPending = true
            thorClosedSleepIntent = true
            lastThorLockAt = now
            cancelNetworkReadyWait()
            pendingNetworkRestoreAfterHelper = false
            Log.i(TAG, "Thor protection -> lockNow() ($reason)")
            AppPreferences.recordEvent(
                this,
                "Protection → AYN Thor returned to sleep"
            )
            dpm.lockNow()

            handler.removeCallbacks(thorScreenOnRecheckRunnable)
            handler.postDelayed(
                thorScreenOnRecheckRunnable,
                THOR_LOCK_COOLDOWN_MS + 150L
            )
        } catch (t: Throwable) {
            thorSleepRequestPending = false
            Log.e(TAG, "Unable to return Thor to sleep", t)
        }
    }

    private fun buildSleepSummary(
        wifiManaged: Boolean,
        wifiChanged: Boolean,
        wifiAttempted: Boolean = false,
        wifiToggleSuccess: Boolean = true,
        wifiAirplaneMode: Boolean = false,
        bluetoothManaged: Boolean,
        bluetoothChanged: Boolean,
        syncthing: Boolean
    ): String {
        val actions = buildList {
            if (wifiManaged) {
                add(
                    when {
                        wifiAttempted && !wifiToggleSuccess ->
                            "Wi-Fi toggle failed" +
                                if (wifiAirplaneMode) {
                                    " · Airplane mode is enabled"
                                } else {
                                    ""
                                }
                        wifiChanged -> "Wi-Fi off"
                        else -> "Wi-Fi unchanged"
                    }
                )
            }
            if (bluetoothManaged) add(if (bluetoothChanged) "Bluetooth off" else "Bluetooth unchanged")
            if (syncthing) add("Syncthing paused")
        }
        return if (actions.isEmpty()) "Sleep" else "Sleep → " + actions.joinToString(" · ")
    }

    private fun buildWakeSummary(
        wifiManaged: Boolean,
        wifiChanged: Boolean,
        wifiAttempted: Boolean = false,
        wifiToggleSuccess: Boolean = true,
        wifiAirplaneMode: Boolean = false,
        bluetoothManaged: Boolean,
        bluetoothChanged: Boolean,
        syncthing: Boolean
    ): String {
        val actions = buildList {
            if (wifiManaged) {
                add(
                    when {
                        wifiAttempted && !wifiToggleSuccess ->
                            "Wi-Fi toggle failed" +
                                if (wifiAirplaneMode) {
                                    " · Airplane mode is enabled"
                                } else {
                                    ""
                                }
                        wifiChanged -> "Wi-Fi restored to previous state"
                        else -> "Wi-Fi unchanged"
                    }
                )
            }
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
            addAction(Intent.ACTION_POWER_CONNECTED)
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
            .setSmallIcon(R.drawable.ic_notification_sleepmanager)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    override fun onDestroy() {
        cancelNetworkReadyWait()
        pendingNetworkRestoreAfterHelper = false
        disableRestoreRequested = false
        if (!AppPreferences.isEnabled(this)) {
            cancelSleepDelay()
        } else {
            handler.removeCallbacks(sleepGraceRunnable)
            sleepGracePending = false
        }
        handler.removeCallbacks(sleepRadioRunnable)
        cancelTailscaleVerification()
        handler.removeCallbacks(thorCloseGuardRunnable)
        handler.removeCallbacks(thorScreenOnRecheckRunnable)
        handler.removeCallbacks(thorDockDisconnectRunnable)
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
