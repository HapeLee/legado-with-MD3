package io.legado.app.feature.reader.core.gesture

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderGestureSettingsPolicyTest {
    @Test
    fun `zero or invalid configured slop keeps the platform threshold`() {
        assertEquals(12f, ReaderGestureSettingsPolicy.touchSlopPx(12f, 0), 0f)
        assertEquals(12f, ReaderGestureSettingsPolicy.touchSlopPx(12f, -5), 0f)
        assertEquals(0f, ReaderGestureSettingsPolicy.touchSlopPx(-1f, 0), 0f)
    }

    @Test
    fun `positive configured slop is preserved as raw legacy pixels`() {
        assertEquals(30f, ReaderGestureSettingsPolicy.touchSlopPx(12f, 30), 0f)
    }

    @Test
    fun `no animation only collapses a commanded scroll page turn to one step`() {
        assertEquals(1, ReaderGestureSettingsPolicy.scrollPageAnimationSteps(true))
        assertEquals(18, ReaderGestureSettingsPolicy.scrollPageAnimationSteps(false))
    }
}
