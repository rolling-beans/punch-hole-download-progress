package eu.hxreborn.downloadsim;

/**
 * Deterministic simulated download progress based on elapsed monotonic time.
 *
 * <p>Uneven mode uses eight equal-duration phases with normalized speed multipliers.
 * It intentionally contains a complete stall and bursts, while always finishing
 * at the requested duration. No random state or network traffic is involved.
 */
public final class ProgressCurve {
    public static final int MODE_CONSTANT = 0;
    public static final int MODE_UNEVEN = 1;

    private static final double[] UNEVEN_SPEED = {
            0.60, 1.75, 0.15, 0.00, 2.20, 0.35, 1.50, 1.45
    };

    private ProgressCurve() {
    }

    /**
     * Convert the elapsed fraction of the remaining portion into progress units.
     * The first notification contains the exact initial percent; 100% is only
     * published when the selected duration has actually elapsed.
     */
    public static int progressUnits(int initialPercent, long elapsedMs,
            long durationMs, int mode) {
        if (initialPercent < 0 || initialPercent > 99) {
            throw new IllegalArgumentException("initialPercent must be 0..99");
        }
        double remainingFraction = fraction(elapsedMs, durationMs, mode);
        if (elapsedMs >= durationMs) {
            return 10000;
        }
        double percent = initialPercent + (100d - initialPercent) * remainingFraction;
        return Math.min(9999, (int) Math.round(percent * 100d));
    }

    public static double fraction(long elapsedMs, long durationMs, int mode) {
        if (durationMs <= 0) {
            throw new IllegalArgumentException("durationMs must be positive");
        }
        final double time = Math.max(0d, Math.min(1d, (double) elapsedMs / durationMs));
        if (mode != MODE_UNEVEN) {
            return time;
        }
        final int phases = UNEVEN_SPEED.length;
        final double position = time * phases;
        final int completed = Math.min(phases, (int) Math.floor(position));
        double distance = 0d;
        for (int i = 0; i < completed; i++) {
            distance += UNEVEN_SPEED[i] / phases;
        }
        if (completed < phases) {
            distance += (position - completed) * UNEVEN_SPEED[completed] / phases;
        }
        return Math.max(0d, Math.min(1d, distance));
    }
}
