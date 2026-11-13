package eu.hxreborn.phdp.xposed.indicator

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min

/**
 * A bounded progress predictor and continuous, critically damped display controller.
 * All values are percentages (0..100); timestamps use a monotonic millisecond clock.
 * Source updates remain authoritative: prediction never signals completion.
 */
internal class ProgressMotion {
    var displayed: Float = 0f
        private set

    private var target = 0f
    private var velocity = 0f // percent per second
    private var observed = 0f
    private var observedRate = 0f // percent per millisecond
    private var lastObservationMs = -1L
    private var lastFrameMs = -1L
    private var lastSampleMs = -1L
    private var observationGapMs = 1000L

    fun reset(
        displayPercent: Float = 0f,
        observedPercent: Float = 0f,
        nowMs: Long = 0L,
    ) {
        displayed = displayPercent.coerceIn(0f, 100f)
        observed = observedPercent.coerceIn(0f, 100f)
        target = observed
        velocity = 0f
        observedRate = 0f
        observationGapMs = 1000L
        lastObservationMs = nowMs
        lastFrameMs = nowMs
        lastSampleMs = -1L
    }

    fun observe(
        percent: Int,
        nowMs: Long,
    ) {
        val value = percent.coerceIn(0, 100).toFloat()
        if (lastObservationMs < 0) {
            // Late attachment must never animate backwards from an artificial zero.
            reset(displayPercent = value, observedPercent = value, nowMs = nowMs)
            return
        }
        val gap = (nowMs - lastObservationMs).coerceAtLeast(0L)
        val delta = value - observed
        if (delta > 0f && observed > 0f && value < 100f && gap in 50L..10_000L) {
            val rate = (delta / gap).coerceAtMost(MAX_OBSERVED_RATE)
            observedRate = if (observedRate > 0f) observedRate * 0.5f + rate * 0.5f else rate
            observationGapMs = gap
        } else if (delta < 0f || value <= 0f || value >= 100f || gap > 10_000L) {
            observedRate = 0f
        }
        observed = value
        lastObservationMs = nowMs
        lastSampleMs = -1L // incorporate every real observation immediately
        target = value
        if (value <= 0f) {
            displayed = 0f
            velocity = 0f
        }
    }

    fun frame(
        nowMs: Long,
        smoothnessMs: Int,
        updateRateMs: Int,
        easing: String = "linear",
    ): Float {
        if (lastFrameMs < 0L) lastFrameMs = nowMs
        if (lastSampleMs < 0L || nowMs - lastSampleMs >= updateRateMs.coerceAtLeast(1)) {
            target = prediction(nowMs)
            lastSampleMs = nowMs
        }
        val elapsedSeconds = ((nowMs - lastFrameMs).coerceAtLeast(0L) / 1000f)
        lastFrameMs = nowMs
        if (smoothnessMs <= 0) {
            displayed = target
            velocity = 0f
        } else if (elapsedSeconds > 0f) {
            // Exact solution of x'' + 2ωx' + ω²(x-target) = 0 for fixed target.
            // No re-starting of tweens: position and velocity survive target updates.
            val response =
                when (easing) {
                    "accelerate" -> 0.8f
                    "decelerate" -> 1.2f
                    "ease_in_out" -> 0.95f
                    else -> 1f
                }
            // 95% step settling time for critical damping: (1 + ωT)e^(-ωT) ≈ .05.
            val omega = (4.744f * 1000f / smoothnessMs) * response
            val offset = displayed - target
            val term = velocity + omega * offset
            val decay = exp(-omega * elapsedSeconds)
            displayed = target + (offset + term * elapsedSeconds) * decay
            velocity = (velocity - omega * term * elapsedSeconds) * decay
        }
        displayed = displayed.coerceIn(0f, if (observed >= 100f) 100f else 99.99f)
        return displayed / 100f
    }

    fun needsFrame(nowMs: Long): Boolean =
        abs(displayed - target) > 0.005f || abs(velocity) > 0.01f ||
            (
                observedRate > 0f && observed in 1f..99f &&
                    nowMs - lastObservationMs < predictionHorizonMs()
            )

    private fun prediction(nowMs: Long): Float {
        if (observed !in 1f..99f || observedRate <= 0f) return observed
        val ageMs = (nowMs - lastObservationMs).coerceAtLeast(0L)
        val leadMs = min(ageMs, predictionHorizonMs())
        val lead = min(observedRate * leadMs, MAX_LEAD_PERCENT)
        return (observed + lead).coerceAtMost(99.99f)
    }

    private fun predictionHorizonMs(): Long = (observationGapMs * 5L / 4L).coerceIn(400L, 2_000L)

    private companion object {
        const val MAX_LEAD_PERCENT = 2f
        const val MAX_OBSERVED_RATE = 0.025f // 25% per second; suppress huge spikes
    }
}
