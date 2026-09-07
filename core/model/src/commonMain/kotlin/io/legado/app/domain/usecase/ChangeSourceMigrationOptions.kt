package io.legado.app.domain.usecase

/**
 * 换源迁移选项。从 `ChangeBookSourceUseCase` 拆出下沉，供 `ChangeSourceSettings`
 * 与换源用例共享（纯数据，无平台依赖）。
 */
data class ChangeSourceMigrationOptions(
    val migrateChapters: Boolean = true,
    val migrateReadingProgress: Boolean = true,
    val migrateGroup: Boolean = true,
    val migrateCover: Boolean = true,
    val migrateCategory: Boolean = true,
    val migrateRemark: Boolean = true,
    val migrateReadConfig: Boolean = true,
    val deleteDownloadedChapters: Boolean = false,
)
