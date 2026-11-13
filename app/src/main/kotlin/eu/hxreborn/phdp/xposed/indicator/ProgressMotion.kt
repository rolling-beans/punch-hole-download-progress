package eu.hxreborn.phdp.xposed.indicator

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min

/**
 * Progress predictor and critically damped third-order controller.
 * Position (%), velocity (%/s) and acceleration (%/s²) remain continuous when
 * the target changes. A deliberately disabled smoothness setting may snap.
 */
internal class ProgressMotion {
    var displayed: Float = 0f
        private set

    private var target = 0f
    private var velocity = 0f
    private var acceleration = 0f
    private var observed = 0f
    private var observedRate = 0f // percent/ms
    private var lastObservationMs = -1L
    private var lastFrameMs = -1L
    private var lastSampleMs = -1L
    private var observationGapMs = 1000L

    fun reset(
        displayPercent: Float = 0f,
        observedPercent: Float = 0f,
        nowMs: Long = 0L,
        velocityPercentPerSecond: Float = 0f,
        accelerationPercentPerSecondSquared: Float = 0f,
    ) {
        displayed = displayPercent.coerceIn(0f, 100f)
        observed = observedPercent.coerceIn(0f, 100f)
        target = observed
        velocity = velocityPercentPerSecond
        acceleration = accelerationPercentPerSecondSquared
        observedRate = 0f
        observationGapMs = 1000L
        lastObservationMs = nowMs
        lastFrameMs = nowMs
        lastSampleMs = -1L
    }

    fun kinematics(): ProgressKinematics =
        ProgressKinematics(
            displayed / 100f,
            velocity / 100_000f,
            acceleration / 100_000_000f,
        )

    fun observe(
        percent: Int,
        nowMs: Long,
    ) {
        val value = percent.coerceIn(0, 100).toFloat()
        if (lastObservationMs < 0) {
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
        lastSampleMs = -1L
        target = value
        if (value <= 0f) {
            displayed = 0f
            velocity = 0f
            acceleration = 0f
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
            target = if (updateRateMs < 1000) prediction(nowMs) else observed
            lastSampleMs = nowMs
        }
        val elapsedSeconds = (nowMs - lastFrameMs).coerceAtLeast(0L) / 1000f
        lastFrameMs = nowMs
        if (smoothnessMs <= 0) {
            displayed = target
            velocity = 0f
            acceleration = 0f
        } else if (elapsedSeconds > 0f) {
            val response =
                when (easing) {
                    "accelerate" -> 0.8f
                    "decelerate" -> 1.2f
                    "ease_in_out" -> 0.95f
                    else -> 1f
                }
            // For triple-pole critical damping: e^-z(1+z+z²/2) ~= 0.05 at z=6.296.
            val omega = (6.296f * 1000f / smoothnessMs) * response
            val offset = displayed - target
            val c1 = velocity + omega * offset
            val c2 = 0.5f * (acceleration + 2f * omega * velocity + omega * omega * offset)
            val t = elapsedSeconds
            val q = offset + (c1 + c2 * t) * t
            val q1 = c1 + 2f * c2 * t
            val decay = exp(-omega * t)
            displayed = target + q * decay
            velocity = (q1 - omega * q) * decay
            acceleration = (2f * c2 - 2f * omega * q1 + omega * omega * q) * decay
        }
        val upperBound = if (observed >= 100f) 100f else 99.99f
        if (displayed < 0f || displayed > upperBound) {
            displayed = displayed.coerceIn(0f, upperBound)
            velocity = 0f
            acceleration = 0f
        }
        return displayed / 100f
    }

    fun needsFrame(nowMs: Long): Boolean =
        abs(displayed - target) > 0.005f ||
            abs(velocity) > 0.01f ||
            abs(acceleration) > 0.1f ||
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
        const val MAX_OBSERVED_RATE = 0.025f
    }
}
