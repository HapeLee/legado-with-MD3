package io.legado.app.ui.book.read

import android.app.Application
import androidx.room.Room
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.readRecord.ReadRecord
import io.legado.app.data.repository.ReadRecordRepository
import io.legado.app.data.repository.SettingsRepository
import io.legado.app.help.config.AppConfigStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ReadRecordAliasDelegateTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: ReadRecordRepository
    private lateinit var settingsRepository: SettingsRepository
    private val testScope = TestScope()

    @Before
    fun setUp() {
        AppConfigStore.init(RuntimeEnvironment.getApplication())
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        )
            .allowMainThreadQueries().build()
        settingsRepository = SettingsRepository()
        repository = ReadRecordRepository(database.readRecordDao, database, settingsRepository)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `check does not show conflict when book has empty or blank author`() = testScope.runTest {
        // 模拟本地书产生或遗留的无作者阅读记录
        database.readRecordDao.insert(
            ReadRecord(
                deviceId = "local_device",
                bookName = "本地小说",
                bookAuthor = "",
                readTime = 60000L
            )
        )

        var conflictShown = false
        val delegate = ReadRecordAliasDelegate(
            scope = testScope,
            localPreferencesRepository = settingsRepository,
            readRecordRepository = repository,
            hasActiveDialog = { false },
            showConflict = { _, _ -> conflictShown = true },
            dismissDialog = {},
        )

        // 1. 作者为空字符串
        val emptyAuthorBook = Book(name = "本地小说", author = "")
        delegate.check(emptyAuthorBook)
        assertFalse("无作者本地书不应弹出归属确认", conflictShown)

        // 2. 作者为纯空白字符
        val blankAuthorBook = Book(name = "本地小说", author = "   \t\n")
        delegate.check(blankAuthorBook)
        assertFalse("纯空白作者本地书不应弹出归属确认", conflictShown)
    }

    @Test
    fun `check shows conflict when book has real author and unknown author records exist`() =
        testScope.runTest {
            // 数据库中存在书名相同但作者为空的记录
            database.readRecordDao.insert(
                ReadRecord(
                    deviceId = "local_device",
                    bookName = "凡人修仙传",
                    bookAuthor = "",
                    readTime = 120000L
                )
            )

            var conflictShown = false
            var conflictBook: Book? = null
            var conflictTime: Long = 0L
            val delegate = ReadRecordAliasDelegate(
                scope = testScope,
                localPreferencesRepository = settingsRepository,
                readRecordRepository = repository,
                hasActiveDialog = { false },
                showConflict = { book, time ->
                    conflictShown = true
                    conflictBook = book
                    conflictTime = time
                },
                dismissDialog = {},
            )

            val realBook = Book(name = "凡人修仙传", author = "忘语")
            delegate.check(realBook)

            assertTrue("有明确作者的书籍发现无作者旧记录时应当弹出归属确认", conflictShown)
            assertEquals("凡人修仙传", conflictBook?.name)
            assertEquals("忘语", conflictBook?.author)
            assertEquals(120000L, conflictTime)
        }
}
