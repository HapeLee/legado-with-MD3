package io.legado.app.data.dao

import androidx.room.*
import io.legado.app.data.entities.RssReadRecord

@Dao
interface RssReadRecordDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertRecord(vararg rssReadRecord: RssReadRecord)

    @Query("select * from rssReadRecords order by readTime desc")
    fun getRecords(): List<RssReadRecord>

    @Query("select * from rssReadRecords where origin = :origin order by readTime desc")
    fun getRecordsByOrigin(origin: String): List<RssReadRecord>

    @Query("select * from rssReadRecords where record = :record and origin = :origin")
    fun getRecord(record: String, origin: String): RssReadRecord?

    @Update
    fun update(vararg rssRecord: RssReadRecord)

    @get:Query("select count(1) from rssReadRecords")
    val countRecords: Int

    @Query("select count(1) from rssReadRecords where origin = :origin")
    fun countRecordsByOrigin(origin: String): Int

    @Query("delete from rssReadRecords")
    fun deleteAllRecord()

    @Query("delete from rssReadRecords where origin = :origin")
    fun deleteRecordsByOrigin(origin: String)

}
