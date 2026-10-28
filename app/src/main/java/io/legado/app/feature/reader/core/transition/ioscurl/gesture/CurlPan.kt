package io.legado.app.feature.reader.core.transition.ioscurl.gesture

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlAxis
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlPoint

enum class CurlTouchKind {
    Down,
    Move,
    Up,
    Cancel,
}

enum class CurlRecognition(val nativeValue: Int) {
    Possible(0),
    Began(1),
    Changed(2),
    Ended(3),
    Cancelled(4),
    Failed(5),
}

data class CurlTouch(
    val kind: CurlTouchKind,
    val pointer: Int,
    val point: CurlPoint,
    val seconds: Double,
)

data class CurlPanResult(
    val state: CurlRecognition,
    val location: CurlPoint,
    val translation: CurlPoint,
    val velocity: CurlPoint,
    val beginDirection: Int,
    val end: CurlRelease,
    val commandBegin: Boolean,
)

/**
 * One tracked pointer, axis-OR 10pt hysteresis and the original two-sample 25/75 velocity filter.
 * Additional pointers fail a Possible recognizer; an active recognizer ignores their positions. The
 * adapter must still deliver their events so callback/sampling order stays explicit.
 */
class CurlPan(val width: Double, val height: Double, val axis: CurlAxis) {
    private companion object {
        const val HYSTERESIS_POINTS = 10.0
        const val MIN_SAMPLE_DURATION_SECONDS = 0.001
        const val CURRENT_VELOCITY_WEIGHT = 0.25
        const val PREVIOUS_VELOCITY_WEIGHT = 0.75
        val FLOAT_EPSILON = Float.fromBits(0x3400_0000).toDouble()
    }

    var sloppiness = 1.0
    var canHandle = true
    var currentValid = true
    var incomingForward = true
    var incomingReverse = true
    var incomingUnknown = true
    var manualUncommitted = false
    var state = CurlRecognition.Possible
        private set

    var trackedPointer: Int? = null
        private set

    private var first = CurlPoint(0.0, 0.0)

    private var last = first

    private var lastRaw = first

    private var lastTime = 0.0

    private data class Sample(
        val start: CurlPoint = CurlPoint(0.0, 0.0),
        val end: CurlPoint = start,
        val dt: Double = 0.0,
    )

    private var sample = Sample()

    private var previous = Sample()

    private fun delta(a: CurlPoint, b: CurlPoint) = CurlPoint(a.x - b.x, a.y - b.y)

    private fun speed(s: Sample): CurlPoint {
        if (s.dt < MIN_SAMPLE_DURATION_SECONDS) return CurlPoint(0.0, 0.0)

        val d = delta(s.end, s.start)
        return CurlPoint(d.x / s.dt, d.y / s.dt)
    }

    private fun velocity(): CurlPoint {
        val a = speed(sample)
        if (previous.dt > FLOAT_EPSILON) {
            val b = speed(previous)
            return CurlPoint(
                a.x * CURRENT_VELOCITY_WEIGHT + b.x * PREVIOUS_VELOCITY_WEIGHT,
                a.y * CURRENT_VELOCITY_WEIGHT + b.y * PREVIOUS_VELOCITY_WEIGHT,
            )
        }
        return a
    }

    fun snapshot(): CurlPanResult {
        val translation = delta(last, first)
        val velocity = velocity()
        val direction = CurlNavigation.begin(axis, translation, velocity)
        return CurlPanResult(
            state,
            last,
            translation,
            velocity,
            direction,
            CurlNavigation.end(axis, width, height, last, velocity),
            (state == CurlRecognition.Began || state == CurlRecognition.Changed) &&
                !manualUncommitted &&
                direction >= 0,
        )
    }

    fun event(event: CurlTouch): CurlPanResult {
        if (state.nativeValue >= 3) return snapshot()
        val point = event.point
        when (event.kind) {
            CurlTouchKind.Down ->
                if (trackedPointer == null) {
                    trackedPointer = event.pointer
                    first = point
                    last = point
                    lastRaw = point
                    lastTime = event.seconds
                } else if (event.pointer != trackedPointer && state == CurlRecognition.Possible)
                    state = CurlRecognition.Failed
                else if (state == CurlRecognition.Began || state == CurlRecognition.Changed)
                    state = CurlRecognition.Changed

            CurlTouchKind.Move ->
                if (trackedPointer != null) {
                    if (event.pointer == trackedPointer) lastRaw = point
                    previous = sample
                    sample = Sample(last, lastRaw, event.seconds - lastTime)
                    last = lastRaw
                    lastTime = event.seconds
                    if (state == CurlRecognition.Possible) {
                        val t = delta(last, first)
                        val h = HYSTERESIS_POINTS * sloppiness
                        if (abs(t.x) >= h || abs(t.y) >= h) {
                            val reduced =
                                CurlPoint(
                                    if (t.x >= 0) max(t.x - h, 0.0) else min(t.x + h, 0.0),
                                    if (t.y >= 0) max(t.y - h, 0.0) else min(t.y + h, 0.0),
                                )
                            first = delta(last, reduced)

                            val d = CurlNavigation.begin(axis, reduced, velocity())
                            state =
                                if (
                                    canHandle &&
                                        currentValid &&
                                        (if (d < 0) incomingUnknown
                                        else if (d == 1) incomingReverse else incomingForward)
                                )
                                    CurlRecognition.Began
                                else CurlRecognition.Failed
                        }
                    } else state = CurlRecognition.Changed
                }

            CurlTouchKind.Up,
            CurlTouchKind.Cancel ->
                if (trackedPointer != null) {
                    if (event.pointer == trackedPointer)
                        state =
                            if (state == CurlRecognition.Possible) CurlRecognition.Failed
                            else if (event.kind == CurlTouchKind.Up) CurlRecognition.Ended
                            else CurlRecognition.Cancelled
                    else if (state == CurlRecognition.Began || state == CurlRecognition.Changed)
                        state = CurlRecognition.Changed
                }
        }
        return snapshot()
    }
}
