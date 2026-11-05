package io.legado.app.feature.reader.core.transition

import com.google.gson.Gson
import io.legado.app.constant.PageAnim
import io.legado.app.help.config.ReadBookConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderAdditionalTransitionsTest {
    @Test fun persistedValuesRemainCompatibleAndBothNewModesRoundTrip() {
        val values = listOf(PageAnim.coverPageAnim, PageAnim.slidePageAnim, PageAnim.simulationPageAnim,
            PageAnim.scrollPageAnim, PageAnim.fadePageAnim, PageAnim.noAnim, PageAnim.iosCurlPageAnim,
            PageAnim.googleCurlPageAnim, PageAnim.parallaxPageAnim)
        assertEquals((0..8).toList(), values)
        val gson = Gson()
        for ((value, mode) in listOf(7 to ReaderTransitionMode.GOOGLE_CURL, 8 to ReaderTransitionMode.PARALLAX)) {
            val config = ReadBookConfig.Config(pageAnim = value)
            val restored = gson.fromJson(gson.toJson(config), ReadBookConfig.Config::class.java)
            assertEquals(value, restored.getPageAnim())
            assertEquals(mode, ReaderTransitionMode.fromPageAnim(restored.getPageAnim()))
        }
    }

    @Test fun parallaxUsesTheReferenceLowerSheetTravelAndCorrectZOrder() {
        val next = ReaderPageTransition(ReaderTurnDirection.NEXT, -400f, 800f, true)
            .transforms(ReaderTransitionMode.PARALLAX)
        assertEquals(-400f, next.current.translationX, 0f)
        assertEquals(88f, requireNotNull(next.next).translationX, .001f)
        assertTrue(next.currentOnTop)
        assertEquals(null, next.previous)
        val previous = ReaderPageTransition(ReaderTurnDirection.PREVIOUS, 400f, 800f, true)
            .transforms(ReaderTransitionMode.PARALLAX)
        assertEquals(88f, previous.current.translationX, .001f)
        assertEquals(-400f, requireNotNull(previous.previous).translationX, 0f)
        assertFalse(previous.currentOnTop)
        assertEquals(null, previous.next)
    }

    @Test fun parallaxEndpointsLeaveExactlyOneWholeVisiblePage() {
        val start = ReaderPageTransition(ReaderTurnDirection.NEXT, 0f, 800f, true)
            .transforms(ReaderTransitionMode.PARALLAX)
        assertEquals(0f, start.current.translationX, 0f)
        assertEquals(176f, requireNotNull(start.next).translationX, .001f)
        val next = ReaderPageTransition(ReaderTurnDirection.NEXT, -800f, 800f, true)
            .transforms(ReaderTransitionMode.PARALLAX)
        assertEquals(-800f, next.current.translationX, 0f)
        assertEquals(0f, requireNotNull(next.next).translationX, 0f)
        val previous = ReaderPageTransition(ReaderTurnDirection.PREVIOUS, 800f, 800f, true)
            .transforms(ReaderTransitionMode.PARALLAX)
        assertEquals(0f, requireNotNull(previous.previous).translationX, 0f)
        val idle = ReaderPageTransition().transforms(ReaderTransitionMode.PARALLAX)
        assertEquals(0f, idle.current.translationX, 0f)
        assertEquals(null, idle.next)
        assertEquals(null, idle.previous)
    }

    @Test fun shadowFollowsTheVisibleUpperSheetAtBothEnds() {
        assertEquals(1f, ReaderParallaxPolicy.shadowFade(ReaderTurnDirection.NEXT, .5f, 800f, 18f), 0f)
        assertEquals(0f, ReaderParallaxPolicy.shadowFade(ReaderTurnDirection.NEXT, 1f, 800f, 18f), 0f)
        assertEquals(0f, ReaderParallaxPolicy.shadowFade(ReaderTurnDirection.PREVIOUS, 0f, 800f, 18f), 0f)
        assertEquals(.5f, ReaderParallaxPolicy.shadowFade(ReaderTurnDirection.NEXT, .99f, 900f, 18f), .00001f)
        assertEquals(.5f, ReaderParallaxPolicy.shadowFade(ReaderTurnDirection.PREVIOUS, .01f, 900f, 18f), .00001f)
    }

    @Test fun bothModesUseEverySpeedSettingAndScaleTheRemainingDistance() {
        for (mode in listOf(ReaderTransitionMode.GOOGLE_CURL, ReaderTransitionMode.PARALLAX)) {
            for (speed in ReaderPageTurnSpeed.entries) {
                val duration = speed.baseDurationMillis
                assertEquals(duration, ReaderPageTransitionPolicy.settleDurationMillis(mode, 0f, -800f, 800f, duration))
                assertEquals(duration / 2, ReaderPageTransitionPolicy.settleDurationMillis(mode, -400f, -800f, 800f, duration))
                assertEquals(duration / 2, ReaderPageTransitionPolicy.settleDurationMillis(mode, -400f, 0f, 800f, duration))
            }
        }
    }
}
