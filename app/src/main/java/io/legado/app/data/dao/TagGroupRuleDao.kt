package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.legado.app.data.entities.TagGroupRule
import kotlinx.coroutines.flow.Flow

@Dao
interface TagGroupRuleDao {

    @Query("SELECT * FROM tag_group_rules ORDER BY `order`")
    fun flowAll(): Flow<List<TagGroupRule>>

    @Query("SELECT * FROM tag_group_rules ORDER BY `order` ASC")
    fun getAll(): List<TagGroupRule>

    @Query("SELECT * FROM tag_group_rules WHERE id = :id")
    suspend fun getById(id: Long): TagGroupRule?

    @Query("SELECT * FROM tag_group_rules WHERE id IN (:ids)")
    suspend fun getByIds(ids: Set<Long>): List<TagGroupRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg rule: TagGroupRule)

    @Update
    suspend fun update(vararg rule: TagGroupRule)

    @Delete
    suspend fun delete(vararg rule: TagGroupRule)

    @Query("SELECT * FROM tag_group_rules WHERE groupName = :groupName LIMIT 1")
    suspend fun getByGroupName(groupName: String): TagGroupRule?

    @get:Query("SELECT COALESCE(MAX(`order`), -1) FROM tag_group_rules")
    val maxOrder: Int

    @Query("DELETE FROM tag_group_rules")
    suspend fun deleteAll()

    @androidx.room.Transaction
    suspend fun replaceAll(rules: List<TagGroupRule>) {
        deleteAll()
        if (rules.isNotEmpty()) {
            insert(*rules.toTypedArray())
        }
    }
}
