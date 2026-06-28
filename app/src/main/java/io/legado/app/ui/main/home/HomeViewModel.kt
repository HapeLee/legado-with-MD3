package io.legado.app.ui.main.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.data.local.preferences.LocalPreferencesKeys
import io.legado.app.data.local.preferences.LocalPreferencesRepository
import io.legado.app.data.repository.BookRepository
import io.legado.app.data.repository.ReadRecordRepository
import io.legado.app.domain.model.WebDavBackup
import io.legado.app.domain.usecase.BackupRestoreUseCase
import io.legado.app.domain.usecase.WebDavBackupUseCase
import io.legado.app.ui.config.backupConfig.BackupConfig
import io.legado.app.utils.isContentScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

class HomeViewModel(
    private val readRecordRepository: ReadRecordRepository,
    private val bookRepository: BookRepository,
    private val localPreferencesRepository: LocalPreferencesRepository,
    private val webDavBackupUseCase: WebDavBackupUseCase,
    private val backupRestoreUseCase: BackupRestoreUseCase,
) : ViewModel() {

    private val _backupState = MutableStateFlow(HomeBackupState())
    private val _activeDialog = MutableStateFlow<HomeDialog?>(null)
    private val _activeSheet = MutableStateFlow<HomeSheet?>(null)
    private val _effects = MutableSharedFlow<HomeEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private val dashboardData = combine(
        readRecordRepository.getLatestReadRecords(),
        readRecordRepository.getTotalReadTime(),
        readRecordRepository.getAllRecordDetails(),
        bookRepository.getAllBooks(),
        localPreferencesRepository.getPreference(
            LocalPreferencesKeys.DAILY_READING_GOAL_MINUTES,
            DEFAULT_DAILY_READING_GOAL_MINUTES,
        ),
    ) { records, totalReadTime, details, books, dailyGoalMinutes ->
        val latestRecord = records.firstOrNull()
        val latestBook = latestRecord?.let { record ->
            books.firstOrNull {
                it.name == record.bookName && it.author == record.bookAuthor
            }
        }
        val today = LocalDate.now().toString()

        HomeDashboardData(
            totalReadBooks = records
                .distinctBy { it.bookName to it.bookAuthor }
                .size,
            totalReadTimeMillis = totalReadTime,
            todayReadTimeMillis = details
                .asSequence()
                .filter { it.date == today }
                .sumOf { it.readTime },
            dailyGoalMinutes = dailyGoalMinutes.coerceIn(
                1,
                MAX_DAILY_READING_GOAL_MINUTES,
            ),
            recentBook = latestRecord?.let { record ->
                HomeRecentBookUi(
                    bookUrl = latestBook?.bookUrl,
                    name = record.bookName,
                    author = record.bookAuthor,
                    origin = latestBook?.origin,
                    coverPath = latestBook?.getDisplayCover(),
                    chapterTitle = latestBook?.durChapterTitle,
                    chapterProgress = latestBook?.let { book ->
                        if (book.totalChapterNum > 0) {
                            (book.durChapterIndex + 1)
                                .coerceIn(0, book.totalChapterNum)
                                .toFloat() / book.totalChapterNum
                        } else {
                            null
                        }
                    },
                )
            },
        )
    }

    val uiState = combine(
        dashboardData,
        _backupState,
        _activeDialog,
        _activeSheet,
    ) { dashboard, backup, dialog, sheet ->
        HomeUiState(
            totalReadBooks = dashboard.totalReadBooks,
            totalReadTimeMillis = dashboard.totalReadTimeMillis,
            todayReadTimeMillis = dashboard.todayReadTimeMillis,
            dailyGoalMinutes = dashboard.dailyGoalMinutes,
            recentBook = dashboard.recentBook,
            latestBackup = backup.latest?.toUi(),
            isBackupLoading = backup.isLoading,
            isBackupActionRunning = backup.isActionRunning,
            activeDialog = dialog,
            activeSheet = sheet,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    init {
        refreshLatestBackup()
    }

    fun onIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.RecentBookClick -> openRecentBook()
            HomeIntent.ReadingGoalClick -> {
                _activeDialog.value = HomeDialog.SetReadingGoal(
                    uiState.value.dailyGoalMinutes
                )
            }

            is HomeIntent.UpdateReadingGoal -> updateReadingGoal(intent.minutes)
            HomeIntent.BackupClick -> _activeSheet.value = HomeSheet.BackupOptions
            is HomeIntent.BackupDestinationSelected -> {
                requestBackup(intent.destination)
            }

            is HomeIntent.BackupDirectorySelected -> {
                backup(
                    destination = intent.destination,
                    path = intent.path,
                    savePath = true,
                )
            }

            HomeIntent.RestoreClick -> _activeSheet.value = HomeSheet.RestoreOptions
            HomeIntent.RestoreFromLocal -> {
                _activeSheet.value = null
                _effects.tryEmit(HomeEffect.SelectRestoreFile)
            }

            HomeIntent.RestoreFromNetwork -> {
                _activeSheet.value = null
                requestRestore()
            }

            is HomeIntent.RestoreLocalFileSelected -> restoreLocal(intent.uri)
            HomeIntent.ConfirmRestore -> restore()
            HomeIntent.BackupSettingsClick -> {
                _effects.tryEmit(HomeEffect.OpenBackupSettings)
            }

            HomeIntent.DismissDialog -> _activeDialog.value = null
            HomeIntent.DismissSheet -> _activeSheet.value = null
        }
    }

    private fun openRecentBook() {
        val bookUrl = uiState.value.recentBook?.bookUrl ?: return
        viewModelScope.launch {
            bookRepository.getBook(bookUrl)?.let {
                _effects.emit(HomeEffect.OpenBook(it))
            }
        }
    }

    private fun updateReadingGoal(minutes: Int) {
        val validMinutes = minutes.coerceIn(1, MAX_DAILY_READING_GOAL_MINUTES)
        _activeDialog.value = null
        viewModelScope.launch {
            localPreferencesRepository.updatePreference(
                LocalPreferencesKeys.DAILY_READING_GOAL_MINUTES,
                validMinutes,
            )
        }
    }

    private fun requestRestore() {
        val backup = _backupState.value.latest
        if (backup == null) {
            _effects.tryEmit(HomeEffect.ShowMessage(R.string.home_no_webdav_backup))
            return
        }
        _activeDialog.value = HomeDialog.ConfirmRestore(backup.name)
    }

    private fun requestBackup(destination: HomeBackupDestination) {
        _activeSheet.value = null
        if (destination == HomeBackupDestination.WebDav) {
            backup(destination = destination, path = null)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val path = BackupConfig.backupPath
            if (path.isNullOrBlank()) {
                _effects.emit(HomeEffect.SelectBackupDirectory(destination))
            } else if (!path.isContentScheme()) {
                _effects.emit(
                    HomeEffect.RequestBackupStoragePermission(
                        destination = destination,
                        path = path,
                    )
                )
            } else {
                backup(destination = destination, path = path)
            }
        }
    }

    private fun backup(
        destination: HomeBackupDestination,
        path: String?,
        savePath: Boolean = false,
    ) {
        if (_backupState.value.isActionRunning) return
        _backupState.update { it.copy(isActionRunning = true) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                if (savePath) {
                    BackupConfig.backupPath = path
                }
                if (destination != HomeBackupDestination.Local) {
                    webDavBackupUseCase.refreshConfig()
                }
                backupRestoreUseCase.backup(path, destination.mode)
            }.onSuccess {
                _effects.emit(HomeEffect.ShowMessage(R.string.backup_success))
                if (destination != HomeBackupDestination.Local) {
                    loadLatestBackup()
                }
            }.onFailure { error ->
                _effects.emit(
                    HomeEffect.ShowMessage(
                        messageRes = R.string.backup_error,
                        detail = error.localizedMessage,
                    )
                )
            }
            _backupState.update { it.copy(isActionRunning = false) }
        }
    }

    private fun restoreLocal(uri: String) {
        if (_backupState.value.isActionRunning) return
        _backupState.update { it.copy(isActionRunning = true) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                backupRestoreUseCase.restoreLocal(uri)
            }.onSuccess {
                _effects.emit(HomeEffect.ShowMessage(R.string.restore_success))
            }.onFailure { error ->
                _effects.emit(
                    HomeEffect.ShowMessage(
                        messageRes = R.string.restore_error,
                        detail = error.localizedMessage,
                    )
                )
            }
            _backupState.update { it.copy(isActionRunning = false) }
        }
    }

    private fun restore() {
        val backup = _backupState.value.latest ?: return
        _activeDialog.value = null
        if (_backupState.value.isActionRunning) return
        _backupState.update { it.copy(isActionRunning = true) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                webDavBackupUseCase.restore(backup.name)
            }.onSuccess {
                _effects.emit(HomeEffect.ShowMessage(R.string.restore_success))
            }.onFailure { error ->
                _effects.emit(
                    HomeEffect.ShowMessage(
                        messageRes = R.string.restore_error,
                        detail = error.localizedMessage,
                    )
                )
            }
            _backupState.update { it.copy(isActionRunning = false) }
        }
    }

    private fun refreshLatestBackup() {
        viewModelScope.launch(Dispatchers.IO) {
            loadLatestBackup()
        }
    }

    private suspend fun loadLatestBackup() {
        _backupState.update { it.copy(isLoading = true) }
        val latest = runCatching {
            webDavBackupUseCase.getLatestBackup()
        }.getOrNull()
        _backupState.update { it.copy(latest = latest, isLoading = false) }
    }

    private data class HomeDashboardData(
        val totalReadBooks: Int,
        val totalReadTimeMillis: Long,
        val todayReadTimeMillis: Long,
        val dailyGoalMinutes: Int,
        val recentBook: HomeRecentBookUi?,
    )

    private data class HomeBackupState(
        val latest: WebDavBackup? = null,
        val isLoading: Boolean = true,
        val isActionRunning: Boolean = false,
    )

    private fun WebDavBackup.toUi() = HomeBackupUi(
        name = name,
        lastModify = lastModify,
    )
}
