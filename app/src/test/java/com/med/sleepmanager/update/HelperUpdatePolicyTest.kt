package com.med.sleepmanager.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HelperUpdatePolicyTest {
    private fun helper(
        versionName: String = "0.5.4",
        versionCode: Long? = 529L,
        minimumCompatibleVersionCode: Long? = 528L
    ) = HelperUpdateInfo(
        versionName = versionName,
        versionCode = versionCode,
        minimumCompatibleVersionCode = minimumCompatibleVersionCode,
        releaseUrl = "https://github.com/Baggio94/SleepManager/releases/tag/v0.5.4"
    )

    @Test
    fun compatibleInstalledHelperDoesNotNeedUpdate() {
        assertFalse(
            UpdateChecker.helperNeedsUpdate(
                installedVersionCode = 528L,
                installedVersionName = "0.5.3",
                helper = helper()
            )
        )
    }

    @Test
    fun helperOlderThanMinimumNeedsUpdate() {
        assertTrue(
            UpdateChecker.helperNeedsUpdate(
                installedVersionCode = 527L,
                installedVersionName = "0.5.2",
                helper = helper()
            )
        )
    }

    @Test
    fun legacyMetadataFallsBackToLatestVersionCode() {
        assertTrue(
            UpdateChecker.helperNeedsUpdate(
                installedVersionCode = 528L,
                installedVersionName = "0.5.3",
                helper = helper(minimumCompatibleVersionCode = null)
            )
        )
    }

    @Test
    fun legacyMetadataFallsBackToVersionName() {
        assertTrue(
            UpdateChecker.helperNeedsUpdate(
                installedVersionCode = 528L,
                installedVersionName = "0.5.3",
                helper = helper(
                    versionName = "0.5.4",
                    versionCode = null,
                    minimumCompatibleVersionCode = null
                )
            )
        )
    }
}
