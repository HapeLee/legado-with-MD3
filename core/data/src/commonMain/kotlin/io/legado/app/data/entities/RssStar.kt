package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import io.legado.app.core.platform.JsonCodec
import io.legado.app.core.platform.systemTimeMillis


@Entity(
    tableName = "rssStars",
    primaryKeys = ["origin", "link"]
)
data class RssStar(
    override var origin: String = "",
    var sort: String = "",
    var title: String = "",
    var starTime: Long = 0,
    override var link: String = "",
    var pubDate: String? = null,
    var description: String? = null,
    var content: String? = null,
    var image: String? = null,
    @ColumnInfo(defaultValue = "默认分组")
    var group: String = "默认分组",
    override var variable: String? = null,
    /**类型 0网页，1图片，2视频**/
    @ColumnInfo(defaultValue = "0")
    var type: Int = 0,
    /**阅读进度**/
    @ColumnInfo(defaultValue = "0")
    var durPos: Int = 0
) : BaseRssArticle {

    @delegate:Transient
    @delegate:Ignore
    override val variableMap: HashMap<String, String> by lazy {
        JsonCodec.decodeStringMap(variable)?.let { HashMap(it) } ?: hashMapOf()
    }

    fun toRssArticle() = RssArticle(
        origin = origin,
        sort = sort,
        title = title,
        link = link,
        pubDate = pubDate,
        description = description,
        content = content,
        image = image,
        group = group,
        variable = variable,
        type = type,
        durPos = durPos
    )

    fun toRecord() = RssReadRecord(
        origin = origin,
        sort = sort,
        title = title,
        readTime = systemTimeMillis(),
        record = link,
        image = image,
        type = type,
        durPos = durPos,
        pubDate = pubDate
    )
}
