package com.med.sleepmanager.ui.controller

import androidx.compose.ui.focus.FocusRequester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerTargetRegistryTest {
    @Test
    fun activatingSelectedRowRunsExactlyOneAction() {
        val registry = ControllerTargetRegistry()
        var activations = 0
        registry.register("HOME", "wifi", FocusRequester(), { activations++ }, { emptyList() })
        registry.select("HOME", "wifi")

        assertTrue(registry.activate("HOME"))
        assertEquals(1, activations)
    }

    @Test
    fun firstAOnlySelectsAndNeverChangesSetting() {
        val registry = ControllerTargetRegistry()
        var activations = 0
        registry.register("HOME", "bluetooth", FocusRequester(), { activations++ }, { emptyList() })

        // Unit-test requesters are not attached to a Compose focus tree.
        assertFalse(registry.activate("HOME"))
        assertEquals(0, activations)
        assertEquals(null, registry.selectedId)
    }

    @Test
    fun arrowsSelectSecondaryActionsWithoutActivatingThem() {
        val registry = ControllerTargetRegistry()
        var selected = ""
        val actions = listOf(
            ControllerAction("Toggle") { selected = "toggle" },
            ControllerAction("Open") { selected = "open" },
            ControllerAction("Allow", false) { selected = "forbidden" }
        )
        registry.register("ADVANCED", "basicsync", FocusRequester(), {}, { actions })
        registry.select("ADVANCED", "basicsync")
        assertTrue(registry.changeAction("ADVANCED", 1))
        assertEquals("Open", registry.activeActionLabel("ADVANCED", "basicsync"))
        assertEquals("", selected)
        assertTrue(registry.activate("ADVANCED"))
        assertEquals("open", selected)
        assertFalse(registry.changeAction("ADVANCED", 1))
    }

    @Test
    fun sectionBoundariesDoNotExecuteStaleSelection() {
        val registry = ControllerTargetRegistry()
        var activations = 0
        registry.register("HOME", "wifi", FocusRequester(), { activations++ }, { emptyList() })
        registry.select("HOME", "wifi")
        assertFalse(registry.activate("ABOUT"))
        assertEquals(0, activations)
    }
}
