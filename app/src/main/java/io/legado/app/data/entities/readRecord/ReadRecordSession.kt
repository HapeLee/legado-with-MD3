package io.legado.app.data.entities.readRecord

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "readRecordSession")
data class ReadRecordSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val deviceId: String = "",
    val bookName: String = "",
    @ColumnInfo(defaultValue = "")
    val bookAuthor: String = "",

    /**
     * 会话所属的书籍副本。
     *
     * 书架允许书名作者相同的作品共存，而 [readRecord] 汇总表以 (书名, 作者) 为主键，
     * 因此同一份汇总记录下可能挂着多个副本。必须按副本记账，才能让「每个副本各自计时」，
     * 避免出现后加入的副本直接继承、叠加已有作品时长的情况。
     *
     * 空串表示「未归属」，只会是本次迁移之前写入的历史会话；它们按作品维度参与统计，
     * 保证旧时长不丢失，但不计入任何单个副本。
     */
    @ColumnInfo(defaultValue = "")
    val bookUrl: String = "",

    // 一次阅读的开始/结束
    val startTime: Long = 0,
    val endTime: Long = 0,

    // 本次阅读时所在的章节序号（durChapterIndex），用于时间线定位章节标题；不是字数
    val words: Long = 0
) {
    /** 跨设备稳定身份，不依赖 Room 自动生成的数据库行 ID。 */
    val stableFingerprint: String
        get() = listOf(deviceId, bookName, bookAuthor, bookUrl, startTime, endTime, words)
            .joinToString("\u0001")

    /** 判断会话是否属于指定书籍副本；副本、书名、作者三者都必须一致。 */
    fun matchesBook(url: String, name: String, author: String): Boolean =
        bookUrl == url && bookName == name && bookAuthor == author
}
