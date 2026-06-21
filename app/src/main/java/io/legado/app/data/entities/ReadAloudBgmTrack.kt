package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "read_aloud_bgm_tracks",
    indices = [
        Index(value = ["groupId", "sortOrder", "id"]),
        Index(value = ["assetType", "enabled", "groupId", "sortOrder", "id"]),
        Index(value = ["checksum"]),
        Index(value = ["enabled"])
    ]
)
data class ReadAloudBgmTrack(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val groupId: Long = 0L,
    @ColumnInfo(defaultValue = "'bgm'")
    val assetType: String = TYPE_BGM,
    @ColumnInfo(defaultValue = "")
    val name: String = "",
    @ColumnInfo(defaultValue = "")
    val fileName: String = "",
    @ColumnInfo(defaultValue = "")
    val filePath: String = "",
    @ColumnInfo(defaultValue = "")
    val tags: String = "",
    @ColumnInfo(defaultValue = "")
    val checksum: String = "",
    @ColumnInfo(defaultValue = "0")
    val durationMs: Long = 0L,
    @ColumnInfo(defaultValue = "1.0")
    val defaultVolume: Float = 1.0f,
    @ColumnInfo(defaultValue = "1")
    val enabled: Boolean = true,
    @ColumnInfo(defaultValue = "0")
    val sortOrder: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun displayName(): String = name.ifBlank {
        fileName.ifBlank {
            if (normalizedAssetType() == TYPE_SFX) "未命名音效" else "未命名音乐"
        }
    }

    fun normalizedAssetType(): String = normalizeAssetType(assetType)

    fun assetTypeLabel(): String = if (normalizedAssetType() == TYPE_SFX) "音效" else "配乐"

    companion object {
        const val TYPE_BGM = "bgm"
        const val TYPE_SFX = "sfx"

        fun normalizeAssetType(value: String): String {
            return when (value) {
                TYPE_SFX -> TYPE_SFX
                else -> TYPE_BGM
            }
        }
    }
}
