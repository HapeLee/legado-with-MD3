package io.legado.app.feature.reader.core.selection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSelectionDragStateTest {
    @Test
    fun movementBelowSlopKeepsInitialLongPressSelection() {
        val state = ReaderSelectionDragState().update(
            longPressed = true,
            handleGrabbed = false,
            distancePx = 3f,
            touchSlopPx = 8f,
        )

        assertFalse(state.started)
    }

    @Test
    fun movementAtSlopStartsGraphemeDrag() {
        val state = ReaderSelectionDragState().update(
            longPressed = true,
            handleGrabbed = false,
            distancePx = 8f,
            touchSlopPx = 8f,
        )

        assertTrue(state.started)
    }

    @Test
    fun dragRemainsStartedAfterCrossingSlop() {
        val started = ReaderSelectionDragState().update(
            longPressed = true,
            handleGrabbed = false,
            distancePx = 8f,
            touchSlopPx = 8f,
        )

        assertTrue(
            started.update(
                longPressed = true,
                handleGrabbed = false,
                distancePx = 2f,
                touchSlopPx = 8f,
            ).started
        )
    }

    @Test
    fun grabbedHandleStartsImmediatelyWithoutSlop() {
        val state = ReaderSelectionDragState().update(
            longPressed = false,
            handleGrabbed = true,
            distancePx = 0f,
            touchSlopPx = 8f,
        )

        assertTrue(state.started)
    }
}
