package io.legado.app.smoke.roomprobe

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Minimal DAO with suspend + Flow functions — the only styles Room 2.8.4 KMP
 * allows on non-Android targets.
 */
@Dao
interface ProbeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ProbeEntity): Long

    @Query("SELECT * FROM probe ORDER BY id ASC")
    suspend fun getAll(): List<ProbeEntity>

    @Query("SELECT * FROM probe ORDER BY id ASC")
    fun observeAll(): Flow<List<ProbeEntity>>

    @Query("SELECT COUNT(*) FROM probe")
    suspend fun count(): Int

    @Query("DELETE FROM probe WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}
