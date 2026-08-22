package io.legado.app.data.repository

import androidx.room.withTransaction
import io.legado.app.data.AppDatabase
import io.legado.app.data.dao.ReadRecordDao
import io.legado.app.data.entities.readRecord.ReadRecord
import io.legado.app.data.entities.readRecord.ReadRecordAliasAction
import io.legado.app.data.entities.readRecord.ReadRecordAliasDecision
import io.legado.app.data.entities.readRecord.ReadRecordDetail
import io.legado.app.data.entities.readRecord.ReadRecordSession
import io.legado.app.data.entities.readRecord.ReadRecordTimelineDay
import io.legado.app.data.entities.readRecord.ReadRecordIdentity
import io.legado.app.data.entities.readRecord.ReadRecordRepairReport
import io.legado.app.data.entities.readRecord.ReadRecordTimeTotals
import io.legado.app.data.local.preferences.LocalPreferencesKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.max
import kotlin.math.min

class ReadRecordRepository(
    private val dao: ReadRecordDao,
    private val database: AppDatabase,
    private val localPreferencesRepository: SettingsRepository,
) {
    private fun getCurrentDeviceId(): String = ""

    private fun Long.toDateString(): String =
        Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

    val readRecordEnabled: Flow<Boolean> =
        localPreferencesRepository.getPreference(LocalPreferencesKeys.ENABLE_READ_RECORD, true)

    suspend fun setReadRecordEnabled(enabled: Boolean) {
        localPreferencesRepository.updatePreference(LocalPreferencesKeys.ENABLE_READ_RECORD, enabled)
    }

    /**
     * 获取总阅读时长流
     */
    fun getTotalReadTime(): Flow<Long> {
        return dao.getTotalReadTime().map { it ?: 0L }
    }

    /**
     * 根据搜索关键字获取跨设备聚合后的最新阅读书籍列表流。
     * 同一本书的各设备记录会合并阅读时长，并保留最新阅读时间。
     */
    fun getLatestReadRecords(query: String = ""): Flow<List<ReadRecord>> {
        return if (query.isBlank()) {
            dao.getAllReadRecordsSortedByLastRead()
        } else {
            dao.searchReadRecordsByLastRead(query)
        }
            .map { records ->
                records.groupBy { it.bookName to it.bookAuthor }
                    .values
                    .map { sameBook ->
                        sameBook.first().copy(
                            deviceId = "",
                            readTime = sameBook.sumOf { it.readTime },
                            lastRead = sameBook.maxOf { it.lastRead },
                        )
                    }
                    .sortedByDescending { it.lastRead }
            }
    }

    /**
     * 获取跨设备聚合后的每日统计详情流。
     * 同一本书同一天的各设备详情会合并阅读时长和字数。
     */
    fun getAllRecordDetails(query: String = ""): Flow<List<ReadRecordDetail>> {
        return if (query.isBlank()) {
            dao.getAllDetails()
        } else {
            dao.searchDetails(query)
        }
            .map { details ->
                details.groupBy { Triple(it.bookName, it.bookAuthor, it.date) }
                    .values
                    .map { sameDay ->
                        sameDay.first().copy(
                            deviceId = "",
                            readTime = sameDay.sumOf { it.readTime },
                            readWords = sameDay.sumOf { it.readWords },
                            firstReadTime = sameDay.map { it.firstReadTime }
                                .filter { it > 0L }
                                .minOrNull() ?: 0L,
                            lastReadTime = sameDay.maxOf { it.lastReadTime },
                        )
                    }
                    .sortedWith(compareByDescending<ReadRecordDetail> { it.date }.thenByDescending { it.lastReadTime })
            }
    }

    fun getAllSessions(): Flow<List<ReadRecordSession>> {
        // UI 展示的是跨设备合并后的时间线；去重键不包含自增 id，避免同步副本重复计时。
        return dao.getAllSessions().map { sessions ->
            sessions.distinctBy {
                listOf(it.bookName, it.bookAuthor, it.startTime, it.endTime, it.words)
            }
        }
    }

    fun getBookSessions(bookName: String, bookAuthor: String): Flow<List<ReadRecordSession>> {
        // 时间线按书名、作者和阅读时段内容去重，避免同步副本重复计时。
        return dao.getAllSessions().map { sessions ->
            sessions.asSequence()
                .filter { it.bookName == bookName && it.bookAuthor == bookAuthor }
                .distinctBy { listOf(it.bookName, it.bookAuthor, it.startTime, it.endTime, it.words) }
                .toList()
        }
    }

    fun getBookTimelineDays(bookName: String, bookAuthor: String): Flow<List<ReadRecordTimelineDay>> {
        return getBookSessions(bookName, bookAuthor).map { sessions ->
            sessions.groupBy { it.startTime.toDateString() }
                .toSortedMap(compareByDescending { it })
                .map { (date, daySessions) ->
                    ReadRecordTimelineDay(
                        date = date,
                        sessions = daySessions.sortedByDescending { it.startTime }
                    )
                }
        }
    }

    fun getBookReadTime(bookName: String, bookAuthor: String): Flow<Long> {
        // 统计所有设备的汇总时长，与跨设备时间线保持一致。
        return dao.getReadTimeFlow(bookName, bookAuthor).map { it ?: 0L }
    }

    suspend fun getMergeCandidates(targetRecord: ReadRecord): List<ReadRecord> {
        return if (targetRecord.deviceId.isBlank()) {
            dao.getMergeCandidatesAcrossDevices(targetRecord.bookName, targetRecord.bookAuthor)
        } else {
            dao.getMergeCandidates(
                targetRecord.deviceId,
                targetRecord.bookName,
                targetRecord.bookAuthor
            )
        }
    }

    /** 获取指定书名下作者为空的旧记录，供打开书籍时确认归属。 */
    suspend fun getUnknownAuthorRecords(bookName: String): List<ReadRecord> {
        return dao.getUnknownAuthorRecords(bookName)
    }

    /**
     * 保存一个完整的阅读时段记录.
     */
    suspend fun saveReadSession(newSession: ReadRecordSession) {
        if (!readRecordEnabled.first()) return
        if (newSession.endTime <= newSession.startTime) return
        val normalizedSession = newSession.copy(
            bookName = ReadRecordIdentity.bookName(newSession.bookName),
            bookAuthor = ReadRecordIdentity.author(newSession.bookAuthor),
        )
        database.withTransaction {
            // 旧版记录可能没有作者；打开同一本有作者的书后，将未知作者记录并入当前记录，
            // 避免第一次阅读时重新创建一条独立的阅读统计。
            // 用户曾明确选择「保留独立记录」时尊重该决定，不自动合并。
            if (normalizedSession.bookAuthor.isNotBlank() &&
                !hasKeepAliasDecision(normalizedSession.bookName, normalizedSession.bookAuthor)
            ) {
                val unknownAuthorRecord = dao.getReadRecord(
                    normalizedSession.deviceId,
                    normalizedSession.bookName,
                    ""
                )
                if (unknownAuthorRecord != null) {
                    mergeSingleReadRecordInto(
                        targetRecord = ReadRecord(
                            deviceId = normalizedSession.deviceId,
                            bookName = normalizedSession.bookName,
                            bookAuthor = normalizedSession.bookAuthor
                        ),
                        sourceRecord = unknownAuthorRecord
                    )
                }
            }
            val existingSession = dao.getSession(
                normalizedSession.deviceId,
                normalizedSession.bookName,
                normalizedSession.bookAuthor,
                normalizedSession.startTime,
                normalizedSession.endTime,
                normalizedSession.words
            )
            if (existingSession != null) return@withTransaction

            val segmentDuration = normalizedSession.endTime - normalizedSession.startTime
            dao.insertSession(normalizedSession)
            val dateString = normalizedSession.startTime.toDateString()
            updateReadRecordDetail(normalizedSession, segmentDuration, normalizedSession.words, dateString)
            updateReadRecord(normalizedSession, segmentDuration)
        }
    }

    /** 用户是否曾为当前书籍明确选择「保留独立记录」。 */
    private suspend fun hasKeepAliasDecision(bookName: String, bookAuthor: String): Boolean {
        val key = ReadRecordIdentity.key(bookName, bookAuthor)
        return localPreferencesRepository
            .getString(LocalPreferencesKeys.READ_RECORD_ALIAS_DECISIONS.name)
            .first()
            .split('\n')
            .mapNotNull { ReadRecordAliasDecision.decode(it, key) }
            .firstOrNull() == ReadRecordAliasAction.KEEP
    }

    private suspend fun updateReadRecord(session: ReadRecordSession, durationDelta: Long) {
        if (durationDelta <= 0) return
        val existingRecord = dao.getReadRecord(session.deviceId, session.bookName, session.bookAuthor)
        if (existingRecord != null) {
            dao.update(
                existingRecord.copy(
                    readTime = existingRecord.readTime + durationDelta,
                    lastRead = session.endTime
                )
            )
        } else {
            dao.insert(
                ReadRecord(
                    deviceId = session.deviceId,
                    bookName = session.bookName,
                    bookAuthor = session.bookAuthor,
                    readTime = durationDelta,
                    lastRead = session.endTime
                )
            )
        }
    }

    private suspend fun updateReadRecordDetail(
        session: ReadRecordSession,
        durationDelta: Long,
        wordsDelta: Long,
        dateString: String
    ) {
        if (durationDelta <= 0 && wordsDelta <= 0) return
        val existingDetail = dao.getDetail(
            session.deviceId,
            session.bookName,
            session.bookAuthor,
            dateString
        )
        if (existingDetail != null) {
            existingDetail.readTime += durationDelta
            existingDetail.readWords += wordsDelta
            existingDetail.firstReadTime = minPositive(existingDetail.firstReadTime, session.startTime)
            existingDetail.lastReadTime = max(existingDetail.lastReadTime, session.endTime)
            dao.insertDetail(existingDetail)
        } else {
            dao.insertDetail(
                ReadRecordDetail(
                    deviceId = session.deviceId,
                    bookName = session.bookName,
                    bookAuthor = session.bookAuthor,
                    date = dateString,
                    readTime = durationDelta,
                    readWords = wordsDelta,
                    firstReadTime = session.startTime,
                    lastReadTime = session.endTime
                )
            )
        }
    }

    suspend fun deleteDetail(detail: ReadRecordDetail) {
        database.withTransaction {
            // 聚合详情代表所有设备同一天的阅读，删除时必须同步删除底层阅读时段记录。
            val affectedDevices = dao.allSession.asSequence()
                .filter {
                    it.bookName == detail.bookName &&
                        it.bookAuthor == detail.bookAuthor &&
                        it.startTime.toDateString() == detail.date
                }
                .mapTo(linkedSetOf()) { it.deviceId }
                .apply { addAll(dao.getReadRecordsByName(detail.bookName, detail.bookAuthor).map { it.deviceId }) }
            // 汇总记录可能包含没有时段明细的旧版历史时长。删除前先扣除所有现存
            // 时段，只保留这部分真正的历史时长；新版本纯由时段产生的记录应被删除。
            val legacyReadTimes = affectedDevices.associateWith { deviceId ->
                val record = dao.getReadRecord(deviceId, detail.bookName, detail.bookAuthor)
                val sessionTime = dao.getSessionsByBook(deviceId, detail.bookName, detail.bookAuthor)
                    .sumOf { it.endTime - it.startTime }
                ((record?.readTime ?: 0L) - sessionTime).coerceAtLeast(0L)
            }
            dao.deleteDetailByNameAndDate(detail.bookName, detail.bookAuthor, detail.date)
            affectedDevices.forEach { deviceId ->
                dao.deleteSessionsByBookAndDate(deviceId, detail.bookName, detail.bookAuthor, detail.date)
                updateReadRecordTotal(
                    deviceId,
                    detail.bookName,
                    detail.bookAuthor,
                    legacyReadTimes[deviceId] ?: 0L,
                )
            }
        }
    }

    suspend fun deleteSession(session: ReadRecordSession) {
        database.withTransaction {
            val affectedDevices = dao.allSession.asSequence()
                .filter {
                    it.bookName == session.bookName &&
                        it.bookAuthor == session.bookAuthor &&
                        it.startTime == session.startTime &&
                        it.endTime == session.endTime &&
                        it.words == session.words
                }
                .mapTo(linkedSetOf()) { it.deviceId }
                .apply { addAll(dao.getReadRecordsByName(session.bookName, session.bookAuthor).map { it.deviceId }) }
            val legacyReadTimes = affectedDevices.associateWith { deviceId ->
                val record = dao.getReadRecord(deviceId, session.bookName, session.bookAuthor)
                val sessionTime = dao.getSessionsByBook(deviceId, session.bookName, session.bookAuthor)
                    .sumOf { it.endTime - it.startTime }
                ((record?.readTime ?: 0L) - sessionTime).coerceAtLeast(0L)
            }
            val daySessionsBeforeDelete = affectedDevices.associateWith { deviceId ->
                dao.getSessionsByBookAndDate(
                    deviceId,
                    session.bookName,
                    session.bookAuthor,
                    session.startTime.toDateString(),
                )
            }
            dao.deleteSessionByIdentity(
                session.bookName,
                session.bookAuthor,
                session.startTime,
                session.endTime,
                session.words,
            )
            val dateString = session.startTime.toDateString()
            affectedDevices.forEach { deviceId ->
                    val record = ReadRecord(
                        deviceId = deviceId,
                        bookName = session.bookName,
                        bookAuthor = session.bookAuthor,
                    )
                    val remainingSessions = dao.getSessionsByBookAndDate(
                        deviceId,
                        record.bookName,
                        record.bookAuthor,
                        dateString,
                    )
                    val detail = dao.getDetail(
                        deviceId,
                        record.bookName,
                        record.bookAuthor,
                        dateString,
                    )
                    val legacyDetailTime = ((detail?.readTime ?: 0L) -
                        daySessionsBeforeDelete[deviceId].orEmpty().sumOf { it.endTime - it.startTime }).coerceAtLeast(0L)
                    val legacyDetailWords = ((detail?.readWords ?: 0L) -
                        daySessionsBeforeDelete[deviceId].orEmpty().sumOf { it.words }).coerceAtLeast(0L)
                    if (remainingSessions.isEmpty()) {
                        if (legacyDetailTime <= 0L && legacyDetailWords <= 0L) {
                            detail?.let { dao.deleteDetail(it) }
                        } else {
                            dao.insertDetail(
                                (detail ?: ReadRecordDetail(
                                    deviceId = record.deviceId,
                                    bookName = record.bookName,
                                    bookAuthor = record.bookAuthor,
                                    date = dateString,
                                )).copy(
                                    readTime = legacyDetailTime,
                                    readWords = legacyDetailWords,
                                )
                            )
                        }
                    } else {
                        dao.insertDetail(
                            (detail ?: ReadRecordDetail(
                                deviceId = record.deviceId,
                                bookName = record.bookName,
                                bookAuthor = record.bookAuthor,
                                date = dateString,
                            )).copy(
                                readTime = legacyDetailTime + remainingSessions.sumOf { it.endTime - it.startTime },
                                readWords = legacyDetailWords + remainingSessions.sumOf { it.words },
                                firstReadTime = remainingSessions
                                    .map { it.startTime }
                                    .filter { it > 0L }
                                    .minOrNull() ?: 0L,
                                lastReadTime = remainingSessions.maxOf { it.endTime },
                            )
                        )
                    }
                    updateReadRecordTotal(
                        deviceId,
                        record.bookName,
                        record.bookAuthor,
                        legacyReadTimes[deviceId] ?: 0L,
                    )
                }
        }
    }

    private fun minPositive(left: Long, right: Long): Long = when {
        left <= 0L -> right
        right <= 0L -> left
        else -> min(left, right)
    }

    private suspend fun updateReadRecordTotal(
        deviceId: String,
        bookName: String,
        bookAuthor: String,
        legacyReadTime: Long = 0L,
    ) {
        val allRemainingSessions = dao.getSessionsByBook(deviceId, bookName, bookAuthor)
        val existingRecord = dao.getReadRecord(deviceId, bookName, bookAuthor)
        val sessionTime = allRemainingSessions.sumOf { it.endTime - it.startTime }

        if (allRemainingSessions.isEmpty() && legacyReadTime <= 0L) {
            existingRecord?.let { dao.deleteReadRecord(it) }
        } else {
            val totalTime = ReadRecordTimeTotals.total(sessionTime, legacyReadTime)
            val lastRead = allRemainingSessions.maxOfOrNull { it.endTime } ?: existingRecord?.lastRead ?: 0L

            if (existingRecord == null) {
                dao.insert(
                    ReadRecord(
                        deviceId = deviceId,
                        bookName = bookName,
                        bookAuthor = bookAuthor,
                        readTime = totalTime,
                        lastRead = lastRead,
                    )
                )
            } else {
                dao.update(
                    existingRecord.copy(
                        readTime = totalTime,
                        lastRead = lastRead
                    )
                )
            }
        }
    }

    suspend fun deleteReadRecord(record: ReadRecord) {
        database.withTransaction {
            dao.deleteByName(record.bookName, record.bookAuthor)
            dao.deleteDetailByName(record.bookName, record.bookAuthor)
            dao.deleteSessionByName(record.bookName, record.bookAuthor)
        }
    }

    suspend fun clearReadRecords() {
        database.withTransaction {
            dao.clearReadRecordSessions()
            dao.clearReadRecordDetails()
            dao.clearReadRecords()
        }
    }

    /** 用户主动合并独立阅读记录，累加有效时长并保留旧版历史时长。返回是否实际发生了合并。 */
    suspend fun mergeIndependentReadRecordsInto(targetRecord: ReadRecord, sourceRecords: List<ReadRecord>): Boolean {
        return database.withTransaction {
            mergeIndependentReadRecords(targetRecord, sourceRecords)
        }
    }

    private suspend fun mergeSingleReadRecordInto(targetRecord: ReadRecord, sourceRecord: ReadRecord) =
        mergeIndependentReadRecords(targetRecord, listOf(sourceRecord))

    private suspend fun mergeIndependentReadRecords(targetRecord: ReadRecord, sourceRecords: List<ReadRecord>): Boolean {
        val resolvedTarget = if (targetRecord.deviceId.isBlank()) {
            dao.getReadRecordsByName(targetRecord.bookName, targetRecord.bookAuthor).maxByOrNull { it.lastRead }
        } else {
            dao.getReadRecord(targetRecord.deviceId, targetRecord.bookName, targetRecord.bookAuthor)
        }
        val targetDeviceId = resolvedTarget?.deviceId ?: targetRecord.deviceId.ifBlank { sourceRecords.firstOrNull()?.deviceId ?: return false }
        val target = resolvedTarget ?: targetRecord.copy(deviceId = targetDeviceId, readTime = 0L)
        val sources = sourceRecords.mapNotNull { source ->
            dao.getReadRecord(source.deviceId, source.bookName, source.bookAuthor)
        }.distinctBy { Triple(it.deviceId, it.bookName, it.bookAuthor) }
            .filterNot { it.deviceId == targetDeviceId && it.bookName == target.bookName && it.bookAuthor == target.bookAuthor }
        if (sources.isEmpty()) return false

        val targetSessions = dao.getSessionsByBook(targetDeviceId, target.bookName, target.bookAuthor)
        val targetDetails = dao.getDetailsByBook(targetDeviceId, target.bookName, target.bookAuthor)
        val sourceSessions = sources.associateWith { dao.getSessionsByBook(it.deviceId, it.bookName, it.bookAuthor) }
        val sourceDetails = sources.associateWith { dao.getDetailsByBook(it.deviceId, it.bookName, it.bookAuthor) }
        val targetLegacy = ReadRecordTimeTotals.legacy(target.readTime, targetSessions.sumOf { it.endTime - it.startTime })
        val sourceLegacy = sources.sumOf { source ->
            ReadRecordTimeTotals.legacy(source.readTime, sourceSessions.getValue(source).sumOf { it.endTime - it.startTime })
        }

        rebuildMergedDetails(target, targetDetails, targetSessions, sourceDetails, sourceSessions)
        sources.forEach { source ->
            sourceSessions.getValue(source).forEach { session ->
                dao.updateSession(session.copy(deviceId = targetDeviceId, bookName = target.bookName, bookAuthor = target.bookAuthor))
            }
            dao.deleteReadRecord(source)
        }
        dao.deleteDuplicateSessionsByBook(targetDeviceId, target.bookName, target.bookAuthor)
        dao.insert(target.copy(lastRead = maxOf(target.lastRead, sources.maxOf { it.lastRead })))
        updateReadRecordTotal(targetDeviceId, target.bookName, target.bookAuthor, targetLegacy + sourceLegacy)
        return true
    }

    private suspend fun rebuildMergedDetails(
        target: ReadRecord,
        targetDetails: List<ReadRecordDetail>,
        targetSessions: List<ReadRecordSession>,
        sourceDetails: Map<ReadRecord, List<ReadRecordDetail>>,
        sourceSessions: Map<ReadRecord, List<ReadRecordSession>>,
    ) {
        val allDetails = targetDetails + sourceDetails.values.flatten()
        val originalSessions = targetSessions + sourceSessions.values.flatten()
        val sessionsByRecord = originalSessions.groupBy { Triple(it.deviceId, it.bookName, it.bookAuthor) }
        val sessionsByDate = originalSessions
            .map { it.copy(deviceId = target.deviceId, bookName = target.bookName, bookAuthor = target.bookAuthor) }
            .distinctBy { listOf(it.bookName, it.bookAuthor, it.startTime, it.endTime, it.words) }
            .groupBy { it.startTime.toDateString() }
        val detailsByDate = allDetails.groupBy { it.date }
        dao.deleteDetailsByBook(target.deviceId, target.bookName, target.bookAuthor)
        sourceDetails.keys.forEach { dao.deleteDetailsByBook(it.deviceId, it.bookName, it.bookAuthor) }
        (sessionsByDate.keys + detailsByDate.keys).forEach { date ->
            val sessions = sessionsByDate[date].orEmpty()
            val details = detailsByDate[date].orEmpty()
            val legacyTime = details.sumOf { detail ->
                ReadRecordTimeTotals.legacy(detail.readTime, sessionsByRecord[Triple(detail.deviceId, detail.bookName, detail.bookAuthor)]
                    .orEmpty().filter { it.startTime.toDateString() == date }.sumOf { it.endTime - it.startTime })
            }
            val legacyWords = details.sumOf { detail ->
                (detail.readWords - sessionsByRecord[Triple(detail.deviceId, detail.bookName, detail.bookAuthor)]
                    .orEmpty().filter { it.startTime.toDateString() == date }.sumOf { it.words }).coerceAtLeast(0L)
            }
            val sessionTime = sessions.sumOf { it.endTime - it.startTime }
            val sessionWords = sessions.sumOf { it.words }
            if (sessionTime + legacyTime > 0L || sessionWords + legacyWords > 0L) {
                dao.insertDetail(ReadRecordDetail(
                    deviceId = target.deviceId, bookName = target.bookName, bookAuthor = target.bookAuthor, date = date,
                    readTime = sessionTime + legacyTime, readWords = sessionWords + legacyWords,
                    firstReadTime = details.map { it.firstReadTime }.filter { it > 0L }.plus(sessions.map { it.startTime }.filter { it > 0L }).minOrNull() ?: 0L,
                    lastReadTime = maxOf(details.maxOfOrNull { it.lastReadTime } ?: 0L, sessions.maxOfOrNull { it.endTime } ?: 0L),
                ))
            }
        }
    }

    /** 清理字段完全相同的阅读时段记录，并根据剩余记录重建汇总记录。 */
    suspend fun repairDuplicateSessions(): Int {
        return database.withTransaction {
            val sessionsBefore = dao.allSession
            val recordsBefore = dao.all.associateBy { Triple(it.deviceId, it.bookName, it.bookAuthor) }
            val detailsBefore = dao.allDetail.groupBy { Triple(it.deviceId, it.bookName, it.bookAuthor) }
            val affectedKeys = sessionsBefore
                .map { Triple(it.deviceId, it.bookName, it.bookAuthor) }
                .toSet()
            dao.deleteDuplicateSessions()
            affectedKeys.forEach { (deviceId, bookName, bookAuthor) ->
                val oldSessions = sessionsBefore.filter {
                    it.deviceId == deviceId && it.bookName == bookName && it.bookAuthor == bookAuthor
                }
                val legacyReadTime = recordsBefore[Triple(deviceId, bookName, bookAuthor)]?.let {
                    ReadRecordTimeTotals.legacy(it.readTime, oldSessions.sumOf { session -> session.endTime - session.startTime })
                } ?: 0L
                updateReadRecordTotal(deviceId, bookName, bookAuthor, legacyReadTime)
                rebuildDetailsAfterSessionRepair(
                    deviceId,
                    bookName,
                    bookAuthor,
                    oldSessions,
                    detailsBefore[Triple(deviceId, bookName, bookAuthor)].orEmpty(),
                )
            }
            return@withTransaction sessionsBefore.size - dao.allSession.size
        }
    }

    private suspend fun rebuildDetailsAfterSessionRepair(
        deviceId: String,
        bookName: String,
        bookAuthor: String,
        oldSessions: List<ReadRecordSession>,
        oldDetails: List<ReadRecordDetail>,
    ) {
        val newSessions = dao.getSessionsByBook(deviceId, bookName, bookAuthor)
        val oldSessionsByDate = oldSessions.groupBy { it.startTime.toDateString() }
        val newSessionsByDate = newSessions.groupBy { it.startTime.toDateString() }
        val detailsByDate = oldDetails.associateBy { it.date }
        val dates = (oldSessionsByDate.keys + newSessionsByDate.keys + detailsByDate.keys)
        dates.forEach { date ->
            val oldDetail = detailsByDate[date]
            val oldSessionTime = oldSessionsByDate[date].orEmpty().sumOf { it.endTime - it.startTime }
            val oldSessionWords = oldSessionsByDate[date].orEmpty().sumOf { it.words }
            val legacyTime = oldDetail?.let { (it.readTime - oldSessionTime).coerceAtLeast(0L) } ?: 0L
            val legacyWords = oldDetail?.let { (it.readWords - oldSessionWords).coerceAtLeast(0L) } ?: 0L
            val sessions = newSessionsByDate[date].orEmpty()
            val readTime = sessions.sumOf { it.endTime - it.startTime } + legacyTime
            val readWords = sessions.sumOf { it.words } + legacyWords
            if (readTime <= 0L && readWords <= 0L) {
                oldDetail?.let { dao.deleteDetail(it) }
            } else {
                dao.insertDetail((oldDetail ?: ReadRecordDetail(
                    deviceId = deviceId,
                    bookName = bookName,
                    bookAuthor = bookAuthor,
                    date = date,
                )).copy(
                    readTime = readTime,
                    readWords = readWords,
                    firstReadTime = listOfNotNull(
                        oldDetail?.firstReadTime?.takeIf { it > 0L },
                        sessions.map { it.startTime }.filter { it > 0L }.minOrNull(),
                    ).minOrNull() ?: 0L,
                    lastReadTime = maxOf(
                        oldDetail?.lastReadTime ?: 0L,
                        sessions.maxOfOrNull { it.endTime } ?: 0L,
                    ),
                ))
            }
        }
    }

    /** 规范化书名/作者，并同步合并汇总、日期详情和阅读时段记录中的碰撞记录。 */
    suspend fun repairReadRecordIdentities(): ReadRecordRepairReport {
        return database.withTransaction {
            var merged = 0
            var normalized = 0
            var exceptions = 0
            dao.all.forEach { record ->
                val name = ReadRecordIdentity.bookName(record.bookName)
                val author = ReadRecordIdentity.author(record.bookAuthor)
                if (name != record.bookName || author != record.bookAuthor) {
                    runCatching {
                        mergeIndependentReadRecordsInto(ReadRecord(record.deviceId, name, author), listOf(record))
                        merged++
                        normalized++
                    }.onFailure { exceptions++ }
                }
            }
            dao.allDetail.forEach { detail ->
                val normalized = detail.copy(
                    bookName = ReadRecordIdentity.bookName(detail.bookName),
                    bookAuthor = ReadRecordIdentity.author(detail.bookAuthor),
                )
                if (normalized.bookName != detail.bookName || normalized.bookAuthor != detail.bookAuthor) {
                    val existing = dao.getDetail(normalized.deviceId, normalized.bookName, normalized.bookAuthor, normalized.date)
                    if (existing == null) dao.insertDetail(normalized)
                    else dao.insertDetail(existing.copy(
                        readTime = existing.readTime + detail.readTime,
                        readWords = existing.readWords + detail.readWords,
                        firstReadTime = minPositive(existing.firstReadTime, detail.firstReadTime),
                        lastReadTime = max(existing.lastReadTime, detail.lastReadTime),
                    ))
                    dao.deleteDetail(detail)
                }
            }
            dao.allSession.forEach { session ->
                val normalized = session.copy(
                    bookName = ReadRecordIdentity.bookName(session.bookName),
                    bookAuthor = ReadRecordIdentity.author(session.bookAuthor),
                )
                if (normalized.bookName != session.bookName || normalized.bookAuthor != session.bookAuthor) {
                    val collision = dao.getSession(
                        normalized.deviceId,
                        normalized.bookName,
                        normalized.bookAuthor,
                        normalized.startTime,
                        normalized.endTime,
                        normalized.words,
                    )
                    if (collision == null) dao.updateSession(normalized) else dao.deleteSession(session)
                }
            }
            dao.deleteDuplicateSessions()
            return@withTransaction ReadRecordRepairReport(
                mergedCount = merged,
                exceptionCount = exceptions,
                normalizedRecordCount = normalized,
            )
        }
    }

    /** 只读扫描阅读记录问题，不修改数据库；结果用于展示可修复项数量。 */
    suspend fun scanReadRecordIssues(): ReadRecordRepairReport {
        val records = dao.all
        val details = dao.allDetail
        val sessions = dao.allSession
        val duplicateSessions = sessions.size - sessions.distinctBy {
            listOf(it.deviceId, it.bookName, it.bookAuthor, it.startTime, it.endTime, it.words)
        }.size
        val duplicateRecords = records.size - records.distinctBy {
            listOf(it.deviceId, ReadRecordIdentity.bookName(it.bookName), ReadRecordIdentity.author(it.bookAuthor))
        }.size
        val duplicateDetails = details.size - details.distinctBy {
            listOf(it.deviceId, ReadRecordIdentity.bookName(it.bookName), ReadRecordIdentity.author(it.bookAuthor), it.date)
        }.size
        val normalized = records.count {
            it.bookName != ReadRecordIdentity.bookName(it.bookName) ||
                it.bookAuthor != ReadRecordIdentity.author(it.bookAuthor)
        }
        return ReadRecordRepairReport(
            duplicateSessionCount = duplicateSessions,
            mergedCount = duplicateRecords + duplicateDetails,
            normalizedRecordCount = normalized,
        )
    }

    /*
     * 以阅读时段记录为权威重建汇总与每日明细，并保留未被会话覆盖的历史时长。
     *
     * 备份恢复后调用：会话按身份去重导入（幂等），汇总/明细导入取已有与导入两者中的较大值，
     * 再按会话重算，可避免同一备份重复导入导致时长翻倍，同时正确合并跨设备的会话时长。
     */
    /** 备份恢复后按取大值原则重算汇总，保证重复恢复幂等。 */
    suspend fun reconcileRestoredReadRecordTotals() {
        database.withTransaction {
            dao.all.forEach { record ->
                val sessions = dao.getSessionsByBook(record.deviceId, record.bookName, record.bookAuthor)
                if (sessions.isEmpty()) return@forEach
                dao.update(record.copy(
                    readTime = maxOf(record.readTime, sessions.sumOf { it.endTime - it.startTime }),
                    lastRead = maxOf(record.lastRead, sessions.maxOf { it.endTime }),
                ))
            }
            dao.allDetail.forEach { detail ->
                val sessions = dao.getSessionsByBook(detail.deviceId, detail.bookName, detail.bookAuthor)
                    .filter {
                        it.startTime.toDateString() == detail.date
                    }
                if (sessions.isEmpty()) return@forEach
                dao.insertDetail(detail.copy(
                    readTime = maxOf(detail.readTime, sessions.sumOf { it.endTime - it.startTime }),
                    readWords = maxOf(detail.readWords, sessions.sumOf { it.words }),
                    firstReadTime = minPositive(
                        detail.firstReadTime,
                        sessions.map { it.startTime }.filter { it > 0L }.minOrNull() ?: 0L,
                    ),
                    lastReadTime = maxOf(detail.lastReadTime, sessions.maxOf { it.endTime }),
                ))
            }
        }
    }

}
