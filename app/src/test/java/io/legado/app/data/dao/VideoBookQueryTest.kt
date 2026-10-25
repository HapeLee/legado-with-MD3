package io.legado.app.data.dao

import androidx.room.Room
import io.legado.app.constant.BookType
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [35])
class VideoBookQueryTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun flowVideoReturnsOnlyVideoBooks() = runBlocking {
        db.bookDao.insert(Book(bookUrl = "v1", name = "视频书", type = BookType.video))
        db.bookDao.insert(Book(bookUrl = "t1", name = "文本书", type = BookType.text))

        val result = db.bookDao.flowVideo().first()

        assertEquals(listOf("v1"), result.map { it.bookUrl })
    }

    @Test
    fun flowBookShelfNetNoGroupExcludesVideoBooks() = runBlocking {
        db.bookDao.insert(Book(bookUrl = "v2", name = "视频书", type = BookType.video))
        db.bookDao.insert(Book(bookUrl = "t2", name = "文本书", type = BookType.text))

        val result = db.bookDao.flowBookShelfNetNoGroup().first()

        assertEquals(listOf("t2"), result.map { it.bookUrl })
    }

    @Test
    fun flowGroupPreviewVideoReturnsOnlyVideoBooks() = runBlocking {
        db.bookDao.insert(Book(bookUrl = "v3", name = "视频书", type = BookType.video))
        db.bookDao.insert(Book(bookUrl = "u1", name = "分组书", type = BookType.text, group = 1))

        val result = db.bookDao.flowGroupPreview(BookGroup.IdVideo).first()

        assertEquals(listOf("v3"), result.map { it.bookUrl })
    }
}
