package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "search_content_history",
    indices = [(Index(value = ["bookName", "bookAuthor", "query"], unique = true))]
)
data class SearchContentHistory(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,
    @ColumnInfo(defaultValue = "")
    var bookName: String? = null,
    @ColumnInfo(defaultValue = "")
    var bookAuthor: String? = null,
    var query: String = "",
    var time: Long = System.currentTimeMillis()
)
