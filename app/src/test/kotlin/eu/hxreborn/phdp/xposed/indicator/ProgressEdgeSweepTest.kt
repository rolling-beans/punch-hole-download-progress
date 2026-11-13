package eu.hxreborn.phdp.xposed.indicator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressEdgeSweepTest {
    @Test
    fun `first progress already at 65 percent sweeps from zero in 100ms`() {
        val edge = ProgressEdgeSweep()
        edge.start(0f, 0.65f, 1_000L)
        assertEquals(0f, edge.valueAt(1_000L), 0f)
        assertFalse(edge.isFinished(1_099L))
        assertTrue(edge.valueAt(1_050L) in 0.01f..0.64f)
        assertEquals(0.65f, edge.valueAt(1_100L), 0.00001f)
        assertTrue(edge.isFinished(1_100L))
    }

    @Test
    fun `last progress sweeps to full circle before completion`() {
        val edge = ProgressEdgeSweep()
        edge.start(0.93f, 1f, 20_000L)
        assertEquals(0.93f, edge.valueAt(20_000L), 0.00001f)
        assertTrue(edge.valueAt(20_075L) < 1f)
        assertFalse(edge.isFinished(20_099L))
        assertEquals(1f, edge.valueAt(20_100L), 0f)
        assertTrue(edge.isFinished(20_100L))
    }

    @Test
    fun `updated start percentage preserves continuity when retargeted`() {
        val edge = ProgressEdgeSweep()
        edge.start(0f, 0.65f, 0L)
        val halfway = edge.valueAt(50L)
        edge.start(halfway, 0.73f, 50L)
        assertEquals(halfway, edge.valueAt(50L), 0f)
        assertEquals(0.73f, edge.valueAt(150L), 0.00001f)
    }

    @Test
    fun `rapid completion and a new session cannot inherit stale animation`() {
        val edge = ProgressEdgeSweep()
        edge.start(0f, 0.9f, 100L)
        val current = edge.valueAt(150L)
        edge.start(current, 1f, 150L)
        assertEquals(current, edge.valueAt(150L), 0f)
        assertEquals(1f, edge.valueAt(250L), 0f)
        edge.cancel()
        assertFalse(edge.active)
        edge.start(0f, 0.33f, 300L)
        assertEquals(0f, edge.valueAt(300L), 0f)
    }

    @Test
    fun `sweep remains bounded and monotone across every millisecond`() {
        val edge = ProgressEdgeSweep()
        edge.start(0f, 0.82f, 100L)
        var previous = 0f
        for (time in 100L..200L) {
            val value = edge.valueAt(time)
            assertTrue(value >= previous)
            assertTrue(value in 0f..0.82f)
            previous = value
        }
    }
}
