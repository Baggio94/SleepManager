package com.med.sleepmanager.ui.controller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerFocusMemoryTest {
    @Test fun initialVisitHasNoInventedFocus() {
        val state = ControllerFocusMemory()
        state.enter("HOME")
        assertNull(state.lastFocused("HOME"))
        assertFalse(state.shouldRestore("HOME", "wifi"))
    }

    @Test fun eachSectionRetainsItsOwnFocus() {
        val state = ControllerFocusMemory()
        state.enter("HOME")
        state.remember("HOME", "wifi")
        state.enter("ABOUT")
        state.remember("ABOUT", "updates")
        assertTrue(state.shouldRestore("ABOUT", "updates"))
        assertFalse(state.shouldRestore("ABOUT", "wifi"))
        state.enter("HOME")
        assertEquals("wifi", state.lastFocused("HOME"))
        assertTrue(state.shouldRestore("HOME", "wifi"))
    }

    @Test fun restorationOnlyOccursOnceOnEachVisit() {
        val state = ControllerFocusMemory()
        state.enter("HOME")
        state.remember("HOME", "wifi")
        assertTrue(state.shouldRestore("HOME", "wifi"))
        state.markRestored("HOME", "wifi")
        assertFalse(state.shouldRestore("HOME", "wifi"))
        state.enter("HOME")
        assertFalse(state.shouldRestore("HOME", "wifi"))
        state.enter("ADVANCED")
        state.enter("HOME")
        assertTrue(state.shouldRestore("HOME", "wifi"))
    }

    @Test fun invalidTargetCannotEraseFocus() {
        val state = ControllerFocusMemory()
        state.remember("HOME", "wifi")
        state.remember("HOME", "")
        state.remember("", "ghost")
        assertEquals("wifi", state.lastFocused("HOME"))
    }
}
