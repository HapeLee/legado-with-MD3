package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.Fts4

@Fts4
@Entity(tableName = "ai_memory_items_fts")
data class AiMemoryItemFts(
    val memoryId: String = "",
    val subject: String = "",
    val predicate: String = "",
    val objectValue: String = "",
    val content: String = ""
)

@Fts4
@Entity(tableName = "ai_memory_fragments_fts")
data class AiMemoryFragmentFts(
    val fragmentId: String = "",
    val title: String = "",
    val content: String = "",
    val chapterTitle: String = ""
)
