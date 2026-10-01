package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 正则角色的分组：和角色声音池 / 背景音乐池的分组同一套形状（可嵌套的文件夹树）。
 *
 * 三套东西的列表交互必须一样（用户口径「该有的功能，比如拖动排序、分组啊这些的都要有，
 * 就和我们之前做的差不多」），所以字段照 [VoicePoolGroupEntity] 抄，不发明新形状：
 * [parentId] 空串 = 根层，[order] 是同一父级下的手动顺序，[enabled] 关掉等于整棵子树停用。
 */
@Entity(
    tableName = "regex_cast_groups",
    indices = [
        Index(value = ["parentId"]),
        Index(value = ["parentId", "name"], unique = true),
        Index(value = ["parentId", "order"]),
    ],
)
data class RegexCastGroup(
    @PrimaryKey
    val id: String,
    var name: String,
    /** 父分组 id，空串 = 根层。 */
    @ColumnInfo(defaultValue = "")
    var parentId: String = "",
    /** 父级内手动顺序（长按拖动排序回写这里）。 */
    @ColumnInfo(defaultValue = "0")
    var order: Int = 0,
    @ColumnInfo(defaultValue = "1")
    var enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
)
