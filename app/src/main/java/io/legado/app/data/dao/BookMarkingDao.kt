package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.BookMarking
import kotlinx.coroutines.flow.Flow

@Dao
interface BookMarkingDao {

    @get:Query(
        """
        select * from book_marks
        order by bookName collate localized, bookAuthor collate localized, chapterIndex, createdAt
    """
    )
    val all: List<BookMarking>

    /** 恢复备份用：按主键整批写入（同 id 覆盖，重复导入幂等）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg bookMarkings: BookMarking)

    /**
     * 按创建时的源（bookUrl）查：渲染只画当前源能对上正文的标记。
     * 与 bookmarks 不同，book_marks 认「书名+作者」跨源关联，列表见 [flowByBook]。
     */
    @Query(
        """
        select * from book_marks
        where bookUrl = :bookUrl
          and (:chapterIndex is null or chapterIndex is null or chapterIndex = :chapterIndex)
        order by createdAt
        """
    )
    fun getForChapterSync(bookUrl: String, chapterIndex: Int?): List<BookMarking>

    /** 按「书名+作者」查（含跨源全部标记），供保存去重/定位；chapterIndex 可空。 */
    @Query(
        """
        select * from book_marks
        where bookName = :bookName and bookAuthor = :bookAuthor
          and (:chapterIndex is null or chapterIndex is null or chapterIndex = :chapterIndex)
        order by createdAt
        """
    )
    suspend fun getByBook(
        bookName: String,
        bookAuthor: String,
        chapterIndex: Int?
    ): List<BookMarking>

    /** 按「书名+作者」流式订阅全部章节的标记，供目录 Sheet 笔记页跨源展示。 */
    @Query(
        """
        select * from book_marks
        where bookName = :bookName and bookAuthor = :bookAuthor
        order by chapterIndex, createdAt
        """
    )
    fun flowByBook(bookName: String, bookAuthor: String): Flow<List<BookMarking>>

    @Query("select * from book_marks where id = :id")
    suspend fun getById(id: String): BookMarking?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(bookMarking: BookMarking)

    @Query("update book_marks set enabled = :enabled, updatedAt = :updatedAt where id = :id")
    suspend fun setEnabled(
        id: String,
        enabled: Boolean,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("delete from book_marks where id = :id")
    suspend fun delete(id: String)
}
