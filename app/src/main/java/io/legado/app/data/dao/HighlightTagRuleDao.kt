package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.legado.app.data.entities.HighlightTagRule
import kotlinx.coroutines.flow.Flow

@Dao
interface HighlightTagRuleDao {

    @Query("SELECT * FROM highlight_tag_rules ORDER BY `order` ASC")
    fun flowAll(): Flow<List<HighlightTagRule>>

    @Query("SELECT * FROM highlight_tag_rules WHERE id = :id")
    suspend fun getById(id: Long): HighlightTagRule?

    @Query("SELECT * FROM highlight_tag_rules WHERE id IN (:ids)")
    suspend fun getByIds(ids: Set<Long>): List<HighlightTagRule>

    @Query("SELECT * FROM highlight_tag_rules WHERE enabled = 1 ORDER BY `order` ASC")
    suspend fun getEnabled(): List<HighlightTagRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg rules: HighlightTagRule)

    @Update
    suspend fun update(vararg rules: HighlightTagRule)

    @Delete
    suspend fun delete(vararg rules: HighlightTagRule)

    @Query("DELETE FROM highlight_tag_rules")
    suspend fun deleteAll()

    @Query("SELECT * FROM highlight_tag_rules ORDER BY `order` ASC")
    fun getAll(): List<HighlightTagRule>

    @androidx.room.Transaction
    suspend fun replaceAll(rules: List<HighlightTagRule>) {
        deleteAll()
        if (rules.isNotEmpty()) {
            insert(*rules.toTypedArray())
        }
    }
}
