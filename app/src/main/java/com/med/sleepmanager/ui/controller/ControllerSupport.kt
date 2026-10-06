package com.med.sleepmanager.ui.controller

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent as AndroidKeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.nativeKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

private const val STICK_DEAD_ZONE = 0.55f
private const val RIGHT_STICK_DEAD_ZONE = 0.18f
private const val STICK_REPEAT_MS = 180L
private const val PAGE_SCROLL_DEBOUNCE_MS = 180L
private const val RIGHT_STICK_SCROLL_DP = 42f

private object ControllerInputMode {
    var active by mutableStateOf(false)
}

@Composable
internal fun Modifier.controllerFocusHighlight(
    focused: Boolean,
    shape: Shape = RoundedCornerShape(14.dp)
): Modifier {
    if (!ControllerInputMode.active || !focused) return this

    val color = MaterialTheme.colorScheme.primary
    return this
        .background(color.copy(alpha = 0.08f), shape)
        .border(2.dp, color.copy(alpha = 0.82f), shape)
}

@Composable
internal fun Modifier.controllerNavigation(
    listState: LazyListState,
    enabled: Boolean = true,
    onPreviousSection: () -> Unit,
    onNextSection: () -> Unit
): Modifier {
    val focusManager = LocalFocusManager.current
    val view = LocalView.current
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()

    var lastStickDirection by remember(listState) {
        mutableIntStateOf(0)
    }
    var lastStickMoveAt by remember(listState) {
        mutableLongStateOf(0L)
    }
    var analogTriggerLatch by remember(listState) {
        mutableIntStateOf(0)
    }
    var lastPageScrollAt by remember(listState) {
        mutableLongStateOf(0L)
    }

    fun pageScroll(
        direction: Int,
        eventTime: Long = SystemClock.uptimeMillis()
    ) {
        if (eventTime - lastPageScrollAt < PAGE_SCROLL_DEBOUNCE_MS) return
        lastPageScrollAt = eventTime
        focusManager.clearFocus(force = true)

        val viewport =
            listState.layoutInfo.viewportSize.height
                .takeIf { it > 0 }
                ?: view.height
        if (viewport <= 0) return

        scope.launch {
            listState.animateScrollBy(viewport * 0.82f * direction)
        }
    }

    fun moveFocus(direction: Int, eventTime: Long): Boolean {
        val shouldMove =
            direction != lastStickDirection ||
                eventTime - lastStickMoveAt >= STICK_REPEAT_MS
        if (!shouldMove) return true

        lastStickDirection = direction
        lastStickMoveAt = eventTime
        val focusDirection =
            when (direction) {
                1 -> FocusDirection.Up
                2 -> FocusDirection.Down
                3 -> FocusDirection.Left
                4 -> FocusDirection.Right
                else -> return false
            }
        focusManager.moveFocus(focusDirection)
        return true
    }

    DisposableEffect(view, listState, enabled) {
        val listener =
            View.OnGenericMotionListener { _, event ->
                if (
                    !enabled ||
                    event.action != MotionEvent.ACTION_MOVE ||
                    (event.source and InputDevice.SOURCE_JOYSTICK) !=
                        InputDevice.SOURCE_JOYSTICK
                ) {
                    return@OnGenericMotionListener false
                }

                ControllerInputMode.active = true

                val rz = event.getAxisValue(MotionEvent.AXIS_RZ)
                val ry = event.getAxisValue(MotionEvent.AXIS_RY)
                val rightY = if (abs(rz) > 0.01f) rz else ry
                if (abs(rightY) >= RIGHT_STICK_DEAD_ZONE) {
                    focusManager.clearFocus(force = true)
                    lastStickDirection = 0
                    scope.launch {
                        listState.scrollBy(
                            rightY * RIGHT_STICK_SCROLL_DP * density
                        )
                    }
                    return@OnGenericMotionListener true
                }

                val leftTrigger =
                    max(
                        event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
                        event.getAxisValue(MotionEvent.AXIS_BRAKE)
                    )
                val rightTrigger =
                    max(
                        event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
                        event.getAxisValue(MotionEvent.AXIS_GAS)
                    )
                val triggerDirection =
                    when {
                        rightTrigger >= 0.75f -> 1
                        leftTrigger >= 0.75f -> -1
                        else -> 0
                    }

                if (triggerDirection != 0) {
                    if (analogTriggerLatch != triggerDirection) {
                        analogTriggerLatch = triggerDirection
                        pageScroll(triggerDirection, event.eventTime)
                    }
                    return@OnGenericMotionListener true
                }
                analogTriggerLatch = 0

                val x = event.getAxisValue(MotionEvent.AXIS_X)
                val y = event.getAxisValue(MotionEvent.AXIS_Y)
                val direction =
                    when {
                        abs(y) >= STICK_DEAD_ZONE && abs(y) >= abs(x) ->
                            if (y < 0f) 1 else 2
                        abs(x) >= STICK_DEAD_ZONE ->
                            if (x < 0f) 3 else 4
                        else -> 0
                    }

                if (direction == 0) {
                    lastStickDirection = 0
                    false
                } else {
                    moveFocus(direction, event.eventTime)
                }
            }

        view.setOnGenericMotionListener(listener)
        onDispose {
            view.setOnGenericMotionListener(null)
        }
    }

    return this
        .onPreviewKeyEvent { event ->
            val native = event.nativeKeyEvent
            if (native.action != AndroidKeyEvent.ACTION_DOWN) {
                return@onPreviewKeyEvent false
            }

            val keyCode = native.keyCode
            val controllerKey =
                keyCode in setOf(
                    AndroidKeyEvent.KEYCODE_DPAD_UP,
                    AndroidKeyEvent.KEYCODE_DPAD_DOWN,
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT,
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                    AndroidKeyEvent.KEYCODE_BACK,
                    AndroidKeyEvent.KEYCODE_BUTTON_L1,
                    AndroidKeyEvent.KEYCODE_BUTTON_R1,
                    AndroidKeyEvent.KEYCODE_BUTTON_L2,
                    AndroidKeyEvent.KEYCODE_BUTTON_R2
                )
            if (controllerKey) {
                ControllerInputMode.active = true
            }

            if (!enabled) {
                return@onPreviewKeyEvent false
            }

            when (keyCode) {
                AndroidKeyEvent.KEYCODE_DPAD_UP ->
                    focusManager.moveFocus(FocusDirection.Up)
                AndroidKeyEvent.KEYCODE_DPAD_DOWN ->
                    focusManager.moveFocus(FocusDirection.Down)
                AndroidKeyEvent.KEYCODE_DPAD_LEFT ->
                    focusManager.moveFocus(FocusDirection.Left)
                AndroidKeyEvent.KEYCODE_DPAD_RIGHT ->
                    focusManager.moveFocus(FocusDirection.Right)

                AndroidKeyEvent.KEYCODE_BUTTON_L1 -> {
                    if (native.repeatCount == 0) {
                        focusManager.clearFocus(force = true)
                        onPreviousSection()
                    }
                    true
                }

                AndroidKeyEvent.KEYCODE_BUTTON_R1 -> {
                    if (native.repeatCount == 0) {
                        focusManager.clearFocus(force = true)
                        onNextSection()
                    }
                    true
                }

                AndroidKeyEvent.KEYCODE_BUTTON_L2 -> {
                    if (native.repeatCount == 0) {
                        pageScroll(-1, native.eventTime)
                    }
                    true
                }

                AndroidKeyEvent.KEYCODE_BUTTON_R2 -> {
                    if (native.repeatCount == 0) {
                        pageScroll(1, native.eventTime)
                    }
                    true
                }

                else -> false
            }
        }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (
                        event.changes.any {
                            it.pressed && !it.previousPressed
                        }
                    ) {
                        ControllerInputMode.active = false
                    }
                }
            }
        }
}
