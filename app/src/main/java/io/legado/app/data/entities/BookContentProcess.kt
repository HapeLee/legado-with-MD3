package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "book_content_processes",
    indices = [
        Index(value = ["bookUrl", "chapterIndex", "enabled", "sortOrder"]),
        Index(value = ["bookUrl", "kind"]),
        Index(value = ["aiArtifactId"]),
    ]
)
data class BookContentProcess(
    @PrimaryKey
    val id: String,
    val bookUrl: String,
    val chapterIndex: Int? = null,
    val kind: String,
    val stage: String = STAGE_CONTENT,
    val target: String = TARGET_SELECTION,
    val anchorJson: String,
    val actionJson: String,
    val styleJson: String? = null,
    val source: String = SOURCE_USER,
    val aiArtifactId: String? = null,
    val sourceContentHash: String? = null,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
    val status: Int = STATUS_ACTIVE,
    val schemaVersion: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val KIND_AI_CLEAN = "ai_clean"
        const val KIND_AI_REWRITE = "ai_rewrite"
        const val KIND_USER_UNDERLINE = "user_underline"
        const val KIND_USER_HIGHLIGHT = "user_highlight"

        const val STAGE_CONTENT = "content"
        const val STAGE_STYLE = "style"

        const val TARGET_SELECTION = "selection"
        const val TARGET_PARAGRAPH = "paragraph"
        const val TARGET_CHAPTER = "chapter"

        const val SOURCE_USER = "user"
        const val SOURCE_AI = "ai"

        const val STATUS_DRAFT = 0
        const val STATUS_ACTIVE = 1
        const val STATUS_DISABLED = 2
        const val STATUS_DELETED = 3
    }
}
