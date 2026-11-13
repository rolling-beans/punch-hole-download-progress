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
