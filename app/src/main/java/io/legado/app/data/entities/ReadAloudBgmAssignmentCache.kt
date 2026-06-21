package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "read_aloud_bgm_assignment_caches",
    indices = [
        Index(value = ["bookUrl", "chapterIndex"]),
        Index(value = ["bookUrl", "contentHash"])
    ]
)
data class ReadAloudBgmAssignmentCache(
    @PrimaryKey
    val cacheKey: String = "",
    @ColumnInfo(defaultValue = "")
    val bookUrl: String = "",
    @ColumnInfo(defaultValue = "")
    val chapterKey: String = "",
    @ColumnInfo(defaultValue = "0")
    val chapterIndex: Int = 0,
    @ColumnInfo(defaultValue = "")
    val chapterTitle: String = "",
    @ColumnInfo(defaultValue = "")
    val contentHash: String = "",
    @ColumnInfo(defaultValue = "")
    val modelId: String = "",
    @ColumnInfo(defaultValue = "")
    val catalogHash: String = "",
    @ColumnInfo(defaultValue = "")
    val assignmentsJson: String = "",
    @ColumnInfo(defaultValue = "success")
    val status: String = STATUS_SUCCESS,
    @ColumnInfo(defaultValue = "")
    val lastError: String = "",
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_RUNNING = "running"
        const val STATUS_SUCCESS = "success"
        const val STATUS_FAILED = "failed"
    }
}
