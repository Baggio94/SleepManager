package com.med.sleepmanager.ui.controller

/** Retains the last controller-selected element in each top-level section. */
internal class ControllerFocusMemory {
    private val focusBySection = mutableMapOf<String, String>()
    private var activeSection: String? = null
    private var restoredOnVisit = false

    fun enter(section: String) {
        if (activeSection != section) {
            activeSection = section
            restoredOnVisit = false
        }
    }

    fun remember(section: String, target: String) {
        if (section.isNotBlank() && target.isNotBlank()) {
            focusBySection[section] = target
        }
    }

    fun lastFocused(section: String): String? = focusBySection[section]

    fun shouldRestore(section: String, target: String): Boolean =
        activeSection == section &&
            !restoredOnVisit &&
            focusBySection[section] == target

    fun markRestored(section: String, target: String) {
        if (shouldRestore(section, target)) restoredOnVisit = true
    }
}
