package io.legado.app.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class ReadRecordTimeFormatterTest {

    @Test
    fun `returns zero seconds for sub-second and negative durations`() {
        assertEquals("0秒", formatReadDuration(0))
        assertEquals("0秒", formatReadDuration(999))
        assertEquals("0秒", formatReadDuration(-1_000))
    }

    @Test
    fun `formats minute and second components`() {
        assertEquals("1分钟1秒", formatReadDuration(61_000))
    }

    @Test
    fun `formats all larger components in descending order`() {
        val duration = ((25L * 60 * 60) + (2 * 60) + 3) * 1_000

        assertEquals("1天1小时2分钟3秒", formatReadDuration(duration))
    }
}
