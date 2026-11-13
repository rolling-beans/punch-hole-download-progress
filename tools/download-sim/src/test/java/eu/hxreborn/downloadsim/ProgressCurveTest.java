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
    public void partialStartIsImmediateForBothProfiles() {
        for (int mode : new int[]{ProgressCurve.MODE_CONSTANT,
                ProgressCurve.MODE_UNEVEN}) {
            assertEquals(6500, ProgressCurve.progressUnits(65, 0, 30000, mode));
            assertEquals(10000, ProgressCurve.progressUnits(65, 30000, 30000, mode));
        }
    }

    @Test
    public void constantSpeedCompletesRemainingFractionAtSpecifiedDuration() {
        assertEquals(6500, ProgressCurve.progressUnits(65, 0, 10000,
                ProgressCurve.MODE_CONSTANT));
        assertEquals(7375, ProgressCurve.progressUnits(65, 2500, 10000,
                ProgressCurve.MODE_CONSTANT));
        assertEquals(8250, ProgressCurve.progressUnits(65, 5000, 10000,
                ProgressCurve.MODE_CONSTANT));
        assertEquals(9999, ProgressCurve.progressUnits(65, 9999, 10000,
                ProgressCurve.MODE_CONSTANT));
        assertEquals(10000, ProgressCurve.progressUnits(65, 10000, 10000,
                ProgressCurve.MODE_CONSTANT));
    }

    @Test
    public void partialStartUnevenRetainsStallAndMonotonicity() {
        int atStall = ProgressCurve.progressUnits(65, 3750, 10000,
                ProgressCurve.MODE_UNEVEN);
        assertEquals(atStall, ProgressCurve.progressUnits(65, 5000, 10000,
                ProgressCurve.MODE_UNEVEN));
        int last = 6500;
        for (int millis = 0; millis <= 10000; millis += 10) {
            int actual = ProgressCurve.progressUnits(65, millis, 10000,
                    ProgressCurve.MODE_UNEVEN);
            assertTrue("Progress went backwards", actual >= last);
            assertTrue("Progress outside selected range", actual >= 6500 && actual <= 10000);
            last = actual;
        }
    }

    @Test
    public void boundaryStartsAndInvalidInputs() {
        assertEquals(0, ProgressCurve.progressUnits(0, 0, 10000,
                ProgressCurve.MODE_CONSTANT));
        assertEquals(9900, ProgressCurve.progressUnits(99, 0, 10000,
                ProgressCurve.MODE_UNEVEN));
        assertEquals(10000, ProgressCurve.progressUnits(99, 10000, 10000,
                ProgressCurve.MODE_UNEVEN));
        assertThrows(IllegalArgumentException.class,
                () -> ProgressCurve.progressUnits(-1, 0, 1000, ProgressCurve.MODE_CONSTANT));
        assertThrows(IllegalArgumentException.class,
                () -> ProgressCurve.progressUnits(100, 0, 1000, ProgressCurve.MODE_CONSTANT));
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
