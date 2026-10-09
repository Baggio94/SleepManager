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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp

/** Compact - / current value / +. Touch changes directly, controller edits transactionally. */
@Composable
internal fun ControllerNumericStepper(
    id: String,
    title: String,
    choices: List<Int>,
    current: Int,
    onChange: (Int) -> Unit,
    suffix: String = "%"
) {
    var focused by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val position = choices.indexOf(current).coerceAtLeast(0)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .controllerRememberFocus("number:$id", onActivate = { editing = true })
            .onFocusChanged { focused = it.isFocused }
            .controllerFocusHighlight(focused)
            .focusable()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { if (position > 0) onChange(choices[position - 1]) },
                enabled = position > 0,
                modifier = Modifier.weight(1f)
                    .heightIn(min = 52.dp)
                    .focusProperties { canFocus = !isControllerInputActive() }
            ) { Text("−") }
            Text(
                text = "$current$suffix",
                modifier = Modifier.weight(2f).padding(vertical = 10.dp),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            OutlinedButton(
                onClick = { if (position < choices.lastIndex) onChange(choices[position + 1]) },
                enabled = position < choices.lastIndex,
                modifier = Modifier.weight(1f)
                    .heightIn(min = 52.dp)
                    .focusProperties { canFocus = !isControllerInputActive() }
            ) { Text("+") }
        }
    }
    if (editing) {
        ControllerChoiceDialog(
            title = title,
            options = choices.map { value -> (value.toString() + suffix) to value },
            initialIndex = position,
            onDismiss = { editing = false },
            onConfirm = { value ->
                editing = false
                onChange(value)
            }
        )
    }
}
