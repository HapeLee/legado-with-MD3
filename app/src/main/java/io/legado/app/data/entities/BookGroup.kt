package io.legado.app.data.entities

import android.content.Context
import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import kotlinx.parcelize.Parcelize

@Suppress("ConstPropertyName")
@Parcelize
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
    var bookSort: Int = -1
) : Parcelable {

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
    }

    data class GroupNameInfo(
        val groupName: String,
        val suffix: String? = null
    )

    fun getManageName(context: Context): GroupNameInfo {
        return when (groupId) {
            IdAll -> GroupNameInfo(groupName, context.getString(R.string.all))
            IdAudio -> GroupNameInfo(groupName, context.getString(R.string.audio))
            IdLocal -> GroupNameInfo(groupName, context.getString(R.string.local))
            IdNetNone -> GroupNameInfo(groupName, context.getString(R.string.net_no_group))
            IdLocalNone -> GroupNameInfo(groupName, context.getString(R.string.local_no_group))
            IdManga -> GroupNameInfo(groupName, context.getString(R.string.manga))
            IdText -> GroupNameInfo(groupName, context.getString(R.string.noval))
            IdError -> GroupNameInfo(groupName, context.getString(R.string.update_book_fail))
            IdReading -> GroupNameInfo(groupName, context.getString(R.string.is_reading))
            IdUnread -> GroupNameInfo(groupName, context.getString(R.string.is_unread))
            IdReadFinished -> GroupNameInfo(groupName, context.getString(R.string.is_read_finished))
            else -> GroupNameInfo(groupName)
        }
    }

    fun getRealBookSort(): Int {
        if (bookSort < 0) {
            return AppConfig.bookshelfSort
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
        }
        return false
    }

}