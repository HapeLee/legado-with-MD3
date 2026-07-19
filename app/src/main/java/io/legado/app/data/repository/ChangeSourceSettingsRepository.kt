package io.legado.app.data.repository

import androidx.datastore.preferences.core.Preferences
import io.legado.app.data.local.preferences.LocalPreferencesKeys
import io.legado.app.domain.gateway.ChangeSourceSettingsGateway
import io.legado.app.domain.model.settings.ChangeSourceSettings
import io.legado.app.domain.usecase.ChangeSourceMigrationOptions
import io.legado.app.help.config.AppConfigStore
import io.legado.app.help.config.compatDsBoolean
import io.legado.app.help.config.compatDsString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class ChangeSourceSettingsRepository : ChangeSourceSettingsGateway {
    override val currentSettings: ChangeSourceSettings
        get() = AppConfigStore.preferences.toChangeSourceSettings()

    override val settings: Flow<ChangeSourceSettings> = AppConfigStore.preferencesFlow
        .map(Preferences::toChangeSourceSettings)
        .distinctUntilChanged()

    override suspend fun update(transform: (ChangeSourceSettings) -> ChangeSourceSettings) {
        AppConfigStore.atomicUpdate(
            read = Preferences::toChangeSourceSettings,
            toPrefMap = ChangeSourceSettings::toPrefMap,
            transform = transform,
        )
    }

    override suspend fun setMigrationOptions(options: ChangeSourceMigrationOptions) {
        AppConfigStore.putAll(
            mapOf(
                KEY_MIGRATE_CHAPTERS to options.migrateChapters,
                KEY_MIGRATE_READING_PROGRESS to options.migrateReadingProgress,
                KEY_MIGRATE_GROUP to options.migrateGroup,
                KEY_MIGRATE_COVER to options.migrateCover,
                KEY_MIGRATE_CATEGORY to options.migrateCategory,
                KEY_MIGRATE_REMARK to options.migrateRemark,
                KEY_MIGRATE_READ_CONFIG to options.migrateReadConfig,
                KEY_DELETE_DOWNLOADED_CHAPTERS to options.deleteDownloadedChapters,
            )
        )
    }
}

internal fun Preferences.toChangeSourceSettings() = ChangeSourceSettings(
    searchScope = compatDsString(LocalPreferencesKeys.CHANGE_SOURCE_SEARCH_SCOPE.name).orEmpty(),
    checkAuthor = compatDsBoolean(LocalPreferencesKeys.CHANGE_SOURCE_CHECK_AUTHOR.name) ?: false,
    loadInfo = compatDsBoolean(LocalPreferencesKeys.CHANGE_SOURCE_LOAD_INFO.name) ?: false,
    loadToc = compatDsBoolean(LocalPreferencesKeys.CHANGE_SOURCE_LOAD_TOC.name) ?: false,
    loadWordCount = compatDsBoolean(LocalPreferencesKeys.CHANGE_SOURCE_LOAD_WORD_COUNT.name) ?: false,
    migrateChapters = compatDsBoolean(KEY_MIGRATE_CHAPTERS) ?: true,
    migrateReadingProgress = compatDsBoolean(KEY_MIGRATE_READING_PROGRESS) ?: true,
    migrateGroup = compatDsBoolean(KEY_MIGRATE_GROUP) ?: true,
    migrateCover = compatDsBoolean(KEY_MIGRATE_COVER) ?: true,
    migrateCategory = compatDsBoolean(KEY_MIGRATE_CATEGORY) ?: true,
    migrateRemark = compatDsBoolean(KEY_MIGRATE_REMARK) ?: true,
    migrateReadConfig = compatDsBoolean(KEY_MIGRATE_READ_CONFIG) ?: true,
    deleteDownloadedChapters = compatDsBoolean(KEY_DELETE_DOWNLOADED_CHAPTERS) ?: false,
)

internal fun ChangeSourceSettings.toPrefMap(): Map<String, Any?> = mapOf(
    LocalPreferencesKeys.CHANGE_SOURCE_SEARCH_SCOPE.name to searchScope,
    LocalPreferencesKeys.CHANGE_SOURCE_CHECK_AUTHOR.name to checkAuthor,
    LocalPreferencesKeys.CHANGE_SOURCE_LOAD_INFO.name to loadInfo,
    LocalPreferencesKeys.CHANGE_SOURCE_LOAD_TOC.name to loadToc,
    LocalPreferencesKeys.CHANGE_SOURCE_LOAD_WORD_COUNT.name to loadWordCount,
    KEY_MIGRATE_CHAPTERS to migrateChapters,
    KEY_MIGRATE_READING_PROGRESS to migrateReadingProgress,
    KEY_MIGRATE_GROUP to migrateGroup,
    KEY_MIGRATE_COVER to migrateCover,
    KEY_MIGRATE_CATEGORY to migrateCategory,
    KEY_MIGRATE_REMARK to migrateRemark,
    KEY_MIGRATE_READ_CONFIG to migrateReadConfig,
    KEY_DELETE_DOWNLOADED_CHAPTERS to deleteDownloadedChapters,
)

private const val KEY_MIGRATE_CHAPTERS = "migrateChapters"
private const val KEY_MIGRATE_READING_PROGRESS = "migrateReadingProgress"
private const val KEY_MIGRATE_GROUP = "migrateGroup"
private const val KEY_MIGRATE_COVER = "migrateCover"
private const val KEY_MIGRATE_CATEGORY = "migrateCategory"
private const val KEY_MIGRATE_REMARK = "migrateRemark"
private const val KEY_MIGRATE_READ_CONFIG = "migrateReadConfig"
private const val KEY_DELETE_DOWNLOADED_CHAPTERS = "deleteDownloadedChapters"
