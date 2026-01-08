package io.legado.app.ui.book.readRecord

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.hutool.core.date.DateUtil
import io.legado.app.data.entities.readRecord.ReadRecord
import io.legado.app.data.entities.readRecord.ReadRecordDetail
import io.legado.app.data.entities.readRecord.ReadRecordSession
import io.legado.app.data.repository.BookRepository
import io.legado.app.data.repository.ReadRecordRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date

data class ReadRecordUiState(
    val isLoading: Boolean = true,
    val totalReadTime: Long = 0,
    //每日聚合明细
    val groupedRecords: Map<String, List<ReadRecordDetail>> = emptyMap(),
    //每日所有阅读会话
    val timelineRecords: Map<String, List<ReadRecordSession>> = emptyMap(),
    //最后阅读列表
    val latestRecords: List<ReadRecord> = emptyList(),
    val selectedDate: LocalDate? = null,
    val searchKey: String? = null,
)

enum class DisplayMode {
    AGGREGATE,
    TIMELINE,
    LATEST
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReadRecordViewModel(
    private val repository: ReadRecordRepository,
    private val bookRepository: BookRepository
) : ViewModel() {

    private val _displayMode = MutableStateFlow(DisplayMode.AGGREGATE)
    val displayMode = _displayMode.asStateFlow()

    private val _searchKey = MutableStateFlow("")
    private val _selectedDate = MutableStateFlow<LocalDate?>(null)

    // 数据源：直接从 Repository 获取 Flow
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
        _searchKey
    ) { data, selectedDate, searchKey ->

        val dateStr = selectedDate?.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val filteredDetails = if (dateStr != null) {
            data.details.filter { it.date == dateStr }
        } else data.details

        val timelineMap = data.sessions
            .asSequence()
            .filter { session ->
                val sDate = DateUtil.format(Date(session.startTime), "yyyy-MM-dd")
                (dateStr == null || sDate == dateStr) &&
                        (searchKey.isEmpty() || session.bookName.contains(
                            searchKey,
                            ignoreCase = true
                        ))
            }
            .groupBy { DateUtil.format(Date(it.startTime), "yyyy-MM-dd") }
            .mapValues { (_, sessions) ->
                mergeContinuousSessions(sessions).reversed()
            }

        ReadRecordUiState(
            isLoading = false,
            totalReadTime = data.totalReadTime,
            groupedRecords = filteredDetails.groupBy { it.date },
            timelineRecords = timelineMap,
            latestRecords = data.latestRecords,
            selectedDate = selectedDate,
            searchKey = searchKey
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReadRecordUiState(isLoading = true)
    )

    fun setSearchKey(query: String) {
        _searchKey.value = query
    }

    fun setDisplayMode(mode: DisplayMode) {
        _displayMode.value = mode
    }

    fun setSelectedDate(date: LocalDate?) {
        _selectedDate.value = date
    }

    fun deleteDetail(detail: ReadRecordDetail) {
        viewModelScope.launch { repository.deleteDetail(detail) }
    }

    private fun mergeContinuousSessions(sessions: List<ReadRecordSession>): List<ReadRecordSession> {
        if (sessions.isEmpty()) return emptyList()
        val mergedList = mutableListOf<ReadRecordSession>()
        mergedList.add(sessions.first().copy())

        val gapLimit = 20 * 60 * 1000L

        for (i in 1 until sessions.size) {
            val current = sessions[i]
            val last = mergedList.last()
            if (current.bookName == last.bookName && (current.startTime - last.endTime) <= gapLimit) {
                mergedList[mergedList.lastIndex] = last.copy(endTime = current.endTime)
            } else {
                mergedList.add(current.copy())
            }
        }
        return mergedList
    }

    suspend fun getChapterTitle(bookName: String, chapterIndexLong: Long): String? {
        return bookRepository.getChapterTitle(bookName, chapterIndexLong.toInt())
    }

    suspend fun getBookCover(bookName: String): String? {
        return bookRepository.getBookCoverByName(bookName)
    }

    private data class LoadedData(
        val totalReadTime: Long,
        val details: List<ReadRecordDetail>,
        val latestRecords: List<ReadRecord>,
        val sessions: List<ReadRecordSession>
    )
}