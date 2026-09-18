package com.med.sleepmanager.diagnostics

import android.content.Context
import android.os.Build
import com.med.sleepmanager.data.AppPreferences
import com.med.sleepmanager.data.EventHistoryStore
import com.med.sleepmanager.data.SleepCycleStore
import com.med.sleepmanager.integration.HelperController
import com.med.sleepmanager.integration.SyncthingController
import com.med.sleepmanager.integration.connector.SyncthingConnector
import com.med.sleepmanager.protection.ThorLidMonitor
import com.med.sleepmanager.service.SleepManagerService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiagnosticsBuilder {

    fun build(
        context: Context,
        wifiState: Boolean?,
        bluetoothState: Boolean?
    ): String {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionName = packageInfo.versionName ?: "unknown"
        val versionCode = if (Build.VERSION.SDK_INT >= 28) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }

        val helperVersion = runCatching {
            context.packageManager
                .getPackageInfo(HelperController.PACKAGE, 0)
                .versionName
        }.getOrNull()

        val syncthing = SyncthingController.selectedTarget(context)
        val cycle = SleepCycleStore.current(context)
        val syncthingPending =
            SleepCycleStore.hasConnectorChange(context, SyncthingConnector.id)

        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        return buildString {
            appendLine("SleepManager diagnostics")
            appendLine("Generated: \${formatter.format(Date())}")
            appendLine()
            appendLine("App")
            appendLine("- Version: \$versionName (\$versionCode)")
            appendLine("- Enabled: \${AppPreferences.isEnabled(context)}")
            appendLine("- Service running: \${SleepManagerService.running}")
            appendLine("- Sleep grace: \${AppPreferences.sleepGraceMs(context)} ms")
            appendLine()
            appendLine("Device")
            appendLine("- Model: \${Build.MANUFACTURER} \${Build.MODEL}")
            appendLine("- Android: \${Build.VERSION.RELEASE} (SDK \${Build.VERSION.SDK_INT})")
            appendLine("- Thor hall support: \${ThorLidMonitor.isSupported()}")
            appendLine()
            appendLine("Selected actions")
            appendLine("- Wi-Fi: \${AppPreferences.manageWifi(context)}")
            appendLine("- Bluetooth: \${AppPreferences.manageBluetooth(context)}")
            appendLine("- Syncthing-Fork: \${AppPreferences.manageSyncthing(context)}")
            appendLine("- Thor protection: \${AppPreferences.manageThorProtection(context)}")
            appendLine()
            appendLine("Current state")
            appendLine("- Wi-Fi: \${formatState(wifiState)}")
            appendLine("- Bluetooth: \${formatState(bluetoothState)}")
            appendLine("- Helper: \${if (helperVersion != null) "installed • \$helperVersion" else "not installed"}")
            appendLine(
                "- Syncthing target: " +
                    if (syncthing != null) "\${syncthing.displayName} • \${syncthing.packageName}"
                    else "not detected"
            )
            appendLine()
            appendLine("Transaction")
            appendLine("- Active: \${cycle.active}")
            appendLine("- Cycle id: \${cycle.cycleId}")
            appendLine("- Helper expected: \${cycle.helperExpected}")
            appendLine("- Helper sleep requested: \${cycle.helperSleepRequested}")
            appendLine("- Helper restored: \${cycle.helperRestored}")
            appendLine("- Wi-Fi managed: \${cycle.wifiManaged}")
            appendLine("- Bluetooth managed: \${cycle.bluetoothManaged}")
            appendLine("- Syncthing restore pending: \$syncthingPending")

            val events = EventHistoryStore.recent(context)
            appendLine()
            appendLine("Recent activity (\${events.size})")
            if (events.isEmpty()) {
                appendLine("- none")
            } else {
                events.forEach { event ->
                    appendLine(
                        "- \${formatter.format(Date(event.timestamp))} • \${event.message}"
                    )
                }
            }
        }
    }

    private fun formatState(value: Boolean?): String = when (value) {
        true -> "ON"
        false -> "OFF"
        null -> "unknown"
    }
}
