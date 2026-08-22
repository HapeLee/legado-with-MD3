package io.legado.app.data.repository

import android.app.Application
import androidx.room.Room
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.readRecord.ReadRecord
import io.legado.app.data.entities.readRecord.ReadRecordDetail
import io.legado.app.data.entities.readRecord.ReadRecordSession
import io.legado.app.help.config.AppConfigStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ReadRecordRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: ReadRecordRepository

    @Before
    fun setUp() {
        // ReadRecordRepository 构造即求值 readRecordEnabled，会访问 AppConfigStore；
        // 生产环境由 App.onCreate 首行 init() 完成，测试里在 Robolectric 应用上下文中补上。
        AppConfigStore.init(RuntimeEnvironment.getApplication())
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = ReadRecordRepository(database.readRecordDao, database, SettingsRepository())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `merge accumulates target and source sessions`() = runBlocking {
        mergeAndAssert(targetSessionDuration = 100, sourceSessionDuration = 200)
    }

    @Test
    fun `merge keeps source legacy duration`() = runBlocking {
        mergeAndAssert(targetSessionDuration = 100, sourceLegacyTime = 200)
    }

    @Test
    fun `merge keeps target legacy duration`() = runBlocking {
        mergeAndAssert(targetLegacyTime = 100, sourceSessionDuration = 200)
    }

    @Test
    fun `repeating an independent merge does not add time again`() = runBlocking {
        val source = insertRecord(sourceName, 200)
        insertRecord(targetName, 100)

        repository.mergeIndependentReadRecordsInto(targetRecord(), listOf(source))
        repository.mergeIndependentReadRecordsInto(targetRecord(), listOf(source))

        assertEquals(300L, targetReadTime())
        assertNull(database.readRecordDao.getReadRecord(deviceId, sourceName, author))
    }

    @Test
    fun `cross device merge moves sessions and removes source records`() = runBlocking {
        val targetDevice = "target-device"
        val sourceDevice = "source-device"
        database.readRecordDao.insert(ReadRecord(targetDevice, targetName, author, 100, 2_000))
        val source = ReadRecord(sourceDevice, sourceName, author, 200, 1_000)
        database.readRecordDao.insert(source)
        database.readRecordDao.insertSession(ReadRecordSession(deviceId = sourceDevice, bookName = sourceName, bookAuthor = author, startTime = 3_000, endTime = 3_200, words = 10))

        repository.mergeIndependentReadRecordsInto(ReadRecord("", targetName, author), listOf(source))

        assertEquals(300L, database.readRecordDao.getReadRecord(targetDevice, targetName, author)?.readTime)
        assertEquals(1, database.readRecordDao.getSessionsByBook(targetDevice, targetName, author).size)
        assertNull(database.readRecordDao.getReadRecord(sourceDevice, sourceName, author))
    }

    @Test
    fun `synchronized session copies are only counted once in details`() = runBlocking {
        val targetDevice = "target-device"
        val sourceDevice = "source-device"
        val date = "1970-01-01"
        database.readRecordDao.insert(ReadRecord(targetDevice, targetName, author, 200, 2_000))
        database.readRecordDao.insert(ReadRecord(sourceDevice, sourceName, author, 200, 1_000))
        database.readRecordDao.insertSession(ReadRecordSession(deviceId = targetDevice, bookName = targetName, bookAuthor = author, startTime = 3_000, endTime = 3_200, words = 10))
        database.readRecordDao.insertSession(ReadRecordSession(deviceId = sourceDevice, bookName = sourceName, bookAuthor = author, startTime = 3_000, endTime = 3_200, words = 10))
        database.readRecordDao.insertDetail(ReadRecordDetail(targetDevice, targetName, author, date, 200, 10, 3_000, 3_200))
        database.readRecordDao.insertDetail(ReadRecordDetail(sourceDevice, sourceName, author, date, 200, 10, 3_000, 3_200))

        repository.mergeIndependentReadRecordsInto(ReadRecord("", targetName, author), listOf(ReadRecord(sourceDevice, sourceName, author)))

        val detail = database.readRecordDao.getDetail(targetDevice, targetName, author, date)
        assertEquals(200L, detail?.readTime)
        assertEquals(10L, detail?.readWords)
        assertEquals(1, database.readRecordDao.getSessionsByBook(targetDevice, targetName, author).size)
        assertNull(database.readRecordDao.getDetail(sourceDevice, sourceName, author, date))
    }

    @Test
    fun `repair duplicate sessions removes orphaned duplicate totals`() = runBlocking {
        val targetDevice = "target-device"
        val sourceDevice = "source-device"
        val date = "1970-01-01"
        val session = ReadRecordSession(
            deviceId = targetDevice,
            bookName = targetName,
            bookAuthor = author,
            startTime = 3_000,
            endTime = 3_200,
            words = 10,
        )
        database.readRecordDao.insert(ReadRecord(targetDevice, targetName, author, 200, 2_000))
        database.readRecordDao.insert(ReadRecord(sourceDevice, targetName, author, 200, 2_000))
        database.readRecordDao.insertSession(session)
        database.readRecordDao.insertSession(session.copy(deviceId = sourceDevice))
        database.readRecordDao.insertDetail(ReadRecordDetail(targetDevice, targetName, author, date, 200, 10, 3_000, 3_200))
        database.readRecordDao.insertDetail(ReadRecordDetail(sourceDevice, targetName, author, date, 200, 10, 3_000, 3_200))

        assertEquals(1, repository.repairDuplicateSessions())

        assertEquals(200L, database.readRecordDao.getReadRecord(targetDevice, targetName, author)?.readTime)
        assertNull(database.readRecordDao.getReadRecord(sourceDevice, targetName, author))
        assertEquals(200L, database.readRecordDao.getDetail(targetDevice, targetName, author, date)?.readTime)
        assertNull(database.readRecordDao.getDetail(sourceDevice, targetName, author, date))
    }

    @Test
    fun `repair duplicate sessions keeps legacy duration on orphaned device`() = runBlocking {
        val targetDevice = "target-device"
        val sourceDevice = "source-device"
        val date = "1970-01-01"
        val session = ReadRecordSession(
            deviceId = targetDevice,
            bookName = targetName,
            bookAuthor = author,
            startTime = 3_000,
            endTime = 3_200,
            words = 10,
        )
        // sourceDevice 的汇总时长 = 副本 session 200 + 旧版历史 legacy 100，
        // 修复去重后 session 副本被保留在 targetDevice，sourceDevice 应只剩 legacy。
        database.readRecordDao.insert(ReadRecord(targetDevice, targetName, author, 200, 2_000))
        database.readRecordDao.insert(ReadRecord(sourceDevice, targetName, author, 300, 2_000))
        database.readRecordDao.insertSession(session)
        database.readRecordDao.insertSession(session.copy(deviceId = sourceDevice))
        database.readRecordDao.insertDetail(ReadRecordDetail(targetDevice, targetName, author, date, 200, 10, 3_000, 3_200))
        database.readRecordDao.insertDetail(ReadRecordDetail(sourceDevice, targetName, author, date, 300, 10, 3_000, 3_200))

        assertEquals(1, repository.repairDuplicateSessions())

        assertEquals(200L, database.readRecordDao.getReadRecord(targetDevice, targetName, author)?.readTime)
        assertEquals(100L, database.readRecordDao.getReadRecord(sourceDevice, targetName, author)?.readTime)
        assertEquals(200L, database.readRecordDao.getDetail(targetDevice, targetName, author, date)?.readTime)
        assertEquals(100L, database.readRecordDao.getDetail(sourceDevice, targetName, author, date)?.readTime)
    }

    @Test
    fun `deleting the last aggregate detail removes session-backed total`() = runBlocking {
        val date = "1970-01-01"
        val record = ReadRecord(deviceId, targetName, author, 200, 1_200)
        val session = ReadRecordSession(
            deviceId = deviceId,
            bookName = targetName,
            bookAuthor = author,
            startTime = 1_000,
            endTime = 1_200,
            words = 10,
        )
        database.readRecordDao.insert(record)
        database.readRecordDao.insertSession(session)
        database.readRecordDao.insertDetail(ReadRecordDetail(deviceId, targetName, author, date, 200, 10, 1_000, 1_200))

        repository.deleteDetail(database.readRecordDao.getDetail(deviceId, targetName, author, date)!!)

        assertNull(database.readRecordDao.getReadRecord(deviceId, targetName, author))
        assertNull(database.readRecordDao.getDetail(deviceId, targetName, author, date))
        assertEquals(0, database.readRecordDao.getSessionsByBook(deviceId, targetName, author).size)
    }

    @Test
    fun `deleting the last session preserves legacy total only`() = runBlocking {
        val date = "1970-01-01"
        val record = ReadRecord(deviceId, targetName, author, 300, 1_200)
        val session = ReadRecordSession(
            deviceId = deviceId,
            bookName = targetName,
            bookAuthor = author,
            startTime = 1_000,
            endTime = 1_200,
            words = 10,
        )
        database.readRecordDao.insert(record)
        database.readRecordDao.insertSession(session)
        database.readRecordDao.insertDetail(ReadRecordDetail(deviceId, targetName, author, date, 200, 10, 1_000, 1_200))

        repository.deleteSession(session)

        assertEquals(100L, database.readRecordDao.getReadRecord(deviceId, targetName, author)?.readTime)
        assertNull(database.readRecordDao.getDetail(deviceId, targetName, author, date))
        assertEquals(0, database.readRecordDao.getSessionsByBook(deviceId, targetName, author).size)
    }

    private suspend fun mergeAndAssert(targetSessionDuration: Long = 0, sourceSessionDuration: Long = 0, targetLegacyTime: Long = 0, sourceLegacyTime: Long = 0) {
        val source = insertRecord(sourceName, sourceSessionDuration + sourceLegacyTime, sourceSessionDuration)
        insertRecord(targetName, targetSessionDuration + targetLegacyTime, targetSessionDuration)
        repository.mergeIndependentReadRecordsInto(targetRecord(), listOf(source))
        assertEquals(300L, targetReadTime())
        assertNull(database.readRecordDao.getReadRecord(deviceId, sourceName, author))
    }

    private suspend fun insertRecord(name: String, readTime: Long, sessionDuration: Long = 0): ReadRecord {
        val record = ReadRecord(deviceId, name, author, readTime, 1_000)
        database.readRecordDao.insert(record)
        if (sessionDuration > 0) {
            val start = if (name == sourceName) 2_000L else 1_000L
            database.readRecordDao.insertSession(ReadRecordSession(deviceId = deviceId, bookName = name, bookAuthor = author, startTime = start, endTime = start + sessionDuration, words = 10))
        }
        return record
    }

    private suspend fun targetReadTime() = database.readRecordDao.getReadRecord(deviceId, targetName, author)?.readTime ?: -1
    private fun targetRecord() = ReadRecord(deviceId, targetName, author)

    private companion object {
        const val deviceId = "device"
        const val author = "author"
        const val targetName = "target"
        const val sourceName = "source"
    }
}
