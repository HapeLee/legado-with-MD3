package io.legado.app.ui.config.readMangaConfig

import io.legado.app.constant.PreferKey
import io.legado.app.ui.book.manga.config.MangaScrollMode
import io.legado.app.ui.config.prefDelegate
import io.legado.app.utils.toastOnUi
import splitties.init.appCtx

object ReadMangaConfig {

    var showMangaUi by prefDelegate(
        PreferKey.showMangaUi,
        true
    )

    var disableMangaScale by prefDelegate(
        PreferKey.disableMangaScale,
        true
    )

    var disableMangaScrollAnimation by prefDelegate(
        PreferKey.disableMangaScrollAnimation,
        false
    )

    var disableMangaCrossFade by prefDelegate(
        PreferKey.disableMangaCrossFade,
        false
    )

    var mangaScrollMode by prefDelegate(
        PreferKey.mangaScrollMode,
        MangaScrollMode.WEBTOON
    )

    var mangaPreDownloadNum by prefDelegate(
        PreferKey.mangaPreDownloadNum,
        10
    )

    var mangaAutoPageSpeed by prefDelegate(
        PreferKey.mangaAutoPageSpeed,
        3
    )

    var mangaFooterConfig by prefDelegate(
        PreferKey.mangaFooterConfig,
        ""
    )

    var disableClickScroll by prefDelegate(
        PreferKey.disableClickScroll,
        false
    )

    var mangaLongClick by prefDelegate(
        PreferKey.mangaLongClick,
        true
    )

    var mangaBackground by prefDelegate(
        PreferKey.mangaBackground,
        0xFF000000.toInt()
    )

    var mangaColorFilter by prefDelegate(
        PreferKey.mangaColorFilter,
        ""
    )

    var hideMangaTitle by prefDelegate(
        PreferKey.hideMangaTitle,
        false
    )

    var enableMangaEInk by prefDelegate(
        PreferKey.enableMangaEInk,
        false
    )

    var mangaEInkThreshold by prefDelegate(
        PreferKey.mangaEInkThreshold,
        150
    )

    var enableMangaGray by prefDelegate(
        PreferKey.enableMangaGray,
        false
    )

    var webtoonSidePaddingDp by prefDelegate(
        PreferKey.webtoonSidePaddingDp,
        0
    )

    var mangaVolumeKeyPage by prefDelegate(
        PreferKey.mangaVolumeKeyPage,
        false
    )

    var reverseVolumeKeyPage by prefDelegate(
        PreferKey.reverseVolumeKeyPage,
        false
    )

    // -1无操作 1下一页 2上一页 0显示菜单 3下一章 4上一章
    var mangaClickActionTL by prefDelegate(PreferKey.mangaClickActionTL, -1)
    var mangaClickActionTC by prefDelegate(PreferKey.mangaClickActionTC, -1)
    var mangaClickActionTR by prefDelegate(PreferKey.mangaClickActionTR, 1)
    var mangaClickActionML by prefDelegate(PreferKey.mangaClickActionML, 2)
    var mangaClickActionMC by prefDelegate(PreferKey.mangaClickActionMC, 0)
    var mangaClickActionMR by prefDelegate(PreferKey.mangaClickActionMR, 1)
    var mangaClickActionBL by prefDelegate(PreferKey.mangaClickActionBL, 2)
    var mangaClickActionBC by prefDelegate(PreferKey.mangaClickActionBC, 1)
    var mangaClickActionBR by prefDelegate(PreferKey.mangaClickActionBR, 1)

    fun detectMangaClickArea() {
        if (mangaClickActionTL * mangaClickActionTC * mangaClickActionTR
            * mangaClickActionML * mangaClickActionMC * mangaClickActionMR
            * mangaClickActionBL * mangaClickActionBC * mangaClickActionBR != 0
        ) {
            mangaClickActionMC = 0
            appCtx.toastOnUi("当前没有配置菜单区域,自动恢复中间区域为菜单.")
        }
    }
}
