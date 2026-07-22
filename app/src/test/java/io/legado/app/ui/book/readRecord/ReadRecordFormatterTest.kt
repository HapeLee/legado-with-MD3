package io.legado.app.ui.book.readRecord

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadRecordFormatterTest {

    @Test
    fun hourMinuteDuration_discardsSeconds() {
        assertEquals(
            ReadRecordFormatter.HourMinuteDuration(hours = 1, minutes = 30),
            ReadRecordFormatter.hourMinuteDuration(5_400_999L),
        )
    }

    @Test
    fun hourMinuteDuration_doesNotConvertLongDurationsToDays() {
        assertEquals(
            ReadRecordFormatter.HourMinuteDuration(hours = 49, minutes = 5),
            ReadRecordFormatter.hourMinuteDuration((49L * 60 + 5) * 60 * 1_000),
        )
    }
}
