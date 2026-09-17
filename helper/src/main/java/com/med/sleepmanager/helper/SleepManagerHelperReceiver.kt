package com.med.sleepmanager.helper

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.util.Log

class SleepManagerHelperReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "SleepManagerHelper"
        private const val ACTION_SLEEP = "com.med.sleepmanager.helper.action.SLEEP"
        private const val ACTION_WAKE = "com.med.sleepmanager.helper.action.WAKE"
        private const val ACTION_RESTORE = "com.med.sleepmanager.helper.action.RESTORE"
        private const val ACTION_QUERY = "com.med.sleepmanager.helper.action.QUERY_STATE"
        private const val ACTION_STATE = "com.med.sleepmanager.helper.action.STATE"
        private const val ACTION_RESULT = "com.med.sleepmanager.helper.action.RESULT"
        private const val MAIN_PACKAGE = "com.med.sleepmanager"
        private const val PERMISSION = "com.med.sleepmanager.permission.CONTROL_HELPER"

        private const val EXTRA_WIFI = "wifi"
        private const val EXTRA_BLUETOOTH = "bluetooth"
        private const val EXTRA_WIFI_STATE = "wifi_state"
        private const val EXTRA_BLUETOOTH_STATE = "bluetooth_state"
        private const val EXTRA_PHASE = "phase"
        private const val EXTRA_WIFI_MANAGED = "wifi_managed"
        private const val EXTRA_WIFI_PREVIOUS = "wifi_previous"
        private const val EXTRA_WIFI_CHANGED = "wifi_changed"
        private const val EXTRA_BLUETOOTH_MANAGED = "bluetooth_managed"
        private const val EXTRA_BLUETOOTH_PREVIOUS = "bluetooth_previous"
        private const val EXTRA_BLUETOOTH_CHANGED = "bluetooth_changed"
        private const val PHASE_SLEEP = "sleep"
        private const val PHASE_WAKE = "wake"

        private const val PREFS = "helper_state"
        private const val KEY_CYCLE_ACTIVE = "cycle_active"
        private const val KEY_WIFI_PREVIOUS = "wifi_previous"
        private const val KEY_WIFI_CHANGED = "wifi_changed"
        private const val KEY_BT_PREVIOUS = "bt_previous"
        private const val KEY_BT_CHANGED = "bt_changed"
        private const val KEY_WIFI_MANAGED = "wifi_managed"
        private const val KEY_BT_MANAGED = "bt_managed"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_SLEEP -> enterSleep(
                context,
                manageWifi = intent.getBooleanExtra(EXTRA_WIFI, false),
                manageBluetooth = intent.getBooleanExtra(EXTRA_BLUETOOTH, false)
            )
            ACTION_WAKE, ACTION_RESTORE -> restore(context)
            ACTION_QUERY -> reportCurrentState(context)
        }
    }

    private fun reportCurrentState(context: Context) {
        val wifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val bluetooth = BluetoothAdapter.getDefaultAdapter()

        val wifiOn = safeWifiState(wifiManager)
        val bluetoothOn = safeBluetoothState(bluetooth)

        val response = Intent(ACTION_STATE)
            .setPackage(MAIN_PACKAGE)
            .putExtra(EXTRA_WIFI_STATE, wifiOn)
            .putExtra(EXTRA_BLUETOOTH_STATE, bluetoothOn)

        context.sendBroadcast(response, PERMISSION)
        Log.i(TAG, "Current state reported: wifi=$wifiOn bluetooth=$bluetoothOn")
    }

    private fun enterSleep(
        context: Context,
        manageWifi: Boolean,
        manageBluetooth: Boolean
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_CYCLE_ACTIVE, false)) {
            Log.i(TAG, "Sleep cycle already active; duplicate request ignored")
            return
        }

        val wifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val bluetooth = BluetoothAdapter.getDefaultAdapter()

        val wifiWasOn = safeWifiState(wifiManager)
        val bluetoothWasOn = safeBluetoothState(bluetooth)

        var wifiChanged = false
        var bluetoothChanged = false

        if (manageWifi && wifiWasOn) {
            wifiChanged = setWifi(wifiManager, false)
        }

        if (manageBluetooth && bluetoothWasOn) {
            bluetoothChanged = setBluetooth(bluetooth, false)
        }

        prefs.edit()
            .putBoolean(KEY_CYCLE_ACTIVE, true)
            .putBoolean(KEY_WIFI_PREVIOUS, wifiWasOn)
            .putBoolean(KEY_WIFI_CHANGED, wifiChanged)
            .putBoolean(KEY_BT_PREVIOUS, bluetoothWasOn)
            .putBoolean(KEY_BT_CHANGED, bluetoothChanged)
            .putBoolean(KEY_WIFI_MANAGED, manageWifi)
            .putBoolean(KEY_BT_MANAGED, manageBluetooth)
            .apply()

        Log.i(
            TAG,
            "Sleep applied: wifiWasOn=$wifiWasOn wifiChanged=$wifiChanged " +
                "btWasOn=$bluetoothWasOn btChanged=$bluetoothChanged"
        )

        sendResult(
            context = context,
            phase = PHASE_SLEEP,
            wifiManaged = manageWifi,
            wifiPrevious = wifiWasOn,
            wifiChanged = wifiChanged,
            bluetoothManaged = manageBluetooth,
            bluetoothPrevious = bluetoothWasOn,
            bluetoothChanged = bluetoothChanged
        )
    }

    private fun restore(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_CYCLE_ACTIVE, false)) {
            Log.i(TAG, "No active sleep cycle to restore")
            return
        }

        val wifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val bluetooth = BluetoothAdapter.getDefaultAdapter()

        val wifiPrevious = prefs.getBoolean(KEY_WIFI_PREVIOUS, false)
        val wifiChanged = prefs.getBoolean(KEY_WIFI_CHANGED, false)
        val bluetoothPrevious = prefs.getBoolean(KEY_BT_PREVIOUS, false)
        val bluetoothChanged = prefs.getBoolean(KEY_BT_CHANGED, false)
        val wifiManaged = prefs.getBoolean(KEY_WIFI_MANAGED, false)
        val bluetoothManaged = prefs.getBoolean(KEY_BT_MANAGED, false)

        val wifiRestored = if (wifiChanged && wifiPrevious) {
            setWifi(wifiManager, true)
        } else {
            false
        }
        val bluetoothRestored = if (bluetoothChanged && bluetoothPrevious) {
            setBluetooth(bluetooth, true)
        } else {
            false
        }

        prefs.edit().clear().apply()

        Log.i(
            TAG,
            "Wake restored: wifi=$wifiPrevious (changed=$wifiChanged) " +
                "bluetooth=$bluetoothPrevious (changed=$bluetoothChanged)"
        )

        sendResult(
            context = context,
            phase = PHASE_WAKE,
            wifiManaged = wifiManaged,
            wifiPrevious = wifiPrevious,
            wifiChanged = wifiRestored,
            bluetoothManaged = bluetoothManaged,
            bluetoothPrevious = bluetoothPrevious,
            bluetoothChanged = bluetoothRestored
        )
    }

    private fun sendResult(
        context: Context,
        phase: String,
        wifiManaged: Boolean,
        wifiPrevious: Boolean,
        wifiChanged: Boolean,
        bluetoothManaged: Boolean,
        bluetoothPrevious: Boolean,
        bluetoothChanged: Boolean
    ) {
        val response = Intent(ACTION_RESULT)
            .setPackage(MAIN_PACKAGE)
            .putExtra(EXTRA_PHASE, phase)
            .putExtra(EXTRA_WIFI_MANAGED, wifiManaged)
            .putExtra(EXTRA_WIFI_PREVIOUS, wifiPrevious)
            .putExtra(EXTRA_WIFI_CHANGED, wifiChanged)
            .putExtra(EXTRA_BLUETOOTH_MANAGED, bluetoothManaged)
            .putExtra(EXTRA_BLUETOOTH_PREVIOUS, bluetoothPrevious)
            .putExtra(EXTRA_BLUETOOTH_CHANGED, bluetoothChanged)

        context.sendBroadcast(response, PERMISSION)
    }

    private fun safeWifiState(wifiManager: WifiManager?): Boolean =
        try {
            wifiManager?.isWifiEnabled == true
        } catch (t: Throwable) {
            Log.e(TAG, "Unable to read Wi-Fi state", t)
            false
        }

    @Suppress("DEPRECATION")
    private fun setWifi(wifiManager: WifiManager?, enabled: Boolean): Boolean =
        try {
            val result = wifiManager?.setWifiEnabled(enabled) == true
            Log.i(TAG, "Wi-Fi -> $enabled result=$result")
            result
        } catch (t: Throwable) {
            Log.e(TAG, "Wi-Fi toggle failed", t)
            false
        }

    private fun safeBluetoothState(adapter: BluetoothAdapter?): Boolean =
        try {
            adapter?.isEnabled == true
        } catch (t: Throwable) {
            Log.e(TAG, "Unable to read Bluetooth state", t)
            false
        }

    @Suppress("DEPRECATION")
    private fun setBluetooth(adapter: BluetoothAdapter?, enabled: Boolean): Boolean =
        try {
            val result = if (enabled) {
                adapter?.enable() == true
            } else {
                adapter?.disable() == true
            }
            Log.i(TAG, "Bluetooth -> $enabled result=$result")
            result
        } catch (t: Throwable) {
            Log.e(TAG, "Bluetooth toggle failed", t)
            false
        }
}
