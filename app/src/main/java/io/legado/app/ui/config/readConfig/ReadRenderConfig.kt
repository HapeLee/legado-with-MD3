package io.legado.app.ui.config.readConfig

import io.legado.app.BuildConfig
import io.legado.app.constant.PreferKey
import io.legado.app.ui.config.prefDelegate
import io.legado.app.ui.config.themeConfig.ThemeConfig
import io.legado.app.utils.isNightMode
import io.legado.app.utils.sysConfiguration

/**
 * 渲染/布局相关配置
 *
 * 被 ChapterProvider、TextLine、ContentTextView、ReadView 在渲染循环中读取。
 */
object ReadRenderConfig {

    val isEInkMode: Boolean
        get() = ThemeConfig.appTheme == "4"

    var isNightTheme: Boolean
        get() = when (ThemeConfig.themeMode) {
            "1" -> false
            "2" -> true
            else -> sysConfiguration.isNightMode
        }
        set(value) {
            val newMode = if (value) "2" else "1"
            if (ThemeConfig.themeMode != newMode) {
                ThemeConfig.themeMode = newMode
            }
        }

    val enableReview: Boolean
        get() = BuildConfig.DEBUG && enableReviewPref

    private var enableReviewPref by prefDelegate(
        PreferKey.enableReview,
        false
    )

    var useAntiAlias by prefDelegate(
        PreferKey.antiAlias,
        false
    )

    var systemTypefaces by prefDelegate(
        PreferKey.systemTypefaces,
        0
    )

    var doubleHorizontalPage by prefDelegate(
        PreferKey.doublePageHorizontal,
        "0"
    )

    var adaptSpecialStyle by prefDelegate(
        PreferKey.adaptSpecialStyle,
        true
    )

    var useUnderline by prefDelegate(
        PreferKey.useUnderline,
        false
    )

    var optimizeRender by prefDelegate(
        PreferKey.optimizeRender,
        false
    )

    var noAnimScrollPage by prefDelegate(
        PreferKey.noAnimScrollPage,
        false
    )

    var paddingDisplayCutouts by prefDelegate(
        PreferKey.paddingDisplayCutouts,
        false
    )

    var pageTouchSlop by prefDelegate(
        PreferKey.pageTouchSlop,
        0
    )

    var selectText by prefDelegate(
        PreferKey.selectText,
        true
    )

    var clickImgWay by prefDelegate(
        PreferKey.clickImgWay,
        "2"
    )

    var chineseConverterType by prefDelegate(
        PreferKey.chineseConverterType,
        0
    )
}
