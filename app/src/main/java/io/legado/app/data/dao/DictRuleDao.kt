package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.legado.app.data.entities.DictRule
import kotlinx.coroutines.flow.Flow

@Dao
interface DictRuleDao {

    @get:Query("select * from dictRules order by sortNumber")
    val all: List<DictRule>

    @get:Query("select * from dictRules where enabled = 1 order by sortNumber")
    val enabled: List<DictRule>

    @Query("select * from dictRules order by sortNumber")
    fun flowAll(): Flow<List<DictRule>>

    @Query("select * from dictRules where name LIKE '%' || :key || '%' order by sortNumber")
    fun flowSearch(key: String): Flow<List<DictRule>>

    @Query("select * from dictRules where name = :name")
    fun getByName(name: String): DictRule?

    @Query("SELECT * FROM dictRules WHERE name IN (:names)")
    fun getByNames(names: Set<String>): List<DictRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg dictRule: DictRule)

    @Update
    fun update(vararg dictRule: DictRule)

    @Delete
    fun delete(vararg dictRule: DictRule)

    @Query("UPDATE dictRules SET enabled = :enabled WHERE name IN (:names)")
    suspend fun updateEnabled(names: Set<String>, enabled: Boolean)

    @Query("DELETE FROM dictRules WHERE name IN (:names)")
    suspend fun deleteByIds(names: Set<String>)

}
