package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import io.legado.app.core.platform.systemTimeMillis

@Entity(tableName = "txtTocRules")
data class TxtTocRule(
    @PrimaryKey
    var id: Long = systemTimeMillis(),
    var name: String = "",
    //旧版本备份里该字段键名为 "rule"，兼容逻辑见 app 侧 TxtTocRuleAndroid.jsonDeserializer
    var chapterRule: String = "",
    var volumeRule: String = "",
    var example: String? = null,
    var serialNumber: Int = -1,
    var enable: Boolean = true
) {

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other is TxtTocRule) {
            return id == other.id
        }
        return false
    }

}
