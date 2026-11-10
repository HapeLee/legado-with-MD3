package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.model.BookChapterCacheInfo
import io.legado.app.data.model.ChapterWordCountUpdate
import kotlinx.coroutines.flow.Flow

@Dao
interface BookChapterDao {

    @Query("SELECT * FROM chapters where bookUrl = :bookUrl and title like '%'||:key||'%' order by `index`")
    fun search(bookUrl: String, key: String): List<BookChapter>

    @Query("SELECT * FROM chapters where bookUrl = :bookUrl and `index` >= :start and `index` <= :end and title like '%'||:key||'%' order by `index`")
    fun search(bookUrl: String, key: String, start: Int, end: Int): List<BookChapter>

    @Query("select * from chapters where bookUrl = :bookUrl order by `index`")
    fun getChapterList(bookUrl: String): List<BookChapter>

    /**
     * 缓存维护批量读取：一次 IN 查询覆盖一批书，替代逐本 5000 次 SQL。
     * 调用方必须分批（Room 的 IN 占位上限 999），见 Repository 的 SQL_BATCH。
     */
    @Query("select * from chapters where bookUrl in (:bookUrls) order by `index`")
    fun getChapterList(bookUrls: List<String>): List<BookChapter>

    @Query("select url, title, isVolume, `index` from chapters where bookUrl = :bookUrl order by `index`")
    fun getChapterCacheInfoList(bookUrl: String): List<BookChapterCacheInfo>

    @Query("select * from chapters where bookUrl = :bookUrl order by `index`")
    fun getChapterListFlow(bookUrl: String): Flow<List<BookChapter>>

    @Query("select * from chapters where bookUrl = :bookUrl and `index` >= :start and `index` <= :end order by `index`")
    fun getChapterList(bookUrl: String, start: Int, end: Int): List<BookChapter>

    @Query("select * from chapters where bookUrl = :bookUrl and `index` = :index")
    fun getChapter(bookUrl: String, index: Int): BookChapter?

    @Query("select * from chapters where bookUrl = :bookUrl and `title` = :title")
    fun getChapter(bookUrl: String, title: String): BookChapter?

    @Query("select count(url) from chapters where bookUrl = :bookUrl")
    fun getChapterCount(bookUrl: String): Int

    @Query("select count(url) from chapters where bookUrl = :bookUrl and isVolume = 1")
    fun getVolumeCount(bookUrl: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg bookChapter: BookChapter)

    @Update
    fun update(vararg bookChapter: BookChapter)

    @Query("delete from chapters where bookUrl = :bookUrl")
    fun delByBook(bookUrl: String)

    @Query("update chapters set wordCount = :wordCount where bookUrl = :bookUrl and url = :url")
    fun upWordCount(bookUrl: String, url: String, wordCount: String)

    /**
     * 批量回写章节字数，整批包在一个事务里。
     *
     * 逐条调用 [upWordCount] 时 Room 为每条 SQL 各开一个事务；几十万章会产生
     * 几十万次事务提交（fsync），是字数重算阶段的主导成本。调用方分批调用此方法即可
     * 既享受批量提交、又避免单个超长事务锁库。
     */
    @Transaction
    fun updateWordCounts(updates: List<ChapterWordCountUpdate>) {
        updates.forEach { upWordCount(it.bookUrl, it.url, it.wordCount) }
    }

    @Query("update chapters set start = start + :diff, end = end + :diff where bookUrl = :bookUrl and `index` > :index")
    fun updateOffsets(bookUrl: String, index: Int, diff: Long)

    /**
     * 根据书籍的唯一标识 bookUrl 和章节索引 index 查找章节标题。
     */
    @Query("""
        SELECT title FROM chapters 
        WHERE bookUrl = :bookUrl 
        AND `index` = :chapterIndex
    """)
    suspend fun getChapterTitleByUrlAndIndex(bookUrl: String, chapterIndex: Int): String?
}
