package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import io.legado.app.core.platform.systemTimeMillis

@Entity(tableName = "tag_group_rules")
data class TagGroupRule(
    @PrimaryKey
    var id: Long = systemTimeMillis(),
    var pattern: String = "",
    var groupName: String = "",
    var order: Int = 0,
) {

    override fun hashCode(): Int = id.hashCode()

    override fun equals(other: Any?): Boolean {
        if (other is TagGroupRule) return id == other.id
        return false
    }

}
