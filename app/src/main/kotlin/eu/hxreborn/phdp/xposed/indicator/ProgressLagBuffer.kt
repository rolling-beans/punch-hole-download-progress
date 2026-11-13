package eu.hxreborn.phdp.xposed.indicator

import kotlin.math.max
import kotlin.math.min

/**
 * A causal trailing reservoir for sparse or bursty progress notifications.
 *
 * The ring follows CONFIRMED progress with a small, speed-dependent lag,
 * rather than predicting a future percent from a previous fast segment.
 * After updates stop, the withheld last few percent are released gradually.
 * Thus there remains braking room even when the producer abruptly stalls.
 *
 * targetAt() is monotonic for a session: a temporary rate drop can never
 * command a reverse movement. Completion and explicit resets are handled
 * outside this buffer by IndicatorView.
 */
internal class ProgressLagBuffer {
    var confirmed: Float = 0f
        private set

    private var lastConfirmed = 0f
    private var lastObservationMs = -1L
    private var observationGapMs = 1000L
    private var estimatedRate = 0f // percentage points per ms
    private var withheld = 0f
    private var commanded = 0f

    fun reset(
        displayed: Float,
        observed: Float,
        nowMs: Long,
    ) {
        confirmed = observed.coerceIn(0f, 100f)
        lastConfirmed = confirmed
        lastObservationMs = nowMs
        observationGapMs = 1000L
        estimatedRate = 0f
        withheld = 0f
        commanded = displayed.coerceIn(0f, 100f)
    }

    fun observe(
        value: Float,
        nowMs: Long,
        smoothnessMs: Int,
    ) {
        val newValue = value.coerceIn(0f, 100f)
        val interval = (nowMs - lastObservationMs).coerceAtLeast(0L)
        val change = newValue - lastConfirmed
        if (lastObservationMs >= 0L && interval in 50L..10_000L && change > 0f) {
            val instantRate = (change / interval).coerceAtMost(MAX_ESTIMATED_RATE)
            estimatedRate =
                if (estimatedRate > 0f) {
                    0.65f * estimatedRate + 0.35f * instantRate
                } else {
                    instantRate
                }
            observationGapMs = interval
            // Dynamic buffer is bounded to 6 percentage points; no fixed
            // 1% dead-zone on very slow downloads.
            val windowMs = (smoothnessMs * 0.45f).coerceIn(350f, 650f)
            val desired = (estimatedRate * windowMs).coerceAtMost(MAX_WITHHELD_PERCENT)
            // Do not instantly discard all braking room when the source slows.
            withheld = max(withheld * 0.80f, desired)
        } else if (change <= 0f) {
            estimatedRate *= 0.5f
            // Hold existing reserve until the time-based release below.
        }
        confirmed = newValue
        lastConfirmed = newValue
        lastObservationMs = nowMs
    }

    fun targetAt(
        nowMs: Long,
        smoothnessMs: Int,
        displayed: Float,
    ): Float {
        val age = (nowMs - lastObservationMs).coerceAtLeast(0L)
        // Wait roughly one notification interval before releasing the reserve.
        // Sparse (1 Hz) updates retain their runway while the next sample
        // is expected. Smoothstep release avoids a step in the target signal.
        val releaseStarts = observationGapMs.coerceIn(400L, 1_200L)
        val releaseDuration = smoothnessMs.coerceIn(500, 2_000).toLong()
        val u =
            ((age - releaseStarts).coerceAtLeast(0L).toFloat() / releaseDuration)
                .coerceIn(0f, 1f)
        val easing = u * u * (3f - 2f * u)
        val reserve = withheld * (1f - easing)
        val candidate = (confirmed - reserve).coerceAtLeast(0f)
        // Never command a backwards target, including after a sudden stall.
        commanded = min(confirmed, max(max(commanded, displayed), candidate))
        return commanded
    }

    fun needsCatchUp(displayed: Float): Boolean = confirmed - displayed > 0.02f

    companion object {
        const val MAX_WITHHELD_PERCENT = 6f
        private const val MAX_ESTIMATED_RATE = 0.025f
    }
}
