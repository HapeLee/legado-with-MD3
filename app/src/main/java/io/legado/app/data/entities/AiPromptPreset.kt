package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_prompt_presets",
    indices = [
        Index(value = ["taskType", "enabled", "sortNumber"]),
    ],
)
data class AiPromptPreset(
    @PrimaryKey
    val id: String,
    val taskType: String,
    val name: String,
    val instruction: String,
    val enabled: Boolean = true,
    val builtIn: Boolean = false,
    val sortNumber: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
