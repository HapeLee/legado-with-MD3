package io.legado.app.domain.usecase

import android.app.Application
import androidx.room.Room
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.readRecord.ReadRecordSession
import io.legado.app.data.repository.BookRepository
import io.legado.app.data.repository.BookSourceRepository
import io.legado.app.data.repository.ReadRecordRepository
import io.legado.app.data.repository.SettingsRepository
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.gateway.ReadSettingsGateway
import io.legado.app.domain.model.settings.OtherSettings
import io.legado.app.domain.model.settings.ReadSettings
import io.legado.app.help.config.AppConfigStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ResolveBookshelfConflictUseCaseTest {

    private lateinit var database: AppDatabase
    private lateinit var readRecordRepository: ReadRecordRepository
    private lateinit var bookRepository: BookRepository
    private lateinit var useCase: ResolveBookshelfConflictUseCase

    @Before
    fun setUp() {
        AppConfigStore.init(RuntimeEnvironment.getApplication())
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        )
            .allowMainThreadQueries().build()
        readRecordRepository =
            ReadRecordRepository(database.readRecordDao, database, SettingsRepository())
        bookRepository = BookRepository(database.bookDao, database.bookChapterDao, database)
        useCase = ResolveBookshelfConflictUseCase(
            bookRepository = bookRepository,
            bookSourceRepository = BookSourceRepository(database.bookSourceDao),
            changeBookSourceUseCase = ChangeBookSourceUseCase(
                database = database,
                bookDao = database.bookDao,
                bookChapterDao = database.bookChapterDao,
                otherSettingsGateway = StubOtherSettingsGateway(),
                readSettingsGateway = StubReadSettingsGateway(),
                readRecordRepository = readRecordRepository,
            ),
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `coexist copies only the selected fields to the new book`() = runBlocking {
        insertShelfBook(
            bookUrl = existingUrl,
            group = 7L,
            customCoverUrl = "cover-a",
            customTag = "tag-a",
            remark = "remark-a",
        )
        val newBook = Book(bookUrl = newUrl, name = name, author = author)

        useCase.coexist(
            existingBookUrl = existingUrl,
            newBook = newBook,
            options = ChangeSourceMigrationOptions(
                migrateGroup = true,
                migrateCover = true,
                migrateCategory = false,
                migrateRemark = false,
                migrateReadingProgress = false,
                migrateReadConfig = false,
            ),
            chapters = emptyList(),
        )

        val stored = bookRepository.getBook(newUrl)
        assertNotNull(stored)
        assertEquals(7L, stored?.group)
        assertEquals("cover-a", stored?.customCoverUrl)
        // 未勾选的项必须保持新书自己的值，不能顺手搬过来。
        assertNull(stored?.customTag)
        assertNull(stored?.remark)
    }

    @Test
    fun `coexist never carries reading time over to the new copy`() = runBlocking {
        insertShelfBook(bookUrl = existingUrl)
        // 书架已有作品读了 200ms
        readRecordRepository.saveReadSession(
            ReadRecordSession(
                bookName = name,
                bookAuthor = author,
                bookUrl = existingUrl,
                startTime = 1_000,
                endTime = 1_200,
                words = 10,
            )
        )

        useCase.coexist(
            existingBookUrl = existingUrl,
            newBook = Book(bookUrl = newUrl, name = name, author = author),
            options = ChangeSourceMigrationOptions(),
            chapters = emptyList(),
        )

        assertEquals(
            200L,
            readRecordRepository.getBookCopyReadTime(existingUrl, name, author).first()
        )
        assertEquals(0L, readRecordRepository.getBookCopyReadTime(newUrl, name, author).first())
        // 作品维度仍是 200，没有因为多一个副本而翻倍。
        assertEquals(200L, readRecordRepository.getBookReadTime(name, author).first())
    }

    @Test
    fun `migrate without a toc cancels instead of wiping the existing book`() = runBlocking {
        insertShelfBook(bookUrl = existingUrl, durChapterIndex = 5, durChapterTitle = "第五章")
        val newBook = Book(bookUrl = newUrl, name = name, author = author)

        // 书源不存在 -> 目录取不到，迁移必须中止
        val error = runCatching {
            useCase.migrate(
                existingBookUrl = existingUrl,
                newBook = newBook,
                options = ChangeSourceMigrationOptions(),
            )
        }.exceptionOrNull()
        assertTrue("目录缺失时迁移必须中止", error is BookTocUnavailableException)

        // 旧书与它的进度必须原封不动，新书也不能入库。
        assertEquals(5, bookRepository.getBook(existingUrl)?.durChapterIndex)
        assertEquals("第五章", bookRepository.getBook(existingUrl)?.durChapterTitle)
        assertNull(bookRepository.getBook(newUrl))
    }

    private suspend fun insertShelfBook(
        bookUrl: String,
        group: Long = 0L,
        customCoverUrl: String? = null,
        customTag: String? = null,
        remark: String? = null,
        durChapterIndex: Int = 0,
        durChapterTitle: String? = null,
    ) {
        database.bookDao.insert(
            Book(
                bookUrl = bookUrl,
                name = name,
                author = author,
                group = group,
                customCoverUrl = customCoverUrl,
                customTag = customTag,
                remark = remark,
                durChapterIndex = durChapterIndex,
                durChapterTitle = durChapterTitle,
            )
        )
    }

    private class StubOtherSettingsGateway : OtherSettingsGateway {
        override val currentSettings: OtherSettings = OtherSettings()
        override val settings = MutableStateFlow(OtherSettings())
        override suspend fun update(transform: (OtherSettings) -> OtherSettings) = Unit
    }

    private class StubReadSettingsGateway : ReadSettingsGateway {
        override val currentSettings: ReadSettings = ReadSettings()
        override val settings = MutableStateFlow(ReadSettings())
        override suspend fun update(transform: (ReadSettings) -> ReadSettings) = Unit
    }

    private companion object {
        const val name = "斗破苍穹"
        const val author = "天蚕土豆"
        const val existingUrl = "https://old.example/book"
        const val newUrl = "https://new.example/book"
    }
}
