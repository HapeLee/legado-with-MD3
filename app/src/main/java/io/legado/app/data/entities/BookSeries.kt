package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 书架折叠收纳系列的元数据。
 *
 * 系列归属仍由 [Book.seriesId]（系列名）表达，这张表只补书籍本身放不下的东西：
 * 独立封面。没有成员书时这里可能残留一行，聚合时以 books 为准，不会显示空系列。
 */
@Entity(tableName = "book_series")
data class BookSeries(
    @PrimaryKey
    var name: String = "",
    /** 本地封面文件路径（externalFiles/covers/…），null = 用成员封面拼贴 */
    var cover: String? = null,
)
