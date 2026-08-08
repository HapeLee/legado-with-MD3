package io.legado.app.data.entities

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(
    tableName = "bookmarks",
    indices = [(Index(value = ["bookName", "bookAuthor"], unique = false))]
)
data class Bookmark(
    @PrimaryKey
    val time: Long = System.currentTimeMillis(),
    val bookName: String = "",
    @ColumnInfo(defaultValue = "")
    val bookAuthor: String = "",
    /** 创建时的源（源指纹）：跳转校验用。bookmarks 仍按书名+作者关联，换源后保留。 */
    @ColumnInfo(defaultValue = "")
    val bookUrl: String = "",
    var chapterIndex: Int = 0,
    var chapterPos: Int = 0,
    var chapterName: String = "",
    var bookText: String = "",
    var content: String = ""
) : Parcelable