package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.ReadRecordDaily

@Dao
interface ReadRecordDailyDao {

    @get:Query("select * from readRecordDaily order by date desc")
    val allDesc: List<ReadRecordDaily>

    @get:Query("select count(*) from readRecordDaily")
    val count: Int

    @Query("select * from readRecordDaily where date = :date limit 1")
    fun get(date: String): ReadRecordDaily?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg record: ReadRecordDaily)

    @Query("delete from readRecordDaily where date = :date")
    fun delete(date: String)

    @Query("delete from readRecordDaily")
    fun clear()
}
