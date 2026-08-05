package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import io.legado.app.ui.widget.components.list.SelectableItem

@Entity(tableName = "highlight_tag_rules")
data class HighlightTagRule(
    @PrimaryKey
    var id: Long = System.currentTimeMillis(),
    var title: String = "",
    var pattern: String = "",
    var enabled: Boolean = true,
    var order: Int = 0,
) {

    override fun hashCode(): Int = id.hashCode()

    override fun equals(other: Any?): Boolean {
        if (other is HighlightTagRule) return id == other.id
        return false
    }

}
