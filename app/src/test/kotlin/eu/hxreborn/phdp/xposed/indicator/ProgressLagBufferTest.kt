package eu.hxreborn.phdp.xposed.indicator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressLagBufferTest {
    @Test
    fun `fast updates keep adaptive braking reserve behind confirmed progress`() {
        val buffer = ProgressLagBuffer()
        buffer.reset(displayed = 10f, observed = 10f, nowMs = 0L)
        buffer.observe(20f, 1_000L, 750)
        val goal = buffer.targetAt(1_000L, 750, 10f)
        assertTrue("Expected some free progress for braking", goal < 19f)
        assertTrue("Buffer may not retreat behind the already drawn ring", goal >= 10f)
        assertTrue("No unconfirmed progress is shown", goal <= buffer.confirmed)
    }

    @Test
    fun `when notifications stop withheld percentage releases gradually`() {
        val buffer = ProgressLagBuffer()
        buffer.reset(displayed = 10f, observed = 10f, nowMs = 0L)
        buffer.observe(20f, 1_000L, 750)
        val initial = buffer.targetAt(1_000L, 750, 10f)
        val beforeRelease = buffer.targetAt(2_000L, 750, initial)
        val midway = buffer.targetAt(2_375L, 750, beforeRelease)
        val settled = buffer.targetAt(3_000L, 750, midway)
        assertEquals(initial, beforeRelease, 0.00001f)
        assertTrue("Release must advance toward confirmed percent", midway > beforeRelease)
        assertTrue("Release must not jump to confirmed percent", midway < 20f)
        assertEquals(20f, settled, 0.00001f)
    }

    @Test
    fun `slow following segment retains some braking room instead of snapping`() {
        val buffer = ProgressLagBuffer()
        buffer.reset(displayed = 10f, observed = 10f, nowMs = 0L)
        buffer.observe(35f, 1_000L, 1_500)
        val firstGoal = buffer.targetAt(1_000L, 1_500, 10f)
        buffer.observe(36f, 2_000L, 1_500)
        val slowGoal = buffer.targetAt(2_000L, 1_500, firstGoal)
        assertTrue("Goal must stay monotonic", slowGoal >= firstGoal)
        assertTrue("Slow segment should not immediately exhaust reserve", slowGoal < 36f)
        assertTrue("Even after a stall, no progress is invented", slowGoal <= buffer.confirmed)
    }

    @Test
    fun `subpercent source progress does not create a one-percent dead zone`() {
        val buffer = ProgressLagBuffer()
        buffer.reset(displayed = 40f, observed = 40f, nowMs = 0L)
        buffer.observe(40.5f, 1_000L, 1_500)
        val goal = buffer.targetAt(1_000L, 1_500, 40f)
        assertTrue(goal > 40f)
        assertTrue(goal < 40.5f)
    }

    @Test
    fun `a small source regression cannot command ring reversal`() {
        val buffer = ProgressLagBuffer()
        buffer.reset(displayed = 35f, observed = 40f, nowMs = 0L)
        val first = buffer.targetAt(0L, 750, 35f)
        buffer.observe(39f, 1_000L, 750)
        val second = buffer.targetAt(1_000L, 750, first)
        assertTrue(second >= first)
        assertEquals(39f, buffer.confirmed, 0.00001f)
    }
}
