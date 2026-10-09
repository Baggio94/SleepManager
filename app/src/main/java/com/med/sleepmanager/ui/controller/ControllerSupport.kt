package com.med.sleepmanager.ui.controller

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent as AndroidKeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.med.sleepmanager.ui.SleepManagerFeedbackGate
import com.med.sleepmanager.ui.performSleepManagerFeedback
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

internal fun isControllerInputActive(): Boolean = ControllerInputMode.active

internal val LocalControllerFocusMemory = staticCompositionLocalOf<ControllerFocusMemory?> { null }
internal val LocalControllerSectionId = staticCompositionLocalOf { "" }

internal val LocalControllerTargetRegistry =
    staticCompositionLocalOf<ControllerTargetRegistry?> { null }

/** Registers a stable, logical controller target independently of touch nodes. */
@Composable
internal fun Modifier.controllerRememberFocus(
    targetId: String,
    onActivate: (() -> Unit)? = null,
    actions: List<ControllerAction> = emptyList()
): Modifier {
    val sectionId = LocalControllerSectionId.current
    val memory = LocalControllerFocusMemory.current
    val registry = LocalControllerTargetRegistry.current
    val requester = remember(sectionId, targetId) { FocusRequester() }
    val currentActivate by rememberUpdatedState(onActivate)
    val currentActions by rememberUpdatedState(actions)

    DisposableEffect(registry, sectionId, targetId) {
        registry?.register(
            sectionId, targetId, requester,
            activate = { currentActivate?.invoke() },
            actions = { currentActions }
        )
        onDispose { registry?.unregister(sectionId, targetId) }
    }

    LaunchedEffect(sectionId, targetId) {
        if (ControllerInputMode.active && memory?.shouldRestore(sectionId, targetId) == true) {
            runCatching { requester.requestFocus() }
            memory.markRestored(sectionId, targetId)
        }
    }

    return this
        .focusRequester(requester)
        .onGloballyPositioned { coordinates ->
            registry?.position(sectionId, targetId, coordinates.positionInRoot().y)
        }
        .onFocusChanged { focus ->
            if (focus.isFocused && ControllerInputMode.active) {
                memory?.remember(sectionId, targetId)
                registry?.select(sectionId, targetId)
            }
        }
}

@Composable
internal fun Modifier.controllerFocusHighlight(
    focused: Boolean,
    shape: Shape = RoundedCornerShape(22.dp)
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
    onNextSection: () -> Unit,
    onMenuRequested: () -> Unit,
    drawerOpen: Boolean = false,
    onDrawerMove: (Int) -> Unit = {},
    onDrawerSelect: () -> Unit = {},
    onDrawerDismiss: () -> Unit = {},
    onBackRequested: (() -> Unit)? = null
): Modifier {
    val focusManager = LocalFocusManager.current
    val registry = LocalControllerTargetRegistry.current
    val sectionId = LocalControllerSectionId.current
    val view = LocalView.current
    val backDispatcher =
        LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
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
            direction != lastStickDirection || eventTime - lastStickMoveAt >= STICK_REPEAT_MS
        if (!shouldMove) return true
        lastStickDirection = direction
        lastStickMoveAt = eventTime

        if (direction == 1 || direction == 2) {
            val step = if (direction == 1) -1 else 1
            if (registry?.move(sectionId, step) == true) return true
            // A target may lie below the current LazyColumn viewport.
            val viewport = listState.layoutInfo.viewportSize.height
                .takeIf { it > 0 } ?: view.height
            if (viewport > 0) {
                scope.launch {
                    listState.animateScrollBy(viewport * 0.66f * step)
                    withFrameNanos { }
                    if (registry?.move(sectionId, step) != true) {
                        focusManager.moveFocus(
                            if (step < 0) FocusDirection.Up else FocusDirection.Down
                        )
                    }
                }
                return true
            }
        }
        // Left/right stays local to the selected card before spatial fallback.
        if (direction == 3 && registry?.changeAction(sectionId, -1) == true) return true
        if (direction == 4 && registry?.changeAction(sectionId, 1) == true) return true
        val focusDirection = if (direction == 3) FocusDirection.Left else FocusDirection.Right
        return focusManager.moveFocus(focusDirection)
    }

    val onNavigationKey: (AndroidKeyEvent) -> Boolean = { event ->
        if (event.action != AndroidKeyEvent.ACTION_DOWN) {
            false
        } else {
            when (event.keyCode) {
                AndroidKeyEvent.KEYCODE_BUTTON_START,
                AndroidKeyEvent.KEYCODE_MENU -> {
                    ControllerInputMode.active = true
                    if (enabled || drawerOpen) onMenuRequested()
                    true
                }
                AndroidKeyEvent.KEYCODE_BUTTON_B -> {
                    ControllerInputMode.active = true
                    if (drawerOpen) onDrawerDismiss()
                    else if (onBackRequested != null) onBackRequested()
                    else backDispatcher?.onBackPressed()
                    true
                }
                AndroidKeyEvent.KEYCODE_BUTTON_A -> {
                    if (!enabled && !drawerOpen) false else {
                        ControllerInputMode.active = true
                        if (event.repeatCount == 0) {
                            if (drawerOpen) onDrawerSelect()
                            else registry?.activate(sectionId)
                        }
                        true
                    }
                }
                AndroidKeyEvent.KEYCODE_BUTTON_L1 -> {
                    if (!enabled) false else {
                        ControllerInputMode.active = true
                        if (event.repeatCount == 0) onPreviousSection()
                        true
                    }
                }
                AndroidKeyEvent.KEYCODE_BUTTON_R1 -> {
                    if (!enabled) false else {
                        ControllerInputMode.active = true
                        if (event.repeatCount == 0) onNextSection()
                        true
                    }
                }
                AndroidKeyEvent.KEYCODE_BUTTON_L2 -> {
                    if (!enabled) false else {
                        ControllerInputMode.active = true
                        if (event.repeatCount == 0) pageScroll(-1, event.eventTime)
                        true
                    }
                }
                AndroidKeyEvent.KEYCODE_BUTTON_R2 -> {
                    if (!enabled) false else {
                        ControllerInputMode.active = true
                        if (event.repeatCount == 0) pageScroll(1, event.eventTime)
                        true
                    }
                }
                AndroidKeyEvent.KEYCODE_DPAD_UP,
                AndroidKeyEvent.KEYCODE_DPAD_DOWN,
                AndroidKeyEvent.KEYCODE_DPAD_LEFT,
                AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (!enabled && !drawerOpen) false else {
                        ControllerInputMode.active = true
                        if (drawerOpen) {
                            if (event.keyCode == AndroidKeyEvent.KEYCODE_DPAD_UP) onDrawerMove(-1)
                            if (event.keyCode == AndroidKeyEvent.KEYCODE_DPAD_DOWN) onDrawerMove(1)
                            true
                        } else {
                            val direction = when (event.keyCode) {
                                AndroidKeyEvent.KEYCODE_DPAD_UP -> 1
                                AndroidKeyEvent.KEYCODE_DPAD_DOWN -> 2
                                AndroidKeyEvent.KEYCODE_DPAD_LEFT -> 3
                                else -> 4
                            }
                            moveFocus(direction, event.eventTime)
                        }
                    }
                }
                else -> false
            }
        }
    }

    val latestNavigationKey by androidx.compose.runtime.rememberUpdatedState(onNavigationKey)
    DisposableEffect(view, listState) {
        val globalHandler: (AndroidKeyEvent) -> Boolean = { e -> latestNavigationKey(e) }
        ControllerInputBridge.attach(globalHandler)

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
            ControllerInputBridge.detach(globalHandler)
            view.setOnGenericMotionListener(null)
        }
    }

    return this
        .onPreviewKeyEvent { event ->
            val native = event.nativeKeyEvent

            if (native.keyCode == AndroidKeyEvent.KEYCODE_BUTTON_A) {
                if (native.action == AndroidKeyEvent.ACTION_DOWN) {
                    ControllerInputMode.active = true
                    if (native.repeatCount == 0) return@onPreviewKeyEvent
                        registry?.activate(sectionId) ?: false
                    return@onPreviewKeyEvent true
                }
                return@onPreviewKeyEvent true
            }

            // Compose injection fallback; Activity consumes real handheld keys.
            onNavigationKey(native)
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
