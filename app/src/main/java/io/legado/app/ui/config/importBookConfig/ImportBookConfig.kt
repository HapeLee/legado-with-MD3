package io.legado.app.ui.config.importBookConfig

import io.legado.app.domain.gateway.ImportBookSettingsGateway
import org.koin.core.context.GlobalContext

@Deprecated("使用 ImportBookSettingsGateway.currentSettings 读取，通过 update() 写入")
object ImportBookConfig {
    private val settings get() = GlobalContext.get().get<ImportBookSettingsGateway>().currentSettings

    val importBookPath get() = settings.importBookPath
    val bookImportFileName get() = settings.bookImportFileName
    val localBookImportSort get() = settings.localBookImportSort
    val remoteServerId get() = settings.remoteServerId
}
