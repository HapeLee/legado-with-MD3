package io.legado.app.data.repository

import androidx.room.withTransaction
import io.legado.app.data.AppDatabase
import io.legado.app.data.dao.ReadRecordDao
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.readRecord.ReadRecord
import io.legado.app.data.entities.readRecord.ReadRecordAliasAction
import io.legado.app.data.entities.readRecord.ReadRecordAliasDecision
import io.legado.app.data.entities.readRecord.ReadRecordDetail
import io.legado.app.data.entities.readRecord.ReadRecordIdentity
import io.legado.app.data.entities.readRecord.ReadRecordRepairReport
import io.legado.app.data.entities.readRecord.ReadRecordSession
import io.legado.app.data.entities.readRecord.ReadRecordTimeTotals
import io.legado.app.data.entities.readRecord.ReadRecordTimelineDay
import io.legado.app.data.entities.readRecord.CONTINUOUS_READ_SESSION_GAP_MILLIS
import io.legado.app.data.local.preferences.LocalPreferencesKeys
import io.legado.app.domain.model.BookMatchKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.time.ZoneId
import java.time.LocalDate
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

    /** 将跨自然日的阅读会话按本地时区午夜拆分，时间线仍保留原始整段会话。 */
    private fun ReadRecordSession.durationByDate(): Map<String, Long> {
        if (endTime <= startTime) return emptyMap()
        val zone = ZoneId.systemDefault()
        var cursor = startTime
        val result = linkedMapOf<String, Long>()
        while (cursor < endTime) {
            val date = java.time.Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate()
            val nextMidnight = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val segmentEnd = minOf(endTime, nextMidnight)
            result[date.toString()] = (result[date.toString()] ?: 0L) + (segmentEnd - cursor)
            cursor = segmentEnd
        }
        return result
    }

    private fun List<ReadRecordSession>.durationByDate(): Map<String, Long> =
        flatMap { session -> session.durationByDate().entries }
            .groupingBy { it.key }
            .fold(0L) { total, entry -> total + entry.value }

    private fun List<ReadRecordSession>.wordsByStartDate(): Map<String, Long> =
        groupBy { it.startTime.toDateString() }
            .mapValues { (_, sessions) -> sessions.sumOf { it.words } }

    private fun ReadRecordSession.boundsOnDate(date: String): Pair<Long, Long>? {
        val zone = ZoneId.systemDefault()
        val dayStart = LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = LocalDate.parse(date).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val segmentStart = maxOf(startTime, dayStart)
        val segmentEnd = minOf(endTime, dayEnd)
        return if (segmentStart < segmentEnd) segmentStart to segmentEnd else null
    }

    val readRecordEnabled: Flow<Boolean> =
        localPreferencesRepository.getPreference(LocalPreferencesKeys.ENABLE_READ_RECORD, true)

    val skipDeleteConfirm: Flow<Boolean> =
        localPreferencesRepository.getBoolean(LocalPreferencesKeys.READ_RECORD_SKIP_DELETE_CONFIRM.name, false)

    suspend fun setReadRecordEnabled(enabled: Boolean) {
        localPreferencesRepository.updatePreference(LocalPreferencesKeys.ENABLE_READ_RECORD, enabled)
    }

    suspend fun setSkipDeleteConfirm(enabled: Boolean) {
        localPreferencesRepository.putBoolean(LocalPreferencesKeys.READ_RECORD_SKIP_DELETE_CONFIRM.name, enabled)
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
        // 保留书籍副本维度，避免同名作品的不同副本的会话被误判为重复而丢弃。
        return dao.getAllSessions().map { sessions ->
            sessions.distinctBy {
                listOf(it.bookName, it.bookAuthor, it.bookUrl, it.startTime, it.endTime, it.words)
            }
        }
    }

    fun getBookSessions(bookName: String, bookAuthor: String): Flow<List<ReadRecordSession>> {
        // 时间线按书名、作者和阅读时段内容去重，避免同步副本重复计时。
        return dao.getAllSessions().map { sessions ->
            sessions.asSequence()
                .filter { it.bookName == bookName && it.bookAuthor == bookAuthor }
                .distinctBy {
                    listOf(
                        it.bookName,
                        it.bookAuthor,
                        it.bookUrl,
                        it.startTime,
                        it.endTime,
                        it.words
                    )
                }
                .toList()
        }
    }

    /**
     * 单个书籍副本的阅读时段。
     *
     * 书架允许同名作者作品共存，[getBookSessions] 返回的是作品维度（所有副本），而这里只取
     * 挂在指定 bookUrl 下的会话。
     *
     * 未归属会话（`bookUrl` 为空）是升级前写入的历史，本身不携带副本信息，按下面的规则处理：
     * - 作品在书架里只有这一个副本时，计入该副本。否则老用户升级后只要读一次，新会话就有归属，
     *   历史会话会从详情页凭空消失；
     * - 有多个副本时不计入任何副本，避免同一段历史在两个副本视图里各显示一次。
     * 作品维度口径（[getBookReadTime]）始终包含未归属会话，不受这里影响。
     */
    fun getBookCopySessions(
        bookUrl: String,
        bookName: String,
        bookAuthor: String,
    ): Flow<List<ReadRecordSession>> {
        if (bookUrl.isBlank()) return getBookSessions(bookName, bookAuthor)
        // 同时监听 books 表：副本的增删或改名必须让这里的「是否还有别的副本」重新判定，
        // 否则共存出第二个副本后，原书详情页会一直显示已不属于它的历史时长。
        return combine(dao.getAllSessions(), database.bookDao.flowBookCount()) { sessions, _ ->
            val scoped = sessions.filter { it.bookName == bookName && it.bookAuthor == bookAuthor }
            val owned = scoped.filter { it.bookUrl == bookUrl }
            val unowned = scoped.filter { it.bookUrl.isBlank() }
            val includeUnowned = unowned.isNotEmpty() &&
                    countShelfCopies(bookName, bookAuthor) <= 1
            (if (includeUnowned) owned + unowned else owned)
                .distinctBy {
                    listOf(it.bookName, it.bookAuthor, it.startTime, it.endTime, it.words)
                }
        }
            .flowOn(Dispatchers.IO)
    }

    /**
     * 书架中与 (bookName, bookAuthor) 视为同一作品的作品数。
     *
     * 走 [BookMatchKey] 的比对口径，与加入书架查重完全一致 —— 阅读记录的副本归属判断也依赖它，
     * 两处结论不能互相矛盾。这里连同 [getBookCopySessions] 一起构成「这部作品到底有几个副本」
     * 的唯一实现。
     */
    suspend fun countShelfCopies(bookName: String, bookAuthor: String): Int =
        withContext(Dispatchers.IO) {
            val name = BookMatchKey.of(bookName)
            if (name.isBlank()) return@withContext 0
            val author = BookMatchKey.of(bookAuthor)
            database.bookDao.getShelfBookSummaries()
                .count { candidate ->
                    BookMatchKey.of(candidate.name) == name &&
                            BookMatchKey.authorCompatible(author, BookMatchKey.of(candidate.author))
                }
        }

    /** 单个书籍副本的累计阅读时长（毫秒），口径与 [getBookCopySessions] 一致。 */
    fun getBookCopyReadTime(
        bookUrl: String,
        bookName: String,
        bookAuthor: String,
    ): Flow<Long> = getBookCopySessions(bookUrl, bookName, bookAuthor)
        .map { sessions -> sessions.sumOf { it.endTime - it.startTime } }

    /** 单个书籍副本按天聚合的阅读时间线。 */
    fun getBookCopyTimelineDays(
        bookUrl: String,
        bookName: String,
        bookAuthor: String,
    ): Flow<List<ReadRecordTimelineDay>> = getBookCopySessions(bookUrl, bookName, bookAuthor)
        .map { sessions -> sessions.toTimelineDays() }

    /** 作品维度的累计时长，包含同名作者共存的所有副本，用于阅读统计总览。 */
    fun getBookReadTime(bookName: String, bookAuthor: String): Flow<Long> {
        // 统计所有设备的汇总时长，与跨设备时间线保持一致。
        return dao.getReadTimeFlow(bookName, bookAuthor).map { it ?: 0L }
    }

    /**
     * 书籍副本合并/迁移后，把原副本名下的阅读时段整体改挂到目标副本。
     *
     * 只为「同一段历史换了一个副本」服务：会话是平移与重算，绝不做 sum 合并，
     * 否则同一段阅读会被计入两次。汇总与每日明细均以会话为权威重算，未被会话覆盖的
     * 历史时长按原 key 保留。
     *
     * 调用方必须已经处于事务中（换源会先删旧书再插新书，阅读会话必须和它同生共死）。
     */
    internal suspend fun reassignBookReadSessions(oldBook: Book, newBook: Book) {
        if (oldBook.bookUrl == newBook.bookUrl) return
        val oldName = ReadRecordIdentity.bookName(oldBook.name)
        val oldAuthor = ReadRecordIdentity.author(oldBook.author)
        val newName = ReadRecordIdentity.bookName(newBook.name)
        val newAuthor = ReadRecordIdentity.author(newBook.author)
        val deviceIds = dao.getSessionsByBookUrl(oldBook.bookUrl)
            .mapTo(linkedSetOf()) { it.deviceId }
        // 书名作者不变时，会话改挂只换了副本归属，作品维度的聚合完全不变，无需重算明细。
        val sameIdentity = oldName == newName && oldAuthor == newAuthor
        // 明细里已计入旧会话的时间只能靠改挂前的快照还原，因此必须在 UPDATE 前取数。
        val oldKeySessions =
            deviceIds.associateWith { dao.getSessionsByBook(it, oldName, oldAuthor) }
        val oldKeyDetails = deviceIds.associateWith { dao.getDetailsByBook(it, oldName, oldAuthor) }
        val newKeySessions =
            deviceIds.associateWith { dao.getSessionsByBook(it, newName, newAuthor) }
        val newKeyDetails = deviceIds.associateWith { dao.getDetailsByBook(it, newName, newAuthor) }
        // 汇总里的「历史时长」（没有会话支撑的部分）也属于对应 key，改挂前先算出来，
        // 否则旧 key 会把自己已经不属于它的会话时间当成历史时长保留下来，导致同一段时长被算两次。
        val oldKeyRecords = deviceIds.associateWith { dao.getReadRecord(it, oldName, oldAuthor) }
        val newKeyRecords = deviceIds.associateWith { dao.getReadRecord(it, newName, newAuthor) }
        dao.reassignSessionOwnership(
            oldBookUrl = oldBook.bookUrl,
            newBookUrl = newBook.bookUrl,
            newBookName = newName,
            newBookAuthor = newAuthor,
        )
        if (deviceIds.isEmpty() || sameIdentity) return
        deviceIds.forEach { deviceId ->
            dao.deleteDuplicateSessionsByBook(deviceId, newName, newAuthor)
            rebuildDetailsAfterSessionRepair(
                deviceId,
                oldName,
                oldAuthor,
                oldKeySessions.getValue(deviceId),
                oldKeyDetails.getValue(deviceId),
            )
            rebuildDetailsAfterSessionRepair(
                deviceId,
                newName,
                newAuthor,
                newKeySessions.getValue(deviceId),
                newKeyDetails.getValue(deviceId),
            )
            updateReadRecordTotal(
                deviceId,
                oldName,
                oldAuthor,
                ReadRecordTimeTotals.legacy(
                    oldKeyRecords.getValue(deviceId)?.readTime ?: 0L,
                    oldKeySessions.getValue(deviceId).sumOf { it.endTime - it.startTime },
                ),
            )
            updateReadRecordTotal(
                deviceId,
                newName,
                newAuthor,
                ReadRecordTimeTotals.legacy(
                    newKeyRecords.getValue(deviceId)?.readTime ?: 0L,
                    newKeySessions.getValue(deviceId).sumOf { it.endTime - it.startTime },
                ),
            )
        }
    }

    private fun List<ReadRecordSession>.toTimelineDays(): List<ReadRecordTimelineDay> =
        groupBy { it.startTime.toDateString() }
            .toSortedMap(compareByDescending { it })
            .map { (date, daySessions) ->
                ReadRecordTimelineDay(
                    date = date,
                    sessions = daySessions.sortedByDescending { it.startTime }
                )
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
                normalizedSession.bookUrl,
                normalizedSession.startTime,
                normalizedSession.endTime,
                normalizedSession.words
            )
            if (existingSession != null) return@withTransaction

            val segmentDuration = normalizedSession.endTime - normalizedSession.startTime
            dao.insertSession(normalizedSession)
            normalizedSession.durationByDate().forEach { (date, duration) ->
                updateReadRecordDetail(
                    normalizedSession,
                    duration,
                    if (date == normalizedSession.startTime.toDateString()) normalizedSession.words else 0L,
                    date,
                )
            }
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
        val (segmentStart, segmentEnd) = session.boundsOnDate(dateString) ?: return
        val existingDetail = dao.getDetail(
            session.deviceId,
            session.bookName,
            session.bookAuthor,
            dateString
        )
        if (existingDetail != null) {
            existingDetail.readTime += durationDelta
            existingDetail.readWords += wordsDelta
            existingDetail.firstReadTime = minPositive(existingDetail.firstReadTime, segmentStart)
            existingDetail.lastReadTime = max(existingDetail.lastReadTime, segmentEnd)
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
                    firstReadTime = segmentStart,
                    lastReadTime = segmentEnd
                )
            )
        }
    }

    suspend fun deleteDetail(detail: ReadRecordDetail): Boolean {
        return database.withTransaction {
            val existed = dao.allDetail.any {
                it.bookName == detail.bookName && it.bookAuthor == detail.bookAuthor && it.date == detail.date
            }
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
            existed
        }
    }

    suspend fun deleteSession(requestedSession: ReadRecordSession): Boolean {
        return database.withTransaction {
            // 时间线展示项可能经过合并或跨设备聚合，优先使用 Room 行 ID 定位真实记录，
            // 避免使用展示层的结束时间反查不到底层会话。
            val session = if (requestedSession.id > 0L) {
                dao.allSession.firstOrNull { it.id == requestedSession.id }
            } else {
                dao.allSession.firstOrNull {
                    it.bookName == requestedSession.bookName &&
                        it.bookAuthor == requestedSession.bookAuthor &&
                        it.startTime == requestedSession.startTime &&
                        it.endTime == requestedSession.endTime &&
                        it.words == requestedSession.words
                }
            } ?: return@withTransaction false
            val sessionGroup = dao.allSession
                .asSequence()
                .filter {
                    it.bookName == session.bookName &&
                        it.bookAuthor == session.bookAuthor &&
                        it.startTime.toDateString() == session.startTime.toDateString()
                }
                .sortedBy { it.startTime }
                .toList()
                .let { sessions ->
                    val groups = sessions.fold(mutableListOf<MutableList<ReadRecordSession>>()) { groups, current ->
                        val previous = groups.lastOrNull()?.lastOrNull()
                        if (previous != null && current.startTime - previous.endTime <= CONTINUOUS_READ_SESSION_GAP_MILLIS) {
                            groups.last().add(current)
                        } else {
                            groups.add(mutableListOf(current))
                        }
                        groups
                    }
                    groups.firstOrNull { group -> group.any { it.id == session.id } }
                        ?: groups.firstOrNull { group ->
                            group.any {
                                it.startTime == session.startTime &&
                                    it.endTime == session.endTime &&
                                    it.words == session.words
                            }
                        }
                        .orEmpty()
                }
            val affectedDevices = sessionGroup.mapTo(linkedSetOf()) { it.deviceId }
            val legacyReadTimes = affectedDevices.associateWith { deviceId ->
                val record = dao.getReadRecord(deviceId, session.bookName, session.bookAuthor)
                val sessionTime = dao.getSessionsByBook(deviceId, session.bookName, session.bookAuthor)
                    .sumOf { it.endTime - it.startTime }
                ((record?.readTime ?: 0L) - sessionTime).coerceAtLeast(0L)
            }
            val detailsBeforeDelete = affectedDevices.associateWith { deviceId ->
                dao.getDetailsByBook(deviceId, session.bookName, session.bookAuthor)
            }
            val sessionsBeforeDelete = affectedDevices.associateWith { deviceId ->
                dao.getSessionsByBook(deviceId, session.bookName, session.bookAuthor)
            }
            sessionGroup.forEach { dao.deleteSession(it) }
            affectedDevices.forEach { deviceId ->
                    rebuildDetailsAfterSessionMutation(
                        deviceId,
                        session.bookName,
                        session.bookAuthor,
                        sessionsBeforeDelete[deviceId].orEmpty(),
                        detailsBeforeDelete[deviceId].orEmpty(),
                    )
                    updateReadRecordTotal(
                        deviceId,
                        session.bookName,
                        session.bookAuthor,
                        legacyReadTimes[deviceId] ?: 0L,
                    )
                }
            sessionGroup.isNotEmpty()
        }
    }

    private suspend fun rebuildDetailsAfterSessionMutation(
        deviceId: String,
        bookName: String,
        bookAuthor: String,
        oldSessions: List<ReadRecordSession>,
        oldDetails: List<ReadRecordDetail>,
    ) {
        val newSessions = dao.getSessionsByBook(deviceId, bookName, bookAuthor)
        val oldDurations = oldSessions.durationByDate()
        val newDurations = newSessions.durationByDate()
        val oldWords = oldSessions.wordsByStartDate()
        val newWords = newSessions.wordsByStartDate()
        val detailsByDate = oldDetails.associateBy { it.date }
        val dates = (oldDurations.keys + newDurations.keys + oldDetails.map { it.date }).toSet()
        dates.forEach { date ->
            val oldDetail = detailsByDate[date]
            val legacyTime = ((oldDetail?.readTime ?: 0L) - (oldDurations[date] ?: 0L)).coerceAtLeast(0L)
            val legacyWords = ((oldDetail?.readWords ?: 0L) - (oldWords[date] ?: 0L)).coerceAtLeast(0L)
            val readTime = legacyTime + (newDurations[date] ?: 0L)
            val readWords = legacyWords + (newWords[date] ?: 0L)
            if (readTime <= 0L && readWords <= 0L) {
                oldDetail?.let { dao.deleteDetail(it) }
            } else {
                val dayBounds = newSessions.mapNotNull { it.boundsOnDate(date) }
                dao.insertDetail((oldDetail ?: ReadRecordDetail(deviceId, bookName, bookAuthor, date)).copy(
                    readTime = readTime,
                    readWords = readWords,
                    firstReadTime = dayBounds.minOfOrNull { it.first } ?: oldDetail?.firstReadTime ?: 0L,
                    lastReadTime = dayBounds.maxOfOrNull { it.second } ?: oldDetail?.lastReadTime ?: 0L,
                ))
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

    suspend fun deleteReadRecord(record: ReadRecord): Boolean {
        return database.withTransaction {
            val existed = dao.all.any {
                it.bookName == record.bookName && it.bookAuthor == record.bookAuthor
            }
            dao.deleteByName(record.bookName, record.bookAuthor)
            dao.deleteDetailByName(record.bookName, record.bookAuthor)
            dao.deleteSessionByName(record.bookName, record.bookAuthor)
            existed
        }
    }

    suspend fun clearReadRecords(): Boolean {
        return database.withTransaction {
            val existed = dao.all.isNotEmpty() || dao.allDetail.isNotEmpty() || dao.allSession.isNotEmpty()
            dao.clearReadRecordSessions()
            dao.clearReadRecordDetails()
            dao.clearReadRecords()
            existed
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
        val durationsByRecord = sessionsByRecord.mapValues { it.value.durationByDate() }
        val wordsByRecord = sessionsByRecord.mapValues { it.value.wordsByStartDate() }
        val normalizedSessions = originalSessions
            .map { it.copy(deviceId = target.deviceId, bookName = target.bookName, bookAuthor = target.bookAuthor) }
            .distinctBy { listOf(it.bookName, it.bookAuthor, it.startTime, it.endTime, it.words) }
        val sessionsByDate = normalizedSessions
            .flatMap { session -> session.durationByDate().keys.map { it to session } }
            .groupBy({ it.first }, { it.second })
        val detailsByDate = allDetails.groupBy { it.date }
        dao.deleteDetailsByBook(target.deviceId, target.bookName, target.bookAuthor)
        sourceDetails.keys.forEach { dao.deleteDetailsByBook(it.deviceId, it.bookName, it.bookAuthor) }
        (sessionsByDate.keys + detailsByDate.keys).forEach { date ->
            val sessions = sessionsByDate[date].orEmpty()
            val details = detailsByDate[date].orEmpty()
            val legacyTime = details.sumOf { detail ->
                ReadRecordTimeTotals.legacy(detail.readTime, durationsByRecord[Triple(detail.deviceId, detail.bookName, detail.bookAuthor)]?.get(date) ?: 0L)
            }
            val legacyWords = details.sumOf { detail ->
                (detail.readWords - (wordsByRecord[Triple(detail.deviceId, detail.bookName, detail.bookAuthor)]?.get(date) ?: 0L)).coerceAtLeast(0L)
            }
            val sessionTime = sessions.sumOf { it.durationByDate()[date] ?: 0L }
            val sessionWords = sessions.filter { it.startTime.toDateString() == date }.sumOf { it.words }
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
        val oldSessionsByDate = oldSessions.durationByDate()
        val newSessionsByDate = newSessions.durationByDate()
        val oldWordsByDate = oldSessions.wordsByStartDate()
        val newWordsByDate = newSessions.wordsByStartDate()
        val detailsByDate = oldDetails.associateBy { it.date }
        val dates = (oldSessionsByDate.keys + newSessionsByDate.keys + detailsByDate.keys)
        dates.forEach { date ->
            val oldDetail = detailsByDate[date]
            val oldSessionTime = oldSessionsByDate[date] ?: 0L
            val oldSessionWords = oldWordsByDate[date] ?: 0L
            val legacyTime = oldDetail?.let { (it.readTime - oldSessionTime).coerceAtLeast(0L) } ?: 0L
            val legacyWords = oldDetail?.let { (it.readWords - oldSessionWords).coerceAtLeast(0L) } ?: 0L
            val readTime = (newSessionsByDate[date] ?: 0L) + legacyTime
            val readWords = (newWordsByDate[date] ?: 0L) + legacyWords
            val sessions = newSessions.filter { it.startTime.toDateString() == date }
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
                        normalized.bookUrl,
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
                val sessionTime = sessions.durationByDate()[detail.date] ?: 0L
                val sessionWords = sessions.wordsByStartDate()[detail.date] ?: 0L
                if (sessionTime <= 0L && sessionWords <= 0L) return@forEach
                dao.insertDetail(detail.copy(
                    readTime = maxOf(detail.readTime, sessionTime),
                    readWords = maxOf(detail.readWords, sessionWords),
                    firstReadTime = minPositive(
                        detail.firstReadTime,
                        sessions.filter { it.startTime.toDateString() == detail.date }
                            .map { it.startTime }.filter { it > 0L }.minOrNull() ?: 0L,
                    ),
                    lastReadTime = maxOf(detail.lastReadTime, sessions.maxOf { it.endTime }),
                ))
            }
        }
    }

}
