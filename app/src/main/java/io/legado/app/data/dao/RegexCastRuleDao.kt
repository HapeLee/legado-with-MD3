package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.legado.app.data.entities.RegexCastGroup
import io.legado.app.data.entities.RegexCastRule

/**
 * 正则角色与它的分组的读写。唯一入口是
 * [io.legado.app.help.readaloud.cast.RegexCastRuleStore]（界面与朗读服务都不直连本 DAO）。
 *
 * 范围判定（特定范围 / 排除范围）与官方替换规则同一口径：`scope LIKE '%' || 书名 || '%'`，
 * 空 = 不限。分组链是否停用放在 Store 里用 Kotlin 判，不写递归 SQL——树本来就要拉平成
 * [io.legado.app.help.readaloud.cast.CastGroupRow] 给界面用，两份口径只留一处。
 */
@Dao
interface RegexCastRuleDao {

    @Query("SELECT * FROM regex_cast_rules ORDER BY `order` ASC, id ASC")
    suspend fun all(): List<RegexCastRule>

    @Query(
        """SELECT * FROM regex_cast_rules WHERE enabled = 1
        AND (scope IS NULL OR scope = '' OR scope LIKE '%' || :name || '%' OR scope LIKE '%' || :origin || '%')
        AND (excludeScope IS NULL OR excludeScope = ''
            OR (excludeScope NOT LIKE '%' || :name || '%' AND excludeScope NOT LIKE '%' || :origin || '%'))
        ORDER BY `order` ASC, id ASC"""
    )
    suspend fun findEnabledForBook(name: String, origin: String): List<RegexCastRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: RegexCastRule): Long

    @Update
    suspend fun update(rule: RegexCastRule)

    @Update
    suspend fun updateRules(rules: List<RegexCastRule>)

    @Delete
    suspend fun delete(rule: RegexCastRule)

    @Query("UPDATE regex_cast_rules SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    // ---------- 分组（可嵌套，行为等同文件夹） ----------

    @Query("SELECT * FROM regex_cast_groups ORDER BY `order` ASC, name COLLATE NOCASE")
    suspend fun getGroups(): List<RegexCastGroup>

    @Query("SELECT * FROM regex_cast_groups WHERE id = :id")
    suspend fun getGroup(id: String): RegexCastGroup?

    @Query("SELECT * FROM regex_cast_groups WHERE parentId = :parentId AND name = :name LIMIT 1")
    suspend fun getGroupByName(parentId: String, name: String): RegexCastGroup?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: RegexCastGroup)

    @Update
    suspend fun updateGroups(groups: List<RegexCastGroup>)

    @Query("UPDATE regex_cast_groups SET enabled = :enabled WHERE id = :id")
    suspend fun setGroupEnabled(id: String, enabled: Boolean)

    @Query("DELETE FROM regex_cast_groups WHERE id = :id")
    suspend fun deleteGroupById(id: String)

    /** 删组：组里的规则与子组一起回到父级（不连带删规则，删数据要用户单独动手）。 */
    @Query("UPDATE regex_cast_rules SET groupId = :to WHERE groupId = :from")
    suspend fun moveRulesOutOfGroup(from: String, to: String)

    @Query("UPDATE regex_cast_groups SET parentId = :to WHERE parentId = :from")
    suspend fun moveChildGroupsOutOfGroup(from: String, to: String)
}
