package io.legado.app.ui.book.changesource

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import io.legado.app.data.local.preferences.LocalPreferencesKeys
import io.legado.app.data.local.preferences.LocalPreferencesRepository
import io.legado.app.domain.usecase.ChangeSourceMigrationOptions
import io.legado.app.ui.config.prefDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import splitties.init.appCtx

object ChangeSourceConfig {

    private val repo = LocalPreferencesRepository(appCtx)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _searchScope = mutableStateOf("")
    var searchScope: String
        get() = _searchScope.value
        set(value) {
            if (_searchScope.value != value) {
                Snapshot.withMutableSnapshot { _searchScope.value = value }
                scope.launch {
                    repo.updatePreference(LocalPreferencesKeys.CHANGE_SOURCE_SEARCH_SCOPE, value)
                }
            }
        }

    private val _checkAuthor = mutableStateOf(false)
    var checkAuthor: Boolean
        get() = _checkAuthor.value
        set(value) {
            if (_checkAuthor.value != value) {
                Snapshot.withMutableSnapshot { _checkAuthor.value = value }
                scope.launch {
                    repo.updatePreference(LocalPreferencesKeys.CHANGE_SOURCE_CHECK_AUTHOR, value)
                }
            }
        }

    private val _loadInfo = mutableStateOf(false)
    var loadInfo: Boolean
        get() = _loadInfo.value
        set(value) {
            if (_loadInfo.value != value) {
                Snapshot.withMutableSnapshot { _loadInfo.value = value }
                scope.launch {
                    repo.updatePreference(LocalPreferencesKeys.CHANGE_SOURCE_LOAD_INFO, value)
                }
            }
        }

    private val _loadToc = mutableStateOf(false)
    var loadToc: Boolean
        get() = _loadToc.value
        set(value) {
            if (_loadToc.value != value) {
                Snapshot.withMutableSnapshot { _loadToc.value = value }
                scope.launch {
                    repo.updatePreference(LocalPreferencesKeys.CHANGE_SOURCE_LOAD_TOC, value)
                }
            }
        }

    private val _loadWordCount = mutableStateOf(false)
    var loadWordCount: Boolean
        get() = _loadWordCount.value
        set(value) {
            if (_loadWordCount.value != value) {
                Snapshot.withMutableSnapshot { _loadWordCount.value = value }
                scope.launch {
                    repo.updatePreference(LocalPreferencesKeys.CHANGE_SOURCE_LOAD_WORD_COUNT, value)
                }
            }
        }

    var migrateChapters by prefDelegate(
        key = "migrateChapters",
        defaultValue = true
    )
    var migrateReadingProgress by prefDelegate(
        key = "migrateReadingProgress",
        defaultValue = true
    )
    var migrateGroup by prefDelegate(
        key = "migrateGroup",
        defaultValue = true
    )
    var migrateCover by prefDelegate(
        key = "migrateCover",
        defaultValue = true
    )
    var migrateCategory by prefDelegate(
        key = "migrateCategory",
        defaultValue = true
    )
    var migrateRemark by prefDelegate(
        key = "migrateRemark",
        defaultValue = true
    )
    var migrateReadConfig by prefDelegate(
        key = "migrateReadConfig",
        defaultValue = true
    )
    var deleteDownloadedChapters by prefDelegate(
        key = "deleteDownloadedChapters",
        defaultValue = false
    )

    init {
        scope.launch {
            repo.getPreference(LocalPreferencesKeys.CHANGE_SOURCE_SEARCH_SCOPE, "")
                .collect { Snapshot.withMutableSnapshot { _searchScope.value = it } }
        }
        scope.launch {
            repo.getPreference(LocalPreferencesKeys.CHANGE_SOURCE_CHECK_AUTHOR, false)
                .collect { Snapshot.withMutableSnapshot { _checkAuthor.value = it } }
        }
        scope.launch {
            repo.getPreference(LocalPreferencesKeys.CHANGE_SOURCE_LOAD_INFO, false)
                .collect { Snapshot.withMutableSnapshot { _loadInfo.value = it } }
        }
        scope.launch {
            repo.getPreference(LocalPreferencesKeys.CHANGE_SOURCE_LOAD_TOC, false)
                .collect { Snapshot.withMutableSnapshot { _loadToc.value = it } }
        }
        scope.launch {
            repo.getPreference(LocalPreferencesKeys.CHANGE_SOURCE_LOAD_WORD_COUNT, false)
                .collect { Snapshot.withMutableSnapshot { _loadWordCount.value = it } }
        }
    }

    fun getMigrationOptions(): ChangeSourceMigrationOptions {
        return ChangeSourceMigrationOptions(
            migrateChapters = migrateChapters,
            migrateReadingProgress = migrateReadingProgress,
            migrateGroup = migrateGroup,
            migrateCover = migrateCover,
            migrateCategory = migrateCategory,
            migrateRemark = migrateRemark,
            migrateReadConfig = migrateReadConfig,
            deleteDownloadedChapters = deleteDownloadedChapters,
        )
    }

    fun setMigrationOptions(options: ChangeSourceMigrationOptions) {
        migrateChapters = options.migrateChapters
        migrateReadingProgress = options.migrateReadingProgress
        migrateGroup = options.migrateGroup
        migrateCover = options.migrateCover
        migrateCategory = options.migrateCategory
        migrateRemark = options.migrateRemark
        migrateReadConfig = options.migrateReadConfig
        deleteDownloadedChapters = options.deleteDownloadedChapters
    }
}
