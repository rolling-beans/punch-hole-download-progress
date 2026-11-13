package eu.hxreborn.downloadsim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ProgressCurveTest {
    @Test
    public void constantSpeedIsLinearAndExact() {
        assertEquals(0d, ProgressCurve.fraction(0, 10000,
                ProgressCurve.MODE_CONSTANT), 0d);
        assertEquals(0.25, ProgressCurve.fraction(2500, 10000,
                ProgressCurve.MODE_CONSTANT), 1e-12);
        assertEquals(0.5, ProgressCurve.fraction(5000, 10000,
                ProgressCurve.MODE_CONSTANT), 1e-12);
        assertEquals(1d, ProgressCurve.fraction(10000, 10000,
                ProgressCurve.MODE_CONSTANT), 0d);
    }

    @Test
    public void unevenSpeedHasDeterministicStallAndCompletesOnTime() {
        double beforeStall = ProgressCurve.fraction(3750, 10000,
                ProgressCurve.MODE_UNEVEN);
        double afterStall = ProgressCurve.fraction(5000, 10000,
                ProgressCurve.MODE_UNEVEN);
        assertEquals(0.3125, beforeStall, 1e-12);
        assertEquals(beforeStall, afterStall, 1e-12);
        assertEquals(1d, ProgressCurve.fraction(10000, 10000,
                ProgressCurve.MODE_UNEVEN), 1e-12);
    }

    @Test
    public void profilesAreBoundedAndMonotone() {
        for (int mode : new int[]{ProgressCurve.MODE_CONSTANT,
                ProgressCurve.MODE_UNEVEN}) {
            double previous = 0d;
            for (int millis = 0; millis <= 10000; millis += 5) {
                double value = ProgressCurve.fraction(millis, 10000, mode);
                assertTrue("Progress decreased", value >= previous - 1e-12);
                assertTrue("Progress outside 0..1", value >= 0d && value <= 1d);
                previous = value;
            }
        }
    }

    @Test
    public void clampsTimesAndRejectsNonpositiveDuration() {
        assertEquals(0d, ProgressCurve.fraction(-20, 1000,
                ProgressCurve.MODE_UNEVEN), 0d);
        assertEquals(1d, ProgressCurve.fraction(9000, 1000,
                ProgressCurve.MODE_UNEVEN), 0d);
        assertThrows(IllegalArgumentException.class,
                () -> ProgressCurve.fraction(0, 0, ProgressCurve.MODE_CONSTANT));
    }
}
