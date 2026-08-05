package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 类型常量
 */
object RuleSubType {
    const val BOOK_SOURCE = 0    // 书源
    const val RSS_SOURCE = 1     // 订阅源
    const val REPLACE_RULE = 2   // 替换规则
    const val AUTO = 3           // 自动识别
}

@Entity(tableName = "ruleSubs")
data class RuleSub(
    @PrimaryKey
    val id: Long = System.currentTimeMillis(),
    var name: String = "",
    var url: String = "",
    var type: Int = RuleSubType.BOOK_SOURCE,
    var customOrder: Int = 0,
    var autoUpdate: Boolean = false,
    var update: Long = System.currentTimeMillis()
)
