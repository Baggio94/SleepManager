package com.med.sleepmanager.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Contextual Material You gamepad legend; no permanent screen-height cost. */
@Composable
internal fun ControllerHintOverlay() {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        val compact = maxWidth < 490.dp
        Surface(
            modifier = Modifier.align(Alignment.Center).widthIn(max = 610.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            tonalElevation = 4.dp
        ) {
            if (compact) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        ControllerHintEntry(listOf("A"), "Select")
                        ControllerHintEntry(listOf("B"), "Back")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        ControllerHintEntry(listOf("L1", "R1"), "Tabs")
                        ControllerHintEntry(listOf("L2", "R2"), "Page")
                    }
                }
            } else {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ControllerHintEntry(listOf("A"), "Select")
                    ControllerHintEntry(listOf("B"), "Back")
                    ControllerHintEntry(listOf("L1", "R1"), "Tabs")
                    ControllerHintEntry(listOf("L2", "R2"), "Page")
                }
            }
        }
    }
}

@Composable
private fun ControllerHintEntry(keys: List<String>, action: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        keys.forEach { key ->
            val round = key == "A" || key == "B"
            Surface(
                modifier = if (round) Modifier.size(25.dp) else Modifier,
                shape = if (round) CircleShape else RoundedCornerShape(7.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Text(
                    text = key,
                    modifier = Modifier.padding(
                        horizontal = if (round) 0.dp else 6.dp,
                        vertical = if (round) 4.dp else 3.dp
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
        Text(action, style = MaterialTheme.typography.labelSmall)
    }
}
