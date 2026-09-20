package com.med.sleepmanager.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparatorTest {
    @Test
    fun newerStableVersionIsDetected() {
        assertTrue(VersionComparator.isNewer("0.6.0", "0.5.1"))
    }

    @Test
    fun stableReleaseIsNewerThanMatchingDevelopmentBuild() {
        assertTrue(VersionComparator.isNewer("0.5.1", "0.5.1-dev1"))
    }

    @Test
    fun olderStableReleaseDoesNotReplaceNewerDevelopmentBuild() {
        assertFalse(VersionComparator.isNewer("0.5.0", "0.5.1-dev1"))
    }

    @Test
    fun sameVersionIsNotAnUpdate() {
        assertFalse(VersionComparator.isNewer("0.5.1", "0.5.1"))
    }
}
