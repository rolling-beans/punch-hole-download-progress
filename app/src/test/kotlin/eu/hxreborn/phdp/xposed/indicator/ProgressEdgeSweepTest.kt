package eu.hxreborn.phdp.xposed.indicator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ProgressEdgeSweepTest {
    @Test
    fun `large initial percent gets adaptive duration with smooth endpoints`() {
        val sweep = ProgressEdgeSweep()
        sweep.start(0f, 0.65f, 1_000L)
        assertTrue(sweep.durationMs > 100L)
        assertTrue(sweep.durationMs <= 450L)
        assertEquals(0f, sweep.valueAt(1_000L), 0f)
        assertEquals(0f, sweep.stateAt(1_000L).velocity, 0f)
        assertFalse(sweep.isFinished(1_000L + sweep.durationMs - 1L))
        val end = sweep.stateAt(1_000L + sweep.durationMs)
        assertEquals(0.65f, end.position, 0.00001f)
        assertEquals(0f, end.velocity, 0f)
        assertEquals(0f, end.acceleration, 0f)
        assertTrue(sweep.isFinished(1_000L + sweep.durationMs))
    }

    @Test
    fun `small final percent finishes at full ring in one hundred milliseconds`() {
        val sweep = ProgressEdgeSweep()
        sweep.start(0.93f, 1f, 20_000L)
        assertEquals(100L, sweep.durationMs)
        assertEquals(0.93f, sweep.valueAt(20_000L), 0.00001f)
        assertTrue(sweep.valueAt(20_075L) < 1f)
        assertEquals(1f, sweep.valueAt(20_100L), 0f)
        assertTrue(sweep.isFinished(20_100L))
    }

    @Test
    fun `midflight retarget preserves position velocity and acceleration`() {
        val sweep = ProgressEdgeSweep()
        sweep.start(0f, 0.65f, 0L)
        val before = sweep.stateAt(50L)
        assertTrue(sweep.retarget(0.73f, 50L))
        val after = sweep.stateAt(50L)
        assertEquals(before.position, after.position, 0f)
        assertEquals(before.velocity, after.velocity, 0f)
        assertEquals(before.acceleration, after.acceleration, 0f)
        assertEquals(0.73f, sweep.valueAt(50L + sweep.durationMs), 0.00001f)
    }

    @Test
    fun `completion can interrupt opening sweep without velocity jump`() {
        val sweep = ProgressEdgeSweep()
        sweep.start(0f, 0.65f, 0L)
        val before = sweep.stateAt(20L)
        assertTrue(sweep.retarget(1f, 20L))
        val after = sweep.stateAt(20L)
        assertEquals(before.position, after.position, 0f)
        assertEquals(before.velocity, after.velocity, 0f)
        assertEquals(before.acceleration, after.acceleration, 0f)
        assertEquals(1f, sweep.valueAt(20L + sweep.durationMs), 0f)
    }

    @Test
    fun `impossible short retarget is deferred to a settled C2 boundary`() {
        val sweep = ProgressEdgeSweep()
        sweep.start(0f, 0.65f, 0L)
        val oldDuration = sweep.durationMs
        assertFalse(sweep.retarget(0.66f, oldDuration - 60L))
        val end = sweep.stateAt(oldDuration)
        assertEquals(0.65f, end.position, 0f)
        assertEquals(0f, end.velocity, 0f)
        assertEquals(0f, end.acceleration, 0f)
        assertTrue(sweep.startQueued(oldDuration))
        assertEquals(0.65f, sweep.stateAt(oldDuration).position, 0f)
        assertEquals(0f, sweep.stateAt(oldDuration).velocity, 0f)
        assertEquals(0.66f, sweep.valueAt(oldDuration + sweep.durationMs), 0f)
        assertFalse(sweep.startQueued(oldDuration + sweep.durationMs))
    }

    @Test
    fun `dense frame sampling stays bounded without reversing`() {
        val sweep = ProgressEdgeSweep()
        sweep.start(0f, 0.82f, 100L)
        var previous = 0f
        for (now in 100L..(100L + sweep.durationMs)) {
            val state = sweep.stateAt(now)
            assertTrue("position reversed at $now", state.position >= previous - 0.000001f)
            assertTrue("position exceeded bounds", state.position in 0f..0.82f)
            assertTrue("unbounded velocity", abs(state.velocity) <= 0.0065f)
            assertTrue("unbounded acceleration", abs(state.acceleration) <= 0.00015f)
            previous = state.position
        }
    }

    @Test
    fun `cancellation creates a clean new session`() {
        val sweep = ProgressEdgeSweep()
        sweep.start(0f, 0.9f, 0L)
        sweep.cancel()
        assertFalse(sweep.active)
        sweep.start(0f, 0.33f, 200L)
        assertEquals(0f, sweep.valueAt(200L), 0f)
        assertEquals(0.33f, sweep.valueAt(200L + sweep.durationMs), 0f)
    }
}
