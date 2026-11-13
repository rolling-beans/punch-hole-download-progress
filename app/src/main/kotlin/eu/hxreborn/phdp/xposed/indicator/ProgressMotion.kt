package eu.hxreborn.phdp.xposed.indicator

import kotlin.math.abs

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
    private val lagBuffer = ProgressLagBuffer()
    private var state = ProgressKinematics(0f)
    private var lastSampleMs = -1L
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
        lagBuffer.reset(displayed, observedPercent, nowMs)
        state =
            ProgressKinematics(
                displayed / 100f,
                velocityPercentPerSecond / 100_000f,
                accelerationPercentPerSecondSquared / 100_000_000f,
            )
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
        if (value <= 0f) {
            reset(nowMs = nowMs)
            return
        }
        sampleAt(nowMs)
        lagBuffer.observe(value, nowMs, smoothnessMs)
        lastSampleMs = -1L
        aimAt(lagBuffer.targetAt(nowMs, smoothnessMs, displayed) / 100f, nowMs)
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
        // Re-evaluate the slow reservoir release between notifications. It is
        // independent from their arrival cadence; the actual rendering still
        // runs on the View's VSYNC, not this lower-frequency target refresh.
        val targetIntervalMs = updateRateMs.coerceIn(50, 150)
        if (lastSampleMs < 0L || nowMs - lastSampleMs >= targetIntervalMs) {
            aimAt(lagBuffer.targetAt(nowMs, smoothnessMs, displayed) / 100f, nowMs)
            lastSampleMs = nowMs
        }
        return displayed / 100f
    }

    fun needsFrame(nowMs: Long): Boolean =
        trajectory.active || lagBuffer.needsCatchUp(displayed, nowMs)

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

    private companion object {
        const val MIN_DISTANCE = 0.00001f
    }
}
