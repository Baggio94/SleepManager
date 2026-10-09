package com.med.sleepmanager.ui.controller

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Adaptive controller-first setting. Touch commits the visible option directly;
 * controller A opens a modal editor so navigation never accidentally saves a value.
 */
@Composable
internal fun <T> ControllerChoiceGroup(
    id: String,
    title: String,
    options: List<Pair<String, T>>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    maxColumns: Int = 4
) {
    var focused by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val currentIndex = options.indexOfFirst { it.second == selected }.coerceAtLeast(0)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .controllerRememberFocus("choice:$id")
            .onFocusChanged { focused = it.isFocused }
            .controllerFocusHighlight(focused)
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (native.action == AndroidKeyEvent.ACTION_DOWN &&
                    native.keyCode == AndroidKeyEvent.KEYCODE_BUTTON_A &&
                    native.repeatCount == 0
                ) {
                    editing = true
                    true
                } else false
            }
            .focusable()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val columns =
                if (maxWidth >= 540.dp) maxColumns
                else if (maxWidth >= 250.dp) minOf(2, maxColumns)
                else 1
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.chunked(columns).forEach { chunk ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        chunk.forEach { (label, value) ->
                            val isSelected = selected == value
                            OutlinedButton(
                                onClick = { onSelected(value) },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 52.dp)
                                    .focusProperties { canFocus = !isControllerInputActive() }
                                    .testTag("controller_option_${id}_${options.indexOfFirst { it.second == value }}"),
                                shape = RoundedCornerShape(12.dp),
                                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else MaterialTheme.colorScheme.surface,
                                    contentColor = if (isSelected) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else MaterialTheme.colorScheme.onSurface
                                ),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                            ) {
                                Text(label, maxLines = 2)
                            }
                        }
                        repeat(columns - chunk.size) {
                            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    if (editing && options.isNotEmpty()) {
        ControllerChoiceDialog(
            title = title,
            options = options,
            initialIndex = currentIndex,
            onDismiss = { editing = false },
            onConfirm = { choice ->
                editing = false
                onSelected(choice)
            }
        )
    }
}

/** Modal interception also isolates editing keys from tabs and the screen behind it. */
@Composable
internal fun <T> ControllerChoiceDialog(
    title: String,
    options: List<Pair<String, T>>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    onConfirm: (T) -> Unit
) {
    var index by remember(title, initialIndex) {
        mutableIntStateOf(initialIndex.coerceIn(0, options.lastIndex))
    }
    val requester = remember { FocusRequester() }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .focusRequester(requester)
                    .onPreviewKeyEvent { event ->
                        val native = event.nativeKeyEvent
                        if (native.action != AndroidKeyEvent.ACTION_DOWN) false
                        else when (native.keyCode) {
                            AndroidKeyEvent.KEYCODE_DPAD_LEFT,
                            AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                                index = (index - 1 + options.size) % options.size
                                true
                            }
                            AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
                            AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                                index = (index + 1) % options.size
                                true
                            }
                            AndroidKeyEvent.KEYCODE_BUTTON_A,
                            AndroidKeyEvent.KEYCODE_DPAD_CENTER -> {
                                if (native.repeatCount == 0) onConfirm(options[index].second)
                                true
                            }
                            AndroidKeyEvent.KEYCODE_BUTTON_B,
                            AndroidKeyEvent.KEYCODE_BACK -> {
                                onDismiss()
                                true
                            }
                            else -> false
                        }
                    }
                    .focusable()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                options.forEachIndexed { optionIndex, (label, _) ->
                    val selected = optionIndex == index
                    OutlinedButton(
                        onClick = { index = optionIndex },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .focusProperties { canFocus = !isControllerInputActive() },
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surface
                        )
                    ) { Text(label) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = { onConfirm(options[index].second) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Confirm") }
                }
            }
        }
        LaunchedEffect(Unit) { requester.requestFocus() }
    }
}
