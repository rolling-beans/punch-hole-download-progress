package eu.hxreborn.phdp.xposed.indicator

import kotlin.math.abs
import kotlin.math.min

/**
 * A predictive progress controller whose displayed trajectory is a quintic
 * Hermite curve with continuous position, velocity and acceleration.
 *
 * Progress updates set the goal, not the visible position. The shared sweep
 * planner enforces at most 100 percentage points per second, including when
 * an observer disables the ordinary smoothness preference.
 */
internal class ProgressMotion {
    var displayed: Float = 0f
        private set

    private val trajectory = ProgressEdgeSweep()
    private var state = ProgressKinematics(0f)
    private var observed = 0f
    private var observedRate = 0f // percent/ms
    private var lastObservationMs = -1L
    private var lastSampleMs = -1L
    private var observationGapMs = 1000L
    private var smoothnessMs = 100
    private var easing = "linear"

    fun reset(
        displayPercent: Float = 0f,
        observedPercent: Float = 0f,
        nowMs: Long = 0L,
        velocityPercentPerSecond: Float = 0f,
        accelerationPercentPerSecondSquared: Float = 0f,
    ) {
        trajectory.cancel()
        displayed = displayPercent.coerceIn(0f, 100f)
        observed = observedPercent.coerceIn(0f, 100f)
        state =
            ProgressKinematics(
                displayed / 100f,
                velocityPercentPerSecond / 100_000f,
                accelerationPercentPerSecondSquared / 100_000_000f,
            )
        observedRate = 0f
        observationGapMs = 1000L
        lastObservationMs = nowMs
        lastSampleMs = -1L
    }

    fun kinematics(): ProgressKinematics = state

    /**
     * Preserve an existing safe normal-progress trajectory when the terminal
     * ring takes over; an instantaneous quintic rebuild can exceed the
     * velocity limit even though the inherited path does not.
     */
    fun handoffTo(
        destination: ProgressEdgeSweep,
        nowMs: Long,
    ): Boolean {
        sampleAt(nowMs)
        return destination.takeOver(trajectory)
    }

    fun observe(
        percent: Int,
        nowMs: Long,
    ) {
        val value = percent.coerceIn(0, 100).toFloat()
        if (lastObservationMs < 0L) {
            reset(displayPercent = value, observedPercent = value, nowMs = nowMs)
            return
        }
        sampleAt(nowMs)
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
        if (value <= 0f) {
            reset(nowMs = nowMs)
            return
        }
        aimAt(value / 100f, nowMs)
    }

    fun frame(
        nowMs: Long,
        smoothnessMs: Int,
        updateRateMs: Int,
        easing: String = "linear",
    ): Float {
        this.smoothnessMs = smoothnessMs
        this.easing = easing
        sampleAt(nowMs)
        if (lastSampleMs < 0L ||
            nowMs - lastSampleMs >= updateRateMs.coerceAtLeast(1)
        ) {
            val goal =
                if (updateRateMs < 1000) prediction(nowMs) else observed
            aimAt(goal / 100f, nowMs)
            lastSampleMs = nowMs
        }
        return displayed / 100f
    }

    fun needsFrame(nowMs: Long): Boolean =
        trajectory.active ||
            (
                observedRate > 0f && observed in 1f..99f &&
                    nowMs - lastObservationMs < predictionHorizonMs()
            )

    private fun sampleAt(nowMs: Long) {
        if (trajectory.active) {
            state = trajectory.stateAt(nowMs)
            if (trajectory.isFinished(nowMs)) {
                if (!trajectory.startQueued(nowMs)) trajectory.cancel()
            }
        }
        displayed = state.position.coerceIn(0f, 1f) * 100f
    }

    private fun aimAt(
        goalFraction: Float,
        nowMs: Long,
    ) {
        val goal = goalFraction.coerceIn(0f, if (observed >= 100f) 1f else 0.9999f)
        val minDuration =
            when (easing) {
                "accelerate" -> smoothnessMs * 0.8f
                "decelerate" -> smoothnessMs * 1.2f
                "ease_in_out" -> smoothnessMs * 0.95f
                else -> smoothnessMs.toFloat()
            }.toLong().coerceAtLeast(ProgressEdgeSweep.MIN_DURATION_MS)
        if (trajectory.active) {
            trajectory.retarget(goal, nowMs, minDuration)
        } else if (abs(goal - state.position) > MIN_DISTANCE) {
            trajectory.start(state, goal, nowMs, minDuration)
        }
    }

    private fun prediction(nowMs: Long): Float {
        if (observed !in 1f..99f || observedRate <= 0f) return observed
        val ageMs = (nowMs - lastObservationMs).coerceAtLeast(0L)
        val leadMs = min(ageMs, predictionHorizonMs())
        val lead = min(observedRate * leadMs, MAX_LEAD_PERCENT)
        return (observed + lead).coerceAtMost(99.99f)
    }

    private fun predictionHorizonMs(): Long = (observationGapMs * 5L / 4L).coerceIn(400L, 2_000L)

    private companion object {
        const val MIN_DISTANCE = 0.00001f
        const val MAX_LEAD_PERCENT = 2f
        const val MAX_OBSERVED_RATE = 0.025f
    }
}
