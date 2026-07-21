package io.legado.app.ui.main.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.data.repository.BookRepository
import io.legado.app.domain.gateway.BackupSettingsGateway
import io.legado.app.domain.model.HomeDashboardSection
import io.legado.app.domain.model.HomeReadingBook
import io.legado.app.domain.model.WebDavBackup
import io.legado.app.domain.usecase.BackupRestoreUseCase
import io.legado.app.domain.usecase.HomeDashboardUseCase
import io.legado.app.domain.usecase.WebDavBackupUseCase
import io.legado.app.utils.isContentScheme
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

class HomeViewModel(
    private val homeDashboardUseCase: HomeDashboardUseCase,
    private val bookRepository: BookRepository,
    private val webDavBackupUseCase: WebDavBackupUseCase,
    private val backupRestoreUseCase: BackupRestoreUseCase,
    private val backupSettingsGateway: BackupSettingsGateway,
) : ViewModel() {

    private val _backupState = MutableStateFlow(HomeBackupState())
    private val _activeDialog = MutableStateFlow<HomeDialog?>(null)
    private val _activeSheet = MutableStateFlow<HomeSheet?>(null)
    private val _effects = MutableSharedFlow<HomeEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()
    private var backupRefreshJob: Job? = null
    private val backupActionMutex = Mutex()

    private val dashboardData = combine(
        homeDashboardUseCase.observe(),
        homeDashboardUseCase.observeSelectedSourceSetUrl(),
        homeDashboardUseCase.observeVisibleSections(),
    ) { dashboard, selectedSourceUrl, visibleSections ->
        Triple(dashboard, selectedSourceUrl, visibleSections)
    }

    val uiState = combine(
        dashboardData,
        _backupState,
        _activeDialog,
        _activeSheet,
    ) { dashboardData, backup, dialog, sheet ->
        val (dashboard, selectedSourceUrl, visibleSections) = dashboardData
        HomeUiState(
            totalReadBooks = dashboard.totalReadBooks,
            totalReadTimeMillis = dashboard.totalReadTimeMillis,
            todayReadTimeMillis = dashboard.todayReadTimeMillis,
            dailyGoalMinutes = dashboard.dailyGoalMinutes,
            recentBook = dashboard.recentBooks.firstOrNull()?.toUi(),
            recentBooks = dashboard.recentBooks
                .drop(1)
                .take(6)
                .map { it.toUi() }
                .toImmutableList(),
            selectedSourceSetUrl = selectedSourceUrl,
            visibleSections = visibleSections.toImmutableSet(),
            latestBackup = backup.latest?.toUi(),
            isBackupLoading = backup.isLoading,
            isBackupLoadError = backup.isLoadError,
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
        viewModelScope.launch {
            homeDashboardUseCase.observeVisibleSections()
                .map { HomeDashboardSection.WebDavBackup in it }
                .distinctUntilChanged()
                .collect { visible ->
                    if (visible) {
                        refreshLatestBackup()
                    } else {
                        backupRefreshJob?.cancel()
                        _backupState.update { it.copy(isLoading = false) }
                    }
                }
        }
    }

    fun onIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.RecentBookClick -> openRecentBook()
            is HomeIntent.RecentHistoryBookClick -> openBook(intent.bookUrl)
            is HomeIntent.SelectSourceSet -> selectSourceSet(intent.sourceUrl)
            HomeIntent.DashboardSettingsClick -> {
                _activeSheet.value = HomeSheet.DashboardSettings
            }

            is HomeIntent.SetSectionVisible -> {
                setSectionVisible(intent.section, intent.visible)
            }
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

            HomeIntent.RetryBackupInfo -> refreshLatestBackup()
            HomeIntent.DismissDialog -> _activeDialog.value = null
            HomeIntent.DismissSheet -> _activeSheet.value = null
        }
    }

    private fun openRecentBook() {
        val bookUrl = uiState.value.recentBook?.bookUrl ?: return
        openBook(bookUrl)
    }

    private fun openBook(bookUrl: String) {
        viewModelScope.launch {
            bookRepository.getBook(bookUrl)?.let {
                _effects.emit(HomeEffect.OpenBook(it))
            }
        }
    }

    private fun updateReadingGoal(minutes: Int) {
        _activeDialog.value = null
        viewModelScope.launch {
            homeDashboardUseCase.updateDailyGoal(minutes)
        }
    }

    private fun selectSourceSet(sourceUrl: String) {
        viewModelScope.launch {
            homeDashboardUseCase.updateSelectedSourceSetUrl(sourceUrl)
        }
    }

    private fun setSectionVisible(
        section: HomeDashboardSection,
        visible: Boolean,
    ) {
        val sections = uiState.value.visibleSections.toMutableSet().apply {
            if (visible) add(section) else remove(section)
        }
        viewModelScope.launch {
            homeDashboardUseCase.updateVisibleSections(sections)
        }
    }

    private fun requestRestore() {
        val backup = _backupState.value.latest
        if (backup == null) {
            _effects.tryEmit(
                HomeEffect.ShowMessage(
                    if (_backupState.value.isLoadError) {
                        R.string.home_webdav_backup_load_error
                    } else {
                        R.string.home_no_webdav_backup
                    }
                )
            )
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
            val path = backupSettingsGateway.currentSettings.backupPath
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
        if (!backupActionMutex.tryLock()) return
        _backupState.update { it.copy(isActionRunning = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                runCatching {
                    if (savePath) {
                        backupSettingsGateway.update { it.copy(backupPath = path) }
                    }
                    if (destination != HomeBackupDestination.Local) {
                        webDavBackupUseCase.refreshConfig()
                    }
                    backupRestoreUseCase.backup(path, destination.mode)
                }.onSuccess {
                    _effects.emit(HomeEffect.ShowMessage(R.string.backup_success))
                    if (destination != HomeBackupDestination.Local) {
                        refreshLatestBackup()
                    }
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    _effects.emit(
                        HomeEffect.ShowMessage(
                            messageRes = R.string.backup_error,
                            detail = error.localizedMessage,
                        )
                    )
                }
            } finally {
                _backupState.update { it.copy(isActionRunning = false) }
                backupActionMutex.unlock()
            }
        }
    }

    private fun restoreLocal(uri: String) {
        if (!backupActionMutex.tryLock()) return
        _backupState.update { it.copy(isActionRunning = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                runCatching {
                    backupRestoreUseCase.restoreLocal(uri)
                }.onSuccess {
                    _effects.emit(HomeEffect.ShowMessage(R.string.restore_success))
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    _effects.emit(
                        HomeEffect.ShowMessage(
                            messageRes = R.string.restore_error,
                            detail = error.localizedMessage,
                        )
                    )
                }
            } finally {
                _backupState.update { it.copy(isActionRunning = false) }
                backupActionMutex.unlock()
            }
        }
    }

    private fun restore() {
        val backup = _backupState.value.latest ?: return
        _activeDialog.value = null
        if (!backupActionMutex.tryLock()) return
        _backupState.update { it.copy(isActionRunning = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                runCatching {
                    webDavBackupUseCase.restore(backup.name)
                }.onSuccess {
                    _effects.emit(HomeEffect.ShowMessage(R.string.restore_success))
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    _effects.emit(
                        HomeEffect.ShowMessage(
                            messageRes = R.string.restore_error,
                            detail = error.localizedMessage,
                        )
                    )
                }
            } finally {
                _backupState.update { it.copy(isActionRunning = false) }
                backupActionMutex.unlock()
            }
        }
    }

    private fun refreshLatestBackup() {
        backupRefreshJob?.cancel()
        backupRefreshJob = viewModelScope.launch(Dispatchers.IO) {
            loadLatestBackup()
        }
    }

    private suspend fun loadLatestBackup() {
        _backupState.update {
            it.copy(
                isLoading = true,
                isLoadError = false,
            )
        }
        try {
            val latest = webDavBackupUseCase.getLatestBackup()
            _backupState.update {
                it.copy(
                    latest = latest,
                    isLoading = false,
                    isLoadError = false,
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            _backupState.update {
                it.copy(
                    isLoading = false,
                    isLoadError = true,
                )
            }
        }
    }

    private data class HomeBackupState(
        val latest: WebDavBackup? = null,
        val isLoading: Boolean = true,
        val isLoadError: Boolean = false,
        val isActionRunning: Boolean = false,
    )

    private fun WebDavBackup.toUi() = HomeBackupUi(
        name = name,
        lastModify = lastModify,
    )

    private fun HomeReadingBook.toUi() = HomeRecentBookUi(
        bookUrl = bookUrl,
        name = name,
        author = author,
        origin = origin,
        coverPath = coverPath,
        chapterTitle = chapterTitle,
        chapterProgress = chapterProgress,
    )
}
