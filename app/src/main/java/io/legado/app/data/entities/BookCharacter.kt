package io.legado.app.data.entities

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(
    tableName = "book_characters",
    indices = [
        Index(value = ["bookUrl"]),
        Index(value = ["bookUrl", "name"], unique = true)
    ]
)
data class BookCharacter(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0L,
    @ColumnInfo(defaultValue = "")
    var bookUrl: String = "",
    @ColumnInfo(defaultValue = "")
    var name: String = "",
    @ColumnInfo(defaultValue = "")
    var avatar: String = "",
    @ColumnInfo(defaultValue = "")
    var identity: String = "",
    @ColumnInfo(defaultValue = "")
    var skills: String = "",
    @ColumnInfo(defaultValue = "")
    var attributes: String = "",
    @ColumnInfo(defaultValue = "")
    var appearance: String = "",
    @ColumnInfo(defaultValue = "")
    var personality: String = "",
    @ColumnInfo(defaultValue = "")
    var biography: String = "",
    @ColumnInfo(defaultValue = "0")
    var roleLevel: Int = ROLE_NORMAL,
    @ColumnInfo(defaultValue = "0")
    var sortOrder: Int = 0,
    @ColumnInfo(defaultValue = "0")
    var createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    var updatedAt: Long = System.currentTimeMillis()
) : Parcelable {

    fun displayName(): String = name.ifBlank { "未命名角色" }

    fun roleLabel(): String = when (roleLevel) {
        ROLE_MAIN -> "主角"
        ROLE_IMPORTANT -> "重要角色"
        else -> "普通角色"
    }

    companion object {
        const val ROLE_NORMAL = 0
        const val ROLE_IMPORTANT = 1
        const val ROLE_MAIN = 2
    }
}
