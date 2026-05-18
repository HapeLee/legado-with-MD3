package io.legado.app.ui.book.changesource

import io.legado.app.constant.PreferKey
import io.legado.app.domain.usecase.ChangeSourceMigrationOptions
import io.legado.app.ui.config.prefDelegate

object ChangeSourceConfig {

    var searchGroup by prefDelegate(
        key = "searchGroup",
        defaultValue = ""
    )

    var searchScope by prefDelegate(
        key = "changeSourceSearchScope",
        defaultValue = ""
    )

    var checkAuthor by prefDelegate(
        key = PreferKey.changeSourceCheckAuthor,
        defaultValue = false
    )

    var loadInfo by prefDelegate(
        key = PreferKey.changeSourceLoadInfo,
        defaultValue = false
    )

    var loadToc by prefDelegate(
        key = PreferKey.changeSourceLoadToc,
        defaultValue = false
    )

    var loadWordCount by prefDelegate(
        key = PreferKey.changeSourceLoadWordCount,
        defaultValue = false
    )

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
