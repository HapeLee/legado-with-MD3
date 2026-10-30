package io.legado.app.feature.reader.components.ioscurl

import androidx.compose.runtime.MonotonicFrameClock
import com.google.gson.Gson
import io.legado.app.constant.PageAnim
import io.legado.app.feature.reader.core.transition.ioscurl.control.CurlControl
import io.legado.app.feature.reader.core.transition.ioscurl.gesture.CurlTouchKind
import io.legado.app.feature.reader.core.transition.ReaderTransitionMode
import io.legado.app.feature.reader.core.transition.ReaderPageTurnSpeed
import io.legado.app.feature.reader.core.transition.ReaderTurnDirection
import io.legado.app.help.config.ReadBookConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IosCurlTurnTest {
    @Test fun automaticTurnUsesTheSelectedFullPageDurationInBothDirections() = runTest {
        for (speed in ReaderPageTurnSpeed.entries) {
            for (direction in ReaderTurnDirection.entries) {
                val turn = IosCurlTurn()
                val clock = TestFrameClock()
                turn.begin(direction, 320, 480, 1f, this, automatic = true, speed = speed)
                val expected = CurlControl.begin(requireNotNull(turn.config)).to
                turn.rendererReady()
                withContext(clock) { turn.settle(true, 0.0, speed) }
                // One frame establishes the origin; completion rounds up to the next frame.
                assertTrue("$speed: ${clock.elapsedMillis}ms",
                    clock.elapsedMillis in (speed.baseDurationMillis + 16L)..(speed.baseDurationMillis + 32L))
                assertEquals(expected.time, turn.values.time, .00001)
                turn.reset()
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun releaseSpeedScalesCommitAndCancellationWithoutChangingTheEndpoint() = runTest {
        for (speed in ReaderPageTurnSpeed.entries) {
            for (commit in listOf(false, true)) {
                for (velocity in listOf(0.0, 3000.0)) {
                    val turn = IosCurlTurn()
                    val clock = TestFrameClock()
                    turn.down(320, 480, 1f, 300f, 150f, 0, true, true, 10f)
                    turn.event(CurlTouchKind.Move, 160f, 170f, 100)
                    turn.begin(ReaderTurnDirection.NEXT, 320, 480, 1f,
                        CoroutineScope(coroutineContext + clock), automatic = false, speed = speed)
                    turn.rendererReady()
                    advanceTimeBy(600)
                    runCurrent()
                    val config = requireNotNull(turn.config)
                    val expectedMillis = CurlControl.duration(config, velocity, commit, turn.values.time) *
                        speed.baseDurationMillis / 400.0 * 1000.0
                    val start = clock.elapsedMillis
                    withContext(clock) { turn.settle(commit, velocity, speed) }
                    val actual = clock.elapsedMillis - start
                    assertTrue("$speed / $commit / $velocity: $actual vs $expectedMillis",
                        actual >= expectedMillis + 16 && actual < expectedMillis + 32.01)
                    val endpoints = CurlControl.endpoints(config)
                    val expected = if (commit) endpoints.to else endpoints.from
                    assertEquals(expected.time, turn.values.time, .00001)
                    assertEquals(expected.radius, turn.values.radius, .00001)
                    assertEquals(expected.angle, turn.values.angle, .00001)
                    turn.reset()
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun warmUpUsesTheSpeedSettingBeforeFollowingTheFinger() = runTest {
        for (speed in listOf(ReaderPageTurnSpeed.FASTEST, ReaderPageTurnSpeed.ELEGANT)) {
            val turn = IosCurlTurn()
            val clock = TestFrameClock()
            turn.down(320, 480, 1f, 300f, 150f, 0, true, true, 10f)
            turn.event(CurlTouchKind.Move, 160f, 170f, 100)
            turn.begin(ReaderTurnDirection.NEXT, 320, 480, 1f,
                CoroutineScope(coroutineContext + clock), automatic = false, speed = speed)
            val config = requireNotNull(turn.config)
            val target = CurlControl.update(config, config.initial).renderValues()
            turn.rendererReady()
            advanceTimeBy(200)
            runCurrent()
            if (speed == ReaderPageTurnSpeed.FASTEST) {
                assertEquals(target, turn.values)
            } else {
                assertTrue(target != turn.values)
            }
            advanceTimeBy(200)
            runCurrent()
            assertEquals(target, turn.values)
            turn.reset()
        }
    }

    @Test fun styleExportAndImportPreserveTheNewAnimationValue() {
        val gson = Gson()
        val style = ReadBookConfig.Config(pageAnim = PageAnim.iosCurlPageAnim)
        val restored = gson.fromJson(gson.toJson(style), ReadBookConfig.Config::class.java)
        assertEquals(PageAnim.iosCurlPageAnim, restored.getPageAnim())
        assertEquals(ReaderTransitionMode.IOS_CURL,
            ReaderTransitionMode.fromPageAnim(restored.getPageAnim()))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun coldCaptureDoesNotConsumeTheBeginAnimationAndCancellationReturnsToOrigin() = runTest {
        val clock = TestFrameClock()
        val turn = IosCurlTurn()
        turn.down(320, 480, 1f, 300f, 150f, 0, true, true, 10f)
        turn.event(CurlTouchKind.Move, 240f, 170f, 100)
        turn.begin(ReaderTurnDirection.NEXT, 320, 480, 1f,
            CoroutineScope(coroutineContext + clock), automatic = false)
        turn.event(CurlTouchKind.Move, 90f, 210f, 200)
        turn.drag()
        val start = turn.values
        advanceTimeBy(500)
        runCurrent()
        assertEquals(start, turn.values)
        turn.rendererReady()
        advanceTimeBy(300)
        runCurrent()
        assertTrue(turn.values.time != start.time)
        val config = requireNotNull(turn.config)
        withContext(clock) { turn.settle(commit = false, velocity = 0.0) }
        assertEquals(CurlControl.endpoints(config).from.time, turn.values.time, .00001)
        turn.reset()
    }

    @Test fun newPreferenceDoesNotRenumberExistingModes() {
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6), listOf(PageAnim.coverPageAnim,
            PageAnim.slidePageAnim, PageAnim.simulationPageAnim, PageAnim.scrollPageAnim,
            PageAnim.fadePageAnim, PageAnim.noAnim, PageAnim.iosCurlPageAnim))
        assertEquals(ReaderTransitionMode.IOS_CURL, ReaderTransitionMode.fromPageAnim(6))
        assertEquals(ReaderTransitionMode.NONE, ReaderTransitionMode.fromPageAnim(-1))
    }

    @Test fun densityDoesNotChangeRecognitionOrRelease() {
        for (scale in listOf(1f, 2f, 3f)) {
            val turn = IosCurlTurn()
            turn.down((320 * scale).toInt(), (480 * scale).toInt(), scale,
                300 * scale, 240 * scale, 0, previous = true, next = true, touchSlop = 10 * scale)
            turn.event(CurlTouchKind.Move, 200 * scale, 245 * scale, 100)
            assertEquals(ReaderTurnDirection.NEXT, turn.recognizedDirection)
            turn.event(CurlTouchKind.Move, 60 * scale, 250 * scale, 200)
            turn.event(CurlTouchKind.Up, 60 * scale, 250 * scale, 210)
            assertTrue(turn.release(ReaderTurnDirection.NEXT, cancelled = false).first)
            assertFalse(turn.release(ReaderTurnDirection.NEXT, cancelled = true).first)
        }
    }

    @Test fun slowShortDragCancelsAndSecondFingerCannotBeginATurn() {
        val turn = IosCurlTurn()
        turn.down(320, 480, 1f, 300f, 240f, 0, true, true, 10f)
        turn.event(CurlTouchKind.Move, 280f, 240f, 1000)
        turn.event(CurlTouchKind.Up, 280f, 240f, 1100)
        assertFalse(turn.release(ReaderTurnDirection.NEXT, cancelled = false).first)
        turn.down(320, 480, 1f, 300f, 240f, 2000, true, true, 10f)
        turn.event(CurlTouchKind.Down, 280f, 240f, 2010, pointer = 1)
        turn.event(CurlTouchKind.Move, 100f, 240f, 2100)
        assertEquals(null, turn.recognizedDirection)
    }

    @Test fun automaticTurnsReachTheCorrectEndpointInBothDirections() = runTest {
        for (direction in ReaderTurnDirection.entries) {
            val turn = IosCurlTurn()
            turn.begin(direction, 640, 960, 2f, this, automatic = true)
            val config = requireNotNull(turn.config)
            assertEquals(320.0, config.width, 0.0)
            turn.rendererReady()
            withContext(TestFrameClock()) { turn.settle(commit = true, velocity = 0.0) }
            val expected = CurlControl.begin(config).to
            assertEquals(expected.time, turn.values.time, .00001)
            assertEquals(expected.radius, turn.values.radius, .00001)
            assertEquals(expected.angle, turn.values.angle, .00001)
            turn.reset()
            assertEquals(null, turn.config)
        }
    }

    private class TestFrameClock : MonotonicFrameClock {
        private var nanos = 0L
        val elapsedMillis: Long get() = nanos / 1_000_000
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            delay(16)
            nanos += 16_000_000L
            return onFrame(nanos)
        }
    }
}
