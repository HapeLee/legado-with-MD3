package io.legado.app.ui.book.readRecord

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.data.dao.BookChapterDao
import io.legado.app.data.dao.BookDao
import io.legado.app.data.entities.readRecord.ReadRecord
import io.legado.app.data.entities.readRecord.ReadRecordDetail
import io.legado.app.data.entities.readRecord.ReadRecordSession
import io.legado.app.data.repository.ReadRecordRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class ReadRecordUiState(
    val isLoading: Boolean = true,
    val totalReadTime: Long = 0,
    //每日聚合明细
    val groupedRecords: Map<String, List<ReadRecordDetail>> = emptyMap(),
    //每日所有阅读会话
    val timelineRecords: Map<String, List<ReadRecordSession>> = emptyMap(),
    //最后阅读列表
    val latestRecords: List<ReadRecord> = emptyList(),
    val selectedDate: LocalDate? = null
)

enum class DisplayMode {
    AGGREGATE,
    TIMELINE,
    LATEST
}

class ReadRecordViewModel(
    private val repository: ReadRecordRepository,
    private val bookDao: BookDao,
    private val bookChapterDao: BookChapterDao
) : ViewModel() {
    private val _displayMode = MutableStateFlow(DisplayMode.AGGREGATE)
    val displayMode = _displayMode.asStateFlow()
    private val _uiState = MutableStateFlow(ReadRecordUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun setDisplayMode(mode: DisplayMode) {
        _displayMode.value = mode
    }

    fun setSelectedDate(date: LocalDate?) {
        _uiState.update { it.copy(selectedDate = date) }
        loadData()
    }

    fun loadData(query: String = "") {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val selectedDate = _uiState.value.selectedDate

            if (selectedDate != null) {
                //筛选特定日期
                val dateString = selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                //某一天所有书籍的明细
                val dailyDetails = repository.getAllRecordDetailsByDate(dateString, query)
                val grouped = dailyDetails.groupBy { it.date }
                //某一天所有会话
                val allSessions = repository.getAllSessionsByDate(dateString)
                val mergedSessions = mergeContinuousSessions(allSessions).reversed()
                val timelineMap = mapOf(dateString to mergedSessions)
                //LATEST
                val latest = repository.getLatestReadRecords(query)
                val totalTime = withContext(Dispatchers.IO) { repository.getTotalReadTime() }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        totalReadTime = totalTime,
                        groupedRecords = grouped,
                        timelineRecords = timelineMap,
                        latestRecords = latest
                    )
                }
            } else {
                val details = repository.getAllRecordDetails(query)
                val grouped = details.groupBy { it.date }
                val uniqueDates = grouped.keys.toList()
                val timelineMap = LinkedHashMap<String, List<ReadRecordSession>>()

                for (date in uniqueDates) {
                    val rawSessions = repository.getAllSessionsByDate(date)
                    val mergedSessions = mergeContinuousSessions(rawSessions).reversed()
                    timelineMap[date] = mergedSessions
                }

                val latest = repository.getLatestReadRecords(query)
                val totalTime = withContext(Dispatchers.IO) { repository.getTotalReadTime() }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        totalReadTime = totalTime,
                        groupedRecords = grouped,
                        timelineRecords = timelineMap,
                        latestRecords = latest
                    )
                }
            }
        }
    }

    /**
     * 遍历会话列表，合并同一本书连续阅读且间隔不超过20分钟的会话。
     * 只保留最晚结束的那条会话（更新其结束时间）。
     * @param sessions 原始 ReadRecordSession 列表，按开始时间排序。
     * @return 合并后的 ReadRecordSession 列表。
     */
    fun mergeContinuousSessions(sessions: List<ReadRecordSession>): List<ReadRecordSession> {
        if (sessions.isEmpty()) return emptyList()

        val mergedList = mutableListOf<ReadRecordSession>()
        mergedList.add(sessions.first().copy())

        val twentyMinutesInMillis = 2 * 60 * 1000L

        for (i in 1 until sessions.size) {
            val currentSession = sessions[i]
            val lastMergedSession = mergedList.last()
            val isSameBook = currentSession.bookName == lastMergedSession.bookName
            val timeGap = currentSession.startTime - lastMergedSession.endTime
            val isContinuous = timeGap <= twentyMinutesInMillis && timeGap >= 0

            if (isSameBook && isContinuous) {
                mergedList.removeAt(mergedList.lastIndex)

                val updatedSession = lastMergedSession.copy(
                    endTime = currentSession.endTime
                )
                mergedList.add(updatedSession)

            } else {
                mergedList.add(currentSession.copy())
            }
        }

        return mergedList
    }

    suspend fun getChapterTitle(
        bookName: String,
        chapterIndexLong: Long
    ): String? {
        val chapterIndex = chapterIndexLong.toInt()
        val book = withContext(Dispatchers.IO) {
            bookDao.findByName(bookName).firstOrNull()
        }

        val bookUrl = book?.bookUrl
        if (bookUrl.isNullOrEmpty()) {
            return null
        }

        return withContext(Dispatchers.IO) {
            bookChapterDao.getChapterTitleByUrlAndIndex(bookUrl, chapterIndex)
        }
    }

    fun deleteDetail(detail: ReadRecordDetail) {
        viewModelScope.launch {
            repository.deleteDetail(detail)
            loadData()
        }
    }

    suspend fun getBookCover(bookName: String): String? {
        return withContext(Dispatchers.IO) {
            bookDao.findByName(bookName).firstOrNull()?.getDisplayCover()
        }
    }
}