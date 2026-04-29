package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.BookSource
import io.legado.app.domain.model.CacheableBook
import io.legado.app.help.book.isNotShelf
import io.legado.app.ui.main.bookshelf.BookShelfItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Dao
interface BookDao {

    fun flowByGroup(groupId: Long): Flow<List<Book>> {
        return when (groupId) {
            BookGroup.IdRoot -> flowRoot()
            BookGroup.IdAll -> flowAll()
            BookGroup.IdLocal -> flowLocal()
            BookGroup.IdAudio -> flowAudio()
            BookGroup.IdNetNone -> flowNetNoGroup()
            BookGroup.IdLocalNone -> flowLocalNoGroup()
            BookGroup.IdManga -> flowManga()
            BookGroup.IdText -> flowText()
            BookGroup.IdError -> flowUpdateError()
            BookGroup.IdUnread -> flowUnread()
            BookGroup.IdReading -> flowReading()
            BookGroup.IdReadFinished -> flowReadFinished()
            else -> flowByUserGroup(groupId)
        }.map { list ->
            list.filterNot { it.isNotShelf }
        }
    }

    fun flowBookShelfByGroup(groupId: Long): Flow<List<BookShelfItem>> {
        return when (groupId) {
            BookGroup.IdRoot -> flowBookShelfRoot()
            BookGroup.IdAll -> flowBookShelf()
            BookGroup.IdLocal -> flowBookShelfLocal()
            BookGroup.IdAudio -> flowBookShelfAudio()
            BookGroup.IdNetNone -> flowBookShelfNetNoGroup()
            BookGroup.IdLocalNone -> flowBookShelfLocalNoGroup()
            BookGroup.IdManga -> flowBookShelfManga()
            BookGroup.IdText -> flowBookShelfText()
            BookGroup.IdError -> flowBookShelfUpdateError()
            BookGroup.IdUnread -> flowBookShelfUnread()
            BookGroup.IdReading -> flowBookShelfReading()
            BookGroup.IdReadFinished -> flowBookShelfReadFinished()
            else -> flowBookShelfByUserGroup(groupId)
        }.map { list ->
            list.filterNot { it.isNotShelf }
        }
    }

    @Query(
        """
        select * from books where type & ${BookType.text} > 0
        and type & ${BookType.local} = 0
        and ((SELECT sum(groupId) FROM book_groups where groupId > 0) & `group`) = 0
        and (select show from book_groups where groupId = ${BookGroup.IdNetNone}) != 1
        """
    )
    fun flowRoot(): Flow<List<Book>>

    @Query(
        """
        SELECT 
            bookUrl,
            name,
            author,
            originName,
            coverUrl,
            customCoverUrl,
            durChapterTitle,
            durChapterTime,
            durChapterPos,
            latestChapterTitle,
            latestChapterTime,
            lastCheckCount,
            totalChapterNum,
            durChapterIndex,
            type,
            `group`,
            `order`,
            canUpdate
        FROM books 
        where type & ${BookType.text} > 0
        and type & ${BookType.local} = 0
        and ((SELECT sum(groupId) FROM book_groups where groupId > 0) & `group`) = 0
        and (select show from book_groups where groupId = ${BookGroup.IdNetNone}) != 1
        """
    )
    fun flowBookShelfRoot(): Flow<List<BookShelfItem>>

    @Query("SELECT * FROM books order by durChapterTime desc")
    fun flowAll(): Flow<List<Book>>

    @Query(
        """
    SELECT 
        bookUrl,
        name,
        author,
        originName,
        coverUrl,
        customCoverUrl,
        durChapterTitle,
        durChapterTime,
        durChapterPos,
        latestChapterTitle,
        latestChapterTime,
        lastCheckCount,
        totalChapterNum,
        durChapterIndex,
        type,
        `group`,
        `order`,
        canUpdate
    FROM books
    ORDER BY durChapterTime DESC
"""
    )
    fun flowBookShelf(): Flow<List<BookShelfItem>>

    @Query("SELECT * FROM books WHERE type & ${BookType.audio} > 0")
    fun flowAudio(): Flow<List<Book>>

    @Query(
        """
        SELECT 
            bookUrl,
            name,
            author,
            originName,
            coverUrl,
            customCoverUrl,
            durChapterTitle,
            durChapterTime,
            durChapterPos,
            latestChapterTitle,
            latestChapterTime,
            lastCheckCount,
            totalChapterNum,
            durChapterIndex,
            type,
            `group`,
            `order`,
            canUpdate
        FROM books 
        WHERE type & ${BookType.audio} > 0
        """
    )
    fun flowBookShelfAudio(): Flow<List<BookShelfItem>>

    @Query("SELECT * FROM books WHERE type & ${BookType.local} > 0")
    fun flowLocal(): Flow<List<Book>>

    @Query(
        """
        SELECT 
            bookUrl,
            name,
            author,
            originName,
            coverUrl,
            customCoverUrl,
            durChapterTitle,
            durChapterTime,
            durChapterPos,
            latestChapterTitle,
            latestChapterTime,
            lastCheckCount,
            totalChapterNum,
            durChapterIndex,
            type,
            `group`,
            `order`,
            canUpdate
        FROM books 
        WHERE type & ${BookType.local} > 0
        """
    )
    fun flowBookShelfLocal(): Flow<List<BookShelfItem>>

    @Query(
        """
        select * from books where type & ${BookType.audio} = 0 and type & ${BookType.local} = 0
        and ((SELECT sum(groupId) FROM book_groups where groupId > 0) & `group`) = 0
        """
    )
    fun flowNetNoGroup(): Flow<List<Book>>

    @Query(
        """
        SELECT 
            bookUrl,
            name,
            author,
            originName,
            coverUrl,
            customCoverUrl,
            durChapterTitle,
            durChapterTime,
            durChapterPos,
            latestChapterTitle,
            latestChapterTime,
            lastCheckCount,
            totalChapterNum,
            durChapterIndex,
            type,
            `group`,
            `order`,
            canUpdate
        FROM books 
        where type & ${BookType.audio} = 0 and type & ${BookType.local} = 0
        and ((SELECT sum(groupId) FROM book_groups where groupId > 0) & `group`) = 0
        """
    )
    fun flowBookShelfNetNoGroup(): Flow<List<BookShelfItem>>

    @Query(
        """
        select * from books where type & ${BookType.local} > 0
        and ((SELECT sum(groupId) FROM book_groups where groupId > 0) & `group`) = 0
        """
    )
    fun flowLocalNoGroup(): Flow<List<Book>>

    @Query(
        """
        SELECT 
            bookUrl,
            name,
            author,
            originName,
            coverUrl,
            customCoverUrl,
            durChapterTitle,
            durChapterTime,
            durChapterPos,
            latestChapterTitle,
            latestChapterTime,
            lastCheckCount,
            totalChapterNum,
            durChapterIndex,
            type,
            `group`,
            `order`,
            canUpdate
        FROM books 
        where type & ${BookType.local} > 0
        and ((SELECT sum(groupId) FROM book_groups where groupId > 0) & `group`) = 0
        """
    )
    fun flowBookShelfLocalNoGroup(): Flow<List<BookShelfItem>>

    @Query("SELECT * FROM books WHERE (`group` & :group) > 0")
    fun flowByUserGroup(group: Long): Flow<List<Book>>

    @Query(
        """
        SELECT 
            bookUrl,
            name,
            author,
            originName,
            coverUrl,
            customCoverUrl,
            durChapterTitle,
            durChapterTime,
            durChapterPos,
            latestChapterTitle,
            latestChapterTime,
            lastCheckCount,
            totalChapterNum,
            durChapterIndex,
            type,
            `group`,
            `order`,
            canUpdate
        FROM books 
        WHERE (`group` & :group) > 0
        """
    )
    fun flowBookShelfByUserGroup(group: Long): Flow<List<BookShelfItem>>

    @Query(
        "SELECT * FROM books WHERE name like '%'||:key||'%' or author like '%'||:key||'%' or originName like '%'||:key||'%'"
    )
    fun flowSearch(key: String): Flow<List<Book>>

    @Query(
        """
        SELECT 
            bookUrl,
            name,
            author,
            originName,
            coverUrl,
            customCoverUrl,
            durChapterTitle,
            durChapterTime,
            durChapterPos,
            latestChapterTitle,
            latestChapterTime,
            lastCheckCount,
            totalChapterNum,
            durChapterIndex,
            type,
            `group`,
            `order`,
            canUpdate
        FROM books 
        WHERE name like '%'||:key||'%' or author like '%'||:key||'%' or originName like '%'||:key||'%'
        """
    )
    fun flowBookShelfSearch(key: String): Flow<List<BookShelfItem>>

    @Query("SELECT * FROM books where type & ${BookType.updateError} > 0 order by durChapterTime desc")
    fun flowUpdateError(): Flow<List<Book>>

    @Query(
        """
        SELECT 
            bookUrl,
            name,
            author,
            originName,
            coverUrl,
            customCoverUrl,
            durChapterTitle,
            durChapterTime,
            durChapterPos,
            latestChapterTitle,
            latestChapterTime,
            lastCheckCount,
            totalChapterNum,
            durChapterIndex,
            type,
            `group`,
            `order`,
            canUpdate
        FROM books 
        where type & ${BookType.updateError} > 0 
        order by durChapterTime desc
        """
    )
    fun flowBookShelfUpdateError(): Flow<List<BookShelfItem>>

    @Query("""SELECT * FROM books WHERE durChapterIndex = 0 AND durChapterPos = 0""")
    fun flowUnread(): Flow<List<Book>>

    @Query(
        """
        SELECT 
            bookUrl,
            name,
            author,
            originName,
            coverUrl,
            customCoverUrl,
            durChapterTitle,
            durChapterTime,
            durChapterPos,
            latestChapterTitle,
            latestChapterTime,
            lastCheckCount,
            totalChapterNum,
            durChapterIndex,
            type,
            `group`,
            `order`,
            canUpdate
        FROM books 
        WHERE durChapterIndex = 0 AND durChapterPos = 0
        """
    )
    fun flowBookShelfUnread(): Flow<List<BookShelfItem>>

    @Query("""SELECT * FROM books WHERE totalChapterNum > 0 AND durChapterIndex >= totalChapterNum - 1""")
    fun flowReadFinished(): Flow<List<Book>>

    @Query(
        """
        SELECT 
            bookUrl,
            name,
            author,
            originName,
            coverUrl,
            customCoverUrl,
            durChapterTitle,
            durChapterTime,
            durChapterPos,
            latestChapterTitle,
            latestChapterTime,
            lastCheckCount,
            totalChapterNum,
            durChapterIndex,
            type,
            `group`,
            `order`,
            canUpdate
        FROM books 
        WHERE totalChapterNum > 0 AND durChapterIndex >= totalChapterNum - 1
        """
    )
    fun flowBookShelfReadFinished(): Flow<List<BookShelfItem>>

    @Query("""SELECT * FROM books WHERE totalChapterNum > 0 AND durChapterIndex > 0 AND durChapterIndex < totalChapterNum - 1""")
    fun flowReading(): Flow<List<Book>>

    @Query(
        """
        SELECT 
            bookUrl,
            name,
            author,
            originName,
            coverUrl,
            customCoverUrl,
            durChapterTitle,
            durChapterTime,
            durChapterPos,
            latestChapterTitle,
            latestChapterTime,
            lastCheckCount,
            totalChapterNum,
            durChapterIndex,
            type,
            `group`,
            `order`,
            canUpdate
        FROM books 
        WHERE totalChapterNum > 0 AND durChapterIndex > 0 AND durChapterIndex < totalChapterNum - 1
        """
    )
    fun flowBookShelfReading(): Flow<List<BookShelfItem>>

    @Query("SELECT * FROM books WHERE type & ${BookType.image} > 0")
    fun flowManga(): Flow<List<Book>>

    @Query(
        """
        SELECT 
            bookUrl,
            name,
            author,
            originName,
            coverUrl,
            customCoverUrl,
            durChapterTitle,
            durChapterTime,
            durChapterPos,
            latestChapterTitle,
            latestChapterTime,
            lastCheckCount,
            totalChapterNum,
            durChapterIndex,
            type,
            `group`,
            `order`,
            canUpdate
        FROM books 
        WHERE type & ${BookType.image} > 0
        """
    )
    fun flowBookShelfManga(): Flow<List<BookShelfItem>>

    @Query("SELECT * FROM books WHERE type & ${BookType.text} > 0")
    fun flowText(): Flow<List<Book>>

    @Query(
        """
        SELECT 
            bookUrl,
            name,
            author,
            originName,
            coverUrl,
            customCoverUrl,
            durChapterTitle,
            durChapterTime,
            durChapterPos,
            latestChapterTitle,
            latestChapterTime,
            lastCheckCount,
            totalChapterNum,
            durChapterIndex,
            type,
            `group`,
            `order`,
            canUpdate
        FROM books 
        WHERE type & ${BookType.text} > 0
        """
    )
    fun flowBookShelfText(): Flow<List<BookShelfItem>>

    @Query("SELECT * FROM books WHERE (`group` & :group) > 0")
    fun getBooksByGroup(group: Long): List<Book>

    @Query("SELECT * FROM books WHERE `name` in (:names)")
    fun findByName(vararg names: String): List<Book>

    @Query("select * from books where originName = :fileName")
    fun getBookByFileName(fileName: String): Book?

    @Query("SELECT * FROM books WHERE bookUrl = :bookUrl")
    fun getBook(bookUrl: String): Book?

    @Query(
        """
        SELECT
            bookUrl,
            type & ${BookType.local} > 0 AS isLocal,
            type & ${BookType.audio} > 0 AS isAudio,
            durChapterIndex,
            totalChapterNum - 1 AS lastChapterIndex
        FROM books
        WHERE bookUrl IN (:bookUrls)
        """
    )
    fun getCacheableBooks(bookUrls: Set<String>): List<CacheableBook>

    @Query("SELECT * FROM books WHERE bookUrl = :bookUrl")
    fun flowGetBook(bookUrl: String): Flow<Book?>

    @Query("SELECT * FROM books WHERE name = :name and author = :author")
    fun getBook(name: String, author: String): Book?

    @Query("""select distinct bs.* from books, book_sources bs 
        where origin == bookSourceUrl and origin not like '${BookType.localTag}%' 
        and origin not like '${BookType.webDavTag}%'""")
    fun getAllUseBookSource(): List<BookSource>

    @Query("SELECT * FROM books WHERE name = :name and origin = :origin")
    fun getBookByOrigin(name: String, origin: String): Book?

    @get:Query("select count(bookUrl) from books where (SELECT sum(groupId) FROM book_groups)")
    val noGroupSize: Int

    @get:Query("SELECT * FROM books where type & ${BookType.local} = 0")
    val webBooks: List<Book>

    @get:Query("SELECT * FROM books where type & ${BookType.local} = 0 and canUpdate = 1")
    val hasUpdateBooks: List<Book>

    @get:Query("SELECT * FROM books")
    val all: List<Book>

    @Query("SELECT * FROM books where type & :type > 0 and type & ${BookType.local} = 0")
    fun getByTypeOnLine(type: Int): List<Book>

    @get:Query("SELECT * FROM books where type & ${BookType.text} > 0 ORDER BY durChapterTime DESC limit 1")
    val lastReadBook: Book?

    @get:Query("SELECT bookUrl FROM books")
    val allBookUrls: List<String>

    @get:Query("SELECT COUNT(*) FROM books")
    val allBookCount: Int

    @get:Query("select min(`order`) from books")
    val minOrder: Int

    @get:Query("select max(`order`) from books")
    val maxOrder: Int

    @Query("select exists(select 1 from books where bookUrl = :bookUrl)")
    fun has(bookUrl: String): Boolean

    @Query("select exists(select 1 from books where name = :name and author = :author)")
    fun has(name: String, author: String): Boolean

    @Query(
        """select exists(select 1 from books where type & ${BookType.local} > 0 
        and (originName = :fileName or (origin != '${BookType.localTag}' and origin like '%' || :fileName)))"""
    )
    fun hasFile(fileName: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg book: Book)

    @Update
    fun update(vararg book: Book)

    @Delete
    fun delete(vararg book: Book)

    @Transaction
    fun replace(oldBook: Book, newBook: Book) {
        delete(oldBook)
        insert(newBook)
    }

    @Query("update books set durChapterPos = :pos where bookUrl = :bookUrl")
    fun upProgress(bookUrl: String, pos: Int)

    @Query("update books set `group` = :newGroupId where `group` = :oldGroupId")
    fun upGroup(oldGroupId: Long, newGroupId: Long)

    @Query("update books set `group` = `group` - :group where `group` & :group > 0")
    fun removeGroup(group: Long)

    @Query("delete from books where type & ${BookType.notShelf} > 0")
    fun deleteNotShelfBook()
}
