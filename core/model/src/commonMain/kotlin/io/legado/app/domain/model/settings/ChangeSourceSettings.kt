package io.legado.app.domain.model.settings

import io.legado.app.domain.usecase.ChangeSourceMigrationOptions

data class ChangeSourceSettings(
    val searchScope: String = "",
    val checkAuthor: Boolean = false,
    val loadInfo: Boolean = false,
    val loadToc: Boolean = false,
    val loadWordCount: Boolean = false,
    val migrateChapters: Boolean = true,
    val migrateReadingProgress: Boolean = true,
    val migrateGroup: Boolean = true,
    val migrateCover: Boolean = true,
    val migrateCategory: Boolean = true,
    val migrateRemark: Boolean = true,
    val migrateReadConfig: Boolean = true,
    val deleteDownloadedChapters: Boolean = false,
) {
    fun migrationOptions() = ChangeSourceMigrationOptions(
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
