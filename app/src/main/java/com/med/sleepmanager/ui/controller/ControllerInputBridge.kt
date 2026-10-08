package com.med.sleepmanager.ui.controller

import android.view.KeyEvent

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

    fun dispatch(event: KeyEvent): Boolean =
        event.action == KeyEvent.ACTION_DOWN && callback?.invoke(event) == true
}
