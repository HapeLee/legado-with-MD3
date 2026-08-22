package io.legado.app.ui.book.readRecord

import androidx.lifecycle.ViewModel
import androidx.compose.runtime.Stable
import androidx.lifecycle.viewModelScope
import io.legado.app.data.entities.readRecord.ReadRecord
import io.legado.app.data.entities.readRecord.ReadRecordDetail
import io.legado.app.data.entities.readRecord.ReadRecordSession
import io.legado.app.data.entities.readRecord.ReadRecordRepairReport
import io.legado.app.data.local.preferences.LocalPreferencesKeys
import io.legado.app.data.repository.SettingsRepository
import io.legado.app.data.repository.BookRepository
import io.legado.app.data.repository.ReadRecordRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Stable
data class ReadRecordUiState(
    val isLoading: Boolean = true,
    val totalReadTime: Long = 0,
    val groupedRecords: ImmutableMap<String, ImmutableList<ReadRecordDetail>> = persistentMapOf(),
    val timelineRecords: ImmutableMap<String, ImmutableList<ReadRecordSession>> = persistentMapOf(),
    val latestRecords: ImmutableList<ReadRecord> = persistentListOf(),
    val selectedDate: LocalDate? = null,
    val searchKey: String? = null,
    val dailyReadCounts: ImmutableMap<LocalDate, Int> = persistentMapOf(),
    val dailyReadTimes: ImmutableMap<LocalDate, Long> = persistentMapOf(),
    val displayMode: DisplayMode = DisplayMode.AGGREGATE,
    val readRecordEnabled: Boolean = true,
    val repairReport: ReadRecordRepairReport? = null,
)

enum class DisplayMode {
    AGGREGATE,
    TIMELINE,
    LATEST
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReadRecordViewModel(
    private val repository: ReadRecordRepository,
    private val bookRepository: BookRepository,
    private val localPreferencesRepository: SettingsRepository
) : ViewModel() {

    private val _displayMode = MutableStateFlow(DisplayMode.AGGREGATE)
    val displayMode = _displayMode.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = localPreferencesRepository.getPreference(
                LocalPreferencesKeys.READ_RECORD_DISPLAY_MODE, DisplayMode.AGGREGATE.name
            ).first()
            _displayMode.value = runCatching { DisplayMode.valueOf(saved) }
                .getOrDefault(DisplayMode.AGGREGATE)
        }
    }

    private val _searchKey = MutableStateFlow("")
    private val _repairReport = MutableStateFlow<ReadRecordRepairReport?>(null)
    private val _effects = MutableSharedFlow<ReadRecordEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()
    private val _selectedDate = MutableStateFlow<LocalDate?>(null)
    val readRecordEnabled: StateFlow<Boolean> = repository.readRecordEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val loadedDataFlow = _searchKey
        .flatMapLatest { query ->
            combine(
                repository.getAllRecordDetails(query),
                repository.getLatestReadRecords(query),
                repository.getAllSessions(),
                repository.getTotalReadTime()
            ) { details, latest, sessions, totalTime ->
                LoadedData(totalTime, details, latest, sessions)
            }
        }

    val uiState: StateFlow<ReadRecordUiState> = combine(
        loadedDataFlow,
        _selectedDate,
        _searchKey,
        _displayMode,
        readRecordEnabled,
    ) { data, selectedDate, searchKey, displayMode, enabled ->
        val dateStr = selectedDate?.format(DateTimeFormatter.ISO_LOCAL_DATE)

        val dailyCounts = data.details
            .groupBy { it.date }
            .mapKeys { LocalDate.parse(it.key, DateTimeFormatter.ISO_LOCAL_DATE) }
            .mapValues { it.value.size }

        val dailyTimes = data.sessions
            .groupBy { it.startTime.toDateString() }
            .mapKeys { LocalDate.parse(it.key, DateTimeFormatter.ISO_LOCAL_DATE) }
            .mapValues { (_, sessions) ->
                sessions.sumOf { (it.endTime - it.startTime).coerceAtLeast(0L) }
            }

        val filteredDetails = data.details.filter { detail ->
            dateStr == null || detail.date == dateStr
        }

        val timelineMap = data.sessions
            .asSequence()
            .filter { session ->
                val sDate = session.startTime.toDateString()
                (dateStr == null || sDate == dateStr) &&
                        (searchKey.isEmpty() ||
                                session.bookName.contains(searchKey, ignoreCase = true) ||
                                session.bookAuthor.contains(searchKey, ignoreCase = true))
            }
            .groupBy { it.startTime.toDateString() }
            .mapValues { (_, sessions) ->
                mergeContinuousSessions(sessions).reversed()
            }
            .toSortedMap(compareByDescending { it })

        ReadRecordUiState(
            isLoading = false,
            totalReadTime = data.totalReadTime,
            groupedRecords = filteredDetails.groupBy { it.date }
                .mapValues { (_, value) -> value.toImmutableList() }
                .toImmutableMap(),
            timelineRecords = timelineMap
                .mapValues { (_, value) -> value.toImmutableList() }
                .toImmutableMap(),
            latestRecords = data.latestRecords.toImmutableList(),
            selectedDate = selectedDate,
            searchKey = searchKey,
            dailyReadCounts = dailyCounts.toImmutableMap(),
            dailyReadTimes = dailyTimes.toImmutableMap(),
            displayMode = displayMode,
            readRecordEnabled = enabled,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReadRecordUiState(isLoading = true)
    ).combine(_repairReport) { state, report -> state.copy(repairReport = report) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ReadRecordUiState(isLoading = true),
        )

    fun onIntent(intent: ReadRecordIntent) {
        when (intent) {
            is ReadRecordIntent.Search -> setSearchKey(intent.query)
            is ReadRecordIntent.SetDisplayMode -> setDisplayMode(intent.mode)
            is ReadRecordIntent.SelectDate -> setSelectedDate(intent.date)
            is ReadRecordIntent.DeleteDetail -> deleteDetail(intent.detail)
            is ReadRecordIntent.DeleteSession -> deleteSession(intent.session)
            is ReadRecordIntent.DeleteRecord -> deleteReadRecord(intent.record)
            ReadRecordIntent.ClearRecords -> clearReadRecords()
            is ReadRecordIntent.SetEnabled -> setReadRecordEnabled(intent.enabled)
            is ReadRecordIntent.MergeRecords -> mergeReadRecords(intent.target, intent.sources)
            ReadRecordIntent.ScanRepair -> scanRepair()
            ReadRecordIntent.RepairDatabase -> repairDatabase()
            ReadRecordIntent.DismissRepairReport -> _repairReport.value = null
        }
    }

    fun setSearchKey(query: String) {
        _searchKey.value = query
    }

    /** 执行只读问题扫描，并将结果放入统一 UiState。 */
    private fun scanRepair() {
        viewModelScope.launch {
            runCatching { repository.scanReadRecordIssues() }
                .onSuccess { _repairReport.value = it }
                .onFailure { _effects.tryEmit(ReadRecordEffect.ShowError(it.localizedMessage.orEmpty())) }
        }
    }

    /** 在事务中修复身份碰撞和字段完全相同的阅读时段记录。 */
    private fun repairDatabase() {
        viewModelScope.launch {
            runCatching {
                val identity = repository.repairReadRecordIdentities()
                val sessions = repository.repairDuplicateSessions()
                identity.copy(duplicateSessionCount = sessions)
            }.onSuccess { _repairReport.value = it }
                .onFailure { _effects.tryEmit(ReadRecordEffect.ShowError(it.localizedMessage.orEmpty())) }
        }
    }

    fun setDisplayMode(mode: DisplayMode) {
        _displayMode.value = mode
        viewModelScope.launch {
            localPreferencesRepository.updatePreference(
                LocalPreferencesKeys.READ_RECORD_DISPLAY_MODE, mode.name
            )
        }
    }

    fun setSelectedDate(date: LocalDate?) {
        _selectedDate.value = date
    }

    fun deleteDetail(detail: ReadRecordDetail) {
        viewModelScope.launch { repository.deleteDetail(detail) }
    }

    fun deleteSession(session: ReadRecordSession) {
        viewModelScope.launch { repository.deleteSession(session) }
    }

    fun deleteReadRecord(record: ReadRecord) {
        viewModelScope.launch { repository.deleteReadRecord(record) }
    }

    fun clearReadRecords() {
        viewModelScope.launch { repository.clearReadRecords() }
    }

    fun setReadRecordEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setReadRecordEnabled(enabled) }
    }

    private fun mergeContinuousSessions(sessions: List<ReadRecordSession>): List<ReadRecordSession> {
        if (sessions.isEmpty()) return emptyList()
        val mergedList = mutableListOf<ReadRecordSession>()
        mergedList.add(sessions.first().copy())

        val gapLimit = 20 * 60 * 1000L

        for (i in 1 until sessions.size) {
            val current = sessions[i]
            val last = mergedList.last()
            if (current.bookName == last.bookName &&
                current.bookAuthor == last.bookAuthor &&
                (current.startTime - last.endTime) <= gapLimit
            ) {
                mergedList[mergedList.lastIndex] = last.copy(endTime = current.endTime)
            } else {
                mergedList.add(current.copy())
            }
        }
        return mergedList
    }

    suspend fun getChapterTitle(bookName: String, bookAuthor: String, chapterIndexLong: Long): String? {
        return bookRepository.getChapterTitle(bookName, bookAuthor, chapterIndexLong.toInt())
    }

    suspend fun getBookCover(bookName: String, bookAuthor: String): String? {
        return bookRepository.getBookCoverByNameAndAuthor(bookName, bookAuthor)
    }

    suspend fun getMergeCandidates(targetRecord: ReadRecord): List<ReadRecord> {
        return repository.getMergeCandidates(targetRecord)
    }

    fun mergeReadRecords(targetRecord: ReadRecord, sourceRecords: List<ReadRecord>) {
        if (sourceRecords.isEmpty()) {
            _effects.tryEmit(ReadRecordEffect.ShowError(""))
            return
        }
        viewModelScope.launch {
            val merged = repository.mergeIndependentReadRecordsInto(targetRecord, sourceRecords)
            if (!merged) {
                _effects.tryEmit(ReadRecordEffect.ShowError(""))
            }
        }
    }

    private data class LoadedData(
        val totalReadTime: Long,
        val details: List<ReadRecordDetail>,
        val latestRecords: List<ReadRecord>,
        val sessions: List<ReadRecordSession>
    )

    private fun Long.toDateString(): String =
        Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate().toString()
}

sealed interface ReadRecordIntent {
    data class Search(val query: String) : ReadRecordIntent
    data class SetDisplayMode(val mode: DisplayMode) : ReadRecordIntent
    data class SelectDate(val date: LocalDate?) : ReadRecordIntent
    data class DeleteDetail(val detail: ReadRecordDetail) : ReadRecordIntent
    data class DeleteSession(val session: ReadRecordSession) : ReadRecordIntent
    data class DeleteRecord(val record: ReadRecord) : ReadRecordIntent
    data object ClearRecords : ReadRecordIntent
    data class SetEnabled(val enabled: Boolean) : ReadRecordIntent
    data class MergeRecords(val target: ReadRecord, val sources: List<ReadRecord>) : ReadRecordIntent
    data object ScanRepair : ReadRecordIntent
    data object RepairDatabase : ReadRecordIntent
    data object DismissRepairReport : ReadRecordIntent
}

sealed interface ReadRecordEffect {
    data class ShowError(val message: String) : ReadRecordEffect
}
