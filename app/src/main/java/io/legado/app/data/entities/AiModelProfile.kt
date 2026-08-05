package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_model_profiles",
    indices = [Index(value = ["providerId"])]
)
data class AiModelProfile(
    @PrimaryKey
    val id: String,
    val providerId: String,
    val displayName: String,
    val modelId: String,
    val contextWindow: Int = 0,
    val maxOutputTokens: Int = 0,
    val capabilities: String = "",
    val defaultParamsJson: String? = null,
    val enabled: Boolean = true,
    val sortNumber: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
