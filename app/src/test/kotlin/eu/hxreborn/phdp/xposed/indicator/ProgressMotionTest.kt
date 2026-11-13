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

    @Test
    fun `first partial notification is the initial displayed position`() {
        val motion = ProgressMotion()
        motion.observe(73, 2000L)
        assertTrue(abs(motion.displayed - 73f) < 0.0001f)

        // No animation from zero, even with a long smoothing period.
        motion.frame(2016L, 750, 100)
        assertTrue(abs(motion.displayed - 73f) < 0.0001f)

        motion.observe(75, 2100L)
        val firstTransition = motion.frame(2116L, 750, 100)
        assertTrue(firstTransition >= 0.73f)
        assertTrue(firstTransition < 0.75f)
        var prior = motion.displayed
        for (t in 2132L..2900L step 16L) {
            motion.frame(t, 750, 100)
            assertTrue("unexpected reverse motion at $t", motion.displayed >= prior - 0.0001f)
            prior = motion.displayed
        }
    }

    @Test
    fun `new late-starting session can resume from confirmed partial progress`() {
        val motion = ProgressMotion()
        motion.observe(45, 0L)
        motion.frame(300L, 300, 100)
        motion.observe(0, 1000L)
        assertTrue(motion.frame(1016L, 750, 100) == 0f)

        // This mirrors IndicatorView's first-positive-after-zero session reset.
        motion.reset(
            displayPercent = 88f,
            observedPercent = 88f,
            nowMs = 1200L,
        )
        assertTrue(abs(motion.frame(1216L, 750, 100) - 0.88f) < 0.0001f)
        motion.observe(89, 1300L)
        assertTrue(motion.frame(1316L, 750, 100) >= 0.88f)
    }

    @Test
    fun `mode switch preserves previously drawn fraction`() {
        val motion = ProgressMotion()
        motion.reset(displayPercent = 63f, observedPercent = 67f, nowMs = 1500L)
        assertTrue(abs(motion.frame(1516L, 750, 100) - 0.63f) < 0.001f)
        assertTrue(motion.frame(1700L, 750, 100) > 0.63f)
    }
}
