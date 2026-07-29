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
    val titleBarCompact get() = readSettings.titleBarCompact
    val readMenuFloatingBottomBar get() = readSettings.readMenuFloatingBottomBar
    val readMenuTopBarLiquidGlassButtons get() = readSettings.readMenuTopBarLiquidGlassButtons
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
        if (shareLayout) {
            exportConfig.textFont = shareConfig.textFont
            exportConfig.titleFont = shareConfig.titleFont
            exportConfig.headerFont = shareConfig.headerFont
            exportConfig.footerFont = shareConfig.footerFont
            exportConfig.headerFontSize = shareConfig.headerFontSize
            exportConfig.footerFontSize = shareConfig.footerFontSize
            exportConfig.applyHeaderStyle = shareConfig.applyHeaderStyle
            exportConfig.textBold = shareConfig.textBold
            exportConfig.textSize = shareConfig.textSize
            exportConfig.letterSpacing = shareConfig.letterSpacing
            exportConfig.lineSpacingExtra = shareConfig.lineSpacingExtra
            exportConfig.paragraphSpacing = shareConfig.paragraphSpacing
            exportConfig.titleMode = shareConfig.titleMode
            exportConfig.titleSize = shareConfig.titleSize
            exportConfig.titleTopSpacing = shareConfig.titleTopSpacing
            exportConfig.titleBottomSpacing = shareConfig.titleBottomSpacing
            exportConfig.titleColor = shareConfig.titleColor
            exportConfig.titleColorNight = shareConfig.titleColorNight
            exportConfig.paddingBottom = shareConfig.paddingBottom
            exportConfig.paddingLeft = shareConfig.paddingLeft
            exportConfig.paddingRight = shareConfig.paddingRight
            exportConfig.paddingTop = shareConfig.paddingTop
            exportConfig.headerPaddingBottom = shareConfig.headerPaddingBottom
            exportConfig.headerPaddingLeft = shareConfig.headerPaddingLeft
            exportConfig.headerPaddingRight = shareConfig.headerPaddingRight
            exportConfig.headerPaddingTop = shareConfig.headerPaddingTop
            exportConfig.footerPaddingBottom = shareConfig.footerPaddingBottom
            exportConfig.footerPaddingLeft = shareConfig.footerPaddingLeft
            exportConfig.footerPaddingRight = shareConfig.footerPaddingRight
            exportConfig.footerPaddingTop = shareConfig.footerPaddingTop
            exportConfig.showHeaderLine = shareConfig.showHeaderLine
            exportConfig.showFooterLine = shareConfig.showFooterLine
            exportConfig.tipHeaderLeft = shareConfig.tipHeaderLeft
            exportConfig.tipHeaderMiddle = shareConfig.tipHeaderMiddle
            exportConfig.tipHeaderRight = shareConfig.tipHeaderRight
            exportConfig.tipFooterLeft = shareConfig.tipFooterLeft
            exportConfig.tipFooterMiddle = shareConfig.tipFooterMiddle
            exportConfig.tipFooterRight = shareConfig.tipFooterRight
            exportConfig.tipHeaderColor = shareConfig.tipHeaderColor
            exportConfig.tipHeaderColorNight = shareConfig.tipHeaderColorNight
            exportConfig.tipFooterColor = shareConfig.tipFooterColor
            exportConfig.tipFooterColorNight = shareConfig.tipFooterColorNight
            exportConfig.headerMode = shareConfig.headerMode
            // MD3专有属性
            exportConfig.footerMode = shareConfig.footerMode
            exportConfig.textItalic = shareConfig.textItalic
            exportConfig.textShadow = shareConfig.textShadow
            exportConfig.shadowRadius = shareConfig.shadowRadius
            exportConfig.shadowDx = shareConfig.shadowDx
            exportConfig.shadowDy = shareConfig.shadowDy
            exportConfig.titleBold = shareConfig.titleBold
            exportConfig.titleLineSpacingExtra = shareConfig.titleLineSpacingExtra
            exportConfig.titleLineSpacingSub = shareConfig.titleLineSpacingSub
            exportConfig.titleSegType = shareConfig.titleSegType
            exportConfig.titleSegScaling = shareConfig.titleSegScaling
            exportConfig.titleSegDistance = shareConfig.titleSegDistance
            exportConfig.titleSegFlag = shareConfig.titleSegFlag
            exportConfig.paragraphIndent = shareConfig.paragraphIndent
            exportConfig.underline = shareConfig.underline
            exportConfig.underlineHeight = shareConfig.underlineHeight
            exportConfig.underlinePadding = shareConfig.underlinePadding
            exportConfig.dottedLine = shareConfig.dottedLine
            exportConfig.dottedBase = shareConfig.dottedBase
            exportConfig.dottedRatio = shareConfig.dottedRatio
            exportConfig.bgAlpha = shareConfig.bgAlpha
        }
        return exportConfig
    }

    @Keep
    data class Config(
        var name: String = "",
        var bgStr: String = "#EEEEEE",//白天背景
        var bgStrNight: String = "#000000",//夜间背景
        @Transient
        var menuBgColor: String = "#EEEFE3",
        @Transient
        var menuAcColor: String = "#EEEFE3",
        @Transient
        var menuBgColorNight: String = "#BFCBAD",
        @Transient
        var menuAcColorNight: String = "#586249",
        var bgStrEInk: String = "#FFFFFF",//EInk背景
        var bgAlpha: Int = 100,//背景透明度
        var bgType: Int = 0,//白天背景类型 0:颜色, 1:assets图片, 2其它图片
        var bgTypeNight: Int = 0,//夜间背景类型
        var bgTypeEInk: Int = 0,//EInk背景类型
        private var darkStatusIcon: Boolean = true,//白天是否暗色状态栏
        private var darkStatusIconNight: Boolean = false,//晚上是否暗色状态栏
        private var darkStatusIconEInk: Boolean = true,
        private var textColor: String = "#3E3D3B",//白天文字颜色
        private var textColorNight: String = "#ADADAD",//夜间文字颜色
        private var textColorEInk: String = "#000000",
        private var textAccentColor: String = "#834E00",//白天强调文字颜色
        private var textAccentColorNight: String = "#FE4D55",//夜间强调文字颜色
        private var textAccentColorEInk: String = "#000000",
        private var pageAnim: Int = 0,//翻页动画
        private var pageAnimEInk: Int = 4,
        var textFont: String = "",//字体
        var titleFont: String = "",//标题字体
        var headerFont: String = "",//页眉字体
        var footerFont: String = "",//页脚字体
        var headerFontSize: Int = 12,//页眉字号
        var footerFontSize: Int = 12,//页脚字号
        var applyHeaderStyle: Boolean = true,//页脚是否应用页眉字体样式
        var textBold: Int = 500,//是否粗体字 0:正常, 1:粗体, 2:细体
        var textSize: Int = 20,//文字大小
        var textItalic: Boolean = false,// 是否启用斜体
        var textShadow: Boolean = false,// 是否启用阴影
        var shadowRadius: Float = 16f,// 阴影模糊半径
        var shadowDx: Float = 1f,// 阴影x偏移
        var shadowDy: Float = 1f,// 阴影y偏移
        private var shadowColor: String = "#3E3D3B",
        private var shadowColorN: String = "#3E3D3B",
        var letterSpacing: Float = 0.1f,//字间距
        var lineSpacingExtra: Int = 12,//行间距
        var paragraphSpacing: Int = 2,//段距
        var titleMode: Int = 0,//标题位置 0:居左 1:居中 2:隐藏
        var titleSize: Int = 0,
        var titleTopSpacing: Int = 0,
        var titleBottomSpacing: Int = 0,
        var titleColor: Int = 0,
        var titleColorNight: Int = 0,
        var titleBold: Int = 500,//是否粗体字 0:正常, 1:粗体, 2:细体
        var titleLineSpacingExtra: Int = 12,
        var titleLineSpacingSub: Int = 12,
        var titleSegType: Int = 0,//分段模式
        var titleSegScaling: Float = 1f,//分段缩放，第二段与第一段的字体大小比例
        var titleSegDistance: Int = 4,//分段判断，第几个字符开始分段
        var titleSegFlag: String = "",//分段判断，碰到指定值时分段
        var paragraphIndent: String = "　　",//段落缩进
        var underline: Boolean = false, //下划线
        var underlinePadding: Int = 10,
        var underlineHeight: Int = 1,
        var underlineExtend: Boolean = false, //下划线延伸
        var underlineColor: String = "#3E3D3B",
        var underlineColorNight: String = "#ADADAD",
        var dottedLine: Boolean = false, //虚线
        var dottedBase: Float = 6f, //长度
        var dottedRatio: Float = 6f,
        var paddingBottom: Int = 6,
        var paddingLeft: Int = 16,
        var paddingRight: Int = 16,
        var paddingTop: Int = 6,
        var headerPaddingBottom: Int = 0,
        var headerPaddingLeft: Int = 16,
        var headerPaddingRight: Int = 16,
        var headerPaddingTop: Int = 0,
        var footerPaddingBottom: Int = 6,
        var footerPaddingLeft: Int = 16,
        var footerPaddingRight: Int = 16,
        var footerPaddingTop: Int = 6,
        var showHeaderLine: Boolean = false,
        var showFooterLine: Boolean = true,
        var tipHeaderLeft: Int = ReadTipType.tipTime,
        var tipHeaderMiddle: Int = ReadTipType.tipNone,
        var tipHeaderRight: Int = ReadTipType.tipBattery,
        var tipFooterLeft: Int = ReadTipType.tipChapterTitle,
        var tipFooterMiddle: Int = ReadTipType.tipNone,
        var tipFooterRight: Int = ReadTipType.tipPageAndTotal,
        var customTipHeaderLeft: String = "",
        var customTipHeaderMiddle: String = "",
        var customTipHeaderRight: String = "",
        var customTipFooterLeft: String = "",
        var customTipFooterMiddle: String = "",
        var customTipFooterRight: String = "",
        var tipHeaderColor: Int = 0,
        var tipHeaderColorNight: Int = 0,
        var tipFooterColor: Int = 0,
        var tipFooterColorNight: Int = 0,
        var tipDividerColor: Int = -1,
        var headerMode: Int = 0,
        var footerMode: Int = 0,
        @Transient
        var menuIconShowText: Boolean = true,
        @Transient
        var menuIconStyle: Int = 0,
        @Transient
        var menuIconItemsPerRow: Int = 5,
        @Transient
        var menuIconRowCount: Int = 1,
        @Transient
        var menuBottomCornerRadius: Int = 0,
        @Transient
        var menuBottomHorizontalMargin: Int = 0,
        @Transient
        var menuBottomBottomMargin: Int = 0,
        var highlightRules: ArrayList<HighlightRule> = arrayListOf()
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
            "highlightRules" to highlightRules.map { mapOf("id" to it.id, "name" to it.name, "pattern" to it.pattern, "sampleText" to it.sampleText, "targetScope" to it.targetScope, "enabled" to it.enabled, "position" to it.position, "textColor" to it.textColor, "bgColor" to it.bgColor, "underlineMode" to it.underlineMode, "underlineColor" to it.underlineColor, "underlineWidth" to it.underlineWidth, "underlineOffset" to it.underlineOffset, "underlineSvgPath" to it.underlineSvgPath, "bgImage" to it.bgImage, "bgImageFit" to it.bgImageFit, "bgImageScale" to it.bgImageScale, "configName" to it.configName, "fontPath" to it.fontPath) }
        )

        fun getBgPath(bgIndex: Int): String? {
            return ReadStyleResolver.backgroundPath(this, bgIndex)
        }

        private inline fun updateCurrentMode(
            eInk: () -> Unit,
            night: () -> Unit,
            day: () -> Unit
        ) {
            when (ReadStyleResolver.currentMode()) {
                ReadStyleResolver.ReadStyleMode.EInk -> eInk()
                ReadStyleResolver.ReadStyleMode.Night -> night()
                ReadStyleResolver.ReadStyleMode.Day -> day()
            }
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

        private inline fun updateNightTheme(
            night: () -> Unit,
            day: () -> Unit
        ) {
            if (ReadStyleResolver.isNightTheme()) {
                night()
            } else {
                day()
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

        fun setCurTextAccentColor(color: Int) {
            updateCurrentMode(
                eInk = {
                    textAccentColorEInk = "#${color.hexString}"
                    textAccentColorIntEInk = color
                },
                night = {
                    textAccentColorNight = "#${color.hexString}"
                    textAccentColorIntNight = color
                },
                day = {
                    textAccentColor = "#${color.hexString}"
                    textAccentColorInt = color
                }
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

        fun setCurShadColor(color: Int){
            updateNightTheme(
                night = {
                    shadowColorN = "#${color.hexString}"
                    shadowColorNightInt = color
                },
                day = {
                    shadowColor = "#${color.hexString}"
                    shadowColorInt = color
                }
            )
        }

        fun setCurTextColor(color: Int) {
            updateCurrentMode(
                eInk = {
                    textColorEInk = "#${color.hexString}"
                    textColorIntEInk = color
                },
                night = {
                    textColorNight = "#${color.hexString}"
                    textColorIntNight = color
                },
                day = {
                    textColor = "#${color.hexString}"
                    textColorInt = color
                }
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

        fun setCurStatusIconDark(isDark: Boolean) {
            updateCurrentMode(
                eInk = { darkStatusIconEInk = isDark },
                night = { darkStatusIconNight = isDark },
                day = { darkStatusIcon = isDark }
            )
        }

        fun curStatusIconDark(): Boolean {
            return currentModeValue(
                eInk = { darkStatusIconEInk },
                night = { darkStatusIconNight },
                day = { darkStatusIcon }
            )
        }

        fun setCurPageAnim(@PageAnim.Anim anim: Int) {
            updateCurrentMode(
                eInk = { pageAnimEInk = anim },
                night = { pageAnim = anim },
                day = { pageAnim = anim }
            )
        }

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

        fun setCurBg(bgType: Int, bg: String) {
            ReadStyleResolver.setCurrentBackground(this, bgType, bg)
        }

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

        fun setMenuCurBg(bg: Int) {
            updateNightTheme(
                night = {
                    menuBgColorNight = "#${bg.hexString}"
                    menuBgColorNightInt = bg
                },
                day = {
                    menuBgColor = "#${bg.hexString}"
                    menuBgColorInt = bg
                }
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

        fun setMenuCurAc(bg: Int) {
            updateNightTheme(
                night = {
                    menuAcColorNight = "#${bg.hexString}"
                    menuAcColorNightInt = bg
                },
                day = {
                    menuAcColor = "#${bg.hexString}"
                    menuAcColorInt = bg
                }
            )
        }

        fun curUnderlineColor(): Int {
            ensureColorInts()
            return nightThemeValue(
                night = { underlineColorNightInt },
                day = { underlineColorInt }
            )
        }

        fun setUnderlineColor(bg: Int) {
            updateNightTheme(
                night = {
                    underlineColorNight = "#${bg.hexString}"
                    underlineColorNightInt = bg
                },
                day = {
                    underlineColor = "#${bg.hexString}"
                    underlineColorInt = bg
                }
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
