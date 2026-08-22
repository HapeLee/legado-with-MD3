package io.legado.app.help.config

import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.annotation.Keep
import androidx.core.graphics.toColorInt
import io.legado.app.R
import io.legado.app.constant.PageAnim
import io.legado.app.constant.ReadTipType
import io.legado.app.data.entities.HighlightRule
import io.legado.app.data.repository.ReadStyleConfigStore
import io.legado.app.domain.gateway.ReadSettingsGateway
import io.legado.app.model.ReadSessionState
import io.legado.app.ui.config.readConfig.ReadConfig
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.hexString
import splitties.init.appCtx

/**
 * 阅读界面配置
 */
@Suppress("ConstPropertyName")
@Keep
object ReadBookConfig {
    private lateinit var configStore: ReadStyleConfigStore
    private lateinit var readSettingsGateway: ReadSettingsGateway
    private val readSettings get() = readSettingsGateway.currentSettings

    internal fun initialize(
        configStore: ReadStyleConfigStore,
        readSettingsGateway: ReadSettingsGateway,
    ) {
        this.configStore = configStore
        this.readSettingsGateway = readSettingsGateway
        configStore.initConfigs()
        configStore.initShareConfig()
    }


    const val configFileName = "readConfig.json"
    const val shareConfigFileName = "shareReadConfig.json"
    val configFilePath: String get() = configStore.configFilePath
    val shareConfigFilePath: String get() = configStore.shareConfigFilePath

    /** 共享排版那一份，`config` 与 `getExportConfig()` 要用。 */
    private val shareConfig: Config get() = configStore.shareConfig

    var durConfig
        get() = configStore.configAt(styleSelect)
        set(value) {
            configStore.replaceConfigAt(styleSelect, value, alsoShare = shareLayout)
        }

    val textColor: Int get() = durConfig.curTextColor()
    val textColorNight: Int
        get() = try {
            durConfig.getTextColorNight().toColorInt()
        } catch (_: Exception) {
            0xFFADADAD.toInt()
        }
    val textAccentColor: Int get() = durConfig.curTextAccentColor()
    val textShadowColor: Int get() = durConfig.curTextShadowColor()
    val menuColor: Int get() = readMenuAccentColor

    // DataStore 标量已归入 ReadSettings；这里仅保留旧渲染层所需的同步只读快照。
    val readBodyToLh get() = readSettings.readBodyToLh
    val autoReadSpeed get() = readSettings.autoReadSpeed
    val readStyleSelect get() = readSettings.readStyleSelect
    val comicStyleSelect get() = readSettings.comicStyleSelect
    val shareLayout get() = readSettings.shareLayout
    val textFullJustify get() = readSettings.textFullJustify
    val textBottomJustify get() = readSettings.textBottomJustify
    val hideStatusBar get() = readSettings.hideStatusBar
    val hideNavigationBar get() = readSettings.hideNavigationBar
    val useZhLayout get() = readSettings.useZhLayout
    val readMenuIconShowText get() = readSettings.readMenuIconShowText
    val showMenuIcon get() = readSettings.showMenuIcon
    val readingAnchorEnabled get() = readSettings.readingAnchorEnabled
    val titleBarCompact get() = readSettings.titleBarCompact
    val readMenuFloatingBottomBar get() = readSettings.readMenuFloatingBottomBar
    val readMenuTopBarLiquidGlassButtons get() = readSettings.readMenuTopBarLiquidGlassButtons
    val readMenuTopBarMergeButtons get() = readSettings.readMenuTopBarMergeButtons
    val readMenuTopBarTitleCapsule get() = readSettings.readMenuTopBarTitleCapsule
    val readMenuBottomBarLiquidGlassButtons get() = readSettings.readMenuBottomBarLiquidGlassButtons
    val readMenuFloatingIconLiquidGlass get() = readSettings.readMenuFloatingIconLiquidGlass
    val readMenuBorderColor get() = readSettings.readMenuBorderColor
    val readMenuBorderColorNight get() = readSettings.readMenuBorderColorNight
    val readMenuTextColor get() = readSettings.readMenuTextColor
    val readMenuTextColorNight get() = readSettings.readMenuTextColorNight
    val showTitleBarIcons get() = readSettings.showTitleBarIcons
    val readSliderMode get() = readSettings.readSliderMode
    val showBrightnessView get() = readSettings.showBrightnessView
    val brightnessVwPos get() = readSettings.brightnessVwPos
    val readBrightness get() = readSettings.readBrightness
    val brightnessAuto get() = readSettings.brightnessAuto
    val styleSelect get() = if (ReadSessionState.isComic) comicStyleSelect else readStyleSelect
    val readMenuColorMode get() = readSettings.readMenuColorMode.coerceIn(0, 1)
    val readMenuIconStyle get() = readSettings.readMenuIconStyle.coerceIn(0, 2)
    val titleBarIconStyle get() = readSettings.titleBarIconStyle.coerceIn(0, 2)
    val readMenuIconItemsPerRow get() = readSettings.readMenuIconItemsPerRow.coerceIn(2, 8)
    val readMenuIconRowCount get() = readSettings.readMenuIconRowCount.coerceIn(1, 2)
    val readMenuBottomCornerRadius get() = readSettings.readMenuBottomCornerRadius.coerceIn(0, 32)
    val readMenuTopBarBlurMode get() = readSettings.readMenuTopBarBlurMode.coerceIn(0, 2)
    val readMenuBottomBarBlurMode get() = readSettings.readMenuBottomBarBlurMode.coerceIn(0, 2)
    val readMenuTopBarBlurStyle get() = readSettings.readMenuTopBarBlurStyle.coerceIn(0, 1)
    val readMenuBottomBarBlurStyle get() = readSettings.readMenuBottomBarBlurStyle.coerceIn(0, 1)
    val readMenuBlurRadius get() = readSettings.readMenuBlurRadius.coerceIn(0, 32)
    val readMenuBlurAlpha get() = readSettings.readMenuBlurAlpha.coerceIn(0, 100)
    val readMenuBlurColor get() = readSettings.readMenuBlurColor
    val readMenuBlurColorNight get() = readSettings.readMenuBlurColorNight
    val readMenuPaletteStyle get() = readSettings.readMenuPaletteStyle
    val readMenuLensRadius get() = readSettings.readMenuLensRadius.coerceIn(0f, 48f)
    val readMenuBorderWidth get() = readSettings.readMenuBorderWidth.coerceIn(0, 4)
    val titleBarIconPosition get() = readSettings.titleBarIconPosition.coerceIn(0, 3)
    val readMenuBgColor: Int
        get() = readSettings.readMenuBgColor.takeIf { it != 0 }
            ?: durConfig.menuBgColor(isNight = false)
    val readMenuAccentColor: Int
        get() = readSettings.readMenuAccentColor.takeIf { it != 0 }
            ?: durConfig.menuAccentColor(isNight = false)
    val readMenuContainerColor: Int
        get() = readSettings.readMenuContainerColor.takeIf { it != 0 } ?: readMenuBgColor
    val readMenuBgColorNight: Int
        get() = readSettings.readMenuBgColorNight.takeIf { it != 0 }
            ?: durConfig.menuBgColor(isNight = true)
    val readMenuAccentColorNight: Int
        get() = readSettings.readMenuAccentColorNight.takeIf { it != 0 }
            ?: durConfig.menuAccentColor(isNight = true)
    val readMenuContainerColorNight: Int
        get() = readSettings.readMenuContainerColorNight.takeIf { it != 0 } ?: readMenuBgColorNight

    // region Map properties (JSON string serialization)

    fun encodeReadMenuCustomIcons(value: Map<String, String>): String {
        return GSON.toJson(value.filterValues { it.isNotBlank() })
    }

    private fun parseReadMenuCustomIcons(value: String?): Map<String, String> {
        if (value.isNullOrBlank()) return emptyMap()
        return GSON.fromJsonObject<Map<String, String>>(value).getOrNull()
            ?.filterValues { it.isNotBlank() } ?: emptyMap()
    }

    val readMenuCustomIcons: Map<String, String>
        get() = parseReadMenuCustomIcons(readSettings.readMenuCustomIcons)

    val titleBarCustomIcons: Map<String, String>
        get() = parseReadMenuCustomIcons(readSettings.titleBarCustomIcons)

    // endregion

    val resolvedMenuBgColor: Int
        get() {
            val isNight = ReadStyleResolver.isNightTheme()
            return when (ReadConfig.readBarStyle) {
                1 -> { // 跟随阅读背景
                    val background = ReadStyleResolver.currentBackground(durConfig)
                    if (background.type == 0) {
                        try {
                            background.value.toColorInt()
                        } catch (_: Exception) {
                            if (isNight) Color.BLACK else Color.WHITE
                        }
                    } else {
                        ReadSessionState.backgroundMeanColor.takeIf { it != 0 }
                            ?: (if (isNight) Color.BLACK else Color.WHITE)
                    }
                }
                2 -> { // 自定义
                    if (isNight) readMenuBgColorNight else readMenuBgColor
                }
                else -> {
                    if (isNight) Color.BLACK else Color.WHITE
                }
            }
        }

    val resolvedMenuAccentColor: Int
        get() = if (ReadStyleResolver.isNightTheme()) readMenuAccentColorNight else readMenuAccentColor

    val resolvedMenuContainerColor: Int
        get() = if (ReadStyleResolver.isNightTheme()) readMenuContainerColorNight else readMenuContainerColor

    val resolvedMenuBorderColor: Int
        get() = if (ReadStyleResolver.isNightTheme()) readMenuBorderColorNight else readMenuBorderColor

    val resolvedMenuTextColor: Int
        get() = if (ReadStyleResolver.isNightTheme()) readMenuTextColorNight else readMenuTextColor

    val resolvedMenuBlurColor: Int
        get() = if (ReadStyleResolver.isNightTheme()) readMenuBlurColorNight else readMenuBlurColor


    val config get() = if (shareLayout) shareConfig else durConfig

    val bgAlpha: Int
        get() = config.bgAlpha

    val pageAnim: Int
        get() = config.curPageAnim()

    val textFont: String
        get() = config.textFont

    val titleFont: String
        get() = config.titleFont

    val headerFont: String
        get() = config.headerFont

    val footerFont: String
        get() = config.footerFont

    val headerFontSize: Int
        get() = config.headerFontSize.takeIf { it > 0 } ?: 12

    val footerFontSize: Int
        get() = config.footerFontSize.takeIf { it > 0 } ?: 12

    val applyHeaderStyle: Boolean
        get() = config.applyHeaderStyle

    val textBold: Int
        get() = config.textBold

    val titleBold: Int
        get() = config.titleBold

    val textItalic: Boolean
        get() = config.textItalic

    val textShadow: Boolean
        get() = config.textShadow

    val shadowRadius: Float
        get() = config.shadowRadius

    val shadowDx: Float
        get() = config.shadowDx

    val shadowDy: Float
        get() = config.shadowDy

    val textSize: Int
        get() = config.textSize

    val letterSpacing: Float
        get() = config.letterSpacing

    val lineSpacingExtra: Int
        get() = config.lineSpacingExtra

    val titleLineSpacingExtra: Int
        get() = config.titleLineSpacingExtra

    val titleLineSpacingSub: Int
        get() = config.titleLineSpacingSub

    val paragraphSpacing: Int
        get() = config.paragraphSpacing

    /**
     * 标题位置 0:居左 1:居中 2:隐藏
     */
    val titleMode: Int
        get() = config.titleMode
    val titleSize: Int
        get() = config.titleSize

    val titleSegType: Int
        get() = config.titleSegType

    val titleSegScaling: Float
        //旧版本可能存入负值，负值非法，回落到默认比例
        get() = config.titleSegScaling.let { if (it < 0f) 1f else it.coerceAtMost(2f) }

    val titleSegDistance: Int
        get() = config.titleSegDistance

    val titleSegFlag: String
        get() = config.titleSegFlag

    /**
     * 是否标题居中
     */
    val isMiddleTitle get() = titleMode == 1

    val titleTopSpacing: Int
        get() = config.titleTopSpacing

    val titleBottomSpacing: Int
        get() = config.titleBottomSpacing

    val titleColor: Int
        get() = config.titleColor

    val titleColorNight: Int
        get() = config.titleColorNight

    val resolvedTitleColor: Int
        get() = if (ReadStyleResolver.isNightTheme()) titleColorNight else titleColor

    val paragraphIndent: String
        get() = config.paragraphIndent

    val underline: Boolean
        get() = config.underline

    val underlineHeight: Int
        get() = config.underlineHeight

    val underlinePadding: Int
        get() = config.underlinePadding

    val underlineExtend: Boolean
        get() = config.underlineExtend

    val dottedLine: Boolean
        get() = config.dottedLine

    val dottedBase: Float
        get() = config.dottedBase

    val dottedRatio: Float
        get() = config.dottedRatio

    val paddingBottom: Int
        get() = config.paddingBottom

    val paddingLeft: Int
        get() = config.paddingLeft

    val paddingRight: Int
        get() = config.paddingRight

    val paddingTop: Int
        get() = config.paddingTop

    val headerPaddingBottom: Int
        get() = config.headerPaddingBottom

    val headerPaddingLeft: Int
        get() = config.headerPaddingLeft

    val headerPaddingRight: Int
        get() = config.headerPaddingRight

    val headerPaddingTop: Int
        get() = config.headerPaddingTop

    val footerPaddingBottom: Int
        get() = config.footerPaddingBottom

    val footerPaddingLeft: Int
        get() = config.footerPaddingLeft

    val footerPaddingRight: Int
        get() = config.footerPaddingRight

    val footerPaddingTop: Int
        get() = config.footerPaddingTop

    val showHeaderLine: Boolean
        get() = config.showHeaderLine

    val showFooterLine: Boolean
        get() = config.showFooterLine

    val underlineColor: Int
        get() = config.curUnderlineColor()

    val menuBgColor: Int
        get() = readMenuBgColor

    val menuAcColor: Int
        get() = readMenuAccentColor

    val shadowColor: Int
        get() = config.curTextShadowColor()

    // region Tip / Header / Footer

    val tipHeaderLeft: Int
        get() = config.tipHeaderLeft

    val tipHeaderMiddle: Int
        get() = config.tipHeaderMiddle

    val tipHeaderRight: Int
        get() = config.tipHeaderRight

    val tipFooterLeft: Int
        get() = config.tipFooterLeft

    val tipFooterMiddle: Int
        get() = config.tipFooterMiddle

    val tipFooterRight: Int
        get() = config.tipFooterRight

    val customTipHeaderLeft: String
        get() = config.customTipHeaderLeft

    val customTipHeaderMiddle: String
        get() = config.customTipHeaderMiddle

    val customTipHeaderRight: String
        get() = config.customTipHeaderRight

    val customTipFooterLeft: String
        get() = config.customTipFooterLeft

    val customTipFooterMiddle: String
        get() = config.customTipFooterMiddle

    val customTipFooterRight: String
        get() = config.customTipFooterRight

    val headerMode: Int
        get() = config.headerMode

    val footerMode: Int
        get() = config.footerMode

    val tipHeaderColor: Int
        get() = config.tipHeaderColor

    val tipHeaderColorNight: Int
        get() = config.tipHeaderColorNight

    val resolvedTipHeaderColor: Int
        get() = if (ReadStyleResolver.isNightTheme()) tipHeaderColorNight else tipHeaderColor

    val tipFooterColor: Int
        get() = config.tipFooterColor

    val tipFooterColorNight: Int
        get() = config.tipFooterColorNight

    val resolvedTipFooterColor: Int
        get() = if (ReadStyleResolver.isNightTheme()) tipFooterColorNight else tipFooterColor

    val tipDividerColor: Int
        get() = config.tipDividerColor

    val tipColorNames get() = appCtx.resources.getStringArray(R.array.tip_color).toList()
    val tipDividerColorNames get() = appCtx.resources.getStringArray(R.array.tip_divider_color).toList()

    // endregion

    fun getExportConfig(): Config {
        val exportConfig = durConfig.copy(highlightRules = arrayListOf())
        if (!shareLayout) return exportConfig
        // 共享排版开着时导出的是「当前样式的背景 + 共享那份的排版」，故只覆盖排版项。
        return exportConfig.copy(
            textFont = shareConfig.textFont,
            titleFont = shareConfig.titleFont,
            headerFont = shareConfig.headerFont,
            footerFont = shareConfig.footerFont,
            headerFontSize = shareConfig.headerFontSize,
            footerFontSize = shareConfig.footerFontSize,
            applyHeaderStyle = shareConfig.applyHeaderStyle,
            textBold = shareConfig.textBold,
            textSize = shareConfig.textSize,
            letterSpacing = shareConfig.letterSpacing,
            lineSpacingExtra = shareConfig.lineSpacingExtra,
            paragraphSpacing = shareConfig.paragraphSpacing,
            titleMode = shareConfig.titleMode,
            titleSize = shareConfig.titleSize,
            titleTopSpacing = shareConfig.titleTopSpacing,
            titleBottomSpacing = shareConfig.titleBottomSpacing,
            titleColor = shareConfig.titleColor,
            titleColorNight = shareConfig.titleColorNight,
            paddingBottom = shareConfig.paddingBottom,
            paddingLeft = shareConfig.paddingLeft,
            paddingRight = shareConfig.paddingRight,
            paddingTop = shareConfig.paddingTop,
            headerPaddingBottom = shareConfig.headerPaddingBottom,
            headerPaddingLeft = shareConfig.headerPaddingLeft,
            headerPaddingRight = shareConfig.headerPaddingRight,
            headerPaddingTop = shareConfig.headerPaddingTop,
            footerPaddingBottom = shareConfig.footerPaddingBottom,
            footerPaddingLeft = shareConfig.footerPaddingLeft,
            footerPaddingRight = shareConfig.footerPaddingRight,
            footerPaddingTop = shareConfig.footerPaddingTop,
            showHeaderLine = shareConfig.showHeaderLine,
            showFooterLine = shareConfig.showFooterLine,
            tipHeaderLeft = shareConfig.tipHeaderLeft,
            tipHeaderMiddle = shareConfig.tipHeaderMiddle,
            tipHeaderRight = shareConfig.tipHeaderRight,
            tipFooterLeft = shareConfig.tipFooterLeft,
            tipFooterMiddle = shareConfig.tipFooterMiddle,
            tipFooterRight = shareConfig.tipFooterRight,
            tipHeaderColor = shareConfig.tipHeaderColor,
            tipHeaderColorNight = shareConfig.tipHeaderColorNight,
            tipFooterColor = shareConfig.tipFooterColor,
            tipFooterColorNight = shareConfig.tipFooterColorNight,
            headerMode = shareConfig.headerMode,
            footerMode = shareConfig.footerMode,
            textItalic = shareConfig.textItalic,
            textShadow = shareConfig.textShadow,
            shadowRadius = shareConfig.shadowRadius,
            shadowDx = shareConfig.shadowDx,
            shadowDy = shareConfig.shadowDy,
            titleBold = shareConfig.titleBold,
            titleLineSpacingExtra = shareConfig.titleLineSpacingExtra,
            titleLineSpacingSub = shareConfig.titleLineSpacingSub,
            titleSegType = shareConfig.titleSegType,
            titleSegScaling = shareConfig.titleSegScaling,
            titleSegDistance = shareConfig.titleSegDistance,
            titleSegFlag = shareConfig.titleSegFlag,
            paragraphIndent = shareConfig.paragraphIndent,
            underline = shareConfig.underline,
            underlineHeight = shareConfig.underlineHeight,
            underlinePadding = shareConfig.underlinePadding,
            dottedLine = shareConfig.dottedLine,
            dottedBase = shareConfig.dottedBase,
            dottedRatio = shareConfig.dottedRatio,
            bgAlpha = shareConfig.bgAlpha
        )
    }

    @Keep
    data class Config(
        val name: String = "",
        val bgStr: String = "#EEEEEE",//白天背景
        val bgStrNight: String = "#000000",//夜间背景
        @Transient
        val menuBgColor: String = "#EEEFE3",
        @Transient
        val menuAcColor: String = "#EEEFE3",
        @Transient
        val menuBgColorNight: String = "#BFCBAD",
        @Transient
        val menuAcColorNight: String = "#586249",
        val bgStrEInk: String = "#FFFFFF",//EInk背景
        val bgAlpha: Int = 100,//背景透明度
        val bgType: Int = 0,//白天背景类型 0:颜色, 1:assets图片, 2其它图片
        val bgTypeNight: Int = 0,//夜间背景类型
        val bgTypeEInk: Int = 0,//EInk背景类型
        private val darkStatusIcon: Boolean = true,//白天是否暗色状态栏
        private val darkStatusIconNight: Boolean = false,//晚上是否暗色状态栏
        private val darkStatusIconEInk: Boolean = true,
        private val textColor: String = "#3E3D3B",//白天文字颜色
        private val textColorNight: String = "#ADADAD",//夜间文字颜色
        private val textColorEInk: String = "#000000",
        private val textAccentColor: String = "#834E00",//白天强调文字颜色
        private val textAccentColorNight: String = "#FE4D55",//夜间强调文字颜色
        private val textAccentColorEInk: String = "#000000",
        private val pageAnim: Int = 0,//翻页动画
        private val pageAnimEInk: Int = 4,
        val textFont: String = "",//字体
        val titleFont: String = "",//标题字体
        val headerFont: String = "",//页眉字体
        val footerFont: String = "",//页脚字体
        val headerFontSize: Int = 12,//页眉字号
        val footerFontSize: Int = 12,//页脚字号
        val applyHeaderStyle: Boolean = true,//页脚是否应用页眉字体样式
        val textBold: Int = 500,//是否粗体字 0:正常, 1:粗体, 2:细体
        val textSize: Int = 20,//文字大小
        val textItalic: Boolean = false,// 是否启用斜体
        val textShadow: Boolean = false,// 是否启用阴影
        val shadowRadius: Float = 16f,// 阴影模糊半径
        val shadowDx: Float = 1f,// 阴影x偏移
        val shadowDy: Float = 1f,// 阴影y偏移
        private val shadowColor: String = "#3E3D3B",
        private val shadowColorN: String = "#3E3D3B",
        val letterSpacing: Float = 0.1f,//字间距
        val lineSpacingExtra: Int = 12,//行间距
        val paragraphSpacing: Int = 2,//段距
        val titleMode: Int = 0,//标题位置 0:居左 1:居中 2:隐藏
        val titleSize: Int = 20,
        val titleTopSpacing: Int = 0,
        val titleBottomSpacing: Int = 0,
        val titleColor: Int = 0,
        val titleColorNight: Int = 0,
        val titleBold: Int = 500,//是否粗体字 0:正常, 1:粗体, 2:细体
        val titleLineSpacingExtra: Int = 12,
        val titleLineSpacingSub: Int = 12,
        val titleSegType: Int = 0,//分段模式
        val titleSegScaling: Float = 1f,//分段缩放，第二段与第一段的字体大小比例
        val titleSegDistance: Int = 4,//分段判断，第几个字符开始分段
        val titleSegFlag: String = "",//分段判断，碰到指定值时分段
        val paragraphIndent: String = "　　",//段落缩进
        val underline: Boolean = false, //下划线
        val underlinePadding: Int = 10,
        val underlineHeight: Int = 1,
        val underlineExtend: Boolean = false, //下划线延伸
        val underlineColor: String = "#3E3D3B",
        val underlineColorNight: String = "#ADADAD",
        val dottedLine: Boolean = false, //虚线
        val dottedBase: Float = 6f, //长度
        val dottedRatio: Float = 6f,
        val paddingBottom: Int = 6,
        val paddingLeft: Int = 16,
        val paddingRight: Int = 16,
        val paddingTop: Int = 6,
        val headerPaddingBottom: Int = 0,
        val headerPaddingLeft: Int = 16,
        val headerPaddingRight: Int = 16,
        val headerPaddingTop: Int = 0,
        val footerPaddingBottom: Int = 6,
        val footerPaddingLeft: Int = 16,
        val footerPaddingRight: Int = 16,
        val footerPaddingTop: Int = 6,
        val showHeaderLine: Boolean = false,
        val showFooterLine: Boolean = true,
        val tipHeaderLeft: Int = ReadTipType.tipTime,
        val tipHeaderMiddle: Int = ReadTipType.tipNone,
        val tipHeaderRight: Int = ReadTipType.tipBattery,
        val tipFooterLeft: Int = ReadTipType.tipChapterTitle,
        val tipFooterMiddle: Int = ReadTipType.tipNone,
        val tipFooterRight: Int = ReadTipType.tipPageAndTotal,
        val customTipHeaderLeft: String = "",
        val customTipHeaderMiddle: String = "",
        val customTipHeaderRight: String = "",
        val customTipFooterLeft: String = "",
        val customTipFooterMiddle: String = "",
        val customTipFooterRight: String = "",
        val tipHeaderColor: Int = 0,
        val tipHeaderColorNight: Int = 0,
        val tipFooterColor: Int = 0,
        val tipFooterColorNight: Int = 0,
        val tipDividerColor: Int = -1,
        val headerMode: Int = 0,
        val footerMode: Int = 0,
        @Transient
        val menuIconShowText: Boolean = true,
        @Transient
        val menuIconStyle: Int = 0,
        @Transient
        val menuIconItemsPerRow: Int = 5,
        @Transient
        val menuIconRowCount: Int = 1,
        @Transient
        val menuBottomCornerRadius: Int = 0,
        @Transient
        val menuBottomHorizontalMargin: Int = 0,
        @Transient
        val menuBottomBottomMargin: Int = 0,
        val highlightRules: ArrayList<HighlightRule> = arrayListOf()
    ) {

        @Transient
        private var textColorIntEInk = -1

        @Transient
        private var textColorIntNight = -1

        @Transient
        private var textColorInt = -1

        @Transient
        private var shadowColorNightInt = -1

        @Transient
        private var shadowColorInt = -1

        @Transient
        private var menuBgColorInt = -1

        @Transient
        private var menuBgColorNightInt = -1

        @Transient
        private var menuAcColorInt = -1

        @Transient
        private var menuAcColorNightInt = -1

        @Transient
        private var underlineColorInt = -1

        @Transient
        private var underlineColorNightInt = -1

        @Transient
        private var textAccentColorIntEInk = -1

        @Transient
        private var textAccentColorIntNight = -1

        @Transient
        private var textAccentColorInt = -1

        @Transient
        private var initAccentColorInt = false

        @Transient
        private var initColorInt = false

        fun toMap() = mapOf(
            "name" to name,
            "bgStr" to bgStr,
            "bgStrNight" to bgStrNight,
            "bgStrEInk" to bgStrEInk,
            "bgAlpha" to bgAlpha,
            "bgType" to bgType,
            "bgTypeNight" to bgTypeNight,
            "bgTypeEInk" to bgTypeEInk,
            "darkStatusIcon" to darkStatusIcon,
            "darkStatusIconNight" to darkStatusIconNight,
            "darkStatusIconEInk" to darkStatusIconEInk,
            "textColor" to textColor,
            "textColorNight" to textColorNight,
            "textColorEInk" to textColorEInk,
            "textColorInt" to textColorInt,
            "textColorIntNight" to textColorIntNight,
            "textColorIntEInk" to textColorIntEInk,
            "textAccentColor" to textAccentColor,
            "textAccentColorNight" to textAccentColorNight,
            "textAccentColorEInk" to textAccentColorEInk,
            "textAccentColorInt" to textAccentColorInt,
            "textAccentColorIntNight" to textAccentColorIntNight,
            "textAccentColorIntEInk" to textAccentColorIntEInk,
            "pageAnim" to pageAnim,
            "pageAnimEInk" to pageAnimEInk,
            "textFont" to textFont,
            "titleFont" to titleFont,
            "headerFont" to headerFont,
            "footerFont" to footerFont,
            "headerFontSize" to headerFontSize,
            "footerFontSize" to footerFontSize,
            "applyHeaderStyle" to applyHeaderStyle,
            "textBold" to textBold,
            "textSize" to textSize,
            "letterSpacing" to letterSpacing,
            "lineSpacingExtra" to lineSpacingExtra,
            "paragraphSpacing" to paragraphSpacing,
            "titleMode" to titleMode,
            "titleSize" to titleSize,
            "titleTopSpacing" to titleTopSpacing,
            "titleBottomSpacing" to titleBottomSpacing,
            "titleColor" to titleColor,
            "titleColorNight" to titleColorNight,
            "paragraphIndent" to paragraphIndent,
            "paddingBottom" to paddingBottom,
            "paddingLeft" to paddingLeft,
            "paddingRight" to paddingRight,
            "paddingTop" to paddingTop,
            "headerPaddingBottom" to headerPaddingBottom,
            "headerPaddingLeft" to headerPaddingLeft,
            "headerPaddingRight" to headerPaddingRight,
            "headerPaddingTop" to headerPaddingTop,
            "footerPaddingBottom" to footerPaddingBottom,
            "footerPaddingLeft" to footerPaddingLeft,
            "footerPaddingRight" to footerPaddingRight,
            "footerPaddingTop" to footerPaddingTop,
            "showHeaderLine" to showHeaderLine,
            "showFooterLine" to showFooterLine,
            "tipHeaderLeft" to tipHeaderLeft,
            "tipHeaderMiddle" to tipHeaderMiddle,
            "tipHeaderRight" to tipHeaderRight,
            "tipFooterLeft" to tipFooterLeft,
            "tipFooterMiddle" to tipFooterMiddle,
            "tipFooterRight" to tipFooterRight,
            "tipHeaderColor" to tipHeaderColor,
            "tipHeaderColorNight" to tipHeaderColorNight,
            "tipFooterColor" to tipFooterColor,
            "tipFooterColorNight" to tipFooterColorNight,
            "tipDividerColor" to tipDividerColor,
            "headerMode" to headerMode,
            "footerMode" to footerMode,
            "highlightRules" to highlightRules.map { mapOf("id" to it.id, "name" to it.name, "pattern" to it.pattern, "sampleText" to it.sampleText, "targetScope" to it.targetScope, "enabled" to it.enabled, "position" to it.position, "textColor" to it.textColor, "bgColor" to it.bgColor, "underlineMode" to it.underlineMode, "underlineColor" to it.underlineColor, "underlineWidth" to it.underlineWidth, "underlineOffset" to it.underlineOffset, "underlineSvgPath" to it.underlineSvgPath, "bgImage" to it.bgImage, "bgImageFit" to it.bgImageFit, "bgImageScale" to it.bgImageScale, "configName" to it.configName, "fontPath" to it.fontPath, "fontSizeOffset" to it.fontSizeOffset) }
        )

        fun getBgPath(bgIndex: Int): String? {
            return ReadStyleResolver.backgroundPath(this, bgIndex)
        }

        private inline fun <T> currentModeValue(
            eInk: () -> T,
            night: () -> T,
            day: () -> T
        ): T {
            return when (ReadStyleResolver.currentMode()) {
                ReadStyleResolver.ReadStyleMode.EInk -> eInk()
                ReadStyleResolver.ReadStyleMode.Night -> night()
                ReadStyleResolver.ReadStyleMode.Day -> day()
            }
        }

        private inline fun <T> nightThemeValue(
            night: () -> T,
            day: () -> T
        ): T {
            return if (ReadStyleResolver.isNightTheme()) {
                night()
            } else {
                day()
            }
        }

        private fun String.toColorIntSafe(fallback: Int): Int {
            return runCatching { toColorInt() }.getOrDefault(fallback)
        }

        private fun ensureColorInts() {
            if (initColorInt) {
                return
            }
            textColorIntEInk = textColorEInk.toColorIntSafe(0xFF000000.toInt())
            textColorIntNight = textColorNight.toColorIntSafe(0xFFADADAD.toInt())
            textColorInt = textColor.toColorIntSafe(0xFF3E3D3B.toInt())
            shadowColorNightInt = shadowColorN.toColorIntSafe(0xFF3E3D3B.toInt())
            shadowColorInt = shadowColor.toColorIntSafe(0xFF3E3D3B.toInt())
            menuBgColorInt = menuBgColor.toColorIntSafe(-1)
            menuBgColorNightInt = menuBgColorNight.toColorIntSafe(-1)
            menuAcColorInt = menuAcColor.toColorIntSafe(-1)
            menuAcColorNightInt = menuAcColorNight.toColorIntSafe(-1)
            underlineColorInt = underlineColor.toColorIntSafe(0xFF3E3D3B.toInt())
            underlineColorNightInt = underlineColorNight.toColorIntSafe(0xFFADADAD.toInt())
            initColorInt = true
        }

        private fun ensureAccentColorInts() {
            if (initAccentColorInt) {
                return
            }
            textAccentColorIntEInk = textAccentColorEInk.toColorIntSafe(0xFF000000.toInt())
            textAccentColorIntNight = textAccentColorNight.toColorIntSafe(0xFFFE4D55.toInt())
            textAccentColorInt = textAccentColor.toColorIntSafe(0xFF834E00.toInt())
            initAccentColorInt = true
        }

        fun withCurTextAccentColor(color: Int): Config {
            val hex = "#${color.hexString}"
            return currentModeValue(
                eInk = { copy(textAccentColorEInk = hex) },
                night = { copy(textAccentColorNight = hex) },
                day = { copy(textAccentColor = hex) }
            )
        }

        fun curTextAccentColor(): Int {
            ensureAccentColorInts()
            return currentModeValue(
                eInk = { textAccentColorIntEInk },
                night = { textAccentColorIntNight },
                day = { textAccentColorInt }
            )
        }

        fun withCurShadowColor(color: Int): Config {
            val hex = "#${color.hexString}"
            return nightThemeValue(
                night = { copy(shadowColorN = hex) },
                day = { copy(shadowColor = hex) }
            )
        }

        fun withCurTextColor(color: Int): Config {
            val hex = "#${color.hexString}"
            return currentModeValue(
                eInk = { copy(textColorEInk = hex) },
                night = { copy(textColorNight = hex) },
                day = { copy(textColor = hex) }
            )
        }

        fun curTextColor(): Int {
            ensureColorInts()
            return currentModeValue(
                eInk = { textColorIntEInk },
                night = { textColorIntNight },
                day = { textColorInt }
            )
        }

        fun curTextShadowColor(): Int {
            ensureColorInts()
            return nightThemeValue(
                night = { shadowColorNightInt },
                day = { shadowColorInt }
            )
        }

        fun withCurStatusIconDark(isDark: Boolean): Config = currentModeValue(
            eInk = { copy(darkStatusIconEInk = isDark) },
            night = { copy(darkStatusIconNight = isDark) },
            day = { copy(darkStatusIcon = isDark) }
        )

        fun curStatusIconDark(): Boolean {
            return currentModeValue(
                eInk = { darkStatusIconEInk },
                night = { darkStatusIconNight },
                day = { darkStatusIcon }
            )
        }

        fun withCurPageAnim(@PageAnim.Anim anim: Int): Config = currentModeValue(
            eInk = { copy(pageAnimEInk = anim) },
            night = { copy(pageAnim = anim) },
            day = { copy(pageAnim = anim) }
        )

        fun curPageAnim(): Int {
            return currentModeValue(
                eInk = { pageAnimEInk },
                night = { pageAnim },
                day = { pageAnim }
            )
        }

        // Public getters for mode-specific values (for ReadBookStyleConfig)
        fun getDarkStatusIcon(): Boolean = darkStatusIcon
        fun getDarkStatusIconNight(): Boolean = darkStatusIconNight
        fun getDarkStatusIconEInk(): Boolean = darkStatusIconEInk
        fun getTextColor(): String = textColor
        fun getTextColorNight(): String = textColorNight
        fun getTextColorEInk(): String = textColorEInk
        fun getPageAnim(): Int = pageAnim
        fun getPageAnimEInk(): Int = pageAnimEInk

        fun withCurBg(bgType: Int, bg: String): Config =
            ReadStyleResolver.withCurrentBackground(this, bgType, bg)

        fun curBgStr(): String {
            return ReadStyleResolver.currentBackground(this).value
        }

        fun curMenuBg(): Int {
            ensureColorInts()
            return nightThemeValue(
                night = { menuBgColorNightInt },
                day = { menuBgColorInt }
            )
        }

        fun menuBgColor(isNight: Boolean): Int {
            ensureColorInts()
            return if (isNight) menuBgColorNightInt else menuBgColorInt
        }

        fun withMenuCurBg(bg: Int): Config {
            val hex = "#${bg.hexString}"
            return nightThemeValue(
                night = { copy(menuBgColorNight = hex) },
                day = { copy(menuBgColor = hex) }
            )
        }

        fun curMenuAc(): Int {
            ensureColorInts()
            return nightThemeValue(
                night = { menuAcColorNightInt },
                day = { menuAcColorInt }
            )
        }

        fun menuAccentColor(isNight: Boolean): Int {
            ensureColorInts()
            return if (isNight) menuAcColorNightInt else menuAcColorInt
        }

        fun withMenuCurAc(bg: Int): Config {
            val hex = "#${bg.hexString}"
            return nightThemeValue(
                night = { copy(menuAcColorNight = hex) },
                day = { copy(menuAcColor = hex) }
            )
        }

        fun curUnderlineColor(): Int {
            ensureColorInts()
            return nightThemeValue(
                night = { underlineColorNightInt },
                day = { underlineColorInt }
            )
        }

        fun withCurUnderlineColor(bg: Int): Config {
            val hex = "#${bg.hexString}"
            return nightThemeValue(
                night = { copy(underlineColorNight = hex) },
                day = { copy(underlineColor = hex) }
            )
        }

        fun curBgType(): Int {
            return ReadStyleResolver.currentBackground(this).type
        }

        fun curBgDrawable(width: Int, height: Int): Drawable {
            return ReadStyleResolver.currentBackgroundDrawable(this, width, height)
        }
    }
}
