package com.alpwarestudio.chargefreeze.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FreezePolicyTest {
    @Test
    fun target_staysBelowCurrentLevel() {
        assertEquals(78, FreezePolicy.targetFor(level = 80, margin = 2))
        assertEquals(96, FreezePolicy.targetFor(level = 100, margin = 4))
    }

    @Test
    fun target_respectsMinimumThreshold() {
        assertEquals(20, FreezePolicy.targetFor(level = 21, margin = 5))
    }

    @Test
    fun target_rejectsUnsafeLowBattery() {
        assertThrows(IllegalStateException::class.java) {
            FreezePolicy.targetFor(level = 20, margin = 2)
        }
    }

    @Test
    fun movingThreshold_dropsOnlyNearBoundary() {
        assertEquals(
            78,
            FreezePolicy.nextThreshold(level = 90, currentThreshold = 78, margin = 2)
        )
        assertEquals(
            75,
            FreezePolicy.nextThreshold(level = 77, currentThreshold = 78, margin = 2)
        )
    }

    @Test
    fun movingThreshold_neverDropsBelowMinimum() {
        assertEquals(
            20,
            FreezePolicy.nextThreshold(level = 20, currentThreshold = 21, margin = 5)
        )
        assertEquals(
            20,
            FreezePolicy.nextThreshold(level = 19, currentThreshold = 20, margin = 5)
        )
    }

    @Test
    fun invalidTelemetry_doesNotChangeThreshold() {
        assertEquals(
            80,
            FreezePolicy.nextThreshold(level = 101, currentThreshold = 80, margin = 2)
        )
    }
}
