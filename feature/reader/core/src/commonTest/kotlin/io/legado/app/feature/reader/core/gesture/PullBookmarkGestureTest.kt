package io.legado.app.feature.reader.core.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PullBookmarkGestureTest {
    private val config = PullBookmarkConfig(activationDistancePx = 80f)

    @Test fun sharedDefaultsKeepLegacyAndComposeFeedbackInLockstep() {
        assertEquals(80f, PullBookmarkDefaults.ACTIVATION_DISTANCE_DP)
        assertEquals(1.5f, PullBookmarkDefaults.VERTICAL_DOMINANCE_RATIO)
        assertEquals(0.6f, PullBookmarkDefaults.MAX_PAGE_OFFSET_RATIO)
        assertEquals(8, PullBookmarkDefaults.HINT_CONTENT_TOP_MARGIN_DP)
        assertEquals(120, PullBookmarkDefaults.HINT_FADE_MILLIS)
        assertEquals(24f, PullBookmarkDefaults.HINT_CORNER_RADIUS_DP)
        assertEquals(180, PullBookmarkDefaults.RETURN_DURATION_MILLIS)
        assertEquals(config, PullBookmarkDefaults.config(80f))
    }

    @Test fun armsAfterVerticalThreshold() {
        val result = PullBookmarkGesture.drag(10f, 90f, 1000f, true, true, false, config)
        assertTrue(result.isCandidate)
        assertTrue(result.isArmed)
        assertEquals(90f, result.pageOffsetPx)
    }

    @Test fun disabledPreferenceNeverMovesOrArmsPage() {
        val result = PullBookmarkGesture.drag(0f, 180f, 1000f, false, true, false, config)
        assertFalse(result.isCandidate)
        assertFalse(result.isArmed)
        assertEquals(0f, result.pageOffsetPx)
    }

    @Test fun reversingBelowThresholdDisarmsBeforeRelease() {
        assertTrue(PullBookmarkGesture.drag(0f, 100f, 1000f, true, true, false, config).isArmed)
        val result = PullBookmarkGesture.drag(0f, 50f, 1000f, true, true, false, config)
        assertTrue(result.isCandidate)
        assertFalse(result.isArmed)
        assertEquals(50f, result.pageOffsetPx)
    }

    @Test fun diagonalPageTurnIsReleased() {
        val result = PullBookmarkGesture.drag(100f, 100f, 1000f, true, true, false, config)
        assertFalse(result.isCandidate)
        assertFalse(result.isArmed)
    }

    @Test fun offsetIsCappedAndScrollOrSelectionDisablesGesture() {
        val capped = PullBookmarkGesture.drag(0f, 900f, 1000f, true, true, false, config)
        assertEquals(600f, capped.pageOffsetPx)
        assertFalse(PullBookmarkGesture.drag(0f, 100f, 1000f, true, false, false, config).isCandidate)
        assertFalse(PullBookmarkGesture.drag(0f, 100f, 1000f, true, true, true, config).isCandidate)
    }

    @Test fun releasedGestureCannotReclaimPullAfterDirectionChanges() {
        val rejected = PullBookmarkGesture.drag(100f, 100f, 1000f, true, true, false, config)
        val releasedClaim = PullBookmarkGesture.claim(previouslyReleased = false, rejected)
        assertFalse(releasedClaim.isDragging)
        assertTrue(releasedClaim.isReleased)

        val laterVertical = PullBookmarkGesture.drag(0f, 100f, 1000f, true, true, false, config)
        val lockedClaim = PullBookmarkGesture.claim(releasedClaim.isReleased, laterVertical)
        assertFalse(lockedClaim.isDragging)
        assertTrue(lockedClaim.isReleased)
    }

    @Test fun releaseRechecksTheFinalDistanceAndDirection() {
        val armedMove = PullBookmarkGesture.drag(0f, 100f, 1000f, true, true, false, config)
        val retreatedRelease = PullBookmarkGesture.drag(0f, 50f, 1000f, true, true, false, config)
        val diagonalRelease = PullBookmarkGesture.drag(100f, 100f, 1000f, true, true, false, config)
        val crossedOnRelease = PullBookmarkGesture.drag(0f, 90f, 1000f, true, true, false, config)

        assertTrue(PullBookmarkGesture.shouldToggleOnRelease(true, armedMove))
        assertFalse(PullBookmarkGesture.shouldToggleOnRelease(true, retreatedRelease))
        assertFalse(PullBookmarkGesture.shouldToggleOnRelease(true, diagonalRelease))
        assertTrue(PullBookmarkGesture.shouldToggleOnRelease(true, crossedOnRelease))
        assertFalse(PullBookmarkGesture.shouldToggleOnRelease(false, crossedOnRelease))
    }
}
