package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "rssReadRecords", indices = [Index(value = ["origin"], unique = false)])
data class RssReadRecord(
    @PrimaryKey
    val record: String,
    val title: String? = null,
    val readTime: Long? = null,
    val read: Boolean = true,
    @ColumnInfo(defaultValue = "")
    val origin: String = "",
    @ColumnInfo(defaultValue = "")
    var sort: String = "",
    var image: String? = null,
    /**类型 0网页，1图片，2视频**/
    @ColumnInfo(defaultValue = "0")
    var type: Int = 0,
    /**阅读进度**/
    @ColumnInfo(defaultValue = "0")
    var durPos: Int = 0,
    var pubDate: String? = null
) {
    fun toRssArticle(): RssArticle {
        return RssArticle(
            title = title ?: "",
            origin = origin,
            link = record,
            sort = sort,
            image = image,
            type = type,
            durPos = durPos,
            pubDate = pubDate
        )
    }

    fun toStar(): RssStar {
        return RssStar(
            title = title ?: "",
            origin = origin,
            link = record,
            sort = sort,
            image = image,
            type = type,
            durPos = durPos,
            pubDate = pubDate
        )
    }
}