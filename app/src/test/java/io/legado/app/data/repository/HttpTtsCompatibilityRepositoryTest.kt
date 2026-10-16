package io.legado.app.data.repository

import android.app.Application
import androidx.room.Room
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.HttpTTS
import io.legado.app.data.entities.ReadAloudVoiceEntity
import io.legado.app.domain.model.readaloud.HttpTtsCatalogDraft
import io.legado.app.domain.model.readaloud.HttpTtsEmotionDraft
import io.legado.app.domain.model.readaloud.HttpTtsSpeakerDraft
import io.legado.app.domain.usecase.SyncHttpTtsCatalogUseCase
import io.legado.app.domain.usecase.SyncReadAloudVoicesUseCase
import io.legado.app.help.readaloud.speech.HttpTtsCatalogParser
import io.legado.app.utils.GSON
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class HttpTtsCompatibilityRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: HttpTtsRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = repository(db)
    }

    @After fun tearDown() = db.close()

    @Test fun `delete removes all HTTP voices including excluded user and configured rows`() = runBlocking {
        repository.importWithCatalogs(listOf(source(1) to catalog, source(2) to catalog))
        val original = db.readAloudVoiceDao.getVoices().first { it.engineId == "1" }
        db.readAloudVoiceDao.upsertVoice(original.copy(excluded = true))
        db.readAloudVoiceDao.upsertVoice(original.copy(id = "manual", speakerId = "manual", managedBy = "user"))
        db.readAloudVoiceDao.upsertVoice(original.copy(id = "configured", speakerId = "", managedBy = "configured_tts"))
        repository.deleteById(1)
        assertNull(db.httpTTSDao.get(1))
        assertEquals(listOf("2"), db.readAloudVoiceDao.getVoices().map { it.engineId })
    }

    @Test fun `failed delete rolls back both source and catalog`() = runBlocking {
        repository.importWithCatalogs(listOf(source(1) to catalog))
        db.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER fail_source_delete BEFORE DELETE ON httpTTS
            BEGIN SELECT RAISE(ABORT, 'forced failure'); END
        """.trimIndent())
        assertTrue(runCatching { repository.deleteById(1) }.isFailure)
        assertNotNull(db.httpTTSDao.get(1))
        assertEquals(1, db.readAloudVoiceDao.getVoices().size)
    }

    @Test fun `cancelled catalog transaction rolls back source and voices`() = runBlocking {
        val gateway = ReadAloudVoiceRepository(db.readAloudVoiceDao, db)
        assertTrue(runCatching {
            gateway.withTransaction {
                repository.importWithCatalogs(listOf(source(1) to catalog))
                throw CancellationException("cancel import")
            }
        }.exceptionOrNull() is CancellationException)
        assertNull(db.httpTTSDao.get(1))
        assertTrue(db.readAloudVoiceDao.getVoices().isEmpty())
    }

    @Test fun `catalog failure during import rolls back inserted source`() = runBlocking {
        db.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER fail_voice_insert BEFORE INSERT ON read_aloud_voices
            BEGIN SELECT RAISE(ABORT, 'forced failure'); END
        """.trimIndent())
        assertTrue(runCatching { repository.importWithCatalogs(listOf(source(1) to catalog)) }.isFailure)
        assertNull(db.httpTTSDao.get(1))
        assertTrue(db.readAloudVoiceDao.getVoices().isEmpty())
    }

    @Test fun `orphan cleanup includes excluded rows and candidates reject missing engines`() = runBlocking {
        repository.importWithCatalogs(listOf(source(1) to catalog))
        val original = db.readAloudVoiceDao.getVoices().single()
        db.httpTTSDao.delete(source(1))
        db.readAloudVoiceDao.upsertVoice(original.copy(id = "excluded", speakerId = "other", excluded = true))
        assertTrue(db.readAloudVoiceDao.getEnabledVoices().isEmpty())
        repository.pruneOrphanedVoices()
        assertTrue(db.readAloudVoiceDao.getVoices().isEmpty())
        assertEquals(0, db.readAloudVoiceDao.countExcludedVoices())
    }

    @Test fun `late sync from stale engine snapshot cannot recreate deleted voices`() = runBlocking {
        repository.importWithCatalogs(listOf(source(1) to catalog))
        val gateway = ReadAloudVoiceRepository(db.readAloudVoiceDao, db)
        val stale = gateway.getVoices().single()
        repository.deleteById(1)
        gateway.upsertVoice(stale)
        assertTrue(gateway.getVoices().isEmpty())
    }

    @Test fun `empty database restore preserves HTTP metadata and user state and export retains directory`() = runBlocking {
        repository.importWithCatalogs(listOf(source(1) to catalog))
        val voice = db.readAloudVoiceDao.getVoices().single()
            .copy(enabled = false, excluded = true, revision = 17)
        db.readAloudVoiceDao.upsertVoice(voice)
        val export = repository.exportJson()
        assertEquals(catalog, HttpTtsCatalogParser.parseList(export).single())
        val snapshot = repository.backupSnapshot()
        val voicesJson = GSON.toJson(snapshot.voices)
        val fresh = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            repository(fresh).restore(
                snapshot.sources.map { it to HttpTtsCatalogDraft() },
                GSON.fromJson(voicesJson, Array<ReadAloudVoiceEntity>::class.java).toList(),
            )
            assertEquals(listOf(voice), fresh.readAloudVoiceDao.getVoices())
        } finally { fresh.close() }
    }

    @Test fun `large multi window lists remain consistent while deleting source`() = runBlocking {
        db.httpTTSDao.insert(source(1))
        val rows = (0 until 10000).map {
            ReadAloudVoiceEntity("voice-$it", "http", "1", "$it", "voice-$it", traitsJson = "x".repeat(700))
        }
        db.readAloudVoiceDao.upsertVoices(rows)
        val reads = listOf(
            async(Dispatchers.IO) { db.readAloudVoiceDao.getVoices() },
            async(Dispatchers.IO) { db.readAloudVoiceDao.observeVoices().first() },
            async(Dispatchers.IO) { db.readAloudVoiceDao.getEnabledVoices() },
        )
        val delete = async(Dispatchers.IO) { repository.deleteById(1) }
        reads.awaitAll().forEach { assertTrue(it.isEmpty() || it.size == rows.size) }
        delete.await()
        assertTrue(db.readAloudVoiceDao.getVoices().isEmpty())
    }

    private fun repository(database: AppDatabase): HttpTtsRepository = HttpTtsRepository(
        database.httpTTSDao, database,
        SyncHttpTtsCatalogUseCase(SyncReadAloudVoicesUseCase(ReadAloudVoiceRepository(database.readAloudVoiceDao, database))),
    )

    private fun source(id: Long) = HttpTTS(id = id, name = "HTTP $id", url = "http://localhost/tts")
    private val catalog = HttpTtsCatalogDraft(
        listOf(HttpTtsSpeakerDraft("晓晓", "tone", listOf("女声"))),
        listOf(HttpTtsEmotionDraft("开心", "happy")),
    )
}
