package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "read_aloud_speaker_group_items",
    indices = [
        Index(value = ["groupId", "sortOrder", "id"]),
        Index(value = ["engineType", "engineValue", "toneID"])
    ]
)
data class ReadAloudSpeakerGroupItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val groupId: Long = 0L,
    @ColumnInfo(defaultValue = "")
    val engineType: String = "",
    @ColumnInfo(defaultValue = "")
    val engineValue: String = "",
    @ColumnInfo(defaultValue = "")
    val engineName: String = "",
    @ColumnInfo(defaultValue = "")
    val speakerName: String = "",
    @ColumnInfo(defaultValue = "")
    val toneID: String = "",
    @ColumnInfo(defaultValue = "")
    val sourceGroupId: String = "",
    @ColumnInfo(defaultValue = "")
    val sourceGroupName: String = "",
    @ColumnInfo(defaultValue = "0")
    val sortOrder: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun displayName(): String = speakerName.ifBlank { engineName.ifBlank { "默认发言人" } }
}
