package com.my.kizzy.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for [Version]. The in-app updater compares the latest release
 * tag (e.g. "v6.2.1.6") against BuildConfig.VERSION_NAME ("6.2.1.5"). The previous
 * implementation only parsed major.minor and did not strip the "v" prefix, so every
 * 6.2.1.00X build compared equal (and tags parsed as major 0) — the update prompt
 * never fired. These tests lock in the fixed behaviour.
 */
class VersionTest {

    @Test
    fun `newer build number is detected as an update`() {
        val latest = Version("6.2.1.6")
        val current = Version("6.2.1.5")
        assertTrue(latest.whetherNeedUpdate(current))
        assertFalse(current.whetherNeedUpdate(latest))
    }

    @Test
    fun `leading v prefix on a git tag is stripped before comparing`() {
        // This is exactly the shape the updater passes: tag "v6.2.1.6" vs "6.2.1.5".
        val latest = Version("v6.2.1.6")
        val current = Version("6.2.1.5")
        assertTrue(latest.whetherNeedUpdate(current))
    }

    @Test
    fun `same version is not an update`() {
        assertFalse(Version("v6.2.1.5").whetherNeedUpdate(Version("6.2.1.5")))
        assertEquals(0, Version("6.2.1.5").compareTo(Version("6.2.1.5")))
    }

    @Test
    fun `four-component builds differing only in last part are ordered`() {
        assertTrue(Version("6.2.1.10").whetherNeedUpdate(Version("6.2.1.9")))
        assertTrue(Version("6.2.2.0").whetherNeedUpdate(Version("6.2.1.999")))
    }

    @Test
    fun `shorter versions are padded with zeros`() {
        assertEquals(0, Version("6.2").compareTo(Version("6.2.0.0")))
        assertTrue(Version("6.2.1.5").whetherNeedUpdate(Version("6.2.1")))
        assertTrue(Version("6.3").whetherNeedUpdate(Version("6.2.1.5")))
    }

    @Test
    fun `major and minor comparisons still work`() {
        assertTrue(Version("7.0.0.0").whetherNeedUpdate(Version("6.9.9.9")))
        assertTrue(Version("6.3.0.0").whetherNeedUpdate(Version("6.2.9.9")))
        assertFalse(Version("6.2.0.0").whetherNeedUpdate(Version("6.2.0.1")))
    }

    @Test
    fun `blank and null-ish inputs do not crash and compare as zero`() {
        assertEquals(0, Version("").compareTo(Version()))
        assertEquals(0, Version(null as String?).compareTo(Version("0.0")))
        assertFalse(Version(null as String?).whetherNeedUpdate(Version("6.2.1.5")))
    }

    @Test
    fun `non-numeric components are treated as zero`() {
        // "v" is stripped; a stray non-numeric token falls back to 0.
        assertEquals(0, Version("v").compareTo(Version("0")))
        assertTrue(Version("6.2.1.5").whetherNeedUpdate(Version("abc")))
    }
}
