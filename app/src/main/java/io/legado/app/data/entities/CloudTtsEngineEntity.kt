package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "cloud_tts_engines",
    primaryKeys = ["id"],
    indices = [Index(value = ["provider", "enabled"])],
)
data class CloudTtsEngineEntity(
    val id: String,
    val name: String,
    val provider: String,
    @ColumnInfo(defaultValue = "") val baseUrl: String = "",
    @ColumnInfo(defaultValue = "") val apiKey: String = "",
    @ColumnInfo(defaultValue = "") val secretKey: String = "",
    @ColumnInfo(defaultValue = "") val region: String = "",
    @ColumnInfo(defaultValue = "") val appId: String = "",
    @ColumnInfo(defaultValue = "") val model: String = "",
    @ColumnInfo(defaultValue = "{}") val optionsJson: String = "{}",
    @ColumnInfo(defaultValue = "1") val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
