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
internal fun CompactSideRail(
    currentSection: AppSection,
    onSectionSelected: (AppSection) -> Unit,
    onMenuClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(64.dp)
            .fillMaxHeight()
            .testTag("compact_side_rail")
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        NavigationRail(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            windowInsets = WindowInsets(0, 0, 0, 0),
            header = {
                IconButton(
                    onClick = feedbackClick(onMenuClick),
                    modifier = Modifier.offset(y = (-4).dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_menu),
                        contentDescription = "Open navigation"
                    )
                }
            }
        ) {
            AppSection.values().forEach { section ->
                NavigationRailItem(
                    modifier = Modifier.height(48.dp),
                    selected = currentSection == section,
                    onClick = feedbackClick { onSectionSelected(section) },
                    icon = {
                        Icon(
                            painter = painterResource(section.iconRes),
                            contentDescription = section.label
                        )
                    }
                )
            }
        }
    }
}

@Composable
internal fun OnboardingCard(
    helperInstalled: Boolean,
    helperVersion: String?,
    syncthingTarget: SyncthingController.Target?,
    syncthingEnabled: Boolean,
    tailscaleInstalled: Boolean,
    tailscaleVersion: String?,
    jamesDspTarget: JamesDspController.Target?,
    basicSyncInstalled: Boolean,
    basicSyncVersion: String?,
    managerEnabled: Boolean,
    onGetHelper: () -> Unit,
    onShowTest: () -> Unit
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
                "Quick setup",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                if (helperInstalled) {
                    "✓ Compatibility helper installed${helperVersion?.let { " • $it" } ?: ""}"
                } else {
                    "• Compatibility helper not installed — only needed for Wi-Fi / Bluetooth."
                },
                style = MaterialTheme.typography.bodyMedium
            )

            if (!helperInstalled) {
                TextButton(onClick = feedbackClick(onGetHelper)) {
                    Text("Install Helper")
                }
            }

            Text(
                syncthingTarget?.let { "✓ ${it.displayName} detected" }
                    ?: "• Syncthing-Fork not detected — optional.",
                style = MaterialTheme.typography.bodyMedium
            )

            if (syncthingEnabled && syncthingTarget != null) {
                Text(
                    "Syncthing-Fork: make sure Settings → Behaviour → Service control by broadcast is enabled.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                if (tailscaleInstalled) {
                    val version =
                        tailscaleVersion?.substringBefore("-")
                    "✓ Tailscale" +
                        (version?.let { " • $it" } ?: "") +
                        " detected"
                } else {
                    "• Tailscale not detected — optional."
                },
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                jamesDspTarget?.let { target ->
                    "✓ JamesDSP" +
                        (target.versionName?.let { " • $it" } ?: "") +
                        " detected"
                } ?: "• JamesDSP not detected — optional.",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                if (basicSyncInstalled) {
                    "✓ BasicSync" +
                        (basicSyncVersion?.let { " • $it" } ?: "") +
                        " detected"
                } else {
                    "• BasicSync not detected — optional."
                },
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                if (managerEnabled) {
                    "SleepManager is enabled. Finish setup when your selected actions look right."
                } else {
                    "Choose the actions you want below, enable SleepManager, then tap Finish setup."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                "Sleep statistics start automatically. Complete a sleep session of at least 3 hours without charging to build averages and standby estimates.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            TextButton(onClick = feedbackClick(onShowTest)) {
                Text("How to test sleep / wake")
            }
        }
    }
}

@Composable
internal fun UpdateAvailableCard(
    update: UpdateInfo?,
    helperUpdate: HelperUpdateInfo?,
    onUpdate: () -> Unit,
    onReleaseNotes: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("update_available_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (update != null && helperUpdate != null) {
                        "Updates available"
                    } else {
                        "Update available"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    when {
                        update != null && helperUpdate != null ->
                            "SleepManager ${update.versionName} and Helper ${helperUpdate.versionName} are available."
                        update != null ->
                            "SleepManager ${update.versionName} is available on GitHub."
                        helperUpdate != null ->
                            "SleepManager Helper ${helperUpdate.versionName} is available."
                        else -> "An update is available."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Column(
                horizontalAlignment = Alignment.End
            ) {
                OutlinedButton(onClick = feedbackClick(onUpdate)) {
                    Text("Update")
                }
                if (onReleaseNotes != null) {
                    TextButton(
                        onClick = feedbackClick(onReleaseNotes)
                    ) {
                        Text("Release notes")
                    }
                }
            }
        }
    }
}

@Composable
internal fun StatusCard(
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
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                            .padding(8.dp)
                            .size(22.dp)
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
                        color = if (active) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Button(
                onClick = feedbackClick(onToggle),
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
