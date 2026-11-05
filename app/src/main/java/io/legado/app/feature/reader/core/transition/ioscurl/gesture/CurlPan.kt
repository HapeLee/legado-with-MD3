package io.legado.app.feature.reader.core.transition.ioscurl.gesture

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlAxis
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlPoint

/**
 * Platform-independent touch phases supplied by the host input adapter.
 */
enum class CurlTouchKind {
    Down,
    Move,
    Up,
    Cancel,
}

/**
 * Recognizer lifecycle. Codes 3 and above are terminal; create a new recognizer for the next gesture.
 */
enum class CurlRecognition(val nativeValue: Int) {
    Possible(0),
    Began(1),
    Changed(2),
    Ended(3),
    Cancelled(4),
    Failed(5),
}

/**
 * One touch event in page coordinates.
 *
 * @property pointer Stable pointer identifier during a contact.
 * @property point Location in the same logical units as the recognizer dimensions.
 * @property seconds Timestamp from a monotonic clock, in seconds; send events in chronological order.
 */
data class CurlTouch(
    val kind: CurlTouchKind,
    val pointer: Int,
    val point: CurlPoint,
    val seconds: Double,
)

/**
 * Snapshot of drag recognition and navigation policy.
 *
 * @property location Last tracked location in page coordinates.
 * @property translation Displacement after removing the initial movement threshold.
 * @property velocity Smoothed vector speed in logical units per second.
 * @property beginDirection Navigation code 0/1, or -1 while undecided.
 * @property end Current release recommendation, useful when a gesture terminates.
 * @property commandBegin Whether the host should consider starting a manual turn;
 * this can remain true across updates, so the host must track whether it already began.
 */
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
 * Stateful single-gesture drag recognizer with hysteresis and velocity smoothing.
 * Supply logical page dimensions and one navigation axis; serialize calls to event.
 * Terminal states are retained, so use a new instance for each gesture.
 *
 * Public policy flags let the host reject unavailable pages before recognition begins.
 * manualUncommitted suppresses a new begin command while a manual turn is already outstanding.
 */
class CurlPan(val width: Double, val height: Double, val axis: CurlAxis) {
    private companion object {
        const val HYSTERESIS_POINTS = 10.0
        const val MIN_SAMPLE_DURATION_SECONDS = 0.001
        const val CURRENT_VELOCITY_WEIGHT = 0.25
        const val PREVIOUS_VELOCITY_WEIGHT = 0.75
        val FLOAT_EPSILON = Float.fromBits(0x3400_0000).toDouble()
    }

    /** Multiplier applied to the initial movement threshold, normally 1.0. */
    var sloppiness = 1.0
    /** Host capability gate, checked when the drag first crosses the recognition threshold. */
    var canHandle = true
    /** Whether current content is available for a manual turn. */
    var currentValid = true
    /** Whether a destination page is available for direction code 0. */
    var incomingForward = true
    /** Whether a destination page is available for direction code 1. */
    var incomingReverse = true
    /** Whether recognition may begin while direction is still undecided (-1). */
    var incomingUnknown = true
    /** Suppresses a begin command while the host has an uncommitted manual turn. */
    var manualUncommitted = false
    /** Current lifecycle state; terminal states persist until this instance is discarded. */
    var state = CurlRecognition.Possible
        private set

    /** Identifier of the contact driving this gesture, or null before a contact is accepted. */
    var trackedPointer: Int? = null
        private set

    private var first = CurlPoint(0.0, 0.0)

    private var last = first

    private var lastRaw = first

    private var lastTime = 0.0

    /**
     * A motion interval used for velocity estimation; dt is measured in seconds.
     */
    private data class Sample(
        val start: CurlPoint = CurlPoint(0.0, 0.0),
        val end: CurlPoint = start,
        val dt: Double = 0.0,
    )

    private var sample = Sample()

    private var previous = Sample()

    /**
     * Subtracts points without changing coordinate systems.
     */
    private fun delta(a: CurlPoint, b: CurlPoint) = CurlPoint(a.x - b.x, a.y - b.y)

    /**
     * Computes interval speed, discarding sub-millisecond intervals to avoid unstable division.
     */
    private fun speed(s: Sample): CurlPoint {
        if (s.dt < MIN_SAMPLE_DURATION_SECONDS) return CurlPoint(0.0, 0.0)

        val d = delta(s.end, s.start)
        return CurlPoint(d.x / s.dt, d.y / s.dt)
    }

    /**
     * Blends current and previous interval speeds with weights 0.25 and 0.75.
     */
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

    /**
     * Returns the current recognition state and derived direction without consuming an event.
     */
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

    /**
     * Consumes one ordered touch event and returns a snapshot.
     * A second pointer before recognition fails the gesture; only the tracked pointer can end it.
     */
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
                            // Remove the initial hysteresis distance so geometry does not jump when recognition begins.
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
