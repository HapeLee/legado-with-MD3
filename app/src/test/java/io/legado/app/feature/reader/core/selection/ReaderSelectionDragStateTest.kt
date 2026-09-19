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
            dragSlopPx = 8f,
        )

        assertFalse(state.started)
    }

    @Test
    fun movementAtSlopStartsSelectionDrag() {
        val state = ReaderSelectionDragState().update(
            longPressed = true,
            handleGrabbed = false,
            distancePx = 8f,
            dragSlopPx = 8f,
        )

        assertTrue(state.started)
    }

    @Test
    fun dragRemainsStartedAfterCrossingSlop() {
        val started = ReaderSelectionDragState().update(
            longPressed = true,
            handleGrabbed = false,
            distancePx = 8f,
            dragSlopPx = 8f,
        )

        assertTrue(
            started.update(
                longPressed = true,
                handleGrabbed = false,
                distancePx = 2f,
                dragSlopPx = 8f,
            ).started
        )
    }

    @Test
    fun grabbedHandleStartsImmediatelyWithoutSlop() {
        val state = ReaderSelectionDragState().update(
            longPressed = false,
            handleGrabbed = true,
            distancePx = 0f,
            dragSlopPx = 8f,
        )

        assertTrue(state.started)
    }

    @Test
    fun movementWithoutLongPressOrHandleNeverStartsDrag() {
        val state = ReaderSelectionDragState().update(
            longPressed = false,
            handleGrabbed = false,
            distancePx = 200f,
            dragSlopPx = 8f,
        )

        assertFalse(state.started)
    }

    @Test
    fun aZeroSlopStillRequiresSomeMovement() {
        val state = ReaderSelectionDragState().update(
            longPressed = true,
            handleGrabbed = false,
            distancePx = 0f,
            dragSlopPx = 1f,
        )

        assertFalse(state.started)
    }
}
