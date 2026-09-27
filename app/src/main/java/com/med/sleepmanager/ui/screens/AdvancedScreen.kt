package com.med.sleepmanager

import android.Manifest
import android.app.TimePickerDialog
import android.app.AlarmManager
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.net.Uri
import android.text.format.DateFormat
import android.provider.Settings
import android.widget.Toast
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.core.content.IntentCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.med.sleepmanager.BuildConfig
import com.med.sleepmanager.data.AppPreferences
import com.med.sleepmanager.data.BatterySleepStore
import com.med.sleepmanager.data.EventHistoryStore
import com.med.sleepmanager.data.SleepCycleStore
import com.med.sleepmanager.diagnostics.DiagnosticsBuilder
import com.med.sleepmanager.device.BackgroundReliability
import com.med.sleepmanager.device.DeviceControlController
import com.med.sleepmanager.integration.BasicSyncController
import com.med.sleepmanager.integration.HelperController
import com.med.sleepmanager.integration.JamesDspController
import com.med.sleepmanager.integration.SyncthingController
import com.med.sleepmanager.integration.TailscaleController
import com.med.sleepmanager.integration.connector.BasicSyncConnector
import com.med.sleepmanager.integration.connector.JamesDspConnector
import com.med.sleepmanager.integration.connector.SyncthingConnector
import com.med.sleepmanager.protection.ThorDeviceAdminReceiver
import com.med.sleepmanager.protection.ThorLidMonitor
import com.med.sleepmanager.protection.ThorPowerButtonMonitor
import com.med.sleepmanager.qs.SleepManagerTileService
import com.med.sleepmanager.service.SleepManagerService
import com.med.sleepmanager.sync.ManagedSyncProviders
import com.med.sleepmanager.sync.SyncCompletionState
import com.med.sleepmanager.sync.basicSyncCompletionState
import com.med.sleepmanager.ui.theme.SleepManagerTheme
import com.med.sleepmanager.update.UpdateCheckResult
import com.med.sleepmanager.update.UpdateCheckScheduler
import com.med.sleepmanager.update.UpdateChecker
import com.med.sleepmanager.update.UpdateDownloadResult
import com.med.sleepmanager.update.HelperUpdateInfo
import com.med.sleepmanager.update.UpdateInfo
import com.med.sleepmanager.update.UpdateInstaller
import com.med.sleepmanager.update.UpdateNotifier
import java.io.File
import java.util.Date
import java.util.Locale

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun SleepGraceSelector(
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
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(options.size) { index ->
                val (label, value) = options[index]
                FilterChip(
                    selected = !customDelayEnabled && valueMs == value,
                    onClick = feedbackClick { onChange(value) },
                    enabled = !customDelayEnabled,
                    label = { Text(label) }
                )
            }

            item {
                FilterChip(
                    selected = customDelayEnabled,
                    onClick = feedbackClick(onCustom),
                    label = { Text("Custom") }
                )
            }
        }

        if (customDelayEnabled) {
            TextButton(onClick = feedbackClick(onCustom)) {
                Text("Advanced • ${formatDuration(customDelayMs)}")
            }
        }
    }
}

@Composable
internal fun AdvancedSettingsPage(
    periodicSyncWhileSleeping: Boolean,
    batterySaverControlSupported: Boolean,
    syncThenStopOnSleepWake: Boolean,
    syncConditionsAvailable: Boolean,
    onPeriodicSyncWhileSleepingChange: (Boolean) -> Unit,
    onSyncThenStopOnSleepWakeChange: (Boolean) -> Unit,
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
            title = "Advanced sync conditions",
            subtitle = "Control when managed sync clients run outside their normal sleep behavior."
        )

        SettingsCard {
            AdvancedToggleRow(
                title = "Periodic sync while sleeping",
                subtitle = if (syncConditionsAvailable) {
                    "While the device stays asleep, sync managed clients every 24h, then stop them and restore the sleep state."
                } else {
                    "BasicSync 3.19+ required; Syncthing-Fork support pending."
                },
                checked = periodicSyncWhileSleeping,
                enabled = syncConditionsAvailable,
                onCheckedChange = onPeriodicSyncWhileSleepingChange
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            AdvancedToggleRow(
                title = "Sync then stop on sleep & wake",
                subtitle = if (syncConditionsAvailable) {
                    "Sync managed clients after wake and again before sleep. After each sync completes, stop them to reduce background battery use."
                } else {
                    "BasicSync 3.19+ required; Syncthing-Fork support pending."
                },
                checked = syncThenStopOnSleepWake,
                enabled = syncConditionsAvailable,
                onCheckedChange = onSyncThenStopOnSleepWakeChange
            )
        }

        SectionTitle(
            title = "Advanced sleep conditions",
            subtitle = "Fine-tune when sleep actions are allowed and when they begin."
        )

        SettingsCard {
            AdvancedToggleRow(
                title = "Use custom delay",
                subtitle = if (customDelayEnabled) {
                    "Grace period will show Advanced • ${formatDuration(customDelayMs)}"
                } else {
                    "Grace period uses Immediate / 5s / 10s."
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
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    "Only start sleep actions when unplugged"
                } else {
                    "Ignore charging state"
                },
                checked = notChargingOnly,
                onCheckedChange = onNotChargingOnlyChange
            )

            if (batterySaverControlSupported) {
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
                        style = MaterialTheme.typography.bodyMedium,
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
                                onClick = feedbackClick {
                                    onBatterySaverModeChange(mode)
                                },
                                label = { Text(label) }
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
internal fun AdvancedToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
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
                fontWeight = FontWeight.Medium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                }
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (enabled) 1f else 0.55f
                )
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = feedbackChange(onCheckedChange),
            enabled = enabled,
            modifier = Modifier.semantics {
                contentDescription = "$title toggle"
            }
        )
    }
}
