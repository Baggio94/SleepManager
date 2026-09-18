package com.med.sleepmanager.integration.connector

import android.content.Context

enum class ConnectorState {
    ACTIVE,
    INACTIVE,
    UNKNOWN
}

sealed interface ConnectorAvailability {
    data object Available : ConnectorAvailability
    data class Unavailable(val reason: String) : ConnectorAvailability
}

data class ConnectorSleepResult(
    val attempted: Boolean,
    val changed: Boolean,
    val restoreToken: String? = null,
    val detail: String? = null
)

data class ConnectorWakeResult(
    val attempted: Boolean,
    val success: Boolean,
    val detail: String? = null
)

interface AppConnector {
    val id: String
    val wakeRequiresNetwork: Boolean

    fun isInstalled(context: Context): Boolean
    fun availability(context: Context): ConnectorAvailability
    fun currentState(context: Context): ConnectorState
    fun sleep(context: Context): ConnectorSleepResult
    fun wake(context: Context, restoreToken: String?): ConnectorWakeResult
}
