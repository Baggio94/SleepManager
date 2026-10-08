package com.med.sleepmanager

import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.med.sleepmanager.integration.HelperController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression for KONKR returning to ES-DE when the just-stopped Helper
 * activation trampoline triggered MainActivity.onUserLeaveHint().
 *
 * This verifies the Intent contract; real launcher transition still requires
 * an actual cold Helper start on the handheld.
 */
@RunWith(AndroidJUnit4::class)
class HelperActivationIntentTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun activityLaunchRemainsInSameTaskAndIsNotReportedAsUserLeave() {
        val intent = HelperController.buildActivationIntent(
            context = context,
            launchedFromActivity = true
        )

        assertEquals(HelperController.PACKAGE, intent.component?.packageName)
        assertTrue(intent.component?.className?.contains("HelperActivationActivity") == true)
        assertTrue(intent.hasFlag(Intent.FLAG_ACTIVITY_NO_USER_ACTION))
        assertTrue(intent.hasFlag(Intent.FLAG_ACTIVITY_NO_ANIMATION))
        assertFalse(intent.hasFlag(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    @Test
    fun backgroundCallerRetainsRequiredNewTaskWithoutUserLeaveSignal() {
        val intent = HelperController.buildActivationIntent(
            context = context,
            launchedFromActivity = false
        )

        assertEquals(HelperController.PACKAGE, intent.component?.packageName)
        assertTrue(intent.hasFlag(Intent.FLAG_ACTIVITY_NO_USER_ACTION))
        assertTrue(intent.hasFlag(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun Intent.hasFlag(flag: Int) = flags and flag != 0
}
