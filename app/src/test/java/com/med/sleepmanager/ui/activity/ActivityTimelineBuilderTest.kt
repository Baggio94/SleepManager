package com.med.sleepmanager.ui.activity

import com.med.sleepmanager.data.EventHistoryStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityTimelineBuilderTest {
    @Test
    fun groupsSleepAndWakeAndKeepsStepsChronological() {
        val groups =
            ActivityTimelineBuilder.build(
                listOf(
                    EventHistoryStore.Event(
                        timestamp = 400L,
                        message =
                            "Wake → Wi-Fi restored to previous state · Bluetooth restored to previous state"
                    ),
                    EventHistoryStore.Event(
                        timestamp = 300L,
                        message = "Wake → BasicSync restored · RUNNING"
                    ),
                    EventHistoryStore.Event(
                        timestamp = 200L,
                        message = "Sleep → Wi-Fi off · Bluetooth off"
                    ),
                    EventHistoryStore.Event(
                        timestamp = 100L,
                        message = "Sleep → Tailscale disconnected"
                    )
                )
            )

        assertEquals(2, groups.size)
        assertEquals(ActivityTimelineKind.WAKE, groups[0].kind)
        assertEquals(
            listOf(
                "BasicSync restored",
                "Wi-Fi restored",
                "Bluetooth restored"
            ),
            groups[0].steps.map { it.text }
        )

        assertEquals(ActivityTimelineKind.SLEEP, groups[1].kind)
        assertEquals(
            listOf(
                "Tailscale disconnected",
                "Wi-Fi turned off",
                "Bluetooth turned off"
            ),
            groups[1].steps.map { it.text }
        )
    }

    @Test
    fun hidesTechnicalConfirmationButKeepsRawSequenceUseful() {
        val groups =
            ActivityTimelineBuilder.build(
                listOf(
                    EventHistoryStore.Event(
                        100L,
                        "Sleep → RAOfflineProxy STOP accepted · restore owned"
                    ),
                    EventHistoryStore.Event(
                        110L,
                        "Sleep → RAOfflineProxy gate complete · STOP confirmed"
                    ),
                    EventHistoryStore.Event(
                        120L,
                        "Sleep → Wi-Fi off"
                    )
                )
            )

        assertEquals(1, groups.size)
        assertEquals(
            listOf(
                "RAOfflineProxy stopped",
                "Wi-Fi turned off"
            ),
            groups.single().steps.map { it.text }
        )
    }

    @Test
    fun recoveryMessagesUsePlainLanguage() {
        val groups =
            ActivityTimelineBuilder.build(
                listOf(
                    EventHistoryStore.Event(
                        100L,
                        "Helper reconciliation → replayed cycle 42 attempt 1"
                    ),
                    EventHistoryStore.Event(
                        110L,
                        "Helper reconciliation → no acknowledgement after 3 attempts"
                    )
                )
            )

        assertEquals(ActivityTimelineKind.RECOVERY, groups.single().kind)
        assertTrue(
            groups.single().steps.map { it.text }.containsAll(
                listOf(
                    "Radio restoration retried",
                    "Radio restoration could not be confirmed"
                )
            )
        )
    }
}
