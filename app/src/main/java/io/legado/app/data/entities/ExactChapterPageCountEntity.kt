package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "exact_chapter_page_counts",
    primaryKeys = ["bookId", "chapterId", "layoutSignature"],
    indices = [
        Index(value = ["bookId", "layoutSignature"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["bookUrl"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ExactChapterPageCountEntity(
    val bookId: String,
    val chapterId: String,
    val chapterIndex: Int,
    val contentHash: Long,
    val layoutSignature: Long,
    val engineVersion: Int,
    val pageCount: Int,
    val updatedAt: Long,
)
