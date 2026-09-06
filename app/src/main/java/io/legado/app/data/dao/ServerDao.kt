package io.legado.app.data.dao

import androidx.room.*
import io.legado.app.data.entities.Server
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerDao {

    @Query("select * from servers order by sortNumber")
    fun observeAll(): Flow<List<Server>>

    @Query("select * from servers order by sortNumber")
    suspend fun all(): List<Server>

    @Query("select * from servers where id = :id")
    suspend fun get(id: Long): Server?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg server: Server)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun update(vararg server: Server)

    @Delete
    suspend fun delete(vararg server: Server)

    @Query("delete from servers where id = :id")
    suspend fun delete(id: Long)

    @Query("delete from servers where id < 0")
    suspend fun deleteDefault()
}