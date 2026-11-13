package eu.hxreborn.phdp.xposed.indicator

import kotlin.math.abs
import kotlin.math.ceil

/** Fraction, fraction/ms and fraction/ms². All three survive a handoff. */
internal data class ProgressKinematics(
    val position: Float,
    val velocity: Float = 0f,
    val acceleration: Float = 0f,
)

/** A bounded quintic Hermite sweep, C2-continuous across accepted retargets. */
internal class ProgressEdgeSweep {
    var active = false
        private set

    var durationMs = MIN_DURATION_MS
        private set

    private var startedMs = 0L
    private var destination = 0f
    private var initial = ProgressKinematics(0f)
    private var coefficients = DoubleArray(6)
    private var queuedTarget: Float? = null

    fun start(
        fromFraction: Float,
        toFraction: Float,
        nowMs: Long,
    ) = start(ProgressKinematics(fromFraction), toFraction, nowMs)

    fun start(
        fromState: ProgressKinematics,
        toFraction: Float,
        nowMs: Long,
    ) {
        val target = toFraction.coerceIn(0f, 1f)
        val plan = safePlan(fromState, target) ?: buildPlan(fromState, target, MAX_DURATION_MS)
        // A plan must respect the hard speed ceiling; the typical handoff
        // starts from the rest or a separately rate-limited ordinary controller.
        install(plan, fromState, target, nowMs)
    }

    /** If a safe replan is impossible, keep the current C2 path and queue the new goal. */
    fun retarget(
        toFraction: Float,
        nowMs: Long,
    ): Boolean {
        if (!active) return false
        val target = toFraction.coerceIn(0f, 1f)
        if (abs(target - destination) < EPSILON) {
            queuedTarget = null
            return true
        }
        val state = stateAt(nowMs)
        val plan = safePlan(state, target)
        if (plan == null) {
            queuedTarget = target
            return false
        }
        install(plan, state, target, nowMs)
        return true
    }

    fun startQueued(nowMs: Long): Boolean {
        val goal = queuedTarget ?: return false
        queuedTarget = null
        if (abs(goal - destination) < EPSILON) return false
        start(ProgressKinematics(destination), goal, nowMs)
        return true
    }

    fun valueAt(nowMs: Long): Float = stateAt(nowMs).position

    fun stateAt(nowMs: Long): ProgressKinematics {
        if (!active) return ProgressKinematics(destination)
        val elapsed = (nowMs - startedMs).coerceAtLeast(0L)
        if (elapsed >= durationMs) return ProgressKinematics(destination)
        if (elapsed == 0L) return initial
        return evaluate(coefficients, elapsed.toDouble() / durationMs, durationMs)
    }

    fun isFinished(nowMs: Long): Boolean = active && nowMs - startedMs >= durationMs

    fun cancel() {
        active = false
        queuedTarget = null
    }

    private data class Plan(
        val duration: Long,
        val coefficients: DoubleArray,
    )

    private fun install(
        plan: Plan,
        state: ProgressKinematics,
        goal: Float,
        nowMs: Long,
    ) {
        initial = state
        destination = goal
        startedMs = nowMs
        durationMs = plan.duration
        coefficients = plan.coefficients
        queuedTarget = null
        active = true
    }

    private fun safePlan(
        state: ProgressKinematics,
        goal: Float,
    ): Plan? {
        // At rest, quintic peak speed = 1.875 * distance / duration.
        val minimum =
            maxOf(
                MIN_DURATION_MS,
                ceil(1.875 * abs(goal - state.position) / MAX_SPEED / STEP_MS)
                    .toLong() * STEP_MS,
            )
        for (duration in minimum..MAX_DURATION_MS step STEP_MS) {
            val plan = buildPlan(state, goal, duration)
            if (withinLimits(plan, state, goal)) return plan
        }
        return null
    }

    private fun withinLimits(
        plan: Plan,
        state: ProgressKinematics,
        goal: Float,
    ): Boolean {
        val low = minOf(state.position, goal) - 0.01f
        val high = maxOf(state.position, goal) + 0.01f
        for (i in 0..32) {
            val sample = evaluate(plan.coefficients, i / 32.0, plan.duration)
            if (sample.position !in low..high ||
                sample.position !in -EPSILON..(1f + EPSILON) ||
                abs(sample.velocity).toDouble() > MAX_SPEED + 0.0000001 ||
                abs(sample.acceleration) > MAX_ACCELERATION
            ) {
                return false
            }
            if (goal >= state.position && sample.position > goal + EPSILON) return false
            if (goal < state.position && sample.position < goal - EPSILON) return false
        }
        return true
    }

    private fun buildPlan(
        state: ProgressKinematics,
        goal: Float,
        duration: Long,
    ): Plan {
        val t = duration.toDouble()
        val c0 = state.position.toDouble()
        val c1 = state.velocity.toDouble() * t
        val c2 = 0.5 * state.acceleration.toDouble() * t * t
        val delta = goal.toDouble() - c0 - c1 - c2
        val slope = -c1 - 2.0 * c2
        val curvature = -2.0 * c2
        return Plan(
            duration,
            doubleArrayOf(
                c0,
                c1,
                c2,
                10.0 * delta - 4.0 * slope + 0.5 * curvature,
                -15.0 * delta + 7.0 * slope - curvature,
                6.0 * delta - 3.0 * slope + 0.5 * curvature,
            ),
        )
    }

    private fun evaluate(
        c: DoubleArray,
        u: Double,
        duration: Long,
    ): ProgressKinematics {
        val position =
            c[0] + u * (c[1] + u * (c[2] + u * (c[3] + u * (c[4] + u * c[5]))))
        val first = c[1] + u * (2 * c[2] + u * (3 * c[3] + u * (4 * c[4] + 5 * c[5] * u)))
        val second = 2 * c[2] + u * (6 * c[3] + u * (12 * c[4] + 20 * c[5] * u))
        val t = duration.toDouble()
        return ProgressKinematics(
            position.toFloat(),
            (first / t).toFloat(),
            (second / (t * t)).toFloat(),
        )
    }

    companion object {
        const val MIN_DURATION_MS = 100L
        private const val MAX_DURATION_MS = 10_000L
        private const val STEP_MS = 10L
        const val MAX_SPEED = 0.001 // fraction/ms: no faster than 100% in one second
        private const val MAX_ACCELERATION = 0.00015f
        private const val EPSILON = 0.0001f
    }
}
