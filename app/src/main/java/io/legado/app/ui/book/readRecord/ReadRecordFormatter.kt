package io.legado.app.ui.book.readRecord

import io.legado.app.domain.model.readrecord.formatReadDuration
import kotlin.time.Duration.Companion.milliseconds

object ReadRecordFormatter {
    data class HourMinuteDuration(val hours: Long, val minutes: Int)

    fun formatDuration(millis: Long): String = formatReadDuration(millis)

    fun hourMinuteDuration(millis: Long): HourMinuteDuration =
        millis.milliseconds.toComponents { hours, minutes, _, _ ->
            HourMinuteDuration(hours, minutes)
        }
}
