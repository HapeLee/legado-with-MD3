package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chapter_page_counts",
    indices = [
        Index(value = ["bookUrl", "layoutKey"], unique = false)
    ]
)
data class ChapterPageCount(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookUrl: String,
    val chapterIndex: Int,
    val layoutKey: String,
    val pageCount: Int
)
