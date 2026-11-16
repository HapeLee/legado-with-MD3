package io.legado.app.domain.model.settings

import io.legado.app.domain.usecase.ChangeSourceMigrationOptions

data class ChangeSourceSettings(
    val searchScope: String = "",
    /** 封面换源的书源范围，独立于 [searchScope]；两个面板互不联动。 */
    val coverSearchScope: String = "",
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
    val migrateAuthor: Boolean = true,
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
        migrateAuthor = migrateAuthor,
        migrateReadConfig = migrateReadConfig,
        deleteDownloadedChapters = deleteDownloadedChapters,
    )
}
