package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "book_character_profiles",
    indices = [
        Index(value = ["bookUrl", "name"], unique = true),
        Index(value = ["bookUrl", "updatedAt"]),
    ]
)
data class BookCharacterProfile(
    @PrimaryKey
    val id: String,
    val bookUrl: String,
    val name: String,
    val aliasesJson: String = "[]",
    val avatarUri: String? = null,
    val tagsJson: String = "[]",
    val role: String = "",
    @ColumnInfo(defaultValue = "unknown")
    val voiceGender: String = VOICE_GENDER_UNKNOWN,
    @ColumnInfo(defaultValue = "unknown")
    val voiceAgeBand: String = VOICE_AGE_UNKNOWN,
    val personality: String = "",
    val summary: String = "",
    val status: Int = STATUS_ACTIVE,
    val source: String = SOURCE_USER,
    val confidence: Float = 1f,
    val schemaVersion: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val STATUS_DRAFT = 0
        const val STATUS_ACTIVE = 1
        const val STATUS_DISABLED = 2
        const val STATUS_DELETED = 3

        const val SOURCE_USER = "user"
        const val SOURCE_AI = "ai"
        const val SOURCE_IMPORT = "import"

        const val ROLE_MALE_LEAD = "male_lead"
        const val ROLE_FEMALE_LEAD = "female_lead"
        const val ROLE_MALE_SUPPORTING = "male_supporting"
        const val ROLE_FEMALE_SUPPORTING = "female_supporting"
        val ALL_ROLES =
            listOf(ROLE_MALE_LEAD, ROLE_FEMALE_LEAD, ROLE_MALE_SUPPORTING, ROLE_FEMALE_SUPPORTING)

        const val VOICE_GENDER_MALE = "male"
        const val VOICE_GENDER_FEMALE = "female"
        const val VOICE_GENDER_UNKNOWN = "unknown"
        val ALL_VOICE_GENDERS = listOf(VOICE_GENDER_MALE, VOICE_GENDER_FEMALE, VOICE_GENDER_UNKNOWN)

        const val VOICE_AGE_CHILD = "child"
        const val VOICE_AGE_TEEN = "teen"
        const val VOICE_AGE_YOUNG_ADULT = "young_adult"
        const val VOICE_AGE_ADULT = "adult"
        const val VOICE_AGE_ELDERLY = "elderly"
        const val VOICE_AGE_UNKNOWN = "unknown"
        val ALL_VOICE_AGE_BANDS = listOf(
            VOICE_AGE_CHILD, VOICE_AGE_TEEN, VOICE_AGE_YOUNG_ADULT,
            VOICE_AGE_ADULT, VOICE_AGE_ELDERLY, VOICE_AGE_UNKNOWN,
        )
    }
}

@Entity(
    tableName = "book_character_events",
    indices = [
        Index(value = ["bookUrl", "characterId", "chapterIndex"]),
        Index(value = ["bookUrl", "chapterIndex"]),
    ]
)
data class BookCharacterEvent(
    @PrimaryKey
    val id: String,
    val bookUrl: String,
    val characterId: String,
    val chapterIndex: Int? = null,
    val chapterTitle: String = "",
    val eventTimeText: String = "",
    val content: String,
    val importance: Int = 0,
    val sourceTextHash: String? = null,
    val evidenceJson: String = "[]",
    val source: String = BookCharacterProfile.SOURCE_USER,
    val confidence: Float = 1f,
    val schemaVersion: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "book_character_relations",
    indices = [
        Index(value = ["bookUrl", "fromCharacterId", "toCharacterId"], unique = true),
        Index(value = ["bookUrl", "fromCharacterId"]),
        Index(value = ["bookUrl", "toCharacterId"]),
    ]
)
data class BookCharacterRelation(
    @PrimaryKey
    val id: String,
    val bookUrl: String,
    val fromCharacterId: String,
    val toCharacterId: String,
    val relationType: String,
    val summary: String = "",
    val attitude: String = "",
    val evidenceJson: String = "[]",
    val chapterIndex: Int? = null,
    val status: Int = BookCharacterProfile.STATUS_ACTIVE,
    val source: String = BookCharacterProfile.SOURCE_USER,
    val confidence: Float = 1f,
    val schemaVersion: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "book_knowledge_entries",
    indices = [
        Index(value = ["bookUrl", "type", "priority"]),
        Index(value = ["bookUrl", "title"]),
    ]
)
data class BookKnowledgeEntry(
    @PrimaryKey
    val id: String,
    val bookUrl: String,
    val type: String,
    val title: String,
    val keywordsJson: String = "[]",
    val content: String,
    val scopeStartChapter: Int? = null,
    val scopeEndChapter: Int? = null,
    val priority: Int = 0,
    val source: String = BookCharacterProfile.SOURCE_USER,
    val confidence: Float = 1f,
    val evidenceJson: String = "[]",
    val schemaVersion: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val TYPE_WORLD_RULE = "world_rule"
        const val TYPE_LOCATION = "location"
        const val TYPE_FACTION = "faction"
        const val TYPE_OBJECT = "object"
        const val TYPE_TERMINOLOGY = "terminology"
        const val TYPE_TIMELINE = "timeline"
        const val TYPE_STYLE = "style"
        const val TYPE_THEME = "theme"
    }
}

@Entity(
    tableName = "book_outline_nodes",
    indices = [
        Index(value = ["bookUrl", "nodeType", "startChapterIndex", "endChapterIndex"]),
        Index(value = ["bookUrl", "parentId", "order"]),
    ]
)
data class BookOutlineNode(
    @PrimaryKey
    val id: String,
    val bookUrl: String,
    val parentId: String? = null,
    val nodeType: String,
    val title: String,
    val summary: String,
    val startChapterIndex: Int? = null,
    val endChapterIndex: Int? = null,
    val order: Int = 0,
    val source: String = BookCharacterProfile.SOURCE_USER,
    val confidence: Float = 1f,
    val schemaVersion: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val TYPE_BOOK = "book"
        const val TYPE_VOLUME = "volume"
        const val TYPE_ARC = "arc"
        const val TYPE_CHAPTER = "chapter"
        const val TYPE_SCENE = "scene"
    }
}
