package io.legado.app.data.repository

import androidx.room.Transaction
import cn.hutool.core.date.DatePattern
import cn.hutool.core.date.DateUtil
import io.legado.app.data.dao.ReadRecordDao
import io.legado.app.data.entities.readRecord.ReadRecord
import io.legado.app.data.entities.readRecord.ReadRecordDetail
import io.legado.app.data.entities.readRecord.ReadRecordSession
import java.util.Date
import kotlin.math.max
import kotlin.math.min

class ReadRecordRepository(
    private val dao: ReadRecordDao
) {

    private fun getCurrentDeviceId(): String = ""

    /**
     * 保存一个完整的阅读会话，并同步更新 ReadRecordDetail 和 ReadRecord。
     */
    @Transaction
    suspend fun saveReadSession(session: ReadRecordSession) {
        dao.insertSession(session)

        val sessionDuration = session.endTime - session.startTime
        val dateString = DateUtil.format(Date(session.startTime), DatePattern.NORM_DATE_PATTERN)

        updateReadRecordDetail(session, sessionDuration, dateString)
        updateReadRecord(session, sessionDuration)
    }

    private suspend fun updateReadRecord(session: ReadRecordSession, sessionDuration: Long) {
        val existingRecord = dao.getReadRecord(session.deviceId, session.bookName)

        if (existingRecord != null) {
            val updatedRecord = existingRecord.copy(
                readTime = existingRecord.readTime + sessionDuration,
                lastRead = session.endTime
            )
            dao.update(updatedRecord)
        } else {
            val newRecord = ReadRecord(
                deviceId = session.deviceId,
                bookName = session.bookName,
                readTime = sessionDuration,
                lastRead = session.endTime
            )
            dao.insert(newRecord)
        }
    }

    private suspend fun updateReadRecordDetail(session: ReadRecordSession, sessionDuration: Long, dateString: String) {
        val existingDetail = dao.getDetail(session.deviceId, session.bookName, dateString)

        if (existingDetail != null) {
            existingDetail.readTime += sessionDuration
            existingDetail.readWords += session.words
            existingDetail.firstReadTime = min(existingDetail.firstReadTime, session.startTime)
            existingDetail.lastReadTime = max(existingDetail.lastReadTime, session.endTime)
            dao.insertDetail(existingDetail)
        } else {
            val newDetail = ReadRecordDetail(
                deviceId = session.deviceId,
                bookName = session.bookName,
                date = dateString,
                readTime = sessionDuration,
                readWords = session.words,
                firstReadTime = session.startTime,
                lastReadTime = session.endTime
            )
            dao.insertDetail(newDetail)
        }
    }

    suspend fun getDailyDetails(deviceId: String, date: String): List<ReadRecordDetail> {
        return dao.getDetailsByDate(deviceId, date)
    }

    suspend fun getDailySessions(deviceId: String, bookName: String, date: String): List<ReadRecordSession> {
        return dao.getSessionsByBookAndDate(deviceId, bookName, date)
    }

    suspend fun getLatestReadRecords(query: String = ""): List<ReadRecord> {
        return if (query.isBlank()) {
            dao.getAllReadRecordsSortedByLastRead()
        } else {
            dao.searchReadRecordsByLastRead(query)
        }
    }

    suspend fun getAllSessionsForDate(date: String): List<ReadRecordSession> {
        val deviceId = getCurrentDeviceId()
        return dao.getSessionsByDate(deviceId, date)
    }

    suspend fun getAllRecordDetails(query: String = ""): List<ReadRecordDetail> {
        return if (query.isBlank()) {
            dao.getAllDetails()
        } else {
            dao.searchDetails(query)
        }
    }

    suspend fun deleteDetail(detail: ReadRecordDetail) {
        dao.deleteDetail(detail)
    }

    suspend fun clearAll() {
        dao.clear() // 清除总表
        // dao.clearDetails()
    }

    // 暴露总时长
    val allTime: Long
        get() = dao.allTime

}