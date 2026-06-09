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
    fun getAll(): List<HighlightRule>

    @Query("SELECT * FROM highlightRules WHERE enabled = 1 AND pattern != '' ORDER BY position ASC")
    fun getEnabled(): List<HighlightRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(rules: List<HighlightRule>)

    @Update
    fun update(rule: HighlightRule)

    @Delete
    fun delete(rule: HighlightRule)

    @Query("DELETE FROM highlightRules")
    fun deleteAll()

    @Query("DELETE FROM highlightRules WHERE configName IS NULL")
    fun deleteGlobal()

    @Query("SELECT COUNT(*) FROM highlightRules")
    fun count(): Int

    @Transaction
    fun replaceAll(rules: List<HighlightRule>) {
        deleteAll()
        insertAll(rules)
    }

    @Transaction
    fun replaceGlobal(rules: List<HighlightRule>) {
        deleteGlobal()
        insertAll(rules)
    }
}
