package io.legado.app.data.repository

import io.legado.app.domain.gateway.ChangeSourceSettingsGateway
import io.legado.app.domain.model.settings.ChangeSourceSettings
import io.legado.app.domain.usecase.ChangeSourceMigrationOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class ChangeSourceSettingsRepository(
    private val preferences: PreferenceStore,
) : ChangeSourceSettingsGateway {
    override val currentSettings: ChangeSourceSettings
        get() = preferences.currentSnapshot().toChangeSourceSettings()

    override val settings: Flow<ChangeSourceSettings> = preferences.observeSnapshot()
        .map { it.toChangeSourceSettings() }
        .distinctUntilChanged()

    override suspend fun update(transform: (ChangeSourceSettings) -> ChangeSourceSettings) {
        preferences.atomicUpdateSettings(
            read = { it.toChangeSourceSettings() },
            toPrefMap = ChangeSourceSettings::toPrefMap,
            transform = transform,
        )
    }

    override suspend fun setMigrationOptions(options: ChangeSourceMigrationOptions) {
        preferences.setAllAndAwait(
            mapOf(
                KEY_MIGRATE_CHAPTERS to PreferenceValue.BooleanValue(options.migrateChapters),
                KEY_MIGRATE_READING_PROGRESS to PreferenceValue.BooleanValue(options.migrateReadingProgress),
                KEY_MIGRATE_GROUP to PreferenceValue.BooleanValue(options.migrateGroup),
                KEY_MIGRATE_COVER to PreferenceValue.BooleanValue(options.migrateCover),
                KEY_MIGRATE_CATEGORY to PreferenceValue.BooleanValue(options.migrateCategory),
                KEY_MIGRATE_REMARK to PreferenceValue.BooleanValue(options.migrateRemark),
                KEY_MIGRATE_READ_CONFIG to PreferenceValue.BooleanValue(options.migrateReadConfig),
                KEY_DELETE_DOWNLOADED_CHAPTERS to PreferenceValue.BooleanValue(options.deleteDownloadedChapters),
            ),
        )
    }
}

internal fun Map<String, PreferenceValue>.toChangeSourceSettings() = ChangeSourceSettings(
    searchScope = compatString(CHANGE_SOURCE_SEARCH_SCOPE).orEmpty(),
    checkAuthor = compatBoolean(CHANGE_SOURCE_CHECK_AUTHOR) ?: false,
    loadInfo = compatBoolean(CHANGE_SOURCE_LOAD_INFO) ?: false,
    loadToc = compatBoolean(CHANGE_SOURCE_LOAD_TOC) ?: false,
    loadWordCount = compatBoolean(CHANGE_SOURCE_LOAD_WORD_COUNT) ?: false,
    migrateChapters = compatBoolean(KEY_MIGRATE_CHAPTERS) ?: true,
    migrateReadingProgress = compatBoolean(KEY_MIGRATE_READING_PROGRESS) ?: true,
    migrateGroup = compatBoolean(KEY_MIGRATE_GROUP) ?: true,
    migrateCover = compatBoolean(KEY_MIGRATE_COVER) ?: true,
    migrateCategory = compatBoolean(KEY_MIGRATE_CATEGORY) ?: true,
    migrateRemark = compatBoolean(KEY_MIGRATE_REMARK) ?: true,
    migrateReadConfig = compatBoolean(KEY_MIGRATE_READ_CONFIG) ?: true,
    deleteDownloadedChapters = compatBoolean(KEY_DELETE_DOWNLOADED_CHAPTERS) ?: false,
)

internal fun ChangeSourceSettings.toPrefMap(): Map<String, Any?> = mapOf(
    CHANGE_SOURCE_SEARCH_SCOPE to searchScope,
    CHANGE_SOURCE_CHECK_AUTHOR to checkAuthor,
    CHANGE_SOURCE_LOAD_INFO to loadInfo,
    CHANGE_SOURCE_LOAD_TOC to loadToc,
    CHANGE_SOURCE_LOAD_WORD_COUNT to loadWordCount,
    KEY_MIGRATE_CHAPTERS to migrateChapters,
    KEY_MIGRATE_READING_PROGRESS to migrateReadingProgress,
    KEY_MIGRATE_GROUP to migrateGroup,
    KEY_MIGRATE_COVER to migrateCover,
    KEY_MIGRATE_CATEGORY to migrateCategory,
    KEY_MIGRATE_REMARK to migrateRemark,
    KEY_MIGRATE_READ_CONFIG to migrateReadConfig,
    KEY_DELETE_DOWNLOADED_CHAPTERS to deleteDownloadedChapters,
)

// 原 LocalPreferencesKeys 的 change-source key（key 名即字符串字面量）
private const val CHANGE_SOURCE_SEARCH_SCOPE = "changeSourceSearchScope"
private const val CHANGE_SOURCE_CHECK_AUTHOR = "changeSourceCheckAuthor"
private const val CHANGE_SOURCE_LOAD_INFO = "changeSourceLoadInfo"
private const val CHANGE_SOURCE_LOAD_TOC = "changeSourceLoadToc"
private const val CHANGE_SOURCE_LOAD_WORD_COUNT = "changeSourceLoadWordCount"

private const val KEY_MIGRATE_CHAPTERS = "migrateChapters"
private const val KEY_MIGRATE_READING_PROGRESS = "migrateReadingProgress"
private const val KEY_MIGRATE_GROUP = "migrateGroup"
private const val KEY_MIGRATE_COVER = "migrateCover"
private const val KEY_MIGRATE_CATEGORY = "migrateCategory"
private const val KEY_MIGRATE_REMARK = "migrateRemark"
private const val KEY_MIGRATE_READ_CONFIG = "migrateReadConfig"
private const val KEY_DELETE_DOWNLOADED_CHAPTERS = "deleteDownloadedChapters"
