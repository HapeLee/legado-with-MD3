package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.legado.app.data.entities.HighlightRule

@Dao
interface HighlightRuleDao {

    @Query("SELECT * FROM highlightRules ORDER BY position ASC")
    suspend fun getAll(): List<HighlightRule>

    @Query("SELECT * FROM highlightRules WHERE enabled = 1 AND pattern != '' ORDER BY position ASC")
    suspend fun getEnabled(): List<HighlightRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<HighlightRule>)

    @Update
    suspend fun update(rule: HighlightRule)

    @Delete
    suspend fun delete(rule: HighlightRule)

    @Query("DELETE FROM highlightRules")
    suspend fun deleteAll()

    @Query("DELETE FROM highlightRules WHERE configName IS NULL")
    suspend fun deleteGlobal()

    @Query("SELECT COUNT(*) FROM highlightRules")
    suspend fun count(): Int

    @Transaction
    suspend fun replaceAll(rules: List<HighlightRule>) {
        deleteAll()
        insertAll(rules)
    }

    @Transaction
    suspend fun replaceGlobal(rules: List<HighlightRule>) {
        deleteGlobal()
        insertAll(rules)
    }
}
