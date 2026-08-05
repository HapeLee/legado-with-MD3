package io.legado.app.ui.book.changesource

import io.legado.app.domain.gateway.ChangeSourceSettingsGateway
import org.koin.core.context.GlobalContext

@Deprecated("使用 ChangeSourceSettingsGateway.currentSettings 读取，通过 update() 写入")
object ChangeSourceConfig {
    private val settings
        get() = GlobalContext.get().get<ChangeSourceSettingsGateway>().currentSettings

    val searchScope get() = settings.searchScope
    val checkAuthor get() = settings.checkAuthor
    val loadInfo get() = settings.loadInfo
    val loadToc get() = settings.loadToc
    val loadWordCount get() = settings.loadWordCount
    val migrateChapters get() = settings.migrateChapters
    val migrateReadingProgress get() = settings.migrateReadingProgress
    val migrateGroup get() = settings.migrateGroup
    val migrateCover get() = settings.migrateCover
    val migrateCategory get() = settings.migrateCategory
    val migrateRemark get() = settings.migrateRemark
    val migrateReadConfig get() = settings.migrateReadConfig
    val deleteDownloadedChapters get() = settings.deleteDownloadedChapters

    fun getMigrationOptions() = settings.migrationOptions()
}
