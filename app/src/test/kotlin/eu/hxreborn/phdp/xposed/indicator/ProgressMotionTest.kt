package eu.hxreborn.phdp.xposed.indicator

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ProgressMotionTest {
    @Test
    fun `smoothing settles at the confirmed target`() {
        val motion = ProgressMotion()
        motion.observe(40, 0)
        for (t in 16L..1000L step 16L) motion.frame(t, 100, 100)
        assertTrue(abs(motion.displayed - 40f) < 0.01f)
    }

    @Test
    fun `overlapping samples retain smooth monotone motion`() {
        val motion = ProgressMotion()
        motion.observe(10, 0)
        for (t in 16L..960L step 16L) motion.frame(t, 100, 100)
        motion.observe(11, 1000)
        var previous = motion.displayed
        for (t in 1000L..1900L step 16L) {
            motion.frame(t, 750, 100)
            assertTrue("reversed at $t", motion.displayed >= previous - 0.0001f)
            assertTrue("exceeded forecast bound at $t", motion.displayed <= 13.001f)
            previous = motion.displayed
        }
    }

    @Test
    fun `prediction pauses without source updates and never reaches completion`() {
        val motion = ProgressMotion()
        motion.observe(98, 0)
        for (t in 16L..1000L step 16L) motion.frame(t, 100, 100)
        motion.observe(99, 1600)
        for (t in 1616L..6000L step 16L) {
            motion.frame(t, 100, 100)
            assertTrue("predicted completion at $t", motion.displayed < 100f)
        }
        motion.observe(100, 6100)
        motion.frame(6200, 100, 100)
        assertTrue(motion.displayed > 99f)
    }

    @Test
    fun `zero progress resets prediction and display`() {
        val motion = ProgressMotion()
        motion.observe(20, 0)
        for (t in 16L..1000L step 16L) motion.frame(t, 100, 100)
        motion.observe(23, 1100)
        motion.frame(1200, 100, 100)
        motion.observe(0, 1250)
        assertTrue(motion.frame(1260, 750, 100) == 0f)
        assertTrue(!motion.needsFrame(2000))
    }

    @Test
    fun `no smoothing snaps directly to bounded estimate`() {
        val motion = ProgressMotion()
        motion.observe(10, 0)
        motion.frame(0, 0, 100)
        motion.observe(12, 1000)
        val fraction = motion.frame(1200, 0, 100)
        assertTrue(fraction >= 0.12f && fraction <= 0.14f)
    }
}
