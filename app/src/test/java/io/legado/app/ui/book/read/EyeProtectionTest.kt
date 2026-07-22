package io.legado.app.ui.book.read

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

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

    private fun activeAt(
        hour: Int,
        minute: Int = 0,
        enabled: Boolean = true,
        schedule: Boolean = true,
        start: String = "22:00",
        end: String = "07:00",
    ) = EyeProtection.isActiveAt(enabled, schedule, start, end, LocalTime.of(hour, minute))

    @Test
    fun `总开关关闭时永不生效`() {
        assertFalse(activeAt(23, enabled = false))
        assertFalse(activeAt(23, enabled = false, schedule = false))
    }

    @Test
    fun `未启用定时时总开关开即全天生效`() {
        assertTrue(activeAt(3, schedule = false))
        assertTrue(activeAt(15, schedule = false))
    }

    @Test
    fun `跨零点时段仅在窗口内生效`() {
        assertTrue(activeAt(22))       // 起点
        assertTrue(activeAt(23, 30))   // 午夜前
        assertTrue(activeAt(0, 30))    // 午夜后
        assertTrue(activeAt(6, 59))    // 终点前
        assertFalse(activeAt(7))       // 终点（不含）
        assertFalse(activeAt(12))      // 白天
        assertFalse(activeAt(21, 59))  // 起点前
    }

    @Test
    fun `同日时段仅在窗口内生效`() {
        assertTrue(activeAt(9, start = "08:00", end = "18:00"))
        assertFalse(activeAt(7, 59, start = "08:00", end = "18:00"))
        assertFalse(activeAt(18, start = "08:00", end = "18:00"))
    }

    @Test
    fun `时间无法解析时退化为按总开关生效`() {
        assertTrue(activeAt(12, start = "bad", end = "07:00"))
        assertTrue(activeAt(12, start = "22:00", end = ""))
    }

    @Test
    fun `起止时间相同视为全天生效`() {
        assertTrue(activeAt(3, start = "08:00", end = "08:00"))
    }
}
