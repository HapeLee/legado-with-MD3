package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.legado.app.data.entities.RegexCastRule

/**
 * 正则角色的读写。
 *
 * 列表一次取全（条数量级是几十，不分页）；朗读侧按书取启用规则，范围判定与官方
 * 替换规则同一口径（`scope LIKE '%' || 书名 || '%'`，空 = 不限）。
 */
@Dao
interface RegexCastRuleDao {

    @Query("SELECT * FROM regex_cast_rules ORDER BY `group` ASC, `order` ASC, id ASC")
    suspend fun all(): List<RegexCastRule>

    @Query("SELECT * FROM regex_cast_rules WHERE id = :id")
    suspend fun findById(id: Long): RegexCastRule?

    @Query(
        """SELECT * FROM regex_cast_rules WHERE enabled = 1
        AND (scope IS NULL OR scope = '' OR scope LIKE '%' || :name || '%' OR scope LIKE '%' || :origin || '%')
        AND (excludeScope IS NULL OR excludeScope = ''
            OR (excludeScope NOT LIKE '%' || :name || '%' AND excludeScope NOT LIKE '%' || :origin || '%'))
        ORDER BY `order` ASC, id ASC"""
    )
    suspend fun findEnabledForBook(name: String, origin: String): List<RegexCastRule>

    @Query("SELECT DISTINCT `group` FROM regex_cast_rules WHERE TRIM(`group`) <> '' ORDER BY `group`")
    suspend fun allGroups(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: RegexCastRule): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<RegexCastRule>)

    @Update
    suspend fun update(rule: RegexCastRule)

    @Update
    suspend fun updateAll(rules: List<RegexCastRule>)

    @Delete
    suspend fun delete(rule: RegexCastRule)

    @Query("DELETE FROM regex_cast_rules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE regex_cast_rules SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)
}
