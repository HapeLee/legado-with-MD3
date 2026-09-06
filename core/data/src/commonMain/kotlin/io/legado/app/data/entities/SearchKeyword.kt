package io.legado.app.data.entities

import io.legado.app.core.platform.systemTimeMillis

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey


@Entity(tableName = "search_keywords", indices = [(Index(value = ["word"], unique = true))])
data class SearchKeyword(
    /** 搜索关键词 */
    @PrimaryKey
    var word: String = "",
    /** 使用次数 */
    var usage: Int = 1,
    /** 最后一次使用时间 */
    var lastUseTime: Long = systemTimeMillis()
)
