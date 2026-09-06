package io.legado.app.data.dao

import androidx.room.*
import io.legado.app.data.entities.RssReadRecord

@Dao
interface RssReadRecordDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecord(vararg rssReadRecord: RssReadRecord)

    @Query("select * from rssReadRecords order by readTime desc")
    suspend fun getRecords(): List<RssReadRecord>

    @Query("select count(1) from rssReadRecords")
    suspend fun countRecords(): Int

    @Query("delete from rssReadRecords")
    suspend fun deleteAllRecord()

}
