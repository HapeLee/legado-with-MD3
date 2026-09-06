package io.legado.app.data.entities

import io.legado.app.core.platform.systemTimeMillis

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_task_presets",
    indices = [Index(value = ["taskType"]), Index(value = ["modelProfileId"])]
)
data class AiTaskPreset(
    @PrimaryKey
    val id: String,
    val taskType: String,
    val name: String,
    val modelProfileId: String,
    val promptTemplate: String,
    val paramsJson: String? = null,
    val chunkPolicyJson: String? = null,
    val enabled: Boolean = true,
    val isDefault: Boolean = false,
    val sortNumber: Int = 0,
    val createdAt: Long = systemTimeMillis(),
    val updatedAt: Long = systemTimeMillis()
)
