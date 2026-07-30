package io.legado.app.ui.book.read.page.provider

import android.graphics.Typeface
import androidx.core.net.toUri
import io.legado.app.constant.ReadTipType
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.ui.book.read.sheet.CustomTipTarget
import io.legado.app.utils.isContentScheme
import splitties.init.appCtx
import java.io.File

/**
 * 页眉/页脚与页面外框（状态栏、导航栏、分隔线、背景透明度）的配置快照。
 *
 * `PageView` 有三个实例（prev/cur/next），过去每个实例的 `upStyle()` 都各自把这批配置
 * 从 [ReadBookConfig] 重读一遍——每次读都要走 `config → durConfig → getConfig(styleSelect)`，
 * 而 `getConfig` 是 `@Synchronized`。更要命的是 `loadTypeface(headerFont)` 会**在主线程上
 * 按路径读一次字体文件**，一次样式变更就是三次。
 *
 * 收进快照后：一次样式变更只解析一份配置，字体按路径缓存、只在路径变化时重新加载，
 * 三个 `PageView` 读到的必定是同一份取值。
 *
 * 与 [ChapterProvider.RenderStyle] 一样，配置是唯一输入，所以「配置可能变了」时重建一次即可
 * （`ReadView.upStyle` / `applyThemeColors`，以及 `ReadBookController` 处理任何配置更新 effect 时）。
 */
internal object TipStyleProvider {

    data class TipStyle(
        val headerMode: Int = 0,
        val footerMode: Int = 0,
        val hideStatusBar: Boolean = false,
        val hideNavigationBar: Boolean = false,
        val tipHeaderLeft: Int = ReadTipType.tipNone,
        val tipHeaderMiddle: Int = ReadTipType.tipNone,
        val tipHeaderRight: Int = ReadTipType.tipNone,
        val tipFooterLeft: Int = ReadTipType.tipNone,
        val tipFooterMiddle: Int = ReadTipType.tipNone,
        val tipFooterRight: Int = ReadTipType.tipNone,
        val customTipHeaderLeft: String = "",
        val customTipHeaderMiddle: String = "",
        val customTipHeaderRight: String = "",
        val customTipFooterLeft: String = "",
        val customTipFooterMiddle: String = "",
        val customTipFooterRight: String = "",
        /** 已按 `headerFont` 解析；null 表示回落到 [ChapterProvider.typeface]。 */
        val tipTypeface: Typeface? = null,
        val tipTextSize: Float = 0f,
        val textColor: Int = 0,
        /** 0 表示「跟随正文色」。 */
        val tipHeaderColor: Int = 0,
        /** 0 表示「跟随正文色」。 */
        val tipFooterColor: Int = 0,
        /** -1 表示用主题的 `R.color.divider`，0 表示跟随正文色。 */
        val tipDividerColor: Int = 0,
        val headerPaddingLeft: Int = 0,
        val headerPaddingTop: Int = 0,
        val headerPaddingRight: Int = 0,
        val headerPaddingBottom: Int = 0,
        val footerPaddingLeft: Int = 0,
        val footerPaddingTop: Int = 0,
        val footerPaddingRight: Int = 0,
        val footerPaddingBottom: Int = 0,
        val applyHeaderStyle: Boolean = true,
        /** 已按 `footerFont` 解析；仅在 [applyHeaderStyle] 为 false 时用得上。 */
        val footerTypeface: Typeface? = null,
        val footerTextSize: Float = 0f,
        val showHeaderLine: Boolean = false,
        val showFooterLine: Boolean = false,
        val bgAlpha: Int = 100,
    ) {
        fun tipValueOf(target: CustomTipTarget): Int = when (target) {
            CustomTipTarget.HEADER_LEFT -> tipHeaderLeft
            CustomTipTarget.HEADER_MIDDLE -> tipHeaderMiddle
            CustomTipTarget.HEADER_RIGHT -> tipHeaderRight
            CustomTipTarget.FOOTER_LEFT -> tipFooterLeft
            CustomTipTarget.FOOTER_MIDDLE -> tipFooterMiddle
            CustomTipTarget.FOOTER_RIGHT -> tipFooterRight
        }

        fun customTemplateOf(target: CustomTipTarget): String = when (target) {
            CustomTipTarget.HEADER_LEFT -> customTipHeaderLeft
            CustomTipTarget.HEADER_MIDDLE -> customTipHeaderMiddle
            CustomTipTarget.HEADER_RIGHT -> customTipHeaderRight
            CustomTipTarget.FOOTER_LEFT -> customTipFooterLeft
            CustomTipTarget.FOOTER_MIDDLE -> customTipFooterMiddle
            CustomTipTarget.FOOTER_RIGHT -> customTipFooterRight
        }
    }

    @Volatile
    private var current: TipStyle? = null

    /**
     * 当前快照。首次访问时按需构建——`PageView` 在 `init` 里就要用，早于任何
     * `upTipStyle()` 调用，读默认值会让首帧的页眉页脚全错。
     */
    val style: TipStyle get() = current ?: upTipStyle()

    /** 重建快照并返回。纯派生、幂等。 */
    fun upTipStyle(): TipStyle = ReadBookConfig.run {
        TipStyle(
            headerMode = headerMode,
            footerMode = footerMode,
            hideStatusBar = hideStatusBar,
            hideNavigationBar = hideNavigationBar,
            tipHeaderLeft = tipHeaderLeft,
            tipHeaderMiddle = tipHeaderMiddle,
            tipHeaderRight = tipHeaderRight,
            tipFooterLeft = tipFooterLeft,
            tipFooterMiddle = tipFooterMiddle,
            tipFooterRight = tipFooterRight,
            customTipHeaderLeft = customTipHeaderLeft,
            customTipHeaderMiddle = customTipHeaderMiddle,
            customTipHeaderRight = customTipHeaderRight,
            customTipFooterLeft = customTipFooterLeft,
            customTipFooterMiddle = customTipFooterMiddle,
            customTipFooterRight = customTipFooterRight,
            tipTypeface = typefaceFor(headerFont),
            tipTextSize = headerFontSize.toFloat(),
            applyHeaderStyle = applyHeaderStyle,
            footerTypeface = footerTypefaceFor(footerFont),
            footerTextSize = footerFontSize.toFloat(),
            textColor = textColor,
            tipHeaderColor = resolvedTipHeaderColor,
            tipFooterColor = resolvedTipFooterColor,
            tipDividerColor = tipDividerColor,
            headerPaddingLeft = headerPaddingLeft,
            headerPaddingTop = headerPaddingTop,
            headerPaddingRight = headerPaddingRight,
            headerPaddingBottom = headerPaddingBottom,
            footerPaddingLeft = footerPaddingLeft,
            footerPaddingTop = footerPaddingTop,
            footerPaddingRight = footerPaddingRight,
            footerPaddingBottom = footerPaddingBottom,
            showHeaderLine = showHeaderLine,
            showFooterLine = showFooterLine,
            bgAlpha = bgAlpha,
        ).also { current = it }
    }

    private var cachedFontPath: String? = null
    private var cachedTypeface: Typeface? = null
    private var cachedFooterFontPath: String? = null
    private var cachedFooterTypeface: Typeface? = null

    /** 按路径缓存：字体文件解析是主线程 I/O，只有路径变了才值得重做。 */
    private fun typefaceFor(fontPath: String): Typeface? {
        if (fontPath != cachedFontPath) {
            cachedFontPath = fontPath
            cachedTypeface = loadTypeface(fontPath)
        }
        return cachedTypeface
    }

    /** 页脚字体单独缓存：与页眉字体路径不同，共用一个槽位会互相打掉缓存。 */
    private fun footerTypefaceFor(fontPath: String): Typeface? {
        if (fontPath != cachedFooterFontPath) {
            cachedFooterFontPath = fontPath
            cachedFooterTypeface = loadTypeface(fontPath)
        }
        return cachedFooterTypeface
    }

    private fun loadTypeface(fontPath: String): Typeface? = runCatching {
        when {
            fontPath.isContentScheme() -> appCtx.contentResolver
                .openFileDescriptor(fontPath.toUri(), "r")
                ?.use { Typeface.Builder(it.fileDescriptor).build() }

            fontPath.isNotEmpty() -> Typeface.Builder(File(fontPath)).build()
            else -> null
        }
    }.getOrNull()
}
