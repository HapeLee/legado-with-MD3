package io.legado.app.ui.config.readMangaConfig

import io.legado.app.domain.gateway.MangaSettingsGateway
import org.koin.core.context.GlobalContext

@Deprecated("使用 MangaSettingsGateway.currentSettings 读取，通过 update() 写入")
object ReadMangaConfig {
    private val settings
        get() = GlobalContext.get().get<MangaSettingsGateway>().currentSettings

    val showMangaUi get() = settings.showMangaUi
    val disableMangaScale get() = settings.disableMangaScale
    val disableMangaScrollAnimation get() = settings.disableMangaScrollAnimation
    val disableMangaCrossFade get() = settings.disableMangaCrossFade
    val mangaScrollMode get() = settings.scrollMode
    val mangaPreDownloadNum get() = settings.preDownloadNum
    val mangaAutoPageSpeed get() = settings.autoPageSpeed
    val mangaFooterConfig get() = settings.footerConfig
    val disableClickScroll get() = settings.disableClickScroll
    val mangaLongClick get() = settings.longClick
    val mangaBackground get() = settings.background
    val mangaColorFilter get() = settings.colorFilter
    val hideMangaTitle get() = settings.hideTitle
    val enableMangaEInk get() = settings.enableEInk
    val mangaEInkThreshold get() = settings.eInkThreshold
    val enableMangaGray get() = settings.enableGray
    val webtoonSidePaddingDp get() = settings.webtoonSidePaddingDp
    val mangaVolumeKeyPage get() = settings.volumeKeyPage
    val reverseVolumeKeyPage get() = settings.reverseVolumeKeyPage
    val mangaClickActionTL get() = settings.clickActionTL
    val mangaClickActionTC get() = settings.clickActionTC
    val mangaClickActionTR get() = settings.clickActionTR
    val mangaClickActionML get() = settings.clickActionML
    val mangaClickActionMC get() = settings.clickActionMC
    val mangaClickActionMR get() = settings.clickActionMR
    val mangaClickActionBL get() = settings.clickActionBL
    val mangaClickActionBC get() = settings.clickActionBC
    val mangaClickActionBR get() = settings.clickActionBR
}
