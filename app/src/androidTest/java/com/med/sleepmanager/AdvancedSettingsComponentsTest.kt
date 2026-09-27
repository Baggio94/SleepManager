package com.med.sleepmanager

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.med.sleepmanager.data.AppPreferences
import com.med.sleepmanager.ui.screens.AdvancedSettingsPage
import com.med.sleepmanager.ui.theme.SleepManagerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AdvancedSettingsComponentsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun everyAdvancedControl_invokesItsExpectedCallback() {
        var periodic by mutableStateOf(false)
        var syncThenStop by mutableStateOf(false)
        var customDelayEnabled by mutableStateOf(false)
        var customDelayMs by mutableStateOf(60_000L)
        var batteryCondition by mutableStateOf(false)
        var batteryBelow by mutableStateOf(30)
        var notCharging by mutableStateOf(false)
        var batterySaverMode by mutableStateOf(AppPreferences.BATTERY_SAVER_IGNORE)
        var scheduleEnabled by mutableStateOf(false)
        var startPicked = false
        var endPicked = false

        composeRule.setContent {
            SleepManagerTheme {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    AdvancedSettingsPage(
                        periodicSyncWhileSleeping = periodic,
                        batterySaverControlSupported = true,
                        syncThenStopOnSleepWake = syncThenStop,
                        syncConditionsAvailable = true,
                        onPeriodicSyncWhileSleepingChange = { periodic = it },
                        onSyncThenStopOnSleepWakeChange = { syncThenStop = it },
                        customDelayEnabled = customDelayEnabled,
                        customDelayMs = customDelayMs,
                        batteryConditionEnabled = batteryCondition,
                        batteryBelowPercent = batteryBelow,
                        notChargingOnly = notCharging,
                        batterySaverMode = batterySaverMode,
                        scheduleEnabled = scheduleEnabled,
                        scheduleStartMinutes = 23 * 60,
                        scheduleEndMinutes = 7 * 60,
                        onCustomDelayEnabledChange = {
                            customDelayEnabled = it
                        },
                        onCustomDelayChange = { customDelayMs = it },
                        onBatteryConditionEnabledChange = {
                            batteryCondition = it
                        },
                        onBatteryBelowPercentChange = { batteryBelow = it },
                        onNotChargingOnlyChange = { notCharging = it },
                        onBatterySaverModeChange = {
                            batterySaverMode = it
                        },
                        onScheduleEnabledChange = {
                            scheduleEnabled = it
                        },
                        onPickScheduleStart = { startPicked = true },
                        onPickScheduleEnd = { endPicked = true }
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription(
            "Periodic sync while sleeping toggle"
        ).performScrollTo().performClick()
        assertTrue(periodic)

        composeRule.onNodeWithContentDescription(
            "Sync then stop on sleep & wake toggle"
        ).performScrollTo().performClick()
        assertTrue(syncThenStop)

        composeRule.onNodeWithContentDescription("Use custom delay toggle")
            .performScrollTo().performClick()
        assertTrue(customDelayEnabled)

        composeRule.onNodeWithText("30 min")
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 2_000) {
            customDelayMs == 1_800_000L
        }
        assertEquals(1_800_000L, customDelayMs)
        composeRule.onNodeWithText("30 min")
            .performScrollTo()
            .assertIsSelected()

        composeRule.onNodeWithContentDescription("Battery level toggle")
            .performScrollTo().performClick()
        assertTrue(batteryCondition)

        composeRule.onNodeWithText("< 60%")
            .performScrollTo().performClick()
        assertEquals(60, batteryBelow)

        composeRule.onNodeWithContentDescription("Not charging toggle")
            .performScrollTo().performClick()
        assertTrue(notCharging)

        // Battery Saver's real Home/Advanced mutual-exclusion flow is
        // exercised by Beta4RegressionTest. Here we only verify that the
        // capability-gated controls are composed with stable test semantics.
        composeRule.onNodeWithTag("battery_saver_mode_on")
            .performScrollTo()
            .fetchSemanticsNode()
        composeRule.onNodeWithTag("battery_saver_mode_off")
            .fetchSemanticsNode()

        composeRule.onNodeWithContentDescription("Schedule toggle")
            .performScrollTo().performClick()
        assertTrue(scheduleEnabled)

        composeRule.onNodeWithText("From 23:00")
            .performScrollTo().performClick()
        composeRule.onNodeWithText("To 07:00")
            .performScrollTo().performClick()

        assertTrue(startPicked)
        assertTrue(endPicked)
    }
}
