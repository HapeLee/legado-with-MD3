package io.legado.app.ui.book.read

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EyeProtectionTest {

    @Test
    fun `零强度不改变颜色`() {
        assertArrayEquals(
            floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
            EyeProtection.matrixValuesForIntensity(0),
            0f,
        )
    }

    @Test
    fun `强度限制在有效范围`() {
        assertArrayEquals(
            EyeProtection.matrixValuesForIntensity(0),
            EyeProtection.matrixValuesForIntensity(-1),
            0f,
        )
        assertArrayEquals(
            EyeProtection.matrixValuesForIntensity(100),
            EyeProtection.matrixValuesForIntensity(101),
            0f,
        )
    }

    @Test
    fun `自动模式跟随当前主题`() {
        assertEquals(true, EyeProtection.syncEnabledForNight(isNight = true, autoNight = true))
        assertEquals(false, EyeProtection.syncEnabledForNight(isNight = false, autoNight = true))
        assertNull(EyeProtection.syncEnabledForNight(isNight = true, autoNight = false))
    }
}
