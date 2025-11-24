package io.legado.app.ui.book.readRecord

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

data class ReadRecordUiState(
    val isLoading: Boolean = true,
    val totalReadTime: Long = 0,
    //每日聚合明细
    val groupedRecords: Map<String, List<ReadRecordDetail>> = emptyMap(),
    //每日所有阅读会话
    val timelineRecords: Map<String, List<ReadRecordSession>> = emptyMap(),
    //最后阅读列表
    val latestRecords: List<ReadRecord> = emptyList()
)

enum class DisplayMode {
    AGGREGATE,
    TIMELINE,
    LATEST
}

class ReadRecordViewModel(
    private val repository: ReadRecordRepository,
    private val bookDao: BookDao
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

    fun loadData(query: String = "") {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }


            val details = repository.getAllRecordDetails(query)
            val grouped = details.groupBy { it.date }
            val uniqueDates = grouped.keys.toList()
            val timelineMap = LinkedHashMap<String, List<ReadRecordSession>>()
            for (date in uniqueDates) {
                val sessions = repository.getAllSessionsForDate(date)
                timelineMap[date] = sessions
            }
            val latest = repository.getLatestReadRecords(query)
            val totalTime = withContext(Dispatchers.IO) { repository.allTime }
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