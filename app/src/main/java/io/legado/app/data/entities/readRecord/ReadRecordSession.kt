package io.legado.app.data.entities.readRecord

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "readRecordSession")
data class ReadRecordSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val deviceId: String = "",
    val bookName: String = "",
    val bookAuthor: String = "",

    // 一次阅读的开始/结束
    val startTime: Long = 0,
    val endTime: Long = 0,

    // 本次阅读的字数
    val words: Long = 0
)
