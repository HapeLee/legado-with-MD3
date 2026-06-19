package io.legado.app.data.entities

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(
    tableName = "book_character_relations",
    foreignKeys = [
        ForeignKey(
            entity = BookCharacter::class,
            parentColumns = ["id"],
            childColumns = ["fromCharacterId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = BookCharacter::class,
            parentColumns = ["id"],
            childColumns = ["toCharacterId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["bookUrl"]),
        Index(value = ["fromCharacterId"]),
        Index(value = ["toCharacterId"]),
        Index(value = ["bookUrl", "fromCharacterId", "toCharacterId", "relationName"], unique = true)
    ]
)
data class BookCharacterRelation(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0L,
    @ColumnInfo(defaultValue = "")
    var bookUrl: String = "",
    @ColumnInfo(defaultValue = "0")
    var fromCharacterId: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    var toCharacterId: Long = 0L,
    @ColumnInfo(defaultValue = "")
    var relationName: String = "",
    @ColumnInfo(defaultValue = "")
    var relationType: String = "",
    @ColumnInfo(defaultValue = "")
    var description: String = "",
    @ColumnInfo(defaultValue = "50")
    var strength: Int = 50,
    @ColumnInfo(defaultValue = "0")
    var sortOrder: Int = 0,
    @ColumnInfo(defaultValue = "0")
    var updatedAt: Long = System.currentTimeMillis()
) : Parcelable {

    fun displayName(): String = relationName.ifBlank { relationType.ifBlank { "关系" } }
}
