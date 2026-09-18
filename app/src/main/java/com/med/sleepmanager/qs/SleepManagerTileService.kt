package com.med.sleepmanager.qs

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.med.sleepmanager.data.AppPreferences
import com.med.sleepmanager.data.SleepCycleStore
import com.med.sleepmanager.integration.HelperController
import com.med.sleepmanager.integration.connector.SyncthingConnector
import com.med.sleepmanager.protection.ThorDeviceAdminReceiver
import com.med.sleepmanager.protection.ThorLidMonitor
import com.med.sleepmanager.service.SleepManagerService

class SleepManagerTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        updateTile()
    }

    override fun onClick() {
        super.onClick()

        if (isLocked) {
            unlockAndRun { toggleManager() }
        } else {
            toggleManager()
        }
    }

    private fun toggleManager() {
        if (AppPreferences.isEnabled(this)) {
            disableManager()
        } else {
            enableManager()
        }
        updateTile()
    }

    private fun enableManager() {
        val helperNeeded =
            AppPreferences.manageWifi(this) || AppPreferences.manageBluetooth(this)

        if (helperNeeded && !HelperController.isInstalled(this)) {
            Toast.makeText(
                this,
                "SleepManager helper required for Wi-Fi / Bluetooth",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (AppPreferences.manageThorProtection(this)) {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = ComponentName(this, ThorDeviceAdminReceiver::class.java)

            if (!ThorLidMonitor.isSupported() || !dpm.isAdminActive(admin)) {
                Toast.makeText(
                    this,
                    "Open SleepManager to finish AYN Thor protection setup",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
        }

        AppPreferences.setEnabled(this, true)

        try {
            val service = Intent(this, SleepManagerService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service)
            else startService(service)

            AppPreferences.recordEvent(this, "SleepManager enabled • Quick Settings")
        } catch (t: Throwable) {
            AppPreferences.setEnabled(this, false)
            AppPreferences.recordEvent(
                this,
                "Quick Settings → Unable to start ${t.javaClass.simpleName}"
            )
            Toast.makeText(
                this,
                "Unable to start SleepManager",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun disableManager() {
        AppPreferences.setEnabled(this, false)
        stopService(Intent(this, SleepManagerService::class.java))

        HelperController.restoreNow(this)
        SleepCycleStore.markHelperRestored(this)

        val change = SleepCycleStore.connectorChange(this, SyncthingConnector.id)
        if (change != null) {
            val result = SyncthingConnector.wake(this, change.restoreToken)
            if (result.success) {
                SleepCycleStore.clearConnectorChange(this, SyncthingConnector.id)
            } else {
                AppPreferences.recordEvent(this, "Syncthing restore pending")
            }
        }

        SleepCycleStore.completeIfRestored(this)
        AppPreferences.recordEvent(this, "SleepManager disabled • Quick Settings")
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val enabled = AppPreferences.isEnabled(this)

        tile.label = "SleepManager"
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.contentDescription =
            if (enabled) "SleepManager is active" else "SleepManager is off"
        tile.updateTile()
    }

    companion object {
        fun requestRefresh(context: Context) {
            requestListeningState(
                context,
                ComponentName(context, SleepManagerTileService::class.java)
            )
        }
    }
}
