package com.med.sleepmanager

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
import android.text.format.DateFormat
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.med.sleepmanager.data.AppPreferences
import com.med.sleepmanager.data.SleepCycleStore
import com.med.sleepmanager.diagnostics.DiagnosticsBuilder
import com.med.sleepmanager.integration.HelperController
import com.med.sleepmanager.integration.SyncthingController
import com.med.sleepmanager.integration.connector.SyncthingConnector
import com.med.sleepmanager.protection.ThorDeviceAdminReceiver
import com.med.sleepmanager.protection.ThorLidMonitor
import com.med.sleepmanager.service.SleepManagerService
import com.med.sleepmanager.ui.theme.SleepManagerTheme
import java.util.Date

class MainActivity : ComponentActivity() {

    private var activityRefreshToken by mutableIntStateOf(0)
    private var currentWifiState by mutableStateOf<Boolean?>(null)
    private var currentBluetoothState by mutableStateOf<Boolean?>(null)
    private var helperStateReceiverRegistered = false
    private var pendingThorAdminEnable = false

    private val statusRefreshHandler = Handler(Looper.getMainLooper())
    private val statusRefreshRunnable = object : Runnable {
        override fun run() {
            if (!isFinishing && !isDestroyed) {
                // Refresh the real radio states through the compatibility helper.
                HelperController.requestState(this@MainActivity)

                // Re-read every UI-facing state while the Activity is visible:
                // manager/service state, enabled actions, helper availability,
                // Syncthing targets/selection/version, behavior recap and last activity.
                activityRefreshToken++

                statusRefreshHandler.postDelayed(this, STATUS_REFRESH_INTERVAL_MS)
            }
        }
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
            stopService(Intent(this, SleepManagerService::class.java))

            HelperController.restoreNow(this)
            SleepCycleStore.markHelperRestored(this)

            restoreSyncthingTransactionNow()
            SleepCycleStore.completeIfRestored(this)

            AppPreferences.recordEvent(this, "SleepManager disabled")
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
        } catch (t: Throwable) {
            AppPreferences.setEnabled(this, false)
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

    private fun copyDiagnostics() {
        val diagnostics = DiagnosticsBuilder.build(
            context = this,
            wifiState = currentWifiState,
            bluetoothState = currentBluetoothState
        )
        val clipboard =
            getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(
            ClipData.newPlainText("SleepManager diagnostics", diagnostics)
        )
        Toast.makeText(this, "Diagnostics copied", Toast.LENGTH_SHORT).show()
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
        var thorProtectionEnabled by remember(refreshToken) {
            mutableStateOf(AppPreferences.manageThorProtection(this))
        }
        var sleepGraceMs by remember(refreshToken) {
            mutableStateOf(AppPreferences.sleepGraceMs(this))
        }
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
                        Text("1. Turn the screen off normally.")
                        Text(
                            if (sleepGraceMs > 0L) {
                                "2. Leave it off for more than ${sleepGraceMs / 1000}s so the grace period can finish."
                            } else {
                                "2. Leave it off for a few seconds."
                            }
                        )
                        Text("3. Wake the device normally, then reopen SleepManager.")
                        Text("4. Last activity should show the wake result. Copy diagnostics should show Transaction → Active: false.")
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showTestDialog = false }) {
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

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "SleepManager",
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Smart sleep automation",
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
                                    "Current: ${if (it) "ON" else "OFF"}"
                                } ?: "Current: CHECKING…"
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
                                    "Current: ${if (it) "ON" else "OFF"}"
                                } ?: "Current: CHECKING…"
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

                        HorizontalDivider(
                            modifier = Modifier.padding(start = 64.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        SettingRow(
                            icon = R.drawable.ic_airplane,
                            title = "Airplane mode",
                            subtitle = "Planned - Working on a safe no-ADB method",
                            checked = false,
                            enabled = false,
                            onCheckedChange = {}
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
                            onChange = { value ->
                                sleepGraceMs = value
                                AppPreferences.setSleepGraceMs(this@MainActivity, value)
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
                        SettingRow(
                            icon = R.drawable.ic_sync,
                            title = "Syncthing‑Fork",
                            subtitle = selectedTarget?.displayName
                                ?: "No compatible Syncthing‑Fork build detected",
                            checked = syncthingEnabled && selectedTarget != null,
                            enabled = selectedTarget != null,
                            onCheckedChange = {
                                syncthingEnabled = it
                                AppPreferences.setManageSyncthing(this@MainActivity, it)

                                if (!it && managerEnabled) {
                                    restoreSyncthingTransactionNow()
                                    SleepCycleStore.completeIfRestored(this@MainActivity)
                                }
                            }
                        )

                        if (selectedTarget != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        start = 64.dp,
                                        end = 16.dp,
                                        bottom = 12.dp
                                    ),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        SyncthingController.open(this@MainActivity)
                                    }
                                ) {
                                    Text("Open")
                                }

                                if (targets.size > 1) {
                                    TextButton(
                                        onClick = { showTargetDialog = true }
                                    ) {
                                        Text("Change target")
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    BehaviorCard(
                        wifi = wifiEnabled && helperInstalled,
                        bluetooth = bluetoothEnabled && helperInstalled,
                        syncthing = syncthingEnabled && selectedTarget != null,
                        thorProtection = thorProtectionEnabled && thorAdminActive,
                        sleepGraceMs = sleepGraceMs
                    )
                }

                if (managerEnabled) {
                    item {
                        Button(
                            onClick = { finishSetup() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Finish setup")
                        }
                    }
                }

                item {
                    LastActivityCard(this@MainActivity)
                }

                item {
                    DiagnosticsCard(
                        onCopy = { copyDiagnostics() }
                    )
                }
            }
        }
    }
    companion object {
        private const val STATUS_REFRESH_INTERVAL_MS = 1000L
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
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
                            .padding(10.dp)
                            .size(24.dp)
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
                onClick = onToggle,
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
    onCheckedChange: (Boolean) -> Unit
) {
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
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
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
                    alpha = if (enabled) 1f else 0.55f
                )
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (enabled) 1f else 0.6f
                )
            )

            status?.let { currentStatus ->
                val isOn = currentStatus.contains("ON")
                Text(
                    currentStatus,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        currentStatus.contains("CHECKING") -> MaterialTheme.colorScheme.onSurfaceVariant
                        isOn -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
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
}

@Composable
private fun SleepGraceSelector(
    valueMs: Long,
    onChange: (Long) -> Unit
) {
    val options = listOf(
        "Immediate" to 0L,
        "3 s" to 3000L,
        "5 s" to 5000L,
        "10 s" to 10000L
    )

    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "Sleep grace period",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Text(
            "Wait before applying sleep actions. If the screen wakes during this period, nothing is changed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { (label, value) ->
                FilterChip(
                    selected = valueMs == value,
                    onClick = { onChange(value) },
                    label = { Text(label) }
                )
            }
        }
    }
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
    thorProtection: Boolean,
    sleepGraceMs: Long
) {
    val sleepLines = buildList {
        if (sleepGraceMs > 0L && (wifi || bluetooth || syncthing)) {
            add("Wait ${sleepGraceMs / 1000}s before applying sleep actions")
        }
        if (syncthing) add("Stop Syncthing‑Fork")
        if (wifi) add("Turn Wi‑Fi off")
        if (bluetooth) add("Turn Bluetooth off")
        if (thorProtection) add("Protect AYN Thor against closed-lid wake-ups")
    }

    val wakeLines = buildList {
        if (wifi) add("Restore Wi‑Fi to its previous state")
        if (bluetooth) add("Restore Bluetooth to its previous state")
        if (syncthing) add("Resume Syncthing‑Fork")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Current behavior",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            BehaviorGroup(
                title = "When screen turns OFF",
                lines = sleepLines.ifEmpty { listOf("No sleep actions selected") }
            )

            BehaviorGroup(
                title = "When screen turns ON",
                lines = wakeLines.ifEmpty { listOf("Nothing to restore") }
            )

            if (wifi || bluetooth) {
                Text(
                    "SleepManager restores only states it changed. If Wi‑Fi or Bluetooth was already off before sleep, it stays off after wake.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (thorProtection) {
                Text(
                    "AYN Thor: if the device wakes while the lid is still closed, SleepManager returns it to sleep without restoring normal wake actions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
private fun LastActivityCard(context: Context) {
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
                    else -> null
                }
                val detail = when (subject) {
                    "Wi‑Fi" -> action.removePrefix("Wi‑Fi ").replaceFirstChar { it.uppercase() }
                    "Bluetooth" -> action.removePrefix("Bluetooth ").replaceFirstChar { it.uppercase() }
                    "Syncthing" -> action.removePrefix("Syncthing ").replaceFirstChar { it.uppercase() }
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
    }
}

@Composable
private fun DiagnosticsCard(
    onCopy: () -> Unit
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
                "Diagnostics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                "Copy app, device, transaction and recent activity details for troubleshooting.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(
                onClick = onCopy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Copy diagnostics")
            }
        }
    }
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
                        onClick = { onSelect(target) },
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
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
