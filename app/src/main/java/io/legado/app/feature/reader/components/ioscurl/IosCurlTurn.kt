package io.legado.app.feature.reader.components.ioscurl

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import io.legado.app.feature.reader.core.transition.ReaderPageTurnSpeed
import io.legado.app.feature.reader.core.transition.ioscurl.control.CurlControl
import io.legado.app.feature.reader.core.transition.ioscurl.control.CurlTiming
import io.legado.app.feature.reader.core.transition.ioscurl.gesture.CurlPan
import io.legado.app.feature.reader.core.transition.ioscurl.gesture.CurlPanResult
import io.legado.app.feature.reader.core.transition.ioscurl.gesture.CurlRecognition
import io.legado.app.feature.reader.core.transition.ioscurl.gesture.CurlTouch
import io.legado.app.feature.reader.core.transition.ioscurl.gesture.CurlTouchKind
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlBinding
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlAxis
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlConfig
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlDirection
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlKind
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlPoint
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlValues
import io.legado.app.feature.reader.core.transition.ReaderTurnDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Adapts logical-point curl controls to the reader's existing page transaction. */
@Stable
internal class IosCurlTurn {
    var config by mutableStateOf<CurlConfig?>(null)
        private set
    var values by mutableStateOf(CurlValues(0.0, 0.0, 0.0))
        private set
    private var pan: CurlPan? = null
    private var sample by mutableStateOf<CurlPanResult?>(null)
    private var density = 1.0
    private var beginJob: Job? = null
    private var automatic = false
    private var target = values
    private var elapsed = 0.0
    private var beginDurationSeconds = 0.0
    private var renderReady = false

    val recognizedDirection: ReaderTurnDirection?
        get() = sample?.takeIf { it.commandBegin }?.let {
            if (it.beginDirection == 0) ReaderTurnDirection.NEXT else ReaderTurnDirection.PREVIOUS
        }

    fun down(
        width: Int,
        height: Int,
        density: Float,
        x: Float,
        y: Float,
        millis: Long,
        previous: Boolean,
        next: Boolean,
        touchSlop: Float,
    ) {
        this.density = density.toDouble()
        pan = CurlPan(width / this.density, height / this.density, CurlAxis.Horizontal).also {
            it.incomingForward = next
            it.incomingReverse = previous
            // Preserve the user's anti-accidental-turn setting as a minimum threshold.
            it.sloppiness = maxOf(1.0, touchSlop / this.density / 10.0)
        }
        event(CurlTouchKind.Down, x, y, millis)
    }

    fun event(kind: CurlTouchKind, x: Float, y: Float, millis: Long, pointer: Int = 0) {
        sample = pan?.event(CurlTouch(kind, pointer,
            CurlPoint(x / density, y / density), millis / 1000.0))
    }

    fun begin(
        direction: ReaderTurnDirection,
        width: Int,
        height: Int,
        density: Float,
        scope: CoroutineScope,
        automatic: Boolean,
        speed: ReaderPageTurnSpeed = ReaderPageTurnSpeed.Default,
    ) {
        reset()
        this.density = density.toDouble()
        this.automatic = automatic
        renderReady = false
        val c = CurlConfig(width / this.density, height / this.density,
            if (automatic) CurlPoint(0.0, 0.0) else sample?.location ?: CurlPoint(0.0, 0.0),
            CurlBinding.Left,
            if (direction == ReaderTurnDirection.NEXT) CurlDirection.Forward else CurlDirection.Reverse,
            if (automatic) CurlKind.Automatic else CurlKind.Manual)
        config = c
        val begin = CurlControl.begin(c)
        beginDurationSeconds = scaledDuration(begin.durationSeconds, speed)
        values = begin.from
        target = if (automatic) begin.to else CurlControl.update(c, c.initial).renderValues()
        elapsed = 0.0
        if (!automatic) {
            pan?.manualUncommitted = true
            beginJob = scope.launch {
                awaitRenderer()
                val start = withFrameNanos { it }
                do {
                    elapsed = (withFrameNanos { it } - start) / 1_000_000_000.0
                    values = if (elapsed < beginDurationSeconds) CurlTiming.interpolate(
                        begin.from, target, CurlControl.type(c), false,
                        elapsed / beginDurationSeconds, beginDurationSeconds
                    ) else target
                } while (elapsed < beginDurationSeconds)
            }
        }
    }

    fun drag() {
        val c = config ?: return
        if (automatic) return
        target = CurlControl.update(c, sample?.location ?: c.initial).renderValues()
        if (elapsed >= beginDurationSeconds) values = target
    }

    fun release(direction: ReaderTurnDirection, cancelled: Boolean): Pair<Boolean, Double> {
        val s = sample ?: return false to 0.0
        val expected = if (direction == ReaderTurnDirection.NEXT) 0 else 1
        return (!cancelled && s.state == CurlRecognition.Ended && s.end.direction == expected) to
            s.end.suggestedVelocity
    }

    suspend fun settle(
        commit: Boolean,
        velocity: Double,
        speed: ReaderPageTurnSpeed = ReaderPageTurnSpeed.Default,
    ) {
        beginJob?.cancel()
        val c = config ?: return
        awaitRenderer()
        val begin = CurlControl.begin(c)
        val from = if (automatic) begin.from else values.renderValues()
        val endpoints = CurlControl.endpoints(c)
        val to = if (automatic) begin.to else if (commit) endpoints.to else endpoints.from
        val duration = scaledDuration(
            if (automatic) begin.durationSeconds else
                CurlControl.duration(c, velocity, commit, from.time),
            speed,
        )
        if (duration <= 0.0) {
            values = to
            return
        }
        val start = withFrameNanos { it }
        var fraction: Double
        do {
            fraction = ((withFrameNanos { it } - start) / 1_000_000_000.0 / duration).coerceIn(0.0, 1.0)
            values = CurlTiming.interpolate(from, to, CurlControl.type(c), !automatic, fraction, duration)
        } while (fraction < 1.0)
    }

    fun reset() {
        beginJob?.cancel()
        beginJob = null
        config = null
    }

    fun rendererReady() {
        renderReady = true
    }

    /**
     * 就绪信号由翻页栈在两页捕获完成后发出。该信号可能因组合层重启、翻页栈未进入组合或
     * 捕获被取消而丢失，无上限等待会让本轮翻页永久停在半途（`settle()` 不返回 →
     * `finishPageTurn()` 不执行 → 本次翻页根本不提交）。这里改为最多等待
     * [MAX_RENDERER_WAIT_FRAMES] 帧，超时后照常走降级绘制并把翻页事务走完。
     */
    private suspend fun awaitRenderer() {
        var frames = 0
        while (!renderReady && frames < MAX_RENDERER_WAIT_FRAMES) {
            withFrameNanos { }
            frames++
        }
    }

    // Normalize the native 400ms full turn to the shared speed setting. Keep the
    // relative warm-up/release timing and velocity-dependent settling intact.
    private fun scaledDuration(seconds: Double, speed: ReaderPageTurnSpeed): Double =
        seconds * speed.baseDurationMillis / 400.0

    private companion object {
        /** 约 0.5s @60Hz；足够覆盖一次冷捕获，又不会把翻页挂死。 */
        const val MAX_RENDERER_WAIT_FRAMES = 30
    }
}
