package io.legado.app.data.repository

import io.legado.app.data.dao.RssReadRecordDao
import io.legado.app.data.entities.RssReadRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RssReadRecordRepository(
    private val dao: RssReadRecordDao,
) {

    suspend fun insert(record: RssReadRecord) = withContext(Dispatchers.IO) {
        dao.insertRecord(record)
    }

    fun getAll(): List<RssReadRecord> = dao.getRecords()

    fun count(): Int = dao.countRecords

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        dao.deleteAllRecord()
    }
}
