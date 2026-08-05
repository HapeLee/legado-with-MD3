package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_artifacts",
    indices = [
        Index(value = ["bookUrl", "chapterIndex", "taskType"]),
        Index(value = ["contentHash", "promptHash", "modelProfileId"])
    ]
)
data class AiArtifact(
    @PrimaryKey
    val id: String,
    val taskType: String,
    val bookUrl: String,
    val chapterIndex: Int? = null,
    val contentHash: String,
    val promptHash: String,
    val modelProfileId: String,
    val status: Int = STATUS_PENDING,
    val output: String? = null,
    val errorMessage: String? = null,
    val schemaVersion: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_PENDING = 0
        const val STATUS_RUNNING = 1
        const val STATUS_SUCCESS = 2
        const val STATUS_FAILED = 3
    }
}
