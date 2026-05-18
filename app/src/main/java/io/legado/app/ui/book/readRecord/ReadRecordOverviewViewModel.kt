package io.legado.app.ui.book.readRecord

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.data.repository.BookRepository
import io.legado.app.data.repository.ReadRecordRepository
import io.legado.app.domain.usecase.readRecord.GetReadRecordOverviewUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class ReadRecordOverviewUiState(
    val period: ReadPeriod = ReadPeriod.DAY,
    val referenceDate: LocalDate = LocalDate.now(),
    val totalTime: Long = 0,
    val readingDays: Int = 0,
    val totalBooks: Int = 0,
    val finishedBooks: Int = 0,
    val readingBooks: Int = 0,
    val totalWords: Long = 0,
    val dailyTimeData: List<Pair<LocalDate, Long>> = emptyList(),
    val topBooks: List<ReadBookRanking> = emptyList(),
    val dailyTopBook: Map<LocalDate, Pair<String, String>> = emptyMap(),
    val allReadTimes: Map<LocalDate, Long> = emptyMap(),
    val allReadCounts: Map<LocalDate, Int> = emptyMap()
)

data class ReadBookRanking(
    val bookName: String,
    val bookAuthor: String,
    val readTime: Long,
    var coverPath: String? = null
)

enum class ReadPeriod {
    DAY, WEEK, MONTH, YEAR, ALL
}

class ReadRecordOverviewViewModel(
    private val repository: ReadRecordRepository,
    private val bookRepository: BookRepository,
    private val getReadRecordOverviewUseCase: GetReadRecordOverviewUseCase
) : ViewModel() {

    private val _period = MutableStateFlow(ReadPeriod.DAY)
    private val _referenceDate = MutableStateFlow(LocalDate.now())

    val uiState: StateFlow<ReadRecordOverviewUiState> = combine(
        _period,
        _referenceDate,
        repository.getAllRecordDetails(""),
        repository.getLatestReadRecords(""),
        bookRepository.getAllBooks()
    ) { period, refDate, details, latestRecords, allBooks ->
        getReadRecordOverviewUseCase(period, refDate, details, latestRecords, allBooks)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReadRecordOverviewUiState()
    )

    fun setPeriod(period: ReadPeriod) {
        _period.value = period
    }

    fun nextDate() {
        val current = _referenceDate.value
        _referenceDate.value = when (_period.value) {
            ReadPeriod.DAY -> current.plusDays(1)
            ReadPeriod.WEEK -> current.plusWeeks(1)
            ReadPeriod.MONTH -> current.plusMonths(1)
            ReadPeriod.YEAR -> current.plusYears(1)
            ReadPeriod.ALL -> current
        }
    }

    fun prevDate() {
        val current = _referenceDate.value
        _referenceDate.value = when (_period.value) {
            ReadPeriod.DAY -> current.minusDays(1)
            ReadPeriod.WEEK -> current.minusWeeks(1)
            ReadPeriod.MONTH -> current.minusMonths(1)
            ReadPeriod.YEAR -> current.minusYears(1)
            ReadPeriod.ALL -> current
        }
    }

    suspend fun getBookCover(name: String, author: String) = bookRepository.getBookCoverByNameAndAuthor(name, author)
}
