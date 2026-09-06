package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Suppress("ConstPropertyName")
@Entity(tableName = "book_groups")
data class BookGroup(
    @PrimaryKey
    val groupId: Long = 0b1,
    var groupName: String = "",
    var cover: String? = null,
    var order: Int = 0,
    @ColumnInfo(defaultValue = "1")
    var enableRefresh: Boolean = true,
    @ColumnInfo(defaultValue = "1")
    var show: Boolean = true,
    @ColumnInfo(defaultValue = "-1")
    var bookSort: Int = -1,
    @ColumnInfo(defaultValue = "0")
    var isPrivate: Boolean = false
) {

    companion object {
        const val IdRoot = -100L
        const val IdAll = -1L
        const val IdLocal = -2L
        const val IdAudio = -3L
        const val IdNetNone = -4L
        const val IdLocalNone = -5L
        const val IdManga = -7L
        const val IdText = -8L
        const val IdError = -11L
        const val IdReading = -20L
        const val IdUnread = -21L
        const val IdReadFinished = -22L
        const val IdReadFinishedUpdate = -23L
        const val IdReadFinishedComplete = -24L
    }

    data class GroupNameInfo(
        val groupName: String,
        val suffix: String? = null
    )

    fun getRealBookSort(defaultBookSort: Int): Int {
        if (bookSort < 0) {
            return defaultBookSort
        }
        return bookSort
    }

    override fun hashCode(): Int {
        return groupId.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other is BookGroup) {
            return other.groupId == groupId
                    && other.groupName == groupName
                    && other.cover == cover
                    && other.bookSort == bookSort
                    && other.enableRefresh == enableRefresh
                    && other.show == show
                    && other.order == order
                    && other.isPrivate == isPrivate
        }
        return false
    }

}
