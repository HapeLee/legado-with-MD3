package io.legado.app.data.dao

import androidx.room.*
import io.legado.app.data.entities.KeyboardAssist
import kotlinx.coroutines.flow.Flow

@Dao
interface KeyboardAssistsDao {

    @Query("select * from keyboardAssists order by serialNo")
    suspend fun all(): List<KeyboardAssist>

    @Query("select * from keyboardAssists where type = :type order by serialNo")
    suspend fun getByType(type: Int): List<KeyboardAssist>

    @get:Query("select * from keyboardAssists order by serialNo")
    val flowAll: Flow<List<KeyboardAssist>>

    @Query("select * from keyboardAssists where type = :type order by serialNo")
    fun flowByType(type: Int): Flow<List<KeyboardAssist>>

    @Query("select max(serialNo) from keyboardAssists order by serialNo")
    suspend fun maxSerialNo(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg keyboardAssist: KeyboardAssist)

    @Update
    suspend fun update(vararg keyboardAssist: KeyboardAssist)

    @Delete
    suspend fun delete(vararg keyboardAssist: KeyboardAssist)

}