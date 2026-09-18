package com.med.sleepmanager

import android.app.TimePickerDialog
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.net.Uri
import android.text.format.DateFormat
import android.provider.Settings
import android.widget.Toast
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.med.sleepmanager.data.AppPreferences
import com.med.sleepmanager.data.EventHistoryStore
import com.med.sleepmanager.data.SleepCycleStore
import com.med.sleepmanager.diagnostics.DiagnosticsBuilder
import com.med.sleepmanager.integration.HelperController
import com.med.sleepmanager.integration.SyncthingController
import com.med.sleepmanager.integration.TailscaleController
import com.med.sleepmanager.integration.connector.SyncthingConnector
import com.med.sleepmanager.protection.ThorDeviceAdminReceiver
import com.med.sleepmanager.protection.ThorLidMonitor
import com.med.sleepmanager.qs.SleepManagerTileService
import com.med.sleepmanager.service.SleepManagerService
import com.med.sleepmanager.ui.theme.SleepManagerTheme
import java.util.Date

import kotlinx.coroutines.launch

private fun View.performSleepManagerFeedback() {
    if (isHapticFeedbackEnabled) {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
    if (isSoundEffectsEnabled) {
        playSoundEffect(SoundEffectConstants.CLICK)
    }
}

@Composable
private fun feedbackClick(action: () -> Unit): () -> Unit {
    val view = LocalView.current
    return {
        view.performSleepManagerFeedback()
        action()
    }
}

@Composable
private fun <T> feedbackChange(action: (T) -> Unit): (T) -> Unit {
    val view = LocalView.current
    return { value ->
        view.performSleepManagerFeedback()
        action(value)
    }
}

private enum class AppSection {
    HOME,
    ADVANCED,
    ACTIVITY_LOG,
    ABOUT
}

private val AppSection.label: String
    get() = when (this) {
        AppSection.HOME -> "Home"
        AppSection.ADVANCED -> "Advanced settings"
        AppSection.ACTIVITY_LOG -> "Activity log"
        AppSection.ABOUT -> "About"
    }

private val AppSection.iconRes: Int
    get() = when (this) {
        AppSection.HOME -> R.drawable.ic_home
        AppSection.ADVANCED -> R.drawable.ic_advanced
        AppSection.ACTIVITY_LOG -> R.drawable.ic_activity_log
        AppSection.ABOUT -> R.drawable.ic_info
    }

class MainActivity : ComponentActivity() {

    private var activityRefreshToken by mutableIntStateOf(0)
    private var currentWifiState by mutableStateOf<Boolean?>(null)
    private var currentBluetoothState by mutableStateOf<Boolean?>(null)
    private var currentSyncthingState by mutableStateOf<SyncthingController.RuntimeState?>(null)
    private var currentTailscaleConnected by mutableStateOf<Boolean?>(null)
    @Volatile
    private var syncthingStateProbeRunning = false
    private var helperStateReceiverRegistered = false
    private var pendingThorAdminEnable = false

    private val statusRefreshHandler = Handler(Looper.getMainLooper())
    private val statusRefreshRunnable = object : Runnable {
        override fun run() {
            if (!isFinishing && !isDestroyed) {
                // Refresh the real radio states through the compatibility helper.
                HelperController.requestState(this@MainActivity)
                refreshIntegrationRuntimeStates()

                // Re-read every UI-facing state while the Activity is visible:
                // manager/service state, enabled actions, helper availability,
                // Syncthing targets/selection/version, behavior recap and last activity.
                activityRefreshToken++

                statusRefreshHandler.postDelayed(this, STATUS_REFRESH_INTERVAL_MS)
            }
        }
    }

    private fun refreshIntegrationRuntimeStates() {
        currentTailscaleConnected =
            if (TailscaleController.isInstalled(this)) {
                TailscaleController.isConnected(this)
            } else {
                null
            }

        if (SyncthingController.selectedTarget(this) == null) {
            currentSyncthingState = null
            return
        }

        if (syncthingStateProbeRunning) return
        syncthingStateProbeRunning = true

        Thread {
            val state = SyncthingController.runtimeState(this)
            runOnUiThread {
                currentSyncthingState = state
                syncthingStateProbeRunning = false
            }
        }.start()
    }

    private val helperStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != HelperController.ACTION_STATE) return
            currentWifiState = intent.getBooleanExtra(
                HelperController.EXTRA_WIFI_STATE,
                false
            )
            currentBluetoothState = intent.getBooleanExtra(
                HelperController.EXTRA_BLUETOOTH_STATE,
                false
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SleepManagerTheme {
                SleepManagerScreen()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        registerHelperStateReceiver()
        HelperController.requestState(this)
    }

    override fun onResume() {
        super.onResume()

        if (pendingThorAdminEnable) {
            pendingThorAdminEnable = false
            val granted = isThorAdminActive()
            AppPreferences.setManageThorProtection(this, granted)
            if (!granted) {
                Toast.makeText(
                    this,
                    "Closed-lid protection permission was not enabled",
                    Toast.LENGTH_SHORT
                ).show()
            }
            activityRefreshToken++
        }

        if (
            AppPreferences.manageThorProtection(this) &&
            !isThorAdminActive()
        ) {
            AppPreferences.setManageThorProtection(this, false)
            activityRefreshToken++
        }

        ensureServiceRunning()
        refreshRunningService()

        statusRefreshHandler.removeCallbacks(statusRefreshRunnable)
        statusRefreshRunnable.run()
    }

    override fun onPause() {
        statusRefreshHandler.removeCallbacks(statusRefreshRunnable)
        super.onPause()
    }

    override fun onStop() {
        statusRefreshHandler.removeCallbacks(statusRefreshRunnable)

        if (helperStateReceiverRegistered) {
            try {
                unregisterReceiver(helperStateReceiver)
            } catch (_: IllegalArgumentException) {
            }
            helperStateReceiverRegistered = false
        }
        super.onStop()
    }

    private fun registerHelperStateReceiver() {
        if (helperStateReceiverRegistered) return

        val filter = IntentFilter(HelperController.ACTION_STATE)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                helperStateReceiver,
                filter,
                HelperController.PERMISSION,
                null,
                Context.RECEIVER_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(
                helperStateReceiver,
                filter,
                HelperController.PERMISSION,
                null
            )
        }
        helperStateReceiverRegistered = true
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()

        // Opening Android's Device Admin confirmation also causes the Activity
        // to lose focus. Do not treat that internal permission flow like the
        // user pressing Home, otherwise the SleepManager task is removed before
        // the confirmation screen can be shown.
        if (pendingThorAdminEnable) {
            return
        }

        // SleepManager's foreground service is independent from the Activity.
        // When the user genuinely leaves via Home / gesture navigation, remove
        // only the UI task. The automation service keeps running in background.
        if (AppPreferences.isEnabled(this)) {
            finishAndRemoveTask()
        }
    }

    private fun thorAdminComponent(): ComponentName =
        ComponentName(this, ThorDeviceAdminReceiver::class.java)

    private fun isThorAdminActive(): Boolean {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(thorAdminComponent())
    }

    private fun requestThorAdmin() {
        pendingThorAdminEnable = true
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, thorAdminComponent())
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Allows SleepManager to immediately return the AYN Thor to sleep if it wakes while the lid is still closed."
            )
        }
        startActivity(intent)
    }

    private fun setThorProtectionEnabled(enabled: Boolean) {
        if (!enabled) {
            AppPreferences.setManageThorProtection(this, false)
            refreshRunningService()

            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (dpm.isAdminActive(thorAdminComponent())) {
                runCatching { dpm.removeActiveAdmin(thorAdminComponent()) }
            }
            return
        }

        if (!ThorLidMonitor.isSupported()) {
            Toast.makeText(
                this,
                "AYN Thor hall sensor not detected",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (!isThorAdminActive()) {
            requestThorAdmin()
            return
        }

        AppPreferences.setManageThorProtection(this, true)
        refreshRunningService()
    }

    private fun refreshRunningService() {
        if (!AppPreferences.isEnabled(this)) return

        try {
            val service = Intent(this, SleepManagerService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service)
            else startService(service)
        } catch (_: Throwable) {
        }
    }

    private fun finishSetup() {
        if (!AppPreferences.isEnabled(this)) {
            Toast.makeText(
                this,
                "Enable SleepManager first",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        AppPreferences.setSetupComplete(this, true)
        AppPreferences.recordEvent(this, "Setup finished • background automation active")
        finishAndRemoveTask()
    }

    private fun setManagerEnabled(enabled: Boolean) {
        if (!enabled) {
            AppPreferences.setEnabled(this, false)

            val service = Intent(this, SleepManagerService::class.java)
                .setAction(SleepManagerService.ACTION_DISABLE_AND_RESTORE)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service)
            else startService(service)

            SleepManagerTileService.requestRefresh(this)
            return
        }

        val helperNeeded =
            AppPreferences.manageWifi(this) || AppPreferences.manageBluetooth(this)

        if (helperNeeded && !HelperController.isInstalled(this)) {
            Toast.makeText(
                this,
                "Install the SleepManager compatibility helper first",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (
            AppPreferences.manageThorProtection(this) &&
            (!ThorLidMonitor.isSupported() || !isThorAdminActive())
        ) {
            Toast.makeText(
                this,
                "Enable AYN Thor closed-lid protection permission first",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        AppPreferences.setEnabled(this, true)

        try {
            val service = Intent(this, SleepManagerService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service)
            else startService(service)

            AppPreferences.recordEvent(this, "SleepManager enabled")
            SleepManagerTileService.requestRefresh(this)
        } catch (t: Throwable) {
            AppPreferences.setEnabled(this, false)
            SleepManagerTileService.requestRefresh(this)
            Toast.makeText(
                this,
                "Unable to start: ${t.javaClass.simpleName}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun restoreSyncthingTransactionNow() {
        val change =
            SleepCycleStore.connectorChange(this, SyncthingConnector.id)
                ?: return

        val result = SyncthingConnector.wake(this, change.restoreToken)

        if (result.success) {
            SleepCycleStore.clearConnectorChange(this, SyncthingConnector.id)
        } else {
            AppPreferences.recordEvent(this, "Syncthing restore pending")
        }
    }

    private fun showTimePicker(
        initialMinutes: Int,
        onSelected: (Int) -> Unit
    ) {
        val hour = initialMinutes / 60
        val minute = initialMinutes % 60

        TimePickerDialog(
            this,
            { _, selectedHour, selectedMinute ->
                onSelected(selectedHour * 60 + selectedMinute)
            },
            hour,
            minute,
            true
        ).show()
    }

    private fun copyDiagnostics() {
        val diagnostics = DiagnosticsBuilder.build(
            context = this,
            wifiState = currentWifiState,
            bluetoothState = currentBluetoothState,
            syncthingState = currentSyncthingState,
            tailscaleConnected = currentTailscaleConnected
        )
        val clipboard =
            getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(
            ClipData.newPlainText("SleepManager log", diagnostics)
        )
        Toast.makeText(this, "Log copied", Toast.LENGTH_SHORT).show()
    }

    private fun ensureServiceRunning() {
        if (!AppPreferences.isEnabled(this) || SleepManagerService.running) return

        try {
            val service = Intent(this, SleepManagerService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service)
            else startService(service)
        } catch (_: Throwable) {
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SleepManagerScreen() {
        val refreshToken = activityRefreshToken
        var showTargetDialog by remember { mutableStateOf(false) }
        var showTestDialog by remember { mutableStateOf(false) }
        var currentSection by remember { mutableStateOf(AppSection.HOME) }
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val drawerScope = rememberCoroutineScope()

        var managerEnabled by remember(refreshToken) {
            mutableStateOf(AppPreferences.isEnabled(this))
        }
        var wifiEnabled by remember(refreshToken) {
            mutableStateOf(AppPreferences.manageWifi(this))
        }
        var bluetoothEnabled by remember(refreshToken) {
            mutableStateOf(AppPreferences.manageBluetooth(this))
        }
        var syncthingEnabled by remember(refreshToken) {
            mutableStateOf(AppPreferences.manageSyncthing(this))
        }
        var tailscaleEnabled by remember(refreshToken) {
            mutableStateOf(AppPreferences.manageTailscale(this))
        }
        var thorProtectionEnabled by remember(refreshToken) {
            mutableStateOf(AppPreferences.manageThorProtection(this))
        }
        var sleepGraceMs by remember(refreshToken) {
            mutableStateOf(AppPreferences.sleepGraceMs(this))
        }
        var customDelayEnabled by remember(refreshToken) {
            mutableStateOf(AppPreferences.customDelayEnabled(this))
        }
        var customDelayMs by remember(refreshToken) {
            mutableStateOf(AppPreferences.customDelayMs(this))
        }
        var batteryConditionEnabled by remember(refreshToken) {
            mutableStateOf(AppPreferences.batteryConditionEnabled(this))
        }
        var batteryBelowPercent by remember(refreshToken) {
            mutableStateOf(AppPreferences.batteryBelowPercent(this))
        }
        var notChargingOnly by remember(refreshToken) {
            mutableStateOf(AppPreferences.notChargingOnly(this))
        }
        var batterySaverMode by remember(refreshToken) {
            mutableStateOf(AppPreferences.batterySaverMode(this))
        }
        var scheduleEnabled by remember(refreshToken) {
            mutableStateOf(AppPreferences.scheduleEnabled(this))
        }
        var scheduleStartMinutes by remember(refreshToken) {
            mutableStateOf(AppPreferences.scheduleStartMinutes(this))
        }
        var scheduleEndMinutes by remember(refreshToken) {
            mutableStateOf(AppPreferences.scheduleEndMinutes(this))
        }
        val effectiveSleepDelayMs =
            if (customDelayEnabled) customDelayMs else sleepGraceMs

        val setupComplete = remember(refreshToken) {
            AppPreferences.isSetupComplete(this)
        }

        val helperInstalled = remember(refreshToken) {
            HelperController.isInstalled(this)
        }
        val helperVersion = remember(refreshToken) {
            runCatching {
                packageManager.getPackageInfo(HelperController.PACKAGE, 0).versionName
            }.getOrNull()
        }
        val targets = remember(refreshToken) {
            SyncthingController.installedTargets(this)
        }
        val selectedTarget = remember(refreshToken) {
            SyncthingController.selectedTarget(this)
        }
        val tailscaleInstalled = remember(refreshToken) {
            TailscaleController.isInstalled(this)
        }
        val tailscaleVersion = remember(refreshToken) {
            TailscaleController.versionName(this)
        }
        val thorProtectionSupported = remember(refreshToken) {
            ThorLidMonitor.isSupported()
        }
        val thorAdminActive = remember(refreshToken) {
            isThorAdminActive()
        }

        if (showTestDialog) {
            AlertDialog(
                onDismissRequest = { showTestDialog = false },
                title = { Text("Test sleep / wake") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("1. Choose the sleep actions you want to test below.")
                        Text("2. Enable SleepManager at the top of the app.")
                        Text("3. Turn the screen off normally.")
                        Text(
                            if (effectiveSleepDelayMs > 0L) {
                                "4. Leave it off for more than ${formatDuration(effectiveSleepDelayMs)} so the sleep delay can finish."
                            } else {
                                "4. Leave it off for a few seconds."
                            }
                        )
                        Text("5. Wake the device normally, then reopen SleepManager.")
                        Text("6. Last activity and View log should show the sleep / wake result.")
                        Text("Copy log includes the full transaction details if needed.")
                    }
                },
                confirmButton = {
                    TextButton(onClick = feedbackClick { showTestDialog = false }) {
                        Text("Got it")
                    }
                }
            )
        }

        if (showTargetDialog) {
            SyncthingTargetDialog(
                targets = targets,
                selected = selectedTarget?.packageName,
                onDismiss = { showTargetDialog = false },
                onSelect = { target ->
                    val previous = SyncthingController.selectedTarget(this)?.packageName

                    if (
                        managerEnabled &&
                        syncthingEnabled &&
                        previous != null &&
                        previous != target.packageName
                    ) {
                        val change = SleepCycleStore.connectorChange(
                            this,
                            SyncthingConnector.id
                        )
                        if (change?.restoreToken == previous) {
                            restoreSyncthingTransactionNow()
                            SleepCycleStore.completeIfRestored(this)
                        }
                    }

                    SyncthingController.select(this, target.packageName)
                    showTargetDialog = false
                    activityRefreshToken++
                }
            )
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "SleepManager",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )

                        AppSection.values().forEach { section ->
                            NavigationDrawerItem(
                                icon = {
                                    Icon(
                                        painter = painterResource(section.iconRes),
                                        contentDescription = null
                                    )
                                },
                                label = { Text(section.label) },
                                selected = currentSection == section,
                                onClick = feedbackClick {
                                    currentSection = section
                                    drawerScope.launch { drawerState.close() }
                                }
                            )
                        }
                    }
                }
            }
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                CompactSideRail(
                    currentSection = currentSection,
                    onSectionSelected = { currentSection = it },
                    onMenuClick = {
                        drawerScope.launch { drawerState.open() }
                    }
                )

                Scaffold(
                    modifier = Modifier.weight(1f),
                    containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    when (currentSection) {
                                        AppSection.HOME -> "SleepManager"
                                        AppSection.ADVANCED -> "Advanced"
                                        AppSection.ACTIVITY_LOG -> "Activity log"
                                        AppSection.ABOUT -> "About"
                                    },
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    when (currentSection) {
                                        AppSection.HOME -> "Smart sleep automation"
                                        AppSection.ADVANCED -> "Custom delay and sleep conditions"
                                        AppSection.ACTIVITY_LOG -> "Recent SleepManager activity"
                                        AppSection.ABOUT -> "App information"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                }
            ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    top = 8.dp,
                    bottom = 32.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (currentSection) {
                    AppSection.HOME -> {
                item {
                    StatusCard(
                        enabled = managerEnabled,
                        running = SleepManagerService.running,
                        onToggle = {
                            setManagerEnabled(!managerEnabled)
                            managerEnabled = AppPreferences.isEnabled(this@MainActivity)
                            activityRefreshToken++
                        }
                    )
                }

                if (!setupComplete) {
                    item {
                        OnboardingCard(
                            helperInstalled = helperInstalled,
                            helperVersion = helperVersion,
                            syncthingTarget = selectedTarget,
                            syncthingEnabled = syncthingEnabled,
                            tailscaleInstalled = tailscaleInstalled,
                            tailscaleVersion = tailscaleVersion,
                            managerEnabled = managerEnabled,
                            onShowTest = { showTestDialog = true }
                        )
                    }
                }

                item {
                    SectionTitle(
                        title = "When device sleeps",
                        subtitle = "Choose what SleepManager should temporarily switch off."
                    )
                }

                item {
                    SettingsCard {
                        SettingRow(
                            icon = R.drawable.ic_wifi,
                            title = "Wi‑Fi",
                            subtitle = if (helperInstalled) {
                                "Turn off during sleep. Restore previous state on wake."
                            } else {
                                "Compatibility helper required"
                            },
                            status = if (helperInstalled) {
                                currentWifiState?.let {
                                    "Current state: ${if (it) "ON" else "OFF"}"
                                } ?: "Current state: CHECKING…"
                            } else {
                                null
                            },
                            checked = wifiEnabled,
                            enabled = helperInstalled,
                            onCheckedChange = {
                                wifiEnabled = it
                                AppPreferences.setManageWifi(this@MainActivity, it)
                            }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(start = 64.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        SettingRow(
                            icon = R.drawable.ic_bluetooth,
                            title = "Bluetooth",
                            subtitle = if (helperInstalled) {
                                "Turn off during sleep. Restore previous state on wake."
                            } else {
                                "Compatibility helper required"
                            },
                            status = if (helperInstalled) {
                                currentBluetoothState?.let {
                                    "Current state: ${if (it) "ON" else "OFF"}"
                                } ?: "Current state: CHECKING…"
                            } else {
                                null
                            },
                            checked = bluetoothEnabled,
                            enabled = helperInstalled,
                            onCheckedChange = {
                                bluetoothEnabled = it
                                AppPreferences.setManageBluetooth(this@MainActivity, it)
                            }
                        )

                    }
                }

                item {
                    AnimatedVisibility(visible = !helperInstalled) {
                        InfoCard(
                            title = "Compatibility helper not installed",
                            text = "The helper controls Wi‑Fi and Bluetooth without root or Shizuku. It has no launcher icon and runs only when SleepManager asks it to."
                        )
                    }
                }

                item {
                    SectionTitle(
                        title = "Sleep behavior",
                        subtitle = "Control how SleepManager reacts when the screen turns off."
                    )
                }

                item {
                    SettingsCard {
                        SleepGraceSelector(
                            valueMs = sleepGraceMs,
                            customDelayEnabled = customDelayEnabled,
                            customDelayMs = customDelayMs,
                            onChange = { value ->
                                sleepGraceMs = value
                                AppPreferences.setSleepGraceMs(this@MainActivity, value)
                            },
                            onCustom = {
                                currentSection = AppSection.ADVANCED
                            }
                        )

                        if (thorProtectionSupported) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )

                            SettingRow(
                                icon = R.drawable.ic_lid_lock,
                                title = "AYN Thor closed-lid protection",
                                subtitle = "Return the Thor to sleep after accidental trigger wake-ups with the lid closed.",
                                checked = thorProtectionEnabled && thorAdminActive,
                                enabled = true,
                                onCheckedChange = { enabled ->
                                    setThorProtectionEnabled(enabled)
                                    thorProtectionEnabled =
                                        AppPreferences.manageThorProtection(this@MainActivity)
                                    activityRefreshToken++
                                }
                            )
                        }
                    }
                }

                item {
                    SectionTitle(
                        title = "App integrations",
                        subtitle = "Pause background services that don’t need to run while your device sleeps."
                    )
                }

                item {
                    SettingsCard {
                        CompactIntegrationRow(
                            icon = R.drawable.ic_sync,
                            title = "Syncthing‑Fork",
                            version = selectedTarget?.displayName
                                ?.substringAfter("•")
                                ?.trim()
                                ?: if (selectedTarget != null) "Installed" else "Not detected",
                            status = if (selectedTarget != null) {
                                when (currentSyncthingState) {
                                    SyncthingController.RuntimeState.RUNNING -> "RUNNING"
                                    SyncthingController.RuntimeState.STOPPED -> "STOPPED"
                                    SyncthingController.RuntimeState.UNKNOWN -> "UNKNOWN"
                                    null -> "CHECKING…"
                                }
                            } else {
                                null
                            },
                            checked = syncthingEnabled && selectedTarget != null,
                            enabled = selectedTarget != null,
                            onCheckedChange = {
                                syncthingEnabled = it
                                AppPreferences.setManageSyncthing(this@MainActivity, it)

                                if (!it && managerEnabled) {
                                    restoreSyncthingTransactionNow()
                                    SleepCycleStore.completeIfRestored(this@MainActivity)
                                }
                            },
                            onOpen = if (selectedTarget != null) {
                                { SyncthingController.open(this@MainActivity) }
                            } else {
                                null
                            },
                            secondaryActionLabel =
                                if (targets.size > 1) "Change target" else null,
                            onSecondaryAction =
                                if (targets.size > 1) {
                                    { showTargetDialog = true }
                                } else {
                                    null
                                }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(start = 64.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        CompactIntegrationRow(
                            icon = R.drawable.ic_tailscale,
                            title = "Tailscale",
                            version = if (tailscaleInstalled) {
                                tailscaleVersion?.substringBefore("-") ?: "Installed"
                            } else {
                                "Not detected"
                            },
                            status = if (tailscaleInstalled) {
                                currentTailscaleConnected?.let {
                                    if (it) "CONNECTED" else "DISCONNECTED"
                                } ?: "CHECKING…"
                            } else {
                                null
                            },
                            checked = tailscaleEnabled && tailscaleInstalled,
                            enabled = tailscaleInstalled,
                            onCheckedChange = {
                                tailscaleEnabled = it
                                AppPreferences.setManageTailscale(
                                    this@MainActivity,
                                    it
                                )
                            },
                            onOpen = if (tailscaleInstalled) {
                                { TailscaleController.open(this@MainActivity) }
                            } else {
                                null
                            }
                        )
                    }
                }
                item {
                    BehaviorCard(
                        wifi = wifiEnabled && helperInstalled,
                        bluetooth = bluetoothEnabled && helperInstalled,
                        syncthing = syncthingEnabled && selectedTarget != null,
                        tailscale = tailscaleEnabled && tailscaleInstalled,
                        thorProtection = thorProtectionEnabled && thorAdminActive,
                        sleepGraceMs = effectiveSleepDelayMs,
                        advancedConditions = buildList {
                            if (batteryConditionEnabled) {
                                add("Battery below ${batteryBelowPercent}%")
                            }
                            if (notChargingOnly) {
                                add("Device is not charging")
                            }
                            when (batterySaverMode) {
                                AppPreferences.BATTERY_SAVER_ON ->
                                    add("Battery Saver is ON")
                                AppPreferences.BATTERY_SAVER_OFF ->
                                    add("Battery Saver is OFF")
                            }
                            if (scheduleEnabled) {
                                add(
                                    "Time is between " +
                                        formatTime(scheduleStartMinutes) +
                                        " and " +
                                        formatTime(scheduleEndMinutes)
                                )
                            }
                        }
                    )
                }

                if (managerEnabled && !setupComplete) {
                    item {
                        Button(
                            onClick = feedbackClick { finishSetup() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Finish setup")
                        }
                    }
                }

                item {
                    LastActivityCard(
                        context = this@MainActivity,
                        onViewLog = { currentSection = AppSection.ACTIVITY_LOG },
                        onCopyLog = { copyDiagnostics() }
                    )
                }
                    }

                    AppSection.ADVANCED -> {
                        item {
                            AdvancedSleepRulesPage(
                                customDelayEnabled = customDelayEnabled,
                                customDelayMs = customDelayMs,
                                batteryConditionEnabled = batteryConditionEnabled,
                                batteryBelowPercent = batteryBelowPercent,
                                notChargingOnly = notChargingOnly,
                                batterySaverMode = batterySaverMode,
                                scheduleEnabled = scheduleEnabled,
                                scheduleStartMinutes = scheduleStartMinutes,
                                scheduleEndMinutes = scheduleEndMinutes,
                                onCustomDelayEnabledChange = {
                                    customDelayEnabled = it
                                    AppPreferences.setCustomDelayEnabled(this@MainActivity, it)

                                    if (!it) {
                                        sleepGraceMs = 0L
                                        AppPreferences.setSleepGraceMs(
                                            this@MainActivity,
                                            0L
                                        )
                                    }
                                },
                                onCustomDelayChange = {
                                    customDelayMs = it
                                    AppPreferences.setCustomDelayMs(this@MainActivity, it)
                                },
                                onBatteryConditionEnabledChange = {
                                    batteryConditionEnabled = it
                                    AppPreferences.setBatteryConditionEnabled(this@MainActivity, it)
                                },
                                onBatteryBelowPercentChange = {
                                    batteryBelowPercent = it
                                    AppPreferences.setBatteryBelowPercent(this@MainActivity, it)
                                },
                                onNotChargingOnlyChange = {
                                    notChargingOnly = it
                                    AppPreferences.setNotChargingOnly(this@MainActivity, it)
                                },
                                onBatterySaverModeChange = {
                                    batterySaverMode = it
                                    AppPreferences.setBatterySaverMode(this@MainActivity, it)
                                },
                                onScheduleEnabledChange = {
                                    scheduleEnabled = it
                                    AppPreferences.setScheduleEnabled(this@MainActivity, it)
                                },
                                onPickScheduleStart = {
                                    showTimePicker(scheduleStartMinutes) { value ->
                                        scheduleStartMinutes = value
                                        AppPreferences.setScheduleStartMinutes(
                                            this@MainActivity,
                                            value
                                        )
                                    }
                                },
                                onPickScheduleEnd = {
                                    showTimePicker(scheduleEndMinutes) { value ->
                                        scheduleEndMinutes = value
                                        AppPreferences.setScheduleEndMinutes(
                                            this@MainActivity,
                                            value
                                        )
                                    }
                                }
                            )
                        }
                    }

                    AppSection.ACTIVITY_LOG -> {
                        item {
                            ActivityLogPage(
                                context = this@MainActivity,
                                onCopyLog = { copyDiagnostics() }
                            )
                        }
                    }

                    AppSection.ABOUT -> {
                        item {
                            AboutPage(context = this@MainActivity)
                        }
                    }
                }
            }
                }
            }
        }
    }
    companion object {
        private const val STATUS_REFRESH_INTERVAL_MS = 1000L
    }
}

@Composable
private fun CompactSideRail(
    currentSection: AppSection,
    onSectionSelected: (AppSection) -> Unit,
    onMenuClick: () -> Unit
) {
    NavigationRail(
        modifier = Modifier.width(64.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        header = {
            IconButton(onClick = feedbackClick(onMenuClick)) {
                Icon(
                    painter = painterResource(R.drawable.ic_menu),
                    contentDescription = "Open navigation"
                )
            }
        }
    ) {
        AppSection.values().forEach { section ->
            NavigationRailItem(
                selected = currentSection == section,
                onClick = feedbackClick { onSectionSelected(section) },
                icon = {
                    Icon(
                        painter = painterResource(section.iconRes),
                        contentDescription = section.label
                    )
                }
            )
        }
    }
}

@Composable
private fun OnboardingCard(
    helperInstalled: Boolean,
    helperVersion: String?,
    syncthingTarget: SyncthingController.Target?,
    syncthingEnabled: Boolean,
    tailscaleInstalled: Boolean,
    tailscaleVersion: String?,
    managerEnabled: Boolean,
    onShowTest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Quick setup",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                if (helperInstalled) {
                    "✓ Compatibility helper installed${helperVersion?.let { " • $it" } ?: ""}"
                } else {
                    "• Compatibility helper not installed — only needed for Wi-Fi / Bluetooth."
                },
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                syncthingTarget?.let { "✓ ${it.displayName} detected" }
                    ?: "• Syncthing-Fork not detected — optional.",
                style = MaterialTheme.typography.bodySmall
            )

            if (syncthingEnabled && syncthingTarget != null) {
                Text(
                    "Syncthing-Fork: make sure Settings → Behaviour → Service control by broadcast is enabled.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                if (tailscaleInstalled) {
                    val version =
                        tailscaleVersion?.substringBefore("-")
                    "✓ Tailscale" +
                        (version?.let { " • $it" } ?: "") +
                        " detected"
                } else {
                    "• Tailscale not detected — optional."
                },
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                if (managerEnabled) {
                    "SleepManager is enabled. Finish setup when your selected actions look right."
                } else {
                    "Choose the actions you want below, then enable SleepManager."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            TextButton(onClick = feedbackClick(onShowTest)) {
                Text("How to test sleep / wake")
            }
        }
    }
}

@Composable
private fun StatusCard(
    enabled: Boolean,
    running: Boolean,
    onToggle: () -> Unit
) {
    val active = enabled && running

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (active) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                ) {
                    Icon(
                        painter = painterResource(
                            if (active) R.drawable.ic_shield else R.drawable.ic_shield_off
                        ),
                        contentDescription = null,
                        tint = if (active) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        modifier = Modifier
                            .padding(8.dp)
                            .size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            active -> "SleepManager is active"
                            enabled -> "SleepManager is starting"
                            else -> "SleepManager is off"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = when {
                            active -> "Selected settings turn off or pause on sleep, then restore on wake."
                            enabled -> "Background automation is starting…"
                            else -> "Enable it once, and it will run automatically in the background."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = feedbackClick(onToggle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (enabled) "Disable SleepManager"
                    else "Enable SleepManager"
                )
            }

        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    status: String? = null,
    checked: Boolean,
    enabled: Boolean,
    dimWhenDisabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val contentAlpha = if (enabled || !dimWhenDisabled) 1f else 0.55f
    val secondaryAlpha = if (enabled || !dimWhenDisabled) 1f else 0.6f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = if (enabled) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                },
                modifier = Modifier
                    .padding(10.dp)
                    .size(22.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(
                    alpha = contentAlpha
                )
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = secondaryAlpha
                )
            )

            status?.let { currentStatus ->
                val isActive =
                    currentStatus.endsWith(": ON") ||
                        currentStatus.endsWith(": RUNNING") ||
                        currentStatus.endsWith(": CONNECTED")
                Text(
                    currentStatus,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        currentStatus.contains("CHECKING") -> MaterialTheme.colorScheme.onSurfaceVariant
                        isActive -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = feedbackChange(onCheckedChange),
            enabled = enabled
        )
    }
}

@Composable
private fun CompactIntegrationRow(
    @DrawableRes icon: Int,
    title: String,
    version: String,
    status: String?,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onOpen: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = if (enabled) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = if (enabled) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .padding(10.dp)
                        .size(22.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    version,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                status?.let {
                    val active = it == "RUNNING" || it == "CONNECTED"
                    Text(
                        "State: $it",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }

        if (onOpen != null || (secondaryActionLabel != null && onSecondaryAction != null)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 70.dp, end = 16.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                onOpen?.let { open ->
                    OutlinedButton(onClick = feedbackClick(open)) {
                        Text("Open")
                    }
                }

                if (secondaryActionLabel != null && onSecondaryAction != null) {
                    TextButton(onClick = feedbackClick(onSecondaryAction)) {
                        Text(secondaryActionLabel)
                    }
                }
            }
        }
    }
}
@Composable
private fun SleepGraceSelector(
    valueMs: Long,
    customDelayEnabled: Boolean,
    customDelayMs: Long,
    onChange: (Long) -> Unit,
    onCustom: () -> Unit
) {
    val options = listOf(
        "Immediate" to 0L,
        "5 s" to 5000L,
        "10 s" to 10000L
    )

    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "Grace period",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Text(
            if (customDelayEnabled) {
                "Using custom delay from Advanced settings."
            } else {
                "Wait before applying sleep actions. If the screen wakes during this period, nothing is changed."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEach { (label, value) ->
                FilterChip(
                    selected = !customDelayEnabled && valueMs == value,
                    onClick = feedbackClick { onChange(value) },
                    enabled = !customDelayEnabled,
                    modifier = Modifier.weight(1f),
                    label = {
                        Text(
                            label,
                            maxLines = 1
                        )
                    }
                )
            }

            FilterChip(
                selected = customDelayEnabled,
                onClick = feedbackClick(onCustom),
                modifier = Modifier.weight(1f),
                label = {
                    Text(
                        "Custom",
                        maxLines = 1
                    )
                }
            )
        }

        if (customDelayEnabled) {
            TextButton(onClick = feedbackClick(onCustom)) {
                Text("Advanced • ${formatDuration(customDelayMs)}")
            }
        }
    }
}

@Composable
private fun AdvancedSleepRulesPage(
    customDelayEnabled: Boolean,
    customDelayMs: Long,
    batteryConditionEnabled: Boolean,
    batteryBelowPercent: Int,
    notChargingOnly: Boolean,
    batterySaverMode: String,
    scheduleEnabled: Boolean,
    scheduleStartMinutes: Int,
    scheduleEndMinutes: Int,
    onCustomDelayEnabledChange: (Boolean) -> Unit,
    onCustomDelayChange: (Long) -> Unit,
    onBatteryConditionEnabledChange: (Boolean) -> Unit,
    onBatteryBelowPercentChange: (Int) -> Unit,
    onNotChargingOnlyChange: (Boolean) -> Unit,
    onBatterySaverModeChange: (String) -> Unit,
    onScheduleEnabledChange: (Boolean) -> Unit,
    onPickScheduleStart: () -> Unit,
    onPickScheduleEnd: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionTitle(
            title = "Custom delay",
            subtitle = "Override the short Grace period shown on Home."
        )

        SettingsCard {
            AdvancedToggleRow(
                title = "Use custom delay",
                subtitle = if (customDelayEnabled) {
                    "Home will show Advanced • ${formatDuration(customDelayMs)}"
                } else {
                    "Home uses Immediate / 5s / 10s."
                },
                checked = customDelayEnabled,
                onCheckedChange = onCustomDelayEnabledChange
            )

            if (customDelayEnabled) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Delay before sleep actions",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    val options = listOf(
                        "1 min" to 60_000L,
                        "5 min" to 300_000L,
                        "10 min" to 600_000L,
                        "30 min" to 1_800_000L
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(options.size) { index ->
                            val (label, value) = options[index]
                            FilterChip(
                                selected = customDelayMs == value,
                                onClick = feedbackClick { onCustomDelayChange(value) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        }

        SectionTitle(
            title = "Conditions",
            subtitle = "All enabled conditions must be true."
        )

        Text(
            "Conditions are combined with AND logic. If one enabled condition is false, sleep actions are skipped.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        SettingsCard {
            AdvancedToggleRow(
                title = "Battery level",
                subtitle = if (batteryConditionEnabled) {
                    "Only below ${batteryBelowPercent}%"
                } else {
                    "Ignore battery percentage"
                },
                checked = batteryConditionEnabled,
                onCheckedChange = onBatteryConditionEnabledChange
            )

            if (batteryConditionEnabled) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val levels = listOf(20, 30, 40, 50, 60)
                        items(levels.size) { index ->
                            val level = levels[index]
                            FilterChip(
                                selected = batteryBelowPercent == level,
                                onClick = feedbackClick { onBatteryBelowPercentChange(level) },
                                label = { Text("< ${level}%") }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            AdvancedToggleRow(
                title = "Not charging",
                subtitle = if (notChargingOnly) {
                    "Only run sleep actions while unplugged"
                } else {
                    "Ignore charging state"
                },
                checked = notChargingOnly,
                onCheckedChange = onNotChargingOnlyChange
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Battery Saver",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    when (batterySaverMode) {
                        AppPreferences.BATTERY_SAVER_ON -> "Only when Android Battery Saver is ON"
                        AppPreferences.BATTERY_SAVER_OFF -> "Only when Android Battery Saver is OFF"
                        else -> "Ignore Battery Saver state"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val modes = listOf(
                        "Ignore" to AppPreferences.BATTERY_SAVER_IGNORE,
                        "ON" to AppPreferences.BATTERY_SAVER_ON,
                        "OFF" to AppPreferences.BATTERY_SAVER_OFF
                    )
                    items(modes.size) { index ->
                        val (label, mode) = modes[index]
                        FilterChip(
                            selected = batterySaverMode == mode,
                            onClick = feedbackClick { onBatterySaverModeChange(mode) },
                            label = { Text(label) }
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            AdvancedToggleRow(
                title = "Schedule",
                subtitle = if (scheduleEnabled) {
                    "Only between ${formatTime(scheduleStartMinutes)} and ${formatTime(scheduleEndMinutes)}"
                } else {
                    "No time restriction"
                },
                checked = scheduleEnabled,
                onCheckedChange = onScheduleEnabledChange
            )

            if (scheduleEnabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = feedbackClick(onPickScheduleStart),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("From ${formatTime(scheduleStartMinutes)}")
                    }
                    OutlinedButton(
                        onClick = feedbackClick(onPickScheduleEnd),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("To ${formatTime(scheduleEndMinutes)}")
                    }
                }
            }
        }
    }
}

@Composable
private fun AdvancedToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = feedbackChange(onCheckedChange)
        )
    }
}

@Composable
private fun ActivityLogPage(
    context: Context,
    onCopyLog: () -> Unit
) {
    val events = EventHistoryStore.recent(context)

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            OutlinedButton(onClick = feedbackClick(onCopyLog)) {
                Text("Copy log")
            }
        }

        if (events.isEmpty()) {
            InfoCard(
                title = "Activity log",
                text = "No recent activity"
            )
        } else {
            SettingsCard {
                events.forEachIndexed { index, event ->
                    val date = Date(event.timestamp)
                    val timestamp =
                        DateFormat.getMediumDateFormat(context).format(date) +
                            " • " +
                            DateFormat.getTimeFormat(context).format(date)

                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            event.message,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            timestamp,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (index != events.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutPage(context: Context) {
    val packageInfo = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
    }
    val helperVersion = remember {
        runCatching {
            context.packageManager
                .getPackageInfo(HelperController.PACKAGE, 0)
                .versionName
        }.getOrNull()
    }

    fun openUrl(url: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
            )
        }
    }

    fun openAppInfo() {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}")
                )
            )
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        InfoCard(
            title = "SleepManager",
            text = "Version ${packageInfo?.versionName ?: "Unknown"} • Smart sleep automation for Android."
        )

        SettingsCard {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "What SleepManager does",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Automatically applies the sleep actions you choose when the screen turns off, then restores only the states SleepManager actually changed on wake.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Grace periods, advanced conditions, Syncthing‑Fork and Tailscale integrations, transaction-safe restore, and activity logs are built around the same rule: change only what is necessary.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SectionTitle(
            title = "App details",
            subtitle = "Version and compatibility information."
        )

        SettingsCard {
            AboutInfoRow(
                label = "SleepManager",
                value = packageInfo?.versionName ?: "Unknown"
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            AboutInfoRow(
                label = "Compatibility helper",
                value = helperVersion ?: "Not installed"
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            AboutInfoRow(
                label = "Android",
                value = "${Build.VERSION.RELEASE} • API ${Build.VERSION.SDK_INT}"
            )
        }

        SectionTitle(
            title = "Support & project",
            subtitle = "Useful links for troubleshooting and development."
        )

        SettingsCard {
            AboutActionRow(
                title = "Source code",
                subtitle = "View SleepManager on GitHub",
                actionLabel = "Open",
                onClick = {
                    openUrl("https://github.com/Baggio94/SleepManager")
                }
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            AboutActionRow(
                title = "Report an issue",
                subtitle = "Open the GitHub issue tracker",
                actionLabel = "Open",
                onClick = {
                    openUrl("https://github.com/Baggio94/SleepManager/issues")
                }
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            AboutActionRow(
                title = "Android app info",
                subtitle = "Permissions, battery and storage settings",
                actionLabel = "Open",
                onClick = { openAppInfo() }
            )
        }
    }
}

@Composable
private fun AboutInfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AboutActionRow(
    title: String,
    subtitle: String,
    actionLabel: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        OutlinedButton(onClick = feedbackClick(onClick)) {
            Text(actionLabel)
        }
    }
}

private fun formatDuration(valueMs: Long): String =
    when (valueMs) {
        0L -> "Immediate"
        3000L -> "3 s"
        5000L -> "5 s"
        10000L -> "10 s"
        60000L -> "1 min"
        300000L -> "5 min"
        600000L -> "10 min"
        1800000L -> "30 min"
        else -> "${valueMs / 1000}s"
    }

private fun formatTime(minutes: Int): String {
    val safe = minutes.coerceIn(0, 1439)
    return "%02d:%02d".format(safe / 60, safe % 60)
}

@Composable
private fun InfoCard(title: String, text: String) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
private fun BehaviorCard(
    wifi: Boolean,
    bluetooth: Boolean,
    syncthing: Boolean,
    tailscale: Boolean,
    thorProtection: Boolean,
    sleepGraceMs: Long,
    advancedConditions: List<String>
) {
    var expanded by remember { mutableStateOf(false) }
    val hasSleepAction = wifi || bluetooth || syncthing || tailscale

    val sleepLines = buildList {
        if (sleepGraceMs > 0L && hasSleepAction) {
            add("Wait ${formatDuration(sleepGraceMs)}")
        }
        advancedConditions.forEach { add("Only if $it") }
        if (syncthing) add("Pause Syncthing‑Fork")
        if (tailscale) add("Disconnect Tailscale")
        if (wifi) add("Wi‑Fi off")
        if (bluetooth) add("Bluetooth off")
        if (!hasSleepAction) add("No sleep actions selected")
        if (thorProtection) add("Thor closed-lid protection")
    }

    val wakeLines = buildList {
        if (wifi) add("Restore Wi‑Fi")
        if (bluetooth) add("Restore Bluetooth")
        if (syncthing) add("Resume Syncthing‑Fork")
        if (tailscale) add("Restore Tailscale if SleepManager disconnected it")
    }

    val compactSleepSummary = buildList {
        if (sleepGraceMs > 0L && hasSleepAction) add(formatDuration(sleepGraceMs))
        if (wifi) add("Wi‑Fi")
        if (bluetooth) add("Bluetooth")
        if (syncthing) add("Syncthing")
        if (tailscale) add("Tailscale")
        if (advancedConditions.isNotEmpty()) {
            add("${advancedConditions.size} condition${if (advancedConditions.size > 1) "s" else ""}")
        }
        if (!hasSleepAction) add("No actions")
    }.joinToString(" • ")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Current behavior",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        compactSleepSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(onClick = feedbackClick { expanded = !expanded }) {
                    Text(if (expanded) "Less" else "Details")
                }
            }

            if (expanded) {
                BehaviorGroup(
                    title = "When screen turns OFF",
                    lines = sleepLines
                )

                BehaviorGroup(
                    title = "When screen turns ON",
                    lines = wakeLines.ifEmpty { listOf("Nothing to restore") }
                )

                if (wifi || bluetooth) {
                    Text(
                        "Only states changed by SleepManager are restored.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (thorProtection) {
                    Text(
                        "Thor false wakes with the lid closed are returned to sleep without normal wake restoration.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
@Composable
private fun BehaviorGroup(title: String, lines: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        lines.forEach {
            Text(
                "• $it",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun LastActivityCard(
    context: Context,
    onViewLog: () -> Unit,
    onCopyLog: () -> Unit
) {
    val event = AppPreferences.lastEvent(context)
    val time = AppPreferences.lastEventTime(context)

    val timeText = if (time > 0L) {
        val date = Date(time)
        DateFormat.getMediumDateFormat(context).format(date) +
            " • " + DateFormat.getTimeFormat(context).format(date)
    } else {
        null
    }

    val isStructured = event.contains(" → ")
    val phase = if (isStructured) event.substringBefore(" → ") else null
    val actions = if (isStructured) {
        event.substringAfter(" → ").split(" · ").filter { it.isNotBlank() }
    } else emptyList()

    Column(
        modifier = Modifier.padding(horizontal = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            "Last activity",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (phase != null) {
            Text(phase, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)

            actions.forEach { action ->
                val subject = when {
                    action.startsWith("Wi‑Fi ") -> "Wi‑Fi"
                    action.startsWith("Bluetooth ") -> "Bluetooth"
                    action.startsWith("Syncthing ") -> "Syncthing"
                    action.startsWith("Tailscale ") -> "Tailscale"
                    else -> null
                }
                val detail = when (subject) {
                    "Wi‑Fi" -> action.removePrefix("Wi‑Fi ").replaceFirstChar { it.uppercase() }
                    "Bluetooth" -> action.removePrefix("Bluetooth ").replaceFirstChar { it.uppercase() }
                    "Syncthing" -> action.removePrefix("Syncthing ").replaceFirstChar { it.uppercase() }
                    "Tailscale" -> action.removePrefix("Tailscale ").replaceFirstChar { it.uppercase() }
                    else -> action
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (subject != null) {
                        Text("$subject ·", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (detail.equals("Unchanged", ignoreCase = true)) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }
        } else {
            Text(event, style = MaterialTheme.typography.bodySmall)
        }

        timeText?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onViewLog,
                modifier = Modifier.weight(1f)
            ) {
                Text("View log")
            }

            OutlinedButton(
                onClick = onCopyLog,
                modifier = Modifier.weight(1f)
            ) {
                Text("Copy log")
            }
        }
    }
}

@Composable
private fun ActivityLogDialog(
    context: Context,
    onDismiss: () -> Unit
) {
    val events = EventHistoryStore.recent(context)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Activity log") },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (events.isEmpty()) {
                    item {
                        Text(
                            "No recent activity",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(events.size) { index ->
                        val event = events[index]
                        val date = Date(event.timestamp)
                        val timestamp =
                            DateFormat.getMediumDateFormat(context).format(date) +
                                " • " +
                                DateFormat.getTimeFormat(context).format(date)

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                event.message,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                timestamp,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = feedbackClick(onDismiss)) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun SyncthingTargetDialog(
    targets: List<SyncthingController.Target>,
    selected: String?,
    onDismiss: () -> Unit,
    onSelect: (SyncthingController.Target) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Syncthing‑Fork build") },
        text = {
            Column {
                targets.forEach { target ->
                    TextButton(
                        onClick = feedbackClick { onSelect(target) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                target.displayName,
                                modifier = Modifier.weight(1f)
                            )
                            if (target.packageName == selected) {
                                Text(
                                    "Selected",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = feedbackClick(onDismiss)) {
                Text("Close")
            }
        }
    )
}
