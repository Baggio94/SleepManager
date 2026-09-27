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
internal fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(content = content)
    }
}

@Composable
internal fun SettingRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    status: String? = null,
    checked: Boolean,
    enabled: Boolean,
    dimWhenDisabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val contentAlpha = if (enabled || !dimWhenDisabled) 1f else 0.55f
    val secondaryAlpha = if (enabled || !dimWhenDisabled) 1f else 0.6f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = when {
                checked && enabled -> MaterialTheme.colorScheme.primary
                enabled -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = when {
                    checked && enabled -> MaterialTheme.colorScheme.onPrimary
                    enabled -> MaterialTheme.colorScheme.onSecondaryContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                },
                modifier = Modifier
                    .padding(10.dp)
                    .size(22.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(
                    alpha = contentAlpha
                )
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = secondaryAlpha
                )
            )

            status?.let { currentStatus ->
                val isActive =
                    currentStatus.endsWith(": ON") ||
                        currentStatus.endsWith(": RUNNING") ||
                        currentStatus.endsWith(": CONNECTED")
                Text(
                    currentStatus,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        currentStatus.contains("CHECKING") -> MaterialTheme.colorScheme.onSurfaceVariant
                        isActive -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
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

@Composable
internal fun CompactIntegrationRow(
    @DrawableRes icon: Int,
    title: String,
    version: String,
    status: String?,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onOpen: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null
) {
    val compact = LocalConfiguration.current.screenWidthDp < 600

    val iconContent: @Composable () -> Unit = {
        Surface(
            shape = CircleShape,
            color = when {
                checked && enabled -> MaterialTheme.colorScheme.primary
                enabled -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = when {
                    checked && enabled -> MaterialTheme.colorScheme.onPrimary
                    enabled -> MaterialTheme.colorScheme.onSecondaryContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .padding(10.dp)
                    .size(22.dp)
            )
        }
    }

    val detailsContent: @Composable () -> Unit = {
        Column(
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                title,
                modifier = Modifier.testTag("integration_title_$title"),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    version,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                status?.let {
                    val active =
                        it.contains("Running", ignoreCase = true) ||
                            it.contains("Connected", ignoreCase = true) ||
                            it.contains("Starting", ignoreCase = true)
                    Text(
                        "•",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        it,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }

    val actionsContent: @Composable () -> Unit = {
        if (onOpen != null || (secondaryActionLabel != null && onSecondaryAction != null)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                onOpen?.let { open ->
                    OutlinedButton(onClick = feedbackClick(open)) {
                        Text("Open")
                    }
                }

                if (secondaryActionLabel != null && onSecondaryAction != null) {
                    TextButton(
                        onClick = feedbackClick(onSecondaryAction),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(secondaryActionLabel)
                    }
                }
            }
        }
    }

    if (compact) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                iconContent()
                Box(modifier = Modifier.weight(1f)) {
                    detailsContent()
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

            if (onOpen != null || (secondaryActionLabel != null && onSecondaryAction != null)) {
                Box(
                    modifier = Modifier.padding(start = 54.dp)
                ) {
                    actionsContent()
                }
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            iconContent()
            Box(modifier = Modifier.weight(1f)) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    detailsContent()
                    actionsContent()
                }
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
}
