package eu.hxreborn.phdp.xposed.indicator

/**
 * A short, independent sweep used when a download first becomes visible and
 * immediately before its completion effect. This must not inherit the ordinary
 * progress smoothness setting, which may be 750 ms or disabled entirely.
 *
 * Input/output values are display fractions (0..1), timestamps are uptime ms.
 */
internal class ProgressEdgeSweep {
    var active: Boolean = false
        private set

    private var from = 0f
    private var to = 0f
    private var startedMs = 0L

    fun start(
        fromFraction: Float,
        toFraction: Float,
        nowMs: Long,
    ) {
        from = fromFraction.coerceIn(0f, 1f)
        to = toFraction.coerceIn(0f, 1f)
        startedMs = nowMs
        active = true
    }

    fun valueAt(nowMs: Long): Float {
        if (!active) return to
        val t =
            ((nowMs - startedMs).coerceAtLeast(0L) / DURATION_MS.toFloat())
                .coerceIn(0f, 1f)
        // Smoothstep: no sudden velocity discontinuity at the endpoints.
        val eased = t * t * (3f - 2f * t)
        return (from + (to - from) * eased).coerceIn(0f, 1f)
    }

    fun isFinished(nowMs: Long): Boolean = active && nowMs - startedMs >= DURATION_MS

    fun cancel() {
        active = false
    }

    companion object {
        const val DURATION_MS = 100L
    }
}
