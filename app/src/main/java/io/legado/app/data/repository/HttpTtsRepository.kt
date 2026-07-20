package io.legado.app.data.repository

import io.legado.app.data.dao.HttpTTSDao
import io.legado.app.data.entities.HttpTTS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HttpTtsRepository(
    private val dao: HttpTTSDao,
) {

    suspend fun findById(id: Long): HttpTTS? = withContext(Dispatchers.IO) {
        dao.get(id)
    }

    suspend fun insert(vararg sources: HttpTTS) = withContext(Dispatchers.IO) {
        dao.insert(*sources)
    }
}
