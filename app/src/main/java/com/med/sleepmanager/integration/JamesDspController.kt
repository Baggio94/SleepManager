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
    private val EFFECT_TYPE_NULL =
        UUID.fromString("ec7178ec-e5e1-4432-a3f4-4657e6795210")

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
     * No state is changed and every temporary handle is released immediately.
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

        val constructor = try {
            AudioEffect::class.java.getDeclaredConstructor(
                UUID::class.java,
                UUID::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            ).also { it.isAccessible = true }
        } catch (t: Throwable) {
            val cause = t.cause ?: t
            return PowerProbe(
                ProbeState.BLOCKED,
                "${cause.javaClass.simpleName}: ${cause.message ?: "no message"}"
            )
        }

        fun attempt(type: UUID, label: String): Pair<PowerProbe?, String?> {
            var effect: AudioEffect? = null
            return try {
                effect = constructor.newInstance(
                    type,
                    descriptor.uuid,
                    -1000,
                    0
                ) as AudioEffect

                PowerProbe(
                    if (effect.enabled) ProbeState.ENABLED else ProbeState.DISABLED,
                    "name=${descriptor.name}; mode=$label; control=${effect.hasControl()}"
                ) to null
            } catch (t: Throwable) {
                val cause = t.cause ?: t
                val detail =
                    "$label: ${cause.javaClass.simpleName}: ${cause.message ?: "no message"}"
                null to detail
            } finally {
                runCatching { effect?.release() }
            }
        }

        // AOSP explicitly allows EFFECT_TYPE_NULL when selecting a particular implementation
        // by UUID. Try that first because JamesDSP exposes its own custom effect type.
        val (uuidOnlyResult, uuidOnlyError) =
            attempt(EFFECT_TYPE_NULL, "uuid-only")
        if (uuidOnlyResult != null) return uuidOnlyResult

        // Keep the original typed attempt as a diagnostic fallback.
        val (typedResult, typedError) =
            attempt(descriptor.type, "typed")
        if (typedResult != null) return typedResult

        return PowerProbe(
            ProbeState.ERROR,
            listOfNotNull(uuidOnlyError, typedError).joinToString(" | ")
        )
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
