package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.legado.app.constant.AppPattern
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.utils.cnCompare
import io.legado.app.utils.splitNotBlank
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map


@Dao
interface ReplaceRuleDao {

    @Query("SELECT * FROM replace_rules ORDER BY sortOrder ASC")
    fun flowAll(): Flow<List<ReplaceRule>>

    @Query("SELECT * FROM replace_rules ORDER BY sortOrder ASC")
    fun flowAllAsc(): Flow<List<ReplaceRule>>

    @Query("SELECT * FROM replace_rules ORDER BY sortOrder DESC")
    fun flowAllDesc(): Flow<List<ReplaceRule>>

    @Query("SELECT * FROM replace_rules ORDER BY name COLLATE NOCASE ASC")
    fun flowAllNameAsc(): Flow<List<ReplaceRule>>

    @Query("SELECT * FROM replace_rules ORDER BY name COLLATE NOCASE DESC")
    fun flowAllNameDesc(): Flow<List<ReplaceRule>>

    @Query("SELECT * FROM replace_rules WHERE `group` LIKE :key OR name LIKE :key OR pattern LIKE :key OR replacement LIKE :key OR scope LIKE :key ORDER BY sortOrder ASC")
    fun flowSearchAsc(key: String): Flow<List<ReplaceRule>>

    @Query("SELECT * FROM replace_rules WHERE `group` LIKE :key OR name LIKE :key OR pattern LIKE :key OR replacement LIKE :key OR scope LIKE :key ORDER BY sortOrder DESC")
    fun flowSearchDesc(key: String): Flow<List<ReplaceRule>>

    @Query("SELECT * FROM replace_rules WHERE `group` LIKE :key OR name LIKE :key OR pattern LIKE :key OR replacement LIKE :key OR scope LIKE :key ORDER BY name COLLATE NOCASE ASC")
    fun flowSearchNameAsc(key: String): Flow<List<ReplaceRule>>

    @Query("SELECT * FROM replace_rules WHERE `group` LIKE :key OR name LIKE :key OR pattern LIKE :key OR replacement LIKE :key OR scope LIKE :key ORDER BY name COLLATE NOCASE DESC")
    fun flowSearchNameDesc(key: String): Flow<List<ReplaceRule>>

    @Query("SELECT * FROM replace_rules WHERE `group` LIKE :key ORDER BY sortOrder ASC")
    fun flowGroupSearchAsc(key: String): Flow<List<ReplaceRule>>

    @Query("SELECT * FROM replace_rules WHERE `group` LIKE :key ORDER BY sortOrder DESC")
    fun flowGroupSearchDesc(key: String): Flow<List<ReplaceRule>>

    @Query("SELECT * FROM replace_rules WHERE `group` LIKE :key ORDER BY name COLLATE NOCASE ASC")
    fun flowGroupSearchNameAsc(key: String): Flow<List<ReplaceRule>>

    @Query("SELECT * FROM replace_rules WHERE `group` LIKE :key ORDER BY name COLLATE NOCASE DESC")
    fun flowGroupSearchNameDesc(key: String): Flow<List<ReplaceRule>>

    // === 未分组 ===
    @Query("SELECT * FROM replace_rules WHERE `group` IS NULL OR trim(`group`) = '' OR trim(`group`) LIKE '%未分组%' ORDER BY sortOrder ASC")
    fun flowNoGroupAsc(): Flow<List<ReplaceRule>>

    @Query("SELECT * FROM replace_rules WHERE `group` IS NULL OR trim(`group`) = '' OR trim(`group`) LIKE '%未分组%' ORDER BY sortOrder DESC")
    fun flowNoGroupDesc(): Flow<List<ReplaceRule>>

    @Query("SELECT * FROM replace_rules WHERE `group` IS NULL OR trim(`group`) = '' OR trim(`group`) LIKE '%未分组%' ORDER BY name COLLATE NOCASE ASC")
    fun flowNoGroupNameAsc(): Flow<List<ReplaceRule>>

    @Query("SELECT * FROM replace_rules WHERE `group` IS NULL OR trim(`group`) = '' OR trim(`group`) LIKE '%未分组%' ORDER BY name COLLATE NOCASE DESC")
    fun flowNoGroupNameDesc(): Flow<List<ReplaceRule>>

    @Query("SELECT * FROM replace_rules where `group` like :key or name like :key or pattern like :key or replacement like :key or scope like :key ORDER BY sortOrder ASC")
    fun flowSearch(key: String): Flow<List<ReplaceRule>>

    @Query("SELECT * FROM replace_rules where `group` like :key ORDER BY sortOrder ASC")
    fun flowGroupSearch(key: String): Flow<List<ReplaceRule>>

    @Query("select `group` from replace_rules where `group` is not null and `group` <> ''")
    fun flowGroupsUnProcessed(): Flow<List<String>>

    @Query("select * from replace_rules where `group` is null or trim(`group`) = '' or trim(`group`) like '%未分组%'")
    fun flowNoGroup(): Flow<List<ReplaceRule>>

    @get:Query("SELECT MIN(sortOrder) FROM replace_rules")
    val minOrder: Int

    @get:Query("SELECT MAX(sortOrder) FROM replace_rules")
    val maxOrder: Int

    @get:Query("SELECT * FROM replace_rules ORDER BY sortOrder ASC")
    val all: List<ReplaceRule>

    @get:Query("select distinct `group` from replace_rules where trim(`group`) <> ''")
    val allGroupsUnProcessed: List<String>

    @get:Query("SELECT * FROM replace_rules WHERE isEnabled = 1 ORDER BY sortOrder ASC")
    val allEnabled: List<ReplaceRule>

    @Query("SELECT * FROM replace_rules WHERE id = :id")
    fun findById(id: Long): ReplaceRule?

    @Query("SELECT * FROM replace_rules WHERE id in (:ids)")
    fun findByIds(vararg ids: Long): List<ReplaceRule>

    @Query(
        """SELECT * FROM replace_rules WHERE isEnabled = 1 and scopeContent = 1
        AND (scope LIKE '%' || :name || '%' or scope LIKE '%' || :origin || '%' or scope is null or scope = '')
        and (excludeScope is null or (excludeScope not LIKE '%' || :name || '%' and excludeScope not LIKE '%' || :origin || '%'))
        order by sortOrder"""
    )
    fun findEnabledByContentScope(name: String, origin: String): List<ReplaceRule>

    @Query(
        """SELECT * FROM replace_rules WHERE isEnabled = 1 and scopeTitle = 1
        AND (scope LIKE '%' || :name || '%' or scope LIKE '%' || :origin || '%' or scope is null or scope = '')
        and (excludeScope is null or (excludeScope not LIKE '%' || :name || '%' and excludeScope not LIKE '%' || :origin || '%'))
        order by sortOrder"""
    )
    fun findEnabledByTitleScope(name: String, origin: String): List<ReplaceRule>

    @Query("UPDATE replace_rules SET isEnabled = :enabled WHERE id = :id")
    suspend fun updateEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE replace_rules SET isEnabled = :enabled WHERE id IN (:ids)")
    suspend fun updateEnabled(ids: List<Long>, enabled: Boolean)

    @Query("DELETE FROM replace_rules WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("UPDATE replace_rules SET sortOrder = :order WHERE id = :id")
    suspend fun updateOrder(id: Long, order: Int)

    @Query("SELECT * FROM replace_rules WHERE id IN (:ids)")
    fun getByIds(ids: Set<Long>): List<ReplaceRule>

    @Query("UPDATE replace_rules SET `group` = NULL WHERE `group` IN (:groups)")
    suspend fun clearGroups(groups: List<String>)

    @Query("select * from replace_rules where `group` like '%' || :group || '%'")
    fun getByGroup(group: String): List<ReplaceRule>

    @get:Query("select * from replace_rules where `group` is null or `group` = ''")
    val noGroup: List<ReplaceRule>

    @get:Query("SELECT COUNT(*) - SUM(isEnabled) FROM replace_rules")
    val summary: Int

    @Query("UPDATE replace_rules SET isEnabled = :enable")
    fun enableAll(enable: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg replaceRule: ReplaceRule): List<Long>

    @Update
    fun update(vararg replaceRules: ReplaceRule)

    @Delete
    fun delete(vararg replaceRules: ReplaceRule)

    @Transaction
    suspend fun updateAllOrders(rules: List<ReplaceRule>) {
        rules.forEachIndexed { index, rule ->
            updateOrder(rule.id, index + 1)
        }
    }

    private fun dealGroups(list: List<String>): List<String> {
        val groups = linkedSetOf<String>()
        list.forEach {
            it.splitNotBlank(AppPattern.splitGroupRegex).forEach { group ->
                groups.add(group)
            }
        }
        return groups.sortedWith { o1, o2 ->
            o1.cnCompare(o2)
        }
    }

    fun allGroups(): List<String> = dealGroups(allGroupsUnProcessed)

    fun flowGroups(): Flow<List<String>> {
        return flowGroupsUnProcessed().map { list ->
            dealGroups(list)
        }.flowOn(IO)
    }
}
