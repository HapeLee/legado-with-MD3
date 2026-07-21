package io.legado.app.help.config

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.annotation.Keep
import androidx.core.graphics.toColorInt
import io.legado.app.R
import io.legado.app.constant.PageAnim
import io.legado.app.data.entities.HighlightRule
import io.legado.app.data.repository.ReadStyleRepository
import io.legado.app.domain.gateway.ReadSettingsGateway
import io.legado.app.domain.gateway.ReadStyleGateway
import io.legado.app.domain.gateway.ReadStyleMutation
import io.legado.app.domain.gateway.ReadStyleStringKey
import io.legado.app.help.DefaultData
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.model.ReadSessionState
import io.legado.app.ui.config.readConfig.ReadConfig
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.hexString
import splitties.init.appCtx
import java.io.InputStream

/**
 * 阅读界面配置
 */
@Suppress("ConstPropertyName")
@Keep
object ReadBookConfig {
    private lateinit var readStyleRepository: ReadStyleRepository
    private lateinit var readStyleGateway: ReadStyleGateway
    private lateinit var readSettingsGateway: ReadSettingsGateway
    private val readSettings get() = readSettingsGateway.currentSettings

    internal fun initialize(
        readStyleRepository: ReadStyleRepository,
        readSettingsGateway: ReadSettingsGateway,
    ) {
        this.readStyleRepository = readStyleRepository
        this.readSettingsGateway = readSettingsGateway
        initConfigs()
        initShareConfig()
    }

    internal fun attachGateway(readStyleGateway: ReadStyleGateway) {
        this.readStyleGateway = readStyleGateway
    }

    // region Tip position constants
    const val tipNone = 0
    const val tipChapterTitle = 1
    const val tipTime = 2
    const val tipBattery = 3
    const val tipBatteryPercentage = 10
    const val tipPage = 4
    const val tipTotalProgress = 5
    const val tipPageAndTotal = 6
    const val tipBookName = 7
    const val tipTimeBattery = 8
    const val tipTimeBatteryPercentage = 9
    const val tipTotalProgress1 = 11
    const val tipChapterTitleArrow = 12
    const val tipBatteryInside = 13
    const val tipBatteryIcon = 14
    const val tipBatteryClassic = 15
    const val tipTimeBatteryClassic = 16
    const val tipChapterTitleArrowClassic = 17
    const val tipCustom = 18
    // endregion

    const val configFileName = "readConfig.json"
    const val shareConfigFileName = "shareReadConfig.json"
    val configFilePath: String get() = readStyleRepository.configFilePath
    val shareConfigFilePath: String get() = readStyleRepository.shareConfigFilePath
    private val configList: ArrayList<Config> = arrayListOf()
    private lateinit var shareConfig: Config

    internal fun configsSnapshot(): List<Config> = synchronized(this) {
        configList.map { it.copy() }
    }

    internal fun shareConfigSnapshot(): Config = synchronized(this) { shareConfig.copy() }

    internal fun addConfig(config: Config): Int = synchronized(this) {
        configList.add(config)
        configList.lastIndex
    }

    internal fun importOrReplaceConfig(config: Config): String = synchronized(this) {
        val index = configList.indexOfFirst { it.name == config.name }
        if (index >= 0) {
            configList[index] = config
        } else {
            configList.add(config)
        }
        config.name
    }

    internal val configCount: Int get() = synchronized(this) { configList.size }
    internal fun configNames(): List<String> = synchronized(this) { configList.map { it.name } }
    var durConfig
        get() = getConfig(styleSelect)
        set(value) {
            configList[styleSelect] = value
            if (shareLayout) {
                shareConfig = value
            }
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
    @Synchronized
    fun getConfig(index: Int): Config {
        if (configList.size < 5) {
            resetAll()
        }
        return configList.getOrNull(index) ?: configList[0]
    }

    fun initConfigs() {
        readStyleRepository.readConfigs().let {
            configList.clear()
            configList.addAll(it)
        }
    }

    fun initShareConfig() {
        shareConfig = readStyleRepository.readShareConfig(configList.getOrNull(5) ?: Config())
    }

    fun save() {
        Coroutine.async {
            synchronized(this) {
                readStyleRepository.save(configList, shareConfig)
            }
        }
    }

    fun clearMissingTextFont() {
        readStyleGateway.updateCurrentStyle(
            ReadStyleMutation.StringValue(ReadStyleStringKey.TextFont, "")
        )
        readStyleGateway.save()
    }

    fun getAllPicBgStr(): ArrayList<String> {
        return readStyleRepository.getAllPicBgStr(configList)
    }

    internal fun deleteDur(): Int? {
        if (configList.size > 5) {
            val removeIndex = styleSelect
            configList.removeAt(removeIndex)
            return removeIndex
        }
        return null
    }

    fun clearBgAndCache() {
        readStyleRepository.clearBgAndCache(configList)
    }

    private fun resetAll() {
        DefaultData.readConfigs.let {
            configList.clear()
            configList.addAll(it)
            save()
        }
    }

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

    var bgAlpha: Int
        get() = config.bgAlpha
        set(value) {
            config.bgAlpha = value
        }

    var pageAnim: Int
        get() = config.curPageAnim()
        set(@PageAnim.Anim value) {
            config.setCurPageAnim(value)
        }

    var textFont: String
        get() = config.textFont
        set(value) {
            config.textFont = value
        }

    var titleFont: String
        get() = config.titleFont
        set(value) {
            config.titleFont = value
        }

    var headerFont: String
        get() = config.headerFont
        set(value) {
            config.headerFont = value
        }

    var footerFont: String
        get() = config.footerFont
        set(value) {
            config.footerFont = value
        }

    var headerFontSize: Int
        get() = config.headerFontSize.takeIf { it > 0 } ?: 12
        set(value) {
            config.headerFontSize = value
        }

    var footerFontSize: Int
        get() = config.footerFontSize.takeIf { it > 0 } ?: 12
        set(value) {
            config.footerFontSize = value
        }

    var textBold: Int
        get() = config.textBold
        set(value) {
            config.textBold = value
        }

    var titleBold: Int
        get() = config.titleBold
        set(value) {
            config.titleBold = value
        }

    var textItalic: Boolean
        get() = config.textItalic
        set(value) {
            config.textItalic = value
        }

    var textShadow: Boolean
        get() = config.textShadow
        set(value) {
            config.textShadow = value
        }

    var shadowRadius: Float
        get() = config.shadowRadius
        set(value) {
            config.shadowRadius = value
        }

    var shadowDx: Float
        get() = config.shadowDx
        set(value) {
            config.shadowDx = value
        }

    var shadowDy: Float
        get() = config.shadowDy
        set(value) {
            config.shadowDy = value
        }

    var textSize: Int
        get() = config.textSize
        set(value) {
            config.textSize = value
        }

    var letterSpacing: Float
        get() = config.letterSpacing
        set(value) {
            config.letterSpacing = value
        }

    var lineSpacingExtra: Int
        get() = config.lineSpacingExtra
        set(value) {
            config.lineSpacingExtra = value
        }

    var titleLineSpacingExtra: Int
        get() = config.titleLineSpacingExtra
        set(value) {
            config.titleLineSpacingExtra = value
        }

    var titleLineSpacingSub: Int
        get() = config.titleLineSpacingSub
        set(value) {
            config.titleLineSpacingSub = value
        }

    var paragraphSpacing: Int
        get() = config.paragraphSpacing
        set(value) {
            config.paragraphSpacing = value
        }

    /**
     * 标题位置 0:居左 1:居中 2:隐藏
     */
    var titleMode: Int
        get() = config.titleMode
        set(value) {
            config.titleMode = value
        }
    var titleSize: Int
        get() = config.titleSize
        set(value) {
            config.titleSize = value
        }

    var titleSegType: Int
        get() = config.titleSegType
        set(value) {
            config.titleSegType = value
        }

    var titleSegScaling: Float
        get() = config.titleSegScaling
        set(value) {
            config.titleSegScaling = value
        }

    var titleSegDistance: Int
        get() = config.titleSegDistance
        set(value) {
            config.titleSegDistance = value
        }

    var titleSegFlag: String
        get() = config.titleSegFlag
        set(value) {
            config.titleSegFlag = value
        }

    /**
     * 是否标题居中
     */
    val isMiddleTitle get() = titleMode == 1

    var titleTopSpacing: Int
        get() = config.titleTopSpacing
        set(value) {
            config.titleTopSpacing = value
        }

    var titleBottomSpacing: Int
        get() = config.titleBottomSpacing
        set(value) {
            config.titleBottomSpacing = value
        }

    var titleColor: Int
        get() = config.titleColor
        set(value) {
            config.titleColor = value
        }

    var titleColorNight: Int
        get() = config.titleColorNight
        set(value) {
            config.titleColorNight = value
        }

    val resolvedTitleColor: Int
        get() = if (ReadStyleResolver.isNightTheme()) titleColorNight else titleColor

    var paragraphIndent: String
        get() = config.paragraphIndent
        set(value) {
            config.paragraphIndent = value
        }

    var underline: Boolean
        get() = config.underline
        set(value) {
            config.underline = value
        }

    var underlineHeight: Int
        get() = config.underlineHeight
        set(value) {
            config.underlineHeight = value
        }

    var underlinePadding: Int
        get() = config.underlinePadding
        set(value) {
            config.underlinePadding = value
        }

    var underlineExtend: Boolean
        get() = config.underlineExtend
        set(value) {
            config.underlineExtend = value
        }

    var dottedLine: Boolean
        get() = config.dottedLine
        set(value) {
            config.dottedLine = value
        }

    var dottedBase: Float
        get() = config.dottedBase
        set(value) {
            config.dottedBase = value
        }

    var dottedRatio: Float
        get() = config.dottedRatio
        set(value) {
            config.dottedRatio = value
        }

    var paddingBottom: Int
        get() = config.paddingBottom
        set(value) {
            config.paddingBottom = value
        }

    var paddingLeft: Int
        get() = config.paddingLeft
        set(value) {
            config.paddingLeft = value
        }

    var paddingRight: Int
        get() = config.paddingRight
        set(value) {
            config.paddingRight = value
        }

    var paddingTop: Int
        get() = config.paddingTop
        set(value) {
            config.paddingTop = value
        }

    var headerPaddingBottom: Int
        get() = config.headerPaddingBottom
        set(value) {
            config.headerPaddingBottom = value
        }

    var headerPaddingLeft: Int
        get() = config.headerPaddingLeft
        set(value) {
            config.headerPaddingLeft = value
        }

    var headerPaddingRight: Int
        get() = config.headerPaddingRight
        set(value) {
            config.headerPaddingRight = value
        }

    var headerPaddingTop: Int
        get() = config.headerPaddingTop
        set(value) {
            config.headerPaddingTop = value
        }

    var footerPaddingBottom: Int
        get() = config.footerPaddingBottom
        set(value) {
            config.footerPaddingBottom = value
        }

    var footerPaddingLeft: Int
        get() = config.footerPaddingLeft
        set(value) {
            config.footerPaddingLeft = value
        }

    var footerPaddingRight: Int
        get() = config.footerPaddingRight
        set(value) {
            config.footerPaddingRight = value
        }

    var footerPaddingTop: Int
        get() = config.footerPaddingTop
        set(value) {
            config.footerPaddingTop = value
        }

    var showHeaderLine: Boolean
        get() = config.showHeaderLine
        set(value) {
            config.showHeaderLine = value
        }

    var showFooterLine: Boolean
        get() = config.showFooterLine
        set(value) {
            config.showFooterLine = value
        }

    var underlineColor: Int
        get() = config.curUnderlineColor()
        set(value) {
            config.setUnderlineColor(value)
        }

    val menuBgColor: Int
        get() = readMenuBgColor

    val menuAcColor: Int
        get() = readMenuAccentColor

    var shadowColor: Int
        get() = config.curTextShadowColor()
        set(value) {
            config.setCurShadColor(value)
        }

    // region Tip / Header / Footer

    var tipHeaderLeft: Int
        get() = config.tipHeaderLeft
        set(value) {
            config.tipHeaderLeft = value
        }

    var tipHeaderMiddle: Int
        get() = config.tipHeaderMiddle
        set(value) {
            config.tipHeaderMiddle = value
        }

    var tipHeaderRight: Int
        get() = config.tipHeaderRight
        set(value) {
            config.tipHeaderRight = value
        }

    var tipFooterLeft: Int
        get() = config.tipFooterLeft
        set(value) {
            config.tipFooterLeft = value
        }

    var tipFooterMiddle: Int
        get() = config.tipFooterMiddle
        set(value) {
            config.tipFooterMiddle = value
        }

    var tipFooterRight: Int
        get() = config.tipFooterRight
        set(value) {
            config.tipFooterRight = value
        }

    var customTipHeaderLeft: String
        get() = config.customTipHeaderLeft
        set(value) {
            config.customTipHeaderLeft = value
        }

    var customTipHeaderMiddle: String
        get() = config.customTipHeaderMiddle
        set(value) {
            config.customTipHeaderMiddle = value
        }

    var customTipHeaderRight: String
        get() = config.customTipHeaderRight
        set(value) {
            config.customTipHeaderRight = value
        }

    var customTipFooterLeft: String
        get() = config.customTipFooterLeft
        set(value) {
            config.customTipFooterLeft = value
        }

    var customTipFooterMiddle: String
        get() = config.customTipFooterMiddle
        set(value) {
            config.customTipFooterMiddle = value
        }

    var customTipFooterRight: String
        get() = config.customTipFooterRight
        set(value) {
            config.customTipFooterRight = value
        }

    var headerMode: Int
        get() = config.headerMode
        set(value) {
            config.headerMode = value
        }

    var footerMode: Int
        get() = config.footerMode
        set(value) {
            config.footerMode = value
        }

    var tipHeaderColor: Int
        get() = config.tipHeaderColor
        set(value) {
            config.tipHeaderColor = value
        }

    var tipHeaderColorNight: Int
        get() = config.tipHeaderColorNight
        set(value) {
            config.tipHeaderColorNight = value
        }

    val resolvedTipHeaderColor: Int
        get() = if (ReadStyleResolver.isNightTheme()) tipHeaderColorNight else tipHeaderColor

    var tipFooterColor: Int
        get() = config.tipFooterColor
        set(value) {
            config.tipFooterColor = value
        }

    var tipFooterColorNight: Int
        get() = config.tipFooterColorNight
        set(value) {
            config.tipFooterColorNight = value
        }

    val resolvedTipFooterColor: Int
        get() = if (ReadStyleResolver.isNightTheme()) tipFooterColorNight else tipFooterColor

    var tipDividerColor: Int
        get() = config.tipDividerColor
        set(value) {
            config.tipDividerColor = value
        }

    val tipValues = arrayOf(
        tipNone, tipBookName, tipChapterTitle, tipChapterTitleArrow, tipChapterTitleArrowClassic,
        tipTime, tipBattery, tipBatteryClassic, tipBatteryInside, tipBatteryIcon, tipBatteryPercentage,
        tipPage, tipTotalProgress, tipTotalProgress1, tipPageAndTotal, tipTimeBattery,
        tipTimeBatteryClassic, tipTimeBatteryPercentage, tipCustom
    )
    val tipNames get() = appCtx.resources.getStringArray(R.array.read_tip).toList()
    val tipColorNames get() = appCtx.resources.getStringArray(R.array.tip_color).toList()
    val tipDividerColorNames get() = appCtx.resources.getStringArray(R.array.tip_divider_color).toList()

    fun getHeaderModes(context: Context): LinkedHashMap<Int, String> {
        return linkedMapOf(
            Pair(0, context.getString(R.string.hide_when_status_bar_show)),
            Pair(1, context.getString(R.string.show)),
            Pair(2, context.getString(R.string.hide))
        )
    }

    fun getFooterModes(context: Context): LinkedHashMap<Int, String> {
        return linkedMapOf(
            Pair(0, context.getString(R.string.show)),
            Pair(1, context.getString(R.string.hide))
        )
    }

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

    fun export(): ByteArray {
        return readStyleRepository.export(getExportConfig())
    }

    fun import(byteArray: ByteArray): Config {
        return readStyleRepository.import(byteArray)
    }

    fun saveBackgroundImage(inputStream: InputStream, displayName: String?): String {
        return readStyleRepository.saveBackgroundImage(inputStream, displayName)
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
        var tipHeaderLeft: Int = tipTime,
        var tipHeaderMiddle: Int = tipNone,
        var tipHeaderRight: Int = tipBattery,
        var tipFooterLeft: Int = tipChapterTitle,
        var tipFooterMiddle: Int = tipNone,
        var tipFooterRight: Int = tipPageAndTotal,
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
