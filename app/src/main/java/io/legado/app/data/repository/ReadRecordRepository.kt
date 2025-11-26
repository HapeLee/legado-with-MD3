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

    companion object {
        // 2分钟内重新打开书本，视为同一次阅读
        private const val CONTINUE_THRESHOLD = 120 * 1000L
        // 会话总时长小于10秒则不记录
        private const val MIN_READ_DURATION = 0 * 1000L
    }

    private fun getCurrentDeviceId(): String = ""

    /**
     * 智能保存阅读会话。
     * 自动判断是插入新会话还是合并到上一次会话
     */
    @Transaction
    suspend fun saveOrMergeReadSession(newSession: ReadRecordSession) {

        val segmentDuration = newSession.endTime - newSession.startTime

        if (segmentDuration < MIN_READ_DURATION) {
            return
        }

        val latestSession = dao.getLatestSessionByBook(newSession.bookName)

        if (latestSession != null) {
            val timeGap = newSession.startTime - latestSession.endTime

            if (timeGap <= CONTINUE_THRESHOLD && timeGap >= 0) {

                val durationDelta = segmentDuration
                val wordsDelta = newSession.words

                val mergedSession = latestSession.copy(
                    endTime = newSession.endTime,
                    words = latestSession.words + wordsDelta
                )

                dao.updateSession(mergedSession)

                val dateString = DateUtil.format(Date(mergedSession.startTime), DatePattern.NORM_DATE_PATTERN)
                updateReadRecordDetail(mergedSession, durationDelta, wordsDelta, dateString)
                updateReadRecord(mergedSession, durationDelta)

                return
            }
        }

        // 处理新会话
        dao.insertSession(newSession)

        val dateString = DateUtil.format(Date(newSession.startTime), DatePattern.NORM_DATE_PATTERN)
        updateReadRecordDetail(newSession, segmentDuration, newSession.words, dateString)
        updateReadRecord(newSession, segmentDuration)
    }

    /**
     * 保存一个完整的阅读会话
     * 没几把用
     */
    @Transaction
    suspend fun saveReadSession(session: ReadRecordSession) {
        dao.insertSession(session)

        val sessionDuration = session.endTime - session.startTime
        val dateString = DateUtil.format(Date(session.startTime), DatePattern.NORM_DATE_PATTERN)
        updateReadRecordDetail(session, sessionDuration, session.words, dateString)

        updateReadRecord(session, sessionDuration)
    }

    /**
     * 更新总记录表 (ReadRecord)
     * @param durationDelta 增加的时长
     */
    private suspend fun updateReadRecord(session: ReadRecordSession, durationDelta: Long) {
        if (durationDelta <= 0) return

        val existingRecord = dao.getReadRecord(session.deviceId, session.bookName)

        if (existingRecord != null) {
            val updatedRecord = existingRecord.copy(
                readTime = existingRecord.readTime + durationDelta,
                lastRead = session.endTime
            )
            dao.update(updatedRecord)
        } else {
            val newRecord = ReadRecord(
                deviceId = session.deviceId,
                bookName = session.bookName,
                readTime = durationDelta,
                lastRead = session.endTime
            )
            dao.insert(newRecord)
        }
    }

    /**
     * 更新每日详情表 (ReadRecordDetail)
     * @param durationDelta 增加的时长
     * @param wordsDelta 增加的字数
     */
    private suspend fun updateReadRecordDetail(
        session: ReadRecordSession,
        durationDelta: Long,
        wordsDelta: Long,
        dateString: String
    ) {
        if (durationDelta <= 0 && wordsDelta <= 0) return

        val existingDetail = dao.getDetail(session.deviceId, session.bookName, dateString)

        if (existingDetail != null) {
            existingDetail.readTime += durationDelta
            existingDetail.readWords += wordsDelta
            existingDetail.firstReadTime = min(existingDetail.firstReadTime, session.startTime)
            existingDetail.lastReadTime = max(existingDetail.lastReadTime, session.endTime)
            dao.insertDetail(existingDetail)
        } else {
            val newDetail = ReadRecordDetail(
                deviceId = session.deviceId,
                bookName = session.bookName,
                date = dateString,
                readTime = durationDelta,
                readWords = wordsDelta,
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