package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.legado.app.data.entities.ReadRecordShow
import io.legado.app.data.entities.readRecord.ReadRecord
import io.legado.app.data.entities.readRecord.ReadRecordDetail
import io.legado.app.data.entities.readRecord.ReadRecordSession
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadRecordDao {

    @get:Query("select * from readRecord")
    val all: List<ReadRecord>

    @get:Query(
        """
        select bookName, sum(readTime) as readTime, max(lastRead) as lastRead 
        from readRecord 
        group by bookName 
        order by bookName collate localized"""
    )
    val allShow: List<ReadRecordShow>

    @Query("SELECT sum(readTime) FROM readRecord")
    fun getTotalReadTime(): Flow<Long?>

    @Query(
        """
        select bookName, sum(readTime) as readTime, max(lastRead) as lastRead 
        from readRecord 
        where bookName like '%' || :searchKey || '%'
        group by bookName 
        order by bookName collate localized"""
    )
    fun search(searchKey: String): List<ReadRecordShow>

    @Query("select sum(readTime) from readRecord where bookName = :bookName")
    fun getReadTime(bookName: String): Long?

    @Query("select readTime from readRecord where deviceId = :deviceId and bookName = :bookName")
    fun getReadTime(deviceId: String, bookName: String): Long?

    @Query("SELECT * FROM readRecord WHERE deviceId = :deviceId AND bookName = :bookName")
    suspend fun getReadRecord(deviceId: String, bookName: String): ReadRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg readRecord: ReadRecord)

    @Update
    suspend fun update(vararg record: ReadRecord)

    @Delete
    fun delete(vararg record: ReadRecord)

    @Query("delete from readRecord")
    fun clear()

    @Query("delete from readRecord where bookName = :bookName")
    fun deleteByName(bookName: String)

    /**
     * 插入或更新每日聚合统计记录。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDetail(detail: ReadRecordDetail)

    /**
     * 获取某一本书某一天的详细统计
     * @param date 日期, 推荐格式: YYYY-MM-DD
     */
    @Query("SELECT * FROM readRecordDetail WHERE deviceId = :deviceId AND bookName = :bookName AND date = :date")
    suspend fun getDetail(deviceId: String, bookName: String, date: String): ReadRecordDetail?

    /**
     * 查询所有发生过阅读的日期（用于日历标记）
     */
    @Query("SELECT DISTINCT date FROM readRecordDetail WHERE deviceId = :deviceId ORDER BY date DESC")
    fun getAllReadDates(deviceId: String): List<String>

    /**
     * 获取某一天所有书籍的详细统计 (用于日历页面总览)
     */
    @Query("SELECT * FROM readRecordDetail WHERE deviceId = :deviceId AND date = :date")
    suspend fun getDetailsByDate(deviceId: String, date: String): List<ReadRecordDetail>

    // 清除每天的统计记录
    @Query("DELETE FROM readRecordDetail WHERE bookName = :bookName")
    fun deleteDetailByName(bookName: String)

    /**
     * 获取指定书籍的最后一条阅读会话
     * 用于判断是否可以合并
     */
    @Query("SELECT * FROM readRecordSession WHERE bookName = :bookName ORDER BY endTime DESC LIMIT 1")
    suspend fun getLatestSessionByBook(bookName: String): ReadRecordSession?

    /**
     * 更新现有的会话
     */
    @Update
    suspend fun updateSession(session: ReadRecordSession)

    /**
     * 插入阅读会话记录。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSession(session: ReadRecordSession)

    /** 获取所有 ReadRecord，按最后阅读时间倒序排列 */
    @Query("SELECT * FROM readRecord ORDER BY lastRead DESC")
    fun getAllReadRecordsSortedByLastRead(): Flow<List<ReadRecord>>

    /** 搜索 ReadRecord，按最后阅读时间倒序排列 */
    @Query("SELECT * FROM readRecord WHERE bookName LIKE '%' || :query || '%' ORDER BY lastRead DESC")
    fun searchReadRecordsByLastRead(query: String): Flow<List<ReadRecord>>

    /**
     * 获取某一天某一本书的所有会话记录
     */
    @Query("""
        SELECT * FROM readRecordSession 
        WHERE deviceId = :deviceId 
        AND bookName = :bookName 
        AND STRFTIME('%Y-%m-%d', datetime(startTime/1000, 'unixepoch')) = :date 
        ORDER BY startTime ASC
    """)
    suspend fun getSessionsByBookAndDate(deviceId: String, bookName: String, date: String): List<ReadRecordSession>

    /**
     * 获取某一天所有书籍的会话记录
     */
    @Query("""
    SELECT * FROM readRecordSession 
    WHERE deviceId = :deviceId 
    AND STRFTIME('%Y-%m-%d', datetime(startTime/1000, 'unixepoch', 'localtime')) = :date 
    ORDER BY startTime ASC
    """)
    suspend fun getSessionsByDate(deviceId: String, date: String): List<ReadRecordSession>

    @Query("SELECT * FROM readRecordDetail WHERE deviceId = :deviceId AND date = :date AND bookName LIKE '%' || :query || '%'")
    suspend fun searchDetailsByDate(deviceId: String, date: String, query: String): List<ReadRecordDetail>

    // 清除会话记录
    @Query("DELETE FROM readRecordSession WHERE bookName = :bookName")
    fun deleteSessionByName(bookName: String)

    @Query("SELECT * FROM readRecordDetail ORDER BY date DESC, lastReadTime DESC")
    fun getAllDetails(): Flow<List<ReadRecordDetail>>

    @Query("SELECT * FROM readRecordDetail WHERE bookName LIKE '%' || :query || '%' ORDER BY date DESC, lastReadTime DESC")
    fun searchDetails(query: String): Flow<List<ReadRecordDetail>>

    @Query("SELECT * FROM readRecordSession WHERE deviceId = :deviceId ORDER BY startTime ASC")
    fun getAllSessions(deviceId: String): Flow<List<ReadRecordSession>>

    @Delete
    suspend fun deleteDetail(detail: ReadRecordDetail)
}