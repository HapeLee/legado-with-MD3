package io.legado.app.ui.config.labConfig

import io.legado.app.domain.gateway.LabSettingsGateway
import org.koin.core.context.GlobalContext

@Deprecated("使用 LabSettingsGateway.currentSettings")
object LabConfig {
    private val settings get() = GlobalContext.get().get<LabSettingsGateway>().currentSettings
    val labEnabled get() = settings.enabled
    val eInkDisplay get() = settings.eInkDisplay
    val eyeProtection get() = settings.eyeProtection
}
