package com.med.sleepmanager.ui.components

import android.content.Context
import android.text.format.DateFormat
import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.med.sleepmanager.data.AppPreferences
import com.med.sleepmanager.data.EventHistoryStore
import com.med.sleepmanager.integration.SyncthingController
import com.med.sleepmanager.ui.feedbackClick
import com.med.sleepmanager.ui.screens.formatDuration
import java.util.Date

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun BehaviorCard(
    wifi: Boolean,
    bluetooth: Boolean,
    syncthing: Boolean,
    tailscale: Boolean,
    jamesDsp: Boolean,
    basicSync: Boolean,
    thorProtection: Boolean,
    sleepGraceMs: Long,
    advancedConditions: List<String>
) {
    var expanded by remember { mutableStateOf(false) }
    val hasSleepAction =
        wifi || bluetooth || syncthing || tailscale || jamesDsp || basicSync

    val sleepLines = buildList {
        if (sleepGraceMs > 0L && hasSleepAction) {
            add("Wait ${formatDuration(sleepGraceMs)}")
        }
        advancedConditions.forEach { add("Only if $it") }
        if (syncthing) add("Pause Syncthing‑Fork")
        if (tailscale) add("Disconnect Tailscale")
        if (jamesDsp) add("Power off JamesDSP")
        if (basicSync) add("Stop BasicSync when active")
        if (wifi) add("Wi‑Fi off")
        if (bluetooth) add("Bluetooth off")
        if (!hasSleepAction) add("No sleep actions selected")
        if (thorProtection) add("Thor closed-lid protection")
    }

    val wakeLines = buildList {
        if (wifi) add("Restore Wi‑Fi")
        if (bluetooth) add("Restore Bluetooth")
        if (syncthing) add("Resume Syncthing‑Fork")
        if (tailscale) add("Restore Tailscale if SleepManager disconnected it")
        if (jamesDsp) add("Restore JamesDSP")
        if (basicSync) add("Restore BasicSync previous mode")
    }

    val compactSleepSummary = buildList {
        if (sleepGraceMs > 0L && hasSleepAction) add(formatDuration(sleepGraceMs))
        if (wifi) add("Wi‑Fi")
        if (bluetooth) add("Bluetooth")
        if (syncthing) add("Syncthing")
        if (tailscale) add("Tailscale")
        if (jamesDsp) add("JamesDSP")
        if (basicSync) add("BasicSync")
        if (advancedConditions.isNotEmpty()) {
            add("${advancedConditions.size} condition${if (advancedConditions.size > 1) "s" else ""}")
        }
        if (!hasSleepAction) add("No actions")
    }.joinToString(" • ")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Current behavior",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        compactSleepSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(onClick = feedbackClick { expanded = !expanded }) {
                    Text(if (expanded) "Less" else "Details")
                }
            }

            if (expanded) {
                BehaviorGroup(
                    title = "When screen turns OFF",
                    lines = sleepLines
                )

                BehaviorGroup(
                    title = "When screen turns ON",
                    lines = wakeLines.ifEmpty { listOf("Nothing to restore") }
                )

                if (wifi || bluetooth) {
                    Text(
                        "Only states changed by SleepManager are restored.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (thorProtection) {
                    Text(
                        "Thor false wakes with the lid closed are returned to sleep without normal wake restoration.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
@Composable
internal fun BehaviorGroup(title: String, lines: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        lines.forEach {
            Text(
                "• $it",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
internal fun LastActivityCard(
    context: Context,
    onViewLog: () -> Unit,
    onCopyLog: () -> Unit
) {
    val event = AppPreferences.lastEvent(context)
    val time = AppPreferences.lastEventTime(context)

    val timeText = if (time > 0L) {
        val date = Date(time)
        DateFormat.getMediumDateFormat(context).format(date) +
            " • " + DateFormat.getTimeFormat(context).format(date)
    } else {
        null
    }

    val isStructured = event.contains(" → ")
    val phase = if (isStructured) event.substringBefore(" → ") else null
    val actions = if (isStructured) {
        event.substringAfter(" → ").split(" · ").filter { it.isNotBlank() }
    } else emptyList()

    Column(
        modifier = Modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            "Last activity",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (phase != null) {
            Text(phase, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)

            actions.forEach { action ->
                val subject = when {
                    action.startsWith("Wi‑Fi ") -> "Wi‑Fi"
                    action.startsWith("Bluetooth ") -> "Bluetooth"
                    action.startsWith("Syncthing ") -> "Syncthing"
                    action.startsWith("Tailscale ") -> "Tailscale"
                    else -> null
                }
                val detail = when (subject) {
                    "Wi‑Fi" -> action.removePrefix("Wi‑Fi ").replaceFirstChar { it.uppercase() }
                    "Bluetooth" -> action.removePrefix("Bluetooth ").replaceFirstChar { it.uppercase() }
                    "Syncthing" -> action.removePrefix("Syncthing ").replaceFirstChar { it.uppercase() }
                    "Tailscale" -> action.removePrefix("Tailscale ").replaceFirstChar { it.uppercase() }
                    else -> action
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (subject != null) {
                        Text("$subject ·", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (detail.equals("Unchanged", ignoreCase = true)) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }
        } else {
            Text(event, style = MaterialTheme.typography.bodyMedium)
        }

        timeText?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = feedbackClick(onViewLog),
                modifier = Modifier.weight(1f)
            ) {
                Text("View log")
            }

            OutlinedButton(
                onClick = feedbackClick(onCopyLog),
                modifier = Modifier.weight(1f)
            ) {
                Text("Copy log")
            }
        }
    }
}

@Composable
internal fun ActivityLogDialog(
    context: Context,
    onDismiss: () -> Unit
) {
    val events = EventHistoryStore.recent(context)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Activity log") },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (events.isEmpty()) {
                    item {
                        Text(
                            "No recent activity",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(events.size) { index ->
                        val event = events[index]
                        val date = Date(event.timestamp)
                        val timestamp =
                            DateFormat.getMediumDateFormat(context).format(date) +
                                " • " +
                                DateFormat.getTimeFormat(context).format(date)

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                event.message,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                timestamp,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = feedbackClick(onDismiss)) {
                Text("Close")
            }
        }
    )
}

@Composable
internal fun SyncthingTargetDialog(
    targets: List<SyncthingController.Target>,
    selected: String?,
    onDismiss: () -> Unit,
    onSelect: (SyncthingController.Target) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Syncthing‑Fork build") },
        text = {
            Column {
                targets.forEach { target ->
                    TextButton(
                        onClick = feedbackClick { onSelect(target) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                target.displayName,
                                modifier = Modifier.weight(1f)
                            )
                            if (target.packageName == selected) {
                                Text(
                                    "Selected",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = feedbackClick(onDismiss)) {
                Text("Close")
            }
        }
    )
}

