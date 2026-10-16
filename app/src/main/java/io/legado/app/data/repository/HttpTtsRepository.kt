package io.legado.app.data.repository

import androidx.room.withTransaction
import io.legado.app.data.AppDatabase
import io.legado.app.data.dao.HttpTTSDao
import io.legado.app.data.entities.ReadAloudVoiceEntity
import io.legado.app.domain.model.readaloud.HttpTtsCatalogDraft
import io.legado.app.domain.model.readaloud.ReadAloudVoice
import io.legado.app.domain.usecase.SyncHttpTtsCatalogUseCase
import io.legado.app.help.readaloud.speech.HttpTtsCatalogExporter
import io.legado.app.data.entities.HttpTTS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HttpTtsRepository(
    private val dao: HttpTTSDao,
    private val database: AppDatabase,
    private val syncCatalog: SyncHttpTtsCatalogUseCase,
) {

    suspend fun findById(id: Long): HttpTTS? = withContext(Dispatchers.IO) {
        dao.get(id)
    }

    suspend fun getAll(): List<HttpTTS> = withContext(Dispatchers.IO) {
        dao.all
    }

    fun getAllSync(): List<HttpTTS> = dao.all

    fun getNameSync(id: Long): String? = dao.getName(id)

    suspend fun getEmotionCatalog(engineId: String): String? = withContext(Dispatchers.IO) {
        database.readAloudVoiceDao.getHttpEmotionCatalog(engineId)
    }

    suspend fun insert(vararg sources: HttpTTS) = withContext(Dispatchers.IO) {
        dao.insert(*sources)
    }

    suspend fun deleteById(id: Long) = withContext(Dispatchers.IO) {
        database.withTransaction {
            database.readAloudVoiceDao.deleteHttpVoices(id.toString())
            dao.get(id)?.let { dao.delete(it) }
        }
    }

    suspend fun pruneOrphanedVoices() = withContext(Dispatchers.IO) {
        database.readAloudVoiceDao.deleteOrphanedHttpVoices()
    }

    /** 源与目录同一事务提交，取消或写入失败时整体回滚。 */
    suspend fun importWithCatalogs(entries: List<Pair<HttpTTS, HttpTtsCatalogDraft>>) =
        withContext(Dispatchers.IO) {
            database.withTransaction {
                entries.forEach { (source, draft) ->
                    dao.insert(source)
                    syncCatalog(source.id.toString(), draft, source.lastUpdateTime)
                }
            }
        }

    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        database.withTransaction {
            HttpTtsCatalogExporter.export(dao.all, database.readAloudVoiceDao.getVoices().map { it.toDomain() })
        }
    }

    data class BackupSnapshot(val sources: List<HttpTTS>, val voices: List<ReadAloudVoiceEntity>)

    suspend fun backupSnapshot(): BackupSnapshot = withContext(Dispatchers.IO) {
        database.withTransaction {
            BackupSnapshot(dao.all, database.readAloudVoiceDao.getVoices().filter {
                it.engineType == ReadAloudVoice.ENGINE_HTTP
            })
        }
    }

    suspend fun restore(
        entries: List<Pair<HttpTTS, HttpTtsCatalogDraft>>,
        voices: List<ReadAloudVoiceEntity>?,
    ) = withContext(Dispatchers.IO) {
        database.withTransaction {
            importWithCatalogs(entries)
            val restoredIds = entries.mapTo(mutableSetOf()) { it.first.id.toString() }
            if (voices != null) {
                restoredIds.forEach { database.readAloudVoiceDao.deleteHttpVoices(it) }
                database.readAloudVoiceDao.upsertVoices(voices.filter {
                    it.engineType == ReadAloudVoice.ENGINE_HTTP && it.engineId in restoredIds
                })
            }
            database.readAloudVoiceDao.deleteOrphanedHttpVoices()
        }
    }

    suspend fun delete(vararg sources: HttpTTS) = withContext(Dispatchers.IO) {
        database.withTransaction {
            sources.forEach { database.readAloudVoiceDao.deleteHttpVoices(it.id.toString()) }
            dao.delete(*sources)
        }
    }
}
