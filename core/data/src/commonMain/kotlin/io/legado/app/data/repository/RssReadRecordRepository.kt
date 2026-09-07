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

    suspend fun getAll(): List<RssReadRecord> = withContext(Dispatchers.IO) {
        dao.getRecords()
    }

    suspend fun count(): Int = withContext(Dispatchers.IO) {
        dao.countRecords()
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        dao.deleteAllRecord()
    }
}
