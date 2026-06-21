package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "book_ai_chapter_summaries",
    indices = [
        Index(value = ["bookUrl", "chapterIndex"]),
        Index(value = ["bookUrl", "contentHash"])
    ]
)
data class BookAiChapterSummary(
    @PrimaryKey
    val cacheKey: String = "",
    @ColumnInfo(defaultValue = "")
    val bookUrl: String = "",
    @ColumnInfo(defaultValue = "")
    val bookName: String = "",
    @ColumnInfo(defaultValue = "0")
    val chapterIndex: Int = 0,
    @ColumnInfo(defaultValue = "")
    val chapterKey: String = "",
    @ColumnInfo(defaultValue = "")
    val chapterTitle: String = "",
    @ColumnInfo(defaultValue = "")
    val contentHash: String = "",
    @ColumnInfo(defaultValue = "")
    val modelId: String = "",
    @ColumnInfo(defaultValue = "")
    val modelName: String = "",
    @ColumnInfo(defaultValue = "")
    val summary: String = "",
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis()
)
