package io.legado.app.help.config

import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.annotation.Keep
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt
import com.google.android.material.color.MaterialColors
import io.legado.app.constant.AppLog
import io.legado.app.constant.PageAnim
import io.legado.app.constant.PreferKey
import io.legado.app.help.DefaultData
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.utils.BitmapUtils
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.compress.ZipUtils
import io.legado.app.utils.createFolderReplace
import io.legado.app.utils.externalCache
import io.legado.app.utils.externalFiles
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getFile
import io.legado.app.utils.getMeanColor
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.hexString
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.resizeAndRecycle
import splitties.init.appCtx
import java.io.File

/**
 * 阅读界面配置
 */
@Suppress("ConstPropertyName")
@Keep
object ReadBookConfig {
    const val configFileName = "readConfig.json"
    const val shareConfigFileName = "shareReadConfig.json"
    val configFilePath = FileUtils.getPath(appCtx.filesDir, configFileName)
    val shareConfigFilePath = FileUtils.getPath(appCtx.filesDir, shareConfigFileName)
    val configList: ArrayList<Config> = arrayListOf()
    lateinit var shareConfig: Config
    var durConfig
        get() = getConfig(styleSelect)
        set(value) {
            configList[styleSelect] = value
            if (shareLayout) {
                shareConfig = value
            }
        }

    var isComic: Boolean = false
    var bg: Drawable? = null
    var bgMeanColor: Int = 0
    val textColor: Int get() = durConfig.curTextColor()
    val textAccentColor: Int get() = durConfig.curTextAccentColor()
    val textShadowColor: Int get() = durConfig.curTextShadowColor()
    val menuColor: Int get() = durConfig.curMenuAc()
    init {
        initConfigs()
        initShareConfig()
    }

    @Synchronized
    fun getConfig(index: Int): Config {
        if (configList.size < 5) {
            resetAll()
        }
        return configList.getOrNull(index) ?: configList[0]
    }

    fun initConfigs() {
        val configFile = File(configFilePath)
        var configs: List<Config>? = null
        if (configFile.exists()) {
            try {
                val json = configFile.readText()
                configs = GSON.fromJsonArray<Config>(json).getOrThrow()
            } catch (e: Exception) {
                AppLog.put("读取排版配置文件出错", e)
            }
        }
        (configs ?: DefaultData.readConfigs).let {
            configList.clear()
            configList.addAll(it)
        }
    }

    fun initShareConfig() {
        val configFile = File(shareConfigFilePath)
        var c: Config? = null
        if (configFile.exists()) {
            try {
                val json = configFile.readText()
                c = GSON.fromJsonObject<Config>(json).getOrThrow()
            } catch (e: Exception) {
                e.printOnDebug()
            }
        }
        shareConfig = c ?: configList.getOrNull(5) ?: Config()
    }

    fun upBg(width: Int, height: Int) {
        val drawable = durConfig.curBgDrawable(width, height)
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            bgMeanColor = drawable.bitmap.getMeanColor()
        } else if (drawable is ColorDrawable) {
            bgMeanColor = drawable.color
        }
        val tmp = bg
        bg = drawable
        (tmp as? BitmapDrawable)?.bitmap?.recycle()
    }

    fun save() {
        Coroutine.async {
            synchronized(this) {
                GSON.toJson(configList).let {
                    FileUtils.delete(configFilePath)
                    FileUtils.createFileIfNotExist(configFilePath).writeText(it)
                }
                GSON.toJson(shareConfig).let {
                    FileUtils.delete(shareConfigFilePath)
                    FileUtils.createFileIfNotExist(shareConfigFilePath).writeText(it)
                }
            }
        }
    }

    fun getAllPicBgStr(): ArrayList<String> {
        val list = arrayListOf<String>()
        configList.forEach {
            if (it.bgType == 2) {
                list.add(it.bgStr)
            }
            if (it.bgTypeNight == 2) {
                list.add(it.bgStrNight)
            }
            if (it.bgTypeEInk == 2) {
                list.add(it.bgStrEInk)
            }
        }
        return list
    }

    fun deleteDur(): Boolean {
        if (configList.size > 5) {
            val removeIndex = styleSelect
            configList.removeAt(removeIndex)
            if (removeIndex <= readStyleSelect) {
                readStyleSelect -= 1
            }
            if (removeIndex <= comicStyleSelect) {
                comicStyleSelect -= 1
            }
            return true
        }
        return false
    }

    fun clearBgAndCache() {
        val bgs = hashSetOf<String>()
        configList.forEach { config ->
            repeat(3) {
                config.getBgPath(it)?.let { path ->
                    bgs.add(path)
                }
            }
        }
        appCtx.externalFiles.getFile("bg").listFiles()?.forEach {
            if (!bgs.contains(it.absolutePath)) {
                it.delete()
            }
        }
        FileUtils.delete(appCtx.externalCache.getFile("readConfig"))
        val configZipPath = FileUtils.getPath(appCtx.externalCache, "readConfig.zip")
        FileUtils.delete(configZipPath)
    }

    private fun resetAll() {
        DefaultData.readConfigs.let {
            configList.clear()
            configList.addAll(it)
            save()
        }
    }

    //配置写入读取
    var readBodyToLh = appCtx.getPrefBoolean(PreferKey.readBodyToLh, true)
    var autoReadSpeed = appCtx.getPrefInt(PreferKey.autoReadSpeed, 10)
        set(value) {
            field = value
            appCtx.putPrefInt(PreferKey.autoReadSpeed, value)
        }
    var styleSelect: Int
        get() = if (isComic) comicStyleSelect else readStyleSelect
        set(value) {
            if (isComic) {
                comicStyleSelect = value
            } else {
                readStyleSelect = value
            }
        }
    var readStyleSelect = appCtx.getPrefInt(PreferKey.readStyleSelect)
        set(value) {
            field = value
            if (appCtx.getPrefInt(PreferKey.readStyleSelect) != value) {
                appCtx.putPrefInt(PreferKey.readStyleSelect, value)
            }
        }
    var comicStyleSelect = appCtx.getPrefInt(PreferKey.comicStyleSelect, readStyleSelect)
        set(value) {
            field = value
            if (appCtx.getPrefInt(PreferKey.comicStyleSelect) != value) {
                appCtx.putPrefInt(PreferKey.comicStyleSelect, value)
            }
        }
    var shareLayout = appCtx.getPrefBoolean(PreferKey.shareLayout)
        set(value) {
            field = value
            if (appCtx.getPrefBoolean(PreferKey.shareLayout) != value) {
                appCtx.putPrefBoolean(PreferKey.shareLayout, value)
            }
        }

    /**
     * 两端对齐
     */
    val textFullJustify get() = appCtx.getPrefBoolean(PreferKey.textFullJustify, true)

    /**
     * 底部对齐
     */
    val textBottomJustify get() = appCtx.getPrefBoolean(PreferKey.textBottomJustify, true)
    var hideStatusBar = appCtx.getPrefBoolean(PreferKey.hideStatusBar)
    var hideNavigationBar = appCtx.getPrefBoolean(PreferKey.hideNavigationBar)
    var useZhLayout = appCtx.getPrefBoolean(PreferKey.useZhLayout)

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

    var menuBgColor: Int
        get() = config.curMenuBg()
        set(value) {
            config.setMenuCurBg(value)
        }

    var menuAcColor: Int
        get() = config.curMenuAc()
        set(value) {
            config.setMenuCurAc(value)
        }

    var shadowColor: Int
        get() = config.curTextShadowColor()
        set(value) {
            config.setCurShadColor(value)
        }

    fun getExportConfig(): Config {
        val exportConfig = durConfig.copy()
        if (shareLayout) {
            exportConfig.textFont = shareConfig.textFont
            exportConfig.textBold = shareConfig.textBold
            exportConfig.textSize = shareConfig.textSize
            exportConfig.letterSpacing = shareConfig.letterSpacing
            exportConfig.lineSpacingExtra = shareConfig.lineSpacingExtra
            exportConfig.paragraphSpacing = shareConfig.paragraphSpacing
            exportConfig.titleMode = shareConfig.titleMode
            exportConfig.titleSize = shareConfig.titleSize
            exportConfig.titleTopSpacing = shareConfig.titleTopSpacing
            exportConfig.titleBottomSpacing = shareConfig.titleBottomSpacing
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
            exportConfig.tipColor = shareConfig.tipColor
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
            exportConfig.titleSegType = shareConfig.titleSegType
            exportConfig.titleSegScaling = shareConfig.titleSegScaling
            exportConfig.titleSegDistance = shareConfig.titleSegDistance
            exportConfig.titleSegFlag = shareConfig.titleSegFlag
            exportConfig.paragraphIndent = shareConfig.paragraphIndent
            exportConfig.underline = shareConfig.underline
            exportConfig.underlineHeight = shareConfig.underlineHeight
            exportConfig.dottedLine = shareConfig.dottedLine
            exportConfig.dottedBase = shareConfig.dottedBase
            exportConfig.dottedRatio = shareConfig.dottedRatio
            exportConfig.bgAlpha = shareConfig.bgAlpha
        }
        return exportConfig
    }

    fun import(byteArray: ByteArray): Config {
        val configZipPath = FileUtils.getPath(appCtx.externalCache, "readConfig.zip")
        FileUtils.delete(configZipPath)
        val zipFile = FileUtils.createFileIfNotExist(configZipPath)
        zipFile.writeBytes(byteArray)
        val configDir = appCtx.externalCache.getFile("readConfig")
        configDir.createFolderReplace()
        ZipUtils.unZipToPath(zipFile, configDir)
        val configFile = configDir.getFile(configFileName)
        val config: Config = GSON.fromJsonObject<Config>(configFile.readText()).getOrThrow()
        if (config.textFont.isNotEmpty()) {
            val fontName = config.textFont
            val fontPath =
                FileUtils.getPath(appCtx.externalFiles, "font", fontName)
            val fontFile = configDir.getFile(fontName)
            if (fontFile.exists()) {
                if (!FileUtils.exist(fontPath)) {
                    fontFile.copyTo(File(fontPath))
                }
                config.textFont = fontPath
            } else {
                config.textFont = ""
            }
        }
        if (config.bgType == 2) {
            val bgName = FileUtils.getName(config.bgStr)
            config.bgStr = bgName
            val bgPath = FileUtils.getPath(appCtx.externalFiles, "bg", bgName)
            if (!FileUtils.exist(bgPath)) {
                val bgFile = configDir.getFile(bgName)
                if (bgFile.exists()) {
                    bgFile.copyTo(File(bgPath))
                }
            }
            config.bgStrNight = bgPath
        } else if (config.bgTypeNight == 0) {
            config.bgStrNight.toColorInt()
        }
        if (config.bgTypeEInk == 2) {
            val bgName = FileUtils.getName(config.bgStrEInk)
            config.bgStrEInk = bgName
            val bgPath = FileUtils.getPath(appCtx.externalFiles, "bg", bgName)
            if (!FileUtils.exist(bgPath)) {
                val bgFile = configDir.getFile(bgName)
                if (bgFile.exists()) {
                    bgFile.copyTo(File(bgPath))
                }
            }
            config.bgStrEInk = bgPath
        } else if (config.bgTypeEInk == 0) {
            config.bgStrEInk.toColorInt()
        }
        config.curTextColor()
        config.curTextAccentColor()
        config.curTextShadowColor()
        return config
    }

    @Keep
    data class Config(
        var name: String = "",
        var bgStr: String = "#EEEEEE",//白天背景
        var bgStrNight: String = "#000000",//夜间背景
        var menuBgColor: String = "#EEEFE3",
        var menuAcColor: String = "#EEEFE3",
        var menuBgColorNight: String = "#BFCBAD",
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
        var titleBold: Int = 500,//是否粗体字 0:正常, 1:粗体, 2:细体
        var titleLineSpacingExtra: Int = 12,
        var titleSegType: Int = 0,//分段模式
        var titleSegScaling: Float = 1f,//分段缩放，第二段与第一段的字体大小比例
        var titleSegDistance: Int = 4,//分段判断，第几个字符开始分段
        var titleSegFlag: String = "",//分段判断，碰到指定值时分段
        var paragraphIndent: String = "　　",//段落缩进
        var underline: Boolean = false, //下划线
        var underlineHeight: Int = 1,
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
        var tipHeaderLeft: Int = ReadTipConfig.time,
        var tipHeaderMiddle: Int = ReadTipConfig.none,
        var tipHeaderRight: Int = ReadTipConfig.battery,
        var tipFooterLeft: Int = ReadTipConfig.chapterTitle,
        var tipFooterMiddle: Int = ReadTipConfig.none,
        var tipFooterRight: Int = ReadTipConfig.pageAndTotal,
        var tipColor: Int = 0,
        var tipDividerColor: Int = -1,
        var headerMode: Int = 0,
        var footerMode: Int = 0
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

        fun getBgPath(bgIndex: Int): String? {
            val bgType = when (bgIndex) {
                0 -> bgType
                1 -> bgTypeNight
                2 -> bgTypeEInk
                else -> error("unknown bgIndex: $bgIndex")
            }
            if (bgType != 2) {
                return null
            }
            val bgStr = when (bgIndex) {
                0 -> bgStr
                1 -> bgStrNight
                2 -> bgStrEInk
                else -> error("unknown bgIndex: $bgIndex")
            }
            val path = if (bgStr.contains(File.separator)) {
                bgStr
            } else {
                FileUtils.getPath(appCtx.externalFiles, "bg", bgStr)
            }
            return path
        }

        private fun initColorInt() {
            textColorIntEInk = textColorEInk.toColorInt()
            textColorIntNight = textColorNight.toColorInt()
            textColorInt = textColor.toColorInt()
            shadowColorNightInt = shadowColorN.toColorInt()
            shadowColorInt = shadowColor.toColorInt()
            menuBgColorInt = menuBgColor.toColorInt()
            menuBgColorNightInt = menuBgColorNight.toColorInt()
            menuAcColorInt = menuAcColor.toColorInt()
            menuAcColorNightInt = menuAcColorNight.toColorInt()
            underlineColorInt = underlineColor.toColorInt()
            underlineColorNightInt = underlineColorNight.toColorInt()
            initColorInt = true
        }

        private fun initAccentColorInt() {
            textAccentColorIntEInk = textAccentColorEInk.toColorInt()
            textAccentColorIntNight = textAccentColorNight.toColorInt()
            textAccentColorInt = textAccentColor.toColorInt()
            initAccentColorInt = true
        }

        fun setCurTextAccentColor(color: Int) {
            when {
                AppConfig.isEInkMode -> {
                    textAccentColorEInk = "#${color.hexString}"
                    textAccentColorIntEInk = color
                }

                AppConfig.isNightTheme -> {
                    textAccentColorNight = "#${color.hexString}"
                    textAccentColorIntNight = color
                }

                else -> {
                    textAccentColor = "#${color.hexString}"
                    textAccentColorInt = color
                }
            }
        }

        fun curTextAccentColor(): Int {
            if (!initAccentColorInt) {
                initAccentColorInt()
            }
            return when {
                AppConfig.isEInkMode -> textAccentColorIntEInk
                AppConfig.isNightTheme -> textAccentColorIntNight
                else -> textAccentColorInt
            }
        }

        fun setCurShadColor(color: Int){
            when {
                AppConfig.isNightTheme -> {
                    shadowColorN = "#${color.hexString}"
                    shadowColorNightInt = color
                }
                else -> {
                    shadowColor = "#${color.hexString}"
                    shadowColorInt = color
                }
            }
        }

        fun setCurTextColor(color: Int) {
            when {
                AppConfig.isEInkMode -> {
                    textColorEInk = "#${color.hexString}"
                    textColorIntEInk = color
                }

                AppConfig.isNightTheme -> {
                    textColorNight = "#${color.hexString}"
                    textColorIntNight = color
                }

                else -> {
                    textColor = "#${color.hexString}"
                    textColorInt = color
                }
            }
        }

        fun curTextColor(): Int {
            if (!initColorInt) {
                initColorInt()
            }
            return when {
                AppConfig.isEInkMode -> textColorIntEInk
                AppConfig.isNightTheme -> textColorIntNight
                else -> textColorInt
            }
        }

        fun curTextShadowColor(): Int {
            if (!initColorInt) {
                initColorInt()
            }
            return when {
                AppConfig.isNightTheme -> shadowColorNightInt
                else -> shadowColorInt
            }
        }

        fun setCurStatusIconDark(isDark: Boolean) {
            when {
                AppConfig.isEInkMode -> darkStatusIconEInk = isDark
                AppConfig.isNightTheme -> darkStatusIconNight = isDark
                else -> darkStatusIcon = isDark
            }
        }

        fun curStatusIconDark(): Boolean {
            return when {
                AppConfig.isEInkMode -> darkStatusIconEInk
                AppConfig.isNightTheme -> darkStatusIconNight
                else -> darkStatusIcon
            }
        }

        fun setCurPageAnim(@PageAnim.Anim anim: Int) {
            when {
                AppConfig.isEInkMode -> pageAnimEInk = anim
                else -> pageAnim = anim
            }
        }

        fun curPageAnim(): Int {
            return when {
                AppConfig.isEInkMode -> pageAnimEInk
                else -> pageAnim
            }
        }

        fun setCurBg(bgType: Int, bg: String) {
            when {
                AppConfig.isEInkMode -> {
                    bgTypeEInk = bgType
                    bgStrEInk = bg
                }

                AppConfig.isNightTheme -> {
                    bgTypeNight = bgType
                    bgStrNight = bg
                }

                else -> {
                    this.bgType = bgType
                    bgStr = bg
                }
            }
        }

        fun curBgStr(): String {
            return when {
                AppConfig.isEInkMode -> bgStrEInk
                AppConfig.isNightTheme -> bgStrNight
                else -> bgStr
            }
        }

        fun curMenuBg(): Int {
            return when {
                AppConfig.isNightTheme -> menuBgColorNightInt
                else -> menuBgColorInt
            }
        }

        fun setMenuCurBg(bg: Int) {
            when {
                AppConfig.isNightTheme -> {
                    menuBgColorNight = "#${bg.hexString}"
                    menuBgColorNightInt = bg
                }

                else -> {
                    menuBgColor = "#${bg.hexString}"
                    menuBgColorInt = bg
                }
            }
        }

        fun curMenuAc(): Int {
            return when {
                AppConfig.isNightTheme -> menuAcColorNightInt
                else -> menuAcColorInt
            }
        }

        fun setMenuCurAc(bg: Int) {
            when {
                AppConfig.isNightTheme -> {
                    menuAcColorNight = "#${bg.hexString}"
                    menuAcColorNightInt = bg
                }

                else -> {
                    menuAcColor = "#${bg.hexString}"
                    menuAcColorInt = bg
                }
            }
        }

        fun curUnderlineColor(): Int {
            return when {
                AppConfig.isNightTheme -> underlineColorNightInt
                else -> underlineColorInt
            }
        }

        fun setUnderlineColor(bg: Int) {
            when {
                AppConfig.isNightTheme -> {
                    underlineColorNight = "#${bg.hexString}"
                    underlineColorNightInt = bg
                }

                else -> {
                    underlineColor = "#${bg.hexString}"
                    underlineColorInt = bg
                }
            }
        }

        fun curBgType(): Int {
            return when {
                AppConfig.isEInkMode -> bgTypeEInk
                AppConfig.isNightTheme -> bgTypeNight
                else -> bgType
            }
        }

        fun curBgDrawable(width: Int, height: Int): Drawable {
            if (width == 0 || height == 0) {
                val backgroundColor = MaterialColors.getColor(appCtx, com.google.android.material.R.attr.colorSurface, Color.WHITE)
                return backgroundColor.toDrawable()
            }

            var bgDrawable: Drawable? = null
            val resources = appCtx.resources
            try {
                bgDrawable = when (curBgType()) {
                    0 -> curBgStr().toColorInt().toDrawable()
                    1 -> {
                        val path = "bg" + File.separator + curBgStr()
                        val bitmap = BitmapUtils.decodeAssetsBitmap(appCtx, path, width, height)
                        bitmap?.resizeAndRecycle(width, height)?.toDrawable(resources)
                    }
                    else -> {
                        val path = curBgStr().let {
                            if (it.contains(File.separator)) it
                            else FileUtils.getPath(appCtx.externalFiles, "bg", curBgStr())
                        }
                        val bitmap = BitmapUtils.decodeBitmap(path, width, height)
                        bitmap?.resizeAndRecycle(width, height)?.toDrawable(resources)
                    }
                }
            } catch (e: OutOfMemoryError) {
                e.printOnDebug()
            } catch (e: Exception) {
                e.printOnDebug()
            }

            // fallback 使用 MD3 的 colorSurface 作为背景色
            val fallbackColor = MaterialColors.getColor(appCtx, com.google.android.material.R.attr.colorSurface, Color.WHITE)
            return bgDrawable ?: fallbackColor.toDrawable()
        }
    }
}