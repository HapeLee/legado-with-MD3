package io.legado.app.utils

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * M5-19d：[colorToHsl] 的等价性基线。
 *
 * 这个函数是**纯算法替代**（原先调 AndroidX 的 `ColorUtils.colorToHSL`，那是 Android-only
 * 制品），不同于搬文件 —— 搬文件由编译与 diff 保证等价，**算法替换必须用例锁**。
 *
 * 用例取的是 HSL 定义上的确定值（与 AndroidX 实现同口径）：
 * 三原色的 H 为 0 / 120 / 240、`S = 1`、`L = 0.5`；无彩色（白/黑/灰）`S = 0`；
 * 白色 `L = 1`、黑色 `L = 0`；另外单独锁一条**带 `g < b` 回绕修正**的颜色
 * （品红 `0xFFFF00FF`：`max == r` 且 `g < b` ⇒ H 必须是 300 而不是 -60）。
 */
class ColorHslTest {

    private fun assertHsl(color: Int, hue: Float, s: Float, l: Float) {
        val hsl = colorToHsl(color)
        assertEquals(3, hsl.size)
        assertTrue(
            abs(hsl[0] - hue) < 0.01f,
            "H 期望 $hue 实得 ${hsl[0]}（color=0x${color.toUInt().toString(16)}）",
        )
        assertTrue(abs(hsl[1] - s) < 0.01f, "S 期望 $s 实得 ${hsl[1]}")
        assertTrue(abs(hsl[2] - l) < 0.01f, "L 期望 $l 实得 ${hsl[2]}")
    }

    @Test
    fun `三原色的色相与饱和度`() {
        assertHsl(0xFFFF0000.toInt(), hue = 0f, s = 1f, l = 0.5f)      // 红
        assertHsl(0xFF00FF00.toInt(), hue = 120f, s = 1f, l = 0.5f)    // 绿
        assertHsl(0xFF0000FF.toInt(), hue = 240f, s = 1f, l = 0.5f)    // 蓝
    }

    @Test
    fun `无彩色饱和度为 0 明度取端点`() {
        assertHsl(0xFFFFFFFF.toInt(), hue = 0f, s = 0f, l = 1f)        // 白
        assertHsl(0xFF000000.toInt(), hue = 0f, s = 0f, l = 0f)        // 黑
        assertHsl(0xFF808080.toInt(), hue = 0f, s = 0f, l = 128f / 255f) // 中灰
    }

    @Test
    fun `max 为红且绿小于蓝时色相回绕到 300`() {
        // `max == r` 那一支要加 `g < b ? 6 : 0`，否则得到 -60 —— 这条专门锁它
        assertHsl(0xFFFF00FF.toInt(), hue = 300f, s = 1f, l = 0.5f)
    }

    @Test
    fun `亮色与暗色各有自己的饱和度分支`() {
        // l > 0.5 时 S = delta / (2 - max - min)，与 l <= 0.5 那支不同
        assertHsl(0xFFFF8080.toInt(), hue = 0f, s = 1f, l = 0.75f)
        assertHsl(0xFF800000.toInt(), hue = 0f, s = 1f, l = 0.25f)
    }
}
