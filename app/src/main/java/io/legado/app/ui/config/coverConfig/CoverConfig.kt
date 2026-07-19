package io.legado.app.ui.config.coverConfig

import io.legado.app.domain.gateway.CoverSettingsGateway
import org.koin.core.context.GlobalContext

@Deprecated("使用 CoverSettingsGateway.currentSettings")
object CoverConfig {
    private val settings get() = GlobalContext.get().get<CoverSettingsGateway>().currentSettings
    val loadCoverOnlyWifi get() = settings.loadOnlyOnWifi
    val useDefaultCover get() = settings.useDefaultCover
    val coverShowShadow get() = settings.showShadow
    val coverShowStroke get() = settings.showStroke
    val coverDefaultColor get() = settings.useDefaultColor
    val defaultCover get() = settings.defaultCover
    val coverTextColor get() = settings.textColor
    val coverShadowColor get() = settings.shadowColor
    val coverShowName get() = settings.showName
    val coverShowAuthor get() = settings.showAuthor
    val defaultCoverDark get() = settings.defaultCoverDark
    val coverTextColorN get() = settings.textColorDark
    val coverShadowColorN get() = settings.shadowColorDark
    val coverShowNameN get() = settings.showNameDark
    val coverShowAuthorN get() = settings.showAuthorDark
    val coverInfoOrientation get() = settings.infoOrientation
    val exploreFilterState get() = settings.exploreFilterState
}
