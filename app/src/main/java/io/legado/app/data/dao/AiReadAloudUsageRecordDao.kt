package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import io.legado.app.data.entities.AiReadAloudUsageRecord

@Dao
interface AiReadAloudUsageRecordDao {

    @Query(
        """
        SELECT * FROM ai_read_aloud_usage_records
        WHERE (:type = '' OR type = :type)
          AND (:bookUrl = '' OR bookUrl = :bookUrl)
        ORDER BY createdAt DESC, id DESC
        LIMIT :limit
        """
    )
    fun list(type: String = "", bookUrl: String = "", limit: Int = 1000): List<AiReadAloudUsageRecord>

    @Query("SELECT * FROM ai_read_aloud_usage_records WHERE id IN (:ids)")
    fun records(ids: List<Long>): List<AiReadAloudUsageRecord>

    @Insert
    fun insert(record: AiReadAloudUsageRecord): Long

    @Query("DELETE FROM ai_read_aloud_usage_records WHERE id = :id")
    fun delete(id: Long)

    @Query("DELETE FROM ai_read_aloud_usage_records WHERE id IN (:ids)")
    fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM ai_read_aloud_usage_records")
    fun clear()
}
