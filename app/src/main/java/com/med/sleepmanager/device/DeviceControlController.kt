package com.med.sleepmanager.device

import android.annotation.SuppressLint
import android.content.Context
import android.os.IBinder
import android.os.Parcel
import android.os.PowerManager
import android.provider.Settings
import java.nio.charset.Charset

object DeviceControlController {
    private const val PSERVER_SERVICE = "PServerBinder"
    private const val CHARGING_SEPARATION_KEY = "is_charging_separation"

    data class ControlCapabilities(
        val pServerAvailable: Boolean,
        val batterySaverControl: Boolean,
        val chargingSeparationControl: Boolean
    )

    fun capabilities(context: Context): ControlCapabilities {
        val pServer = findPServerBinder() != null
        return ControlCapabilities(
            pServerAvailable = pServer,
            batterySaverControl = pServer,
            chargingSeparationControl =
                pServer && chargingSeparationState(context) != null
        )
    }

    fun isPServerAvailable(): Boolean = findPServerBinder() != null

    fun supportsBatterySaverControl(context: Context): Boolean =
        capabilities(context).batterySaverControl

    fun batterySaverEnabled(context: Context): Boolean {
        val powerManager =
            context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isPowerSaveMode == true
    }

    fun setBatterySaverEnabled(enabled: Boolean): Boolean {
        val value = if (enabled) 1 else 0
        return executePrivileged("cmd power set-mode $value").isSuccess
    }

    fun chargingSeparationState(context: Context): Boolean? =
        runCatching {
            val raw = Settings.System.getString(
                context.contentResolver,
                CHARGING_SEPARATION_KEY
            ) ?: return null

            when (raw.trim()) {
                "1", "true" -> true
                "0", "false" -> false
                else -> null
            }
        }.getOrNull()

    fun supportsChargingSeparationControl(context: Context): Boolean =
        capabilities(context).chargingSeparationControl

    fun setChargingSeparationEnabled(enabled: Boolean): Boolean {
        val value = if (enabled) 1 else 0
        return executePrivileged(
            "settings put system $CHARGING_SEPARATION_KEY $value"
        ).isSuccess
    }

    @SuppressLint("PrivateApi")
    private fun findPServerBinder(): IBinder? =
        runCatching {
            val serviceManager = Class.forName("android.os.ServiceManager")
            val getService =
                serviceManager.getDeclaredMethod(
                    "getService",
                    String::class.java
                )
            getService.invoke(null, PSERVER_SERVICE) as? IBinder
        }.getOrNull()

    private fun executePrivileged(command: String): Result<String?> {
        val binder =
            findPServerBinder()
                ?: return Result.failure(
                    IllegalStateException("PServerBinder unavailable")
                )

        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeStringArray(arrayOf(command, "1"))
            binder.transact(0, data, reply, 0)
            val output =
                reply.createByteArray()
                    ?.toString(Charset.defaultCharset())
                    ?.trim()
                    ?.takeUnless { it == "null" }
            Result.success(output)
        } catch (t: Throwable) {
            Result.failure(t)
        } finally {
            data.recycle()
            reply.recycle()
        }
    }
}
