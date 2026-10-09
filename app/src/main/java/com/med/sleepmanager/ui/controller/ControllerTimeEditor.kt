package com.med.sleepmanager.ui.controller

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Touch/controller-friendly 24-hour schedule editor.
 * Edits are staged locally until Confirm: B and outside dismissal never save.
 */
@Composable
internal fun ControllerTimeEditor(
    title: String,
    originalMinutes: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var hour by remember(originalMinutes) { mutableIntStateOf(originalMinutes / 60 % 24) }
    var minute by remember(originalMinutes) { mutableIntStateOf(originalMinutes % 60) }
    var field by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    fun change(amount: Int) {
        if (field == 0) hour = (hour + amount + 24) % 24
        else minute = (minute + amount + 60) % 60
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .focusRequester(focus)
                    .onPreviewKeyEvent { event ->
                        val key = event.nativeKeyEvent
                        if (key.action != AndroidKeyEvent.ACTION_DOWN) false
                        else when (key.keyCode) {
                            AndroidKeyEvent.KEYCODE_DPAD_LEFT -> { field = 0; true }
                            AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> { field = 1; true }
                            AndroidKeyEvent.KEYCODE_DPAD_UP -> { change(1); true }
                            AndroidKeyEvent.KEYCODE_DPAD_DOWN -> { change(-1); true }
                            AndroidKeyEvent.KEYCODE_BUTTON_A,
                            AndroidKeyEvent.KEYCODE_DPAD_CENTER -> {
                                if (key.repeatCount == 0) onConfirm(hour * 60 + minute)
                                true
                            }
                            AndroidKeyEvent.KEYCODE_BUTTON_B,
                            AndroidKeyEvent.KEYCODE_BACK -> { onDismiss(); true }
                            else -> false
                        }
                    }
                    .focusable()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Hour", color = if (field == 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(
                            onClick = { field = 0; change(1) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) { Text("+") }
                        Text(
                            hour.toString().padStart(2, '0'),
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        OutlinedButton(
                            onClick = { field = 0; change(-1) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) { Text("−") }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Minute", color = if (field == 1) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(
                            onClick = { field = 1; change(1) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) { Text("+") }
                        Text(
                            minute.toString().padStart(2, '0'),
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        OutlinedButton(
                            onClick = { field = 1; change(-1) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) { Text("−") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = { onConfirm(hour * 60 + minute) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Confirm") }
                }
            }
        }
        LaunchedEffect(Unit) { focus.requestFocus() }
    }
}
