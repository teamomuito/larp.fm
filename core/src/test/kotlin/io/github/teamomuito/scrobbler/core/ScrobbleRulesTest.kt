package io.github.teamomuito.scrobbler.core

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
}
