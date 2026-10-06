package com.med.sleepmanager.ui.screens

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.med.sleepmanager.R
import com.med.sleepmanager.data.AppPreferences
import com.med.sleepmanager.data.EventHistoryStore
import com.med.sleepmanager.ui.activity.ActivityTimelineBuilder
import com.med.sleepmanager.ui.activity.ActivityTimelineGroup
import com.med.sleepmanager.ui.activity.ActivityTimelineKind
import com.med.sleepmanager.ui.components.SettingsCard
import com.med.sleepmanager.ui.feedbackChange
import com.med.sleepmanager.ui.feedbackClick
import java.util.Date

@Composable
internal fun ActivityLogPage(
    context: Context,
    onCopyLog: () -> Unit
) {
    val events = EventHistoryStore.recent(context)
    val timeline = ActivityTimelineBuilder.build(events)
    var advancedDiagnostics by remember(context) {
        mutableStateOf(AppPreferences.advancedDiagnosticsEnabled(context))
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.activity_timeline_description),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(
                onClick = feedbackClick(onCopyLog),
                modifier = Modifier.padding(start = 12.dp)
            ) {
                Text(stringResource(R.string.activity_copy_log))
            }
        }

        if (timeline.isEmpty()) {
            InfoCard(
                title = stringResource(R.string.nav_activity_log),
                text = stringResource(R.string.activity_no_recent_activity)
            )
        } else {
            timeline.forEach { group ->
                ActivityTimelineCard(
                    context = context,
                    group = group
                )
            }
        }

        SettingsCard {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        stringResource(R.string.activity_advanced_diagnostics),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        stringResource(R.string.activity_advanced_diagnostics_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = advancedDiagnostics,
                    onCheckedChange = feedbackChange { enabled ->
                        advancedDiagnostics = enabled
                        AppPreferences.setAdvancedDiagnosticsEnabled(context, enabled)
                    }
                )
            }
        }
    }
}

@Composable
private fun ActivityTimelineCard(
    context: Context,
    group: ActivityTimelineGroup
) {
    val started = Date(group.startedAt)
    val dateTime =
        DateFormat.getMediumDateFormat(context).format(started) +
            " • " +
            DateFormat.getTimeFormat(context).format(started)

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = activityTimelineTitle(group.kind),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = dateTime,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SettingsCard {
            group.steps.forEachIndexed { index, step ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "\${index + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = step.text,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text =
                                DateFormat.getTimeFormat(context)
                                    .format(Date(step.timestamp)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (index != group.steps.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun activityTimelineTitle(
    kind: ActivityTimelineKind
): String =
    stringResource(
        when (kind) {
            ActivityTimelineKind.SLEEP -> R.string.activity_timeline_sleep
            ActivityTimelineKind.WAKE -> R.string.activity_timeline_wake
            ActivityTimelineKind.MAINTENANCE ->
                R.string.activity_timeline_maintenance
            ActivityTimelineKind.PROTECTION ->
                R.string.activity_timeline_protection
            ActivityTimelineKind.RECOVERY ->
                R.string.activity_timeline_recovery
            ActivityTimelineKind.SYSTEM ->
                R.string.activity_timeline_system
        }
    )
