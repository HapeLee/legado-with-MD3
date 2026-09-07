package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.legado.app.data.entities.HttpTTS
import kotlinx.coroutines.flow.Flow

@Dao
interface HttpTTSDao {

    @Query("select * from httpTTS order by name")
    suspend fun all(): List<HttpTTS>

    @Query("select * from httpTTS order by name")
    fun flowAll(): Flow<List<HttpTTS>>

    @Query("select count(*) from httpTTS")
    suspend fun count(): Int

    @Query("select * from httpTTS where id = :id")
    suspend fun get(id: Long): HttpTTS?

    @Query("select name from httpTTS where id = :id")
    suspend fun getName(id: Long): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg httpTTS: HttpTTS)

    @Delete
    suspend fun delete(vararg httpTTS: HttpTTS)

    @Update
    suspend fun update(vararg httpTTS: HttpTTS)

    @Query("delete from httpTTS where id < 0")
    suspend fun deleteDefault()
}
