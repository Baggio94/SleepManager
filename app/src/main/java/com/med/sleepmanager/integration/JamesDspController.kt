package com.med.sleepmanager.integration

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.audiofx.AudioEffect
import android.util.Log
import java.util.UUID

object JamesDspController {
    const val O2P_PACKAGE = "james.dsp"
    const val ROOTLESS_PACKAGE = "me.timschneeberger.rootlessjamesdsp"

    private const val ACTION_SET_POWER =
        "me.timschneeberger.rootlessjamesdsp.SET_POWER_STATE"
    private const val EXTRA_ENABLED = "rootlessjamesdsp.enabled"
    private const val RECEIVER_CLASS =
        "me.timschneeberger.rootlessjamesdsp.receiver.PowerStateReceiver"

    data class Target(
        val packageName: String,
        val displayName: String,
        val versionName: String?
    )

    enum class ProbeState {
        ENABLED,
        DISABLED,
        EFFECT_NOT_FOUND,
        BLOCKED,
        ERROR
    }

    data class PowerProbe(
        val state: ProbeState,
        val detail: String? = null
    )

    private val EFFECT_UUID =
        UUID.fromString("f27317f4-c984-4de6-9a90-545759495bf2")

    fun installedTargets(context: Context): List<Target> =
        listOf(
            O2P_PACKAGE to "JamesDSP Manager",
            ROOTLESS_PACKAGE to "RootlessJamesDSP"
        ).mapNotNull { (packageName, displayName) ->
            packageInfo(context, packageName)?.let { info ->
                Target(
                    packageName = packageName,
                    displayName = displayName,
                    versionName = info.versionName
                )
            }
        }

    fun selectedTarget(context: Context): Target? =
        installedTargets(context).firstOrNull()

    fun isInstalled(context: Context): Boolean =
        selectedTarget(context) != null

    fun open(context: Context): Boolean {
        val target = selectedTarget(context) ?: return false
        val launchIntent =
            context.packageManager.getLaunchIntentForPackage(target.packageName)
                ?: return false

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(launchIntent)
            true
        }.getOrDefault(false)
    }

    fun setPowered(
        context: Context,
        packageName: String,
        enabled: Boolean
    ): Boolean {
        if (packageInfo(context, packageName) == null) return false

        return runCatching {
            val intent =
                Intent(ACTION_SET_POWER)
                    .setComponent(ComponentName(packageName, RECEIVER_CLASS))
                    .putExtra(EXTRA_ENABLED, enabled)
                    .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)

            context.sendBroadcast(intent)
            Log.i(
                "SleepManager",
                "Sent JamesDSP power=${if (enabled) "ON" else "OFF"} to $packageName"
            )
            true
        }.getOrElse {
            Log.e(
                "SleepManager",
                "Unable to send JamesDSP power=${if (enabled) "ON" else "OFF"} to $packageName",
                it
            )
            false
        }
    }

    /**
     * Experimental read-only probe used to learn whether Android lets a normal app attach a
     * low-priority handle to JamesDSP's existing session-0 AudioEffect and read its enabled state.
     * The AudioEffect UUID/type constructor is hidden from the public SDK, so reflection can be
     * blocked by hidden-API enforcement. No state is changed and the handle is always released.
     */
    fun probePowerState(): PowerProbe {
        val descriptor = runCatching {
            AudioEffect.queryEffects()
                ?.firstOrNull { it.uuid == EFFECT_UUID }
        }.getOrElse {
            return PowerProbe(
                ProbeState.ERROR,
                "queryEffects: ${it.javaClass.simpleName}: ${it.message ?: "no message"}"
            )
        } ?: return PowerProbe(ProbeState.EFFECT_NOT_FOUND)

        var effect: AudioEffect? = null
        return try {
            val constructor = AudioEffect::class.java.getDeclaredConstructor(
                UUID::class.java,
                UUID::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            constructor.isAccessible = true
            effect = constructor.newInstance(
                descriptor.type,
                descriptor.uuid,
                -1000,
                0
            ) as AudioEffect

            PowerProbe(
                if (effect.enabled) ProbeState.ENABLED else ProbeState.DISABLED,
                "name=${descriptor.name}; control=${effect.hasControl()}"
            )
        } catch (t: Throwable) {
            val cause = t.cause ?: t
            val blocked =
                cause is NoSuchMethodException ||
                    cause is IllegalAccessException ||
                    cause.javaClass.name.contains("HiddenApi", ignoreCase = true)

            PowerProbe(
                if (blocked) ProbeState.BLOCKED else ProbeState.ERROR,
                "${cause.javaClass.simpleName}: ${cause.message ?: "no message"}"
            )
        } finally {
            runCatching { effect?.release() }
        }
    }

    private fun packageInfo(
        context: Context,
        packageName: String
    ) = try {
        context.packageManager.getPackageInfo(packageName, 0)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
}
