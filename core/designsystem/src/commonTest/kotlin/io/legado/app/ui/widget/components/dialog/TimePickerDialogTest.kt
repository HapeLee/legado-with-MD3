package io.legado.app.ui.widget.components.dialog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * M5-10a-pre：随 [TimePickerDialog] 从 `:core:ui` 的 `src/test`（JVM 专用）迁到本模块
 * `commonTest`，为的是让「把 JVM 专用写法改写成跨端写法」这一步**有断言兜底**而不是靠眼看。
 *
 * 迁移前那版测试的核心是用 `java.util.Locale.setDefault("ar")` 包住断言，证明
 * `String.format(Locale.ROOT, …)` 在阿拉伯语地区**仍输出 ASCII**（否则会是 `٢٢:٠٧`）。
 * 新实现按构造就 locale-free（不碰 `Locale`、不碰 `String.format`，直接用
 * `Int.toString().padStart`），所以那层 `setDefault` 的包装已无意义、也不可用（`java.util`
 * 进不了 `commonTest`）——**但「输出形状恒为 ASCII」这件事仍然被断言**，因为它是用户可见行为。
 *
 * ⚠️ 与 `:core:ui` 那版的第二处差异：`parseTimeNumber` 改用 `Char.digitToIntOrNull(10)`
 * 替代 `Character.digit(char, 10)`。两者在 JVM 上都接受**非拉丁数字**（`٢٢` → 22），
 * 这正是本测试第 2 组断言钉的语义（输入框可能出现阿拉伯-印度数字键盘）。
 * 其它平台（native/JS）的数字表可能只认 ASCII —— 本测试在各端都会跑，若某端失败即暴露该差异。
 */
class TimePickerDialogTest {

    @Test
    fun `时间值恒以 ASCII 两位数输出`() {
        assertEquals("22:07", formatTimeValue(hour = 22, minute = 7))
        assertEquals("00:00", formatTimeValue(hour = 0, minute = 0))
        assertEquals("09:05", formatTimeValue(hour = 9, minute = 5))
    }

    @Test
    fun `解析接受非拉丁数字`() {
        assertEquals(22, parseTimeNumber("٢٢"))
        assertEquals(7, parseTimeNumber("٠٧"))
    }

    @Test
    fun `解析失败与溢出都回落到 null`() {
        assertNull(parseTimeNumber(""))
        assertNull(parseTimeNumber("1a"))
        assertNull(parseTimeNumber("-1"))
        assertNull(
            parseTimeNumber("999999999999"),
            "超出 Int 时应回落 null，而不是抛异常（`HH:mm` 输入框可以被贴上任意文本）",
        )
    }
}
