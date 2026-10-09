package com.med.sleepmanager.ui.controller

import android.view.KeyEvent
import android.view.InputDevice

/**
 * Activity-level navigation entry point. Registered only while the Compose
 * screen is active, so a first D-pad or shoulder press needs no child focus.
 */
internal object ControllerInputBridge {
    @Volatile private var callback: ((KeyEvent) -> Boolean)? = null

    fun attach(handler: (KeyEvent) -> Boolean) {
        callback = handler
    }

    fun detach(handler: (KeyEvent) -> Boolean) {
        if (callback === handler) callback = null
    }

    fun dispatch(event: KeyEvent): Boolean {
        val gamepad =
            event.isFromSource(InputDevice.SOURCE_GAMEPAD) ||
                event.isFromSource(InputDevice.SOURCE_JOYSTICK) ||
                event.isFromSource(InputDevice.SOURCE_DPAD)
        // Some handheld firmware exposes its built-in D-pad as a keyboard
        // instead of SOURCE_GAMEPAD. Recognize the actual controller key codes.
        val handheldKey = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1,
            KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2,
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_MENU -> true
            else -> false
        }
        return (gamepad || handheldKey) && event.action == KeyEvent.ACTION_DOWN &&
            callback?.invoke(event) == true
    }
}
