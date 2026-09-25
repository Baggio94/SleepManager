package com.med.sleepmanager.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

class ProcessExitReasonFormatterTest {
    @Test
    fun commonExitReasonsAreHumanReadable() {
        assertEquals("LOW_MEMORY", ProcessExitReasonFormatter.reasonLabel(3))
        assertEquals("CRASH", ProcessExitReasonFormatter.reasonLabel(4))
        assertEquals("ANR", ProcessExitReasonFormatter.reasonLabel(6))
        assertEquals("USER_REQUESTED", ProcessExitReasonFormatter.reasonLabel(10))
        assertEquals("PACKAGE_UPDATED", ProcessExitReasonFormatter.reasonLabel(16))
    }

    @Test
    fun unknownExitReasonKeepsRawCode() {
        assertEquals("REASON_99", ProcessExitReasonFormatter.reasonLabel(99))
    }

    @Test
    fun processImportanceIsReadable() {
        assertEquals(
            "FOREGROUND_SERVICE",
            ProcessExitReasonFormatter.importanceLabel(125)
        )
        assertEquals("CACHED", ProcessExitReasonFormatter.importanceLabel(400))
        assertEquals(
            "IMPORTANCE_777",
            ProcessExitReasonFormatter.importanceLabel(777)
        )
    }
}
