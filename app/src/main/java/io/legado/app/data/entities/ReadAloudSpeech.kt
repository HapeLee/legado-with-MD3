package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "read_aloud_voices",
    indices = [
        Index(value = ["engineType", "engineId", "speakerId"], unique = true),
        Index(value = ["enabled", "displayName"]),
    ],
    primaryKeys = ["id"],
)
data class ReadAloudVoiceEntity(
    val id: String,
    val engineType: String,
    @ColumnInfo(defaultValue = "")
    val engineId: String = "",
    @ColumnInfo(defaultValue = "")
    val speakerId: String = "",
    val displayName: String,
    @ColumnInfo(defaultValue = "[]")
    val traitsJson: String = "[]",
    @ColumnInfo(defaultValue = "[]")
    val emotionCatalogJson: String = "[]",
    @ColumnInfo(defaultValue = "user")
    val managedBy: String = "user",
    @ColumnInfo(defaultValue = "1")
    val enabled: Boolean = true,
    @ColumnInfo(defaultValue = "1")
    val available: Boolean = true,
    @ColumnInfo(defaultValue = "0")
    val revision: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "book_voice_bindings",
    primaryKeys = ["bookUrl", "subjectType", "subjectId"],
    indices = [
        Index(value = ["bookUrl", "subjectType"]),
        Index(value = ["voiceId"]),
    ],
)
data class BookVoiceBindingEntity(
    val bookUrl: String,
    val subjectType: String,
    val subjectId: String,
    val voiceId: String,
    @ColumnInfo(defaultValue = "0")
    val locked: Boolean = false,
    @ColumnInfo(defaultValue = "user")
    val source: String = "user",
    @ColumnInfo(defaultValue = "1")
    val confidence: Float = 1f,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "chapter_speech_analysis",
    indices = [
        Index(
            value = ["bookUrl", "chapterIndex", "contentHash", "resolverVersion"],
            unique = true,
        ),
        Index(value = ["bookUrl", "chapterIndex", "updatedAt"]),
    ],
    primaryKeys = ["id"],
)
data class ChapterSpeechAnalysisEntity(
    val id: String,
    val bookUrl: String,
    val chapterIndex: Int,
    val contentHash: String,
    val resolverVersion: String,
    @ColumnInfo(defaultValue = "")
    val characterRevision: String = "",
    @ColumnInfo(defaultValue = "pending")
    val status: String = "pending",
    @ColumnInfo(defaultValue = "")
    val error: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "chapter_speech_segments",
    indices = [
        Index(value = ["analysisId", "paragraphIndex", "start", "end"], unique = true),
        Index(value = ["bookUrl", "chapterIndex", "paragraphIndex"]),
        Index(value = ["characterId"]),
    ],
    primaryKeys = ["id"],
)
data class ChapterSpeechSegmentEntity(
    val id: String,
    val analysisId: String,
    val bookUrl: String,
    val chapterIndex: Int,
    val paragraphIndex: Int,
    val start: Int,
    val end: Int,
    val chapterPosition: Int,
    val text: String,
    val roleType: String,
    val characterId: String? = null,
    @ColumnInfo(defaultValue = "")
    val characterName: String = "",
    @ColumnInfo(defaultValue = "")
    val emotion: String = "",
    @ColumnInfo(defaultValue = "0")
    val confidence: Float = 0f,
    val source: String,
    @ColumnInfo(defaultValue = "0")
    val userLocked: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
