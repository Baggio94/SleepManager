package com.med.sleepmanager

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ThorProtectionPrefs {
    private const val PREFS = "thor_protection_test"
    private const val KEY_RUNNING = "running"
    private const val KEY_LOG = "log"

    fun setRunning(context: Context, running: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_RUNNING, running)
            .apply()
    }

    fun isRunning(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_RUNNING, false)

    fun clearLog(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LOG, "")
            .apply()
    }

    fun getLog(context: Context): String {
        val value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LOG, "")
            .orEmpty()
        return value.ifBlank { "No protection events yet" }
    }

    @Synchronized
    fun append(context: Context, message: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val current = prefs.getString(KEY_LOG, "").orEmpty()
        val updated = (current.lineSequence().filter { it.isNotBlank() }.toList() + "$timestamp  $message")
            .takeLast(14)
            .joinToString("\n")
        prefs.edit().putString(KEY_LOG, updated).apply()
    }
}
