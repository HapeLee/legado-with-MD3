package io.legado.app.ui.widget.components.dialog

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimePickerDialogTest {

    @Test
    fun `时间值在非拉丁数字语言下仍使用 ASCII 格式`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar"))
            assertEquals("22:07", formatTimeValue(hour = 22, minute = 7))
            assertEquals(22, parseTimeNumber("٢٢"))
            assertEquals(7, parseTimeNumber("٠٧"))
            assertNull(parseTimeNumber("999999999999"))
        } finally {
            Locale.setDefault(original)
        }
    }
}
