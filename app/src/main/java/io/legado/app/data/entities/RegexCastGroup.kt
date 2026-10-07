package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 正则角色的分组：与角色声音池 / 背景音乐池的分组同一种形状（可嵌套的文件夹树），
 * 列表交互（折叠、拖动排序、组开关）走同一套部件 [io.legado.app.ui.book.readaloud.cast.PoolTreeList]。
 *
 * 字段与 [VoicePoolGroupEntity] 保持一致：[parentId] 空串 = 根层，[order] 是同一父级下的手动顺序，
 * [enabled] 关掉等于整棵子树停用（停用链的判定在 RegexCastRuleStore.listGroups 拉平时算 usable）。
 *
 * 被 [RegexCastRule.groupId] 引用；表 `regex_cast_groups` 对应 Room version 128，
 * 建表与迁移见 `DatabaseMigrations.migration_127_128`。
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
