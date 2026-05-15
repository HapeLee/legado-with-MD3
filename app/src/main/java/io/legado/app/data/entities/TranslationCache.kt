package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "translationCache",
    indices = [
        Index(value = ["cacheKey"], unique = true),
        Index(value = ["bookUrl", "chapterIndex"]),
        Index(value = ["originalContentHash"])
    ]
)
data class TranslationCache(
    @PrimaryKey
    val cacheKey: String,
    val bookUrl: String,
    val chapterIndex: Int,
    val chapterTitleMD5: String,
    val originalContentHash: String,
    val targetLanguage: String,
    val provider: String,
    val chunkIndex: Int,
    val originalChunkContent: String,
    val translatedChunkContent: String?,
    val status: Int = STATUS_PENDING,
    val errorMessage: String? = null,
    val createTime: Long = System.currentTimeMillis(),
    val updateTime: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_PENDING = 0
        const val STATUS_TRANSLATING = 1
        const val STATUS_SUCCESS = 2
        const val STATUS_FAILED = 3
    }

    val isSuccess: Boolean get() = status == STATUS_SUCCESS
    val isFailed: Boolean get() = status == STATUS_FAILED
    val isPending: Boolean get() = status == STATUS_PENDING
}