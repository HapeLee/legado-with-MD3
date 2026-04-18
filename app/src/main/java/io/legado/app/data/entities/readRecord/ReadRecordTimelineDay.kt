package io.legado.app.data.entities.readRecord

data class ReadRecordTimelineDay(
    val date: String,
    val sessions: List<ReadRecordSession>
)
