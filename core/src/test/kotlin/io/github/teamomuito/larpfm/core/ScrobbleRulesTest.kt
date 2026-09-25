package io.github.teamomuito.larpfm.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScrobbleRulesTest {
    @Test
    fun thresholds() {
        assertNull(ScrobbleRules.thresholdMs(29_000))
        assertNull(ScrobbleRules.thresholdMs(30_000))
        assertEquals(15_500L, ScrobbleRules.thresholdMs(31_000))
        assertEquals(120_000L, ScrobbleRules.thresholdMs(240_000))
        assertEquals(240_000L, ScrobbleRules.thresholdMs(600_000))
        assertEquals(240_000L, ScrobbleRules.thresholdMs(0))
    }

    @Test
    fun `custom percentage`() {
        assertEquals(2_000L, ScrobbleRules.thresholdMs(200_000, percent = 1))
        assertEquals(180_000L, ScrobbleRules.thresholdMs(200_000, percent = 90))
        assertEquals("still capped at 4 minutes", 240_000L, ScrobbleRules.thresholdMs(300_000, percent = 100))
        assertNull("short tracks still never count", ScrobbleRules.thresholdMs(20_000, percent = 1))
    }

    @Test
    fun `out of range percentages are clamped`() {
        assertEquals(2_000L, ScrobbleRules.thresholdMs(200_000, percent = 0))
        assertEquals(200_000L, ScrobbleRules.thresholdMs(200_000, percent = 500))
    }
}
