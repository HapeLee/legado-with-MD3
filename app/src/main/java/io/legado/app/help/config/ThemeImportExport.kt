package io.legado.app.help.config

import android.content.Context
import android.net.Uri
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.ui.config.themeConfig.ThemeConfig
import io.legado.app.utils.GSON
import io.legado.app.utils.inputStream
import io.legado.app.utils.outputStream
import splitties.init.appCtx
import java.io.File

/**
 * 轻量级主题导入导出系统
 * 将所有主题配置导出为JSON文件，方便分享和备份
 * 支持保存多个命名主题并在之间切换
 */
object ThemeImportExport {

    private const val DIR_NAME = "saved_themes"
    private val baseDir get() = File(appCtx.filesDir, DIR_NAME)

    private val EXPORT_GSON = GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create()

    private val _savedThemes = mutableListOf<SavedTheme>()
    val savedThemes: List<SavedTheme> get() = _savedThemes

    init {
        loadAll()
    }

    private fun loadAll() {
        _savedThemes.clear()
        baseDir.mkdirs()
        baseDir.listFiles()?.forEach { file ->
            if (file.isFile && file.extension == "json") {
                kotlin.runCatching {
                    val json = file.readText()
                    val data = EXPORT_GSON.fromJson(json, ThemeExportData::class.java)
                    val name = file.nameWithoutExtension
                    _savedThemes.add(SavedTheme(name = name, data = data))
                }
            }
        }
    }

    fun reload() {
        loadAll()
    }

    /**
     * 保存当前设置为新主题
     */
    fun saveCurrentAsTheme(name: String): SavedTheme {
        val data = exportFromCurrent()
        return saveThemeData(name, data)
    }

    /**
     * 保存指定数据为新主题
     */
    fun saveCurrentAsTheme(name: String, data: ThemeExportData): SavedTheme {
        return saveThemeData(name, data)
    }

    private fun saveThemeData(name: String, data: ThemeExportData): SavedTheme {
        val file = File(baseDir, "$name.json")
        baseDir.mkdirs()
        file.writeText(EXPORT_GSON.toJson(data))
        val theme = SavedTheme(name = name, data = data)
        _savedThemes.removeAll { it.name == name }
        _savedThemes.add(theme)
        return theme
    }

    /**
     * 应用已保存的主题
     */
    fun applySavedTheme(theme: SavedTheme): Boolean {
        return try {
            applyToThemeConfig(theme.data)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 删除已保存的主题
     */
    fun deleteSavedTheme(theme: SavedTheme) {
        val file = File(baseDir, "${theme.name}.json")
        if (file.exists()) {
            file.delete()
        }
        _savedThemes.remove(theme)
    }

    /**
     * 导出已保存的主题到文件
     */
    fun exportSavedThemeToFile(context: Context, theme: SavedTheme, uri: Uri): Boolean {
        return try {
            val json = EXPORT_GSON.toJson(theme.data)
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(json.toByteArray())
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 从当前配置创建导出数据
     */
    fun exportFromCurrent(): ThemeExportData {
        return ThemeExportData(
            // 基础主题设置
            appTheme = ThemeConfig.appTheme,
            themeMode = ThemeConfig.themeMode,
            isPureBlack = ThemeConfig.isPureBlack,
            composeEngine = ThemeConfig.composeEngine,
            paletteStyle = ThemeConfig.paletteStyle,
            materialVersion = ThemeConfig.materialVersion,
            customMode = ThemeConfig.customMode,
            customContrast = ThemeConfig.customContrast,
            launcherIcon = ThemeConfig.launcherIcon,
            isPredictiveBackEnabled = ThemeConfig.isPredictiveBackEnabled,
            fontScale = ThemeConfig.fontScale,

            // 深度个性化颜色
            enableDeepPersonalization = ThemeConfig.enableDeepPersonalization,
            cPrimary = ThemeConfig.cPrimary,
            cNPrimary = ThemeConfig.cNPrimary,
            themeColor = ThemeConfig.themeColor,
            secondaryThemeColor = ThemeConfig.secondaryThemeColor,
            primaryTextColor = ThemeConfig.primaryTextColor,
            secondaryTextColor = ThemeConfig.secondaryTextColor,
            themeBackgroundColor = ThemeConfig.themeBackgroundColor,
            labelContainerColor = ThemeConfig.labelContainerColor,
            bookInfoInputColor = ThemeConfig.bookInfoInputColor,

            // 容器设置
            containerOpacity = ThemeConfig.containerOpacity,

            // 分割线设置
            enableItemDivider = ThemeConfig.enableItemDivider,
            itemDividerWidth = ThemeConfig.itemDividerWidth,
            itemDividerLength = ThemeConfig.itemDividerLength,
            itemDividerColor = ThemeConfig.itemDividerColor,

            // 模糊设置
            enableBlur = ThemeConfig.enableBlur,
            enableProgressiveBlur = ThemeConfig.enableProgressiveBlur,
            topBarBlurRadius = ThemeConfig.topBarBlurRadius,
            bottomBarBlurRadius = ThemeConfig.bottomBarBlurRadius,
            topBarBlurAlpha = ThemeConfig.topBarBlurAlpha,
            bottomBarBlurAlpha = ThemeConfig.bottomBarBlurAlpha,
            bottomBarLensRadius = ThemeConfig.bottomBarLensRadius,

            // 透明度设置
            topBarOpacity = ThemeConfig.topBarOpacity,
            bottomBarOpacity = ThemeConfig.bottomBarOpacity,

            // 标签颜色
            enableCustomTagColors = ThemeConfig.enableCustomTagColors,
            customTagColorsJson = ThemeConfig.customTagColorsJson,

            // 主界面设置
            showDiscovery = ThemeConfig.showDiscovery,
            showRss = ThemeConfig.showRss,
            showStatusBar = ThemeConfig.showStatusBar,
            swipeAnimation = ThemeConfig.swipeAnimation,
            showBottomView = ThemeConfig.showBottomView,
            useFloatingBottomBar = ThemeConfig.useFloatingBottomBar,
            useFloatingBottomBarLiquidGlass = ThemeConfig.useFloatingBottomBarLiquidGlass,
            tabletInterface = ThemeConfig.tabletInterface,
            labelVisibilityMode = ThemeConfig.labelVisibilityMode,
            defaultHomePage = ThemeConfig.defaultHomePage,

            // 导航栏图标
            navIconBookshelf = ThemeConfig.navIconBookshelf,
            navIconExplore = ThemeConfig.navIconExplore,
            navIconRss = ThemeConfig.navIconRss,
            navIconMy = ThemeConfig.navIconMy,

            // Miuix 设置
            useMiuixMonet = ThemeConfig.useMiuixMonet,

            // 其他
            useFlexibleTopAppBar = ThemeConfig.useFlexibleTopAppBar,
            bgImageBlurring = ThemeConfig.bgImageBlurring,
            bgImageNBlurring = ThemeConfig.bgImageNBlurring
        )
    }

    /**
     * 将导出数据应用到当前配置
     */
    fun applyToThemeConfig(data: ThemeExportData) {
        // 基础主题设置
        ThemeConfig.appTheme = data.appTheme
        ThemeConfig.themeMode = data.themeMode
        ThemeConfig.isPureBlack = data.isPureBlack
        ThemeConfig.composeEngine = data.composeEngine
        ThemeConfig.paletteStyle = data.paletteStyle
        ThemeConfig.materialVersion = data.materialVersion
        ThemeConfig.customMode = data.customMode
        ThemeConfig.customContrast = data.customContrast
        ThemeConfig.launcherIcon = data.launcherIcon
        ThemeConfig.isPredictiveBackEnabled = data.isPredictiveBackEnabled
        ThemeConfig.fontScale = data.fontScale

        // 深度个性化颜色
        ThemeConfig.enableDeepPersonalization = data.enableDeepPersonalization
        ThemeConfig.cPrimary = data.cPrimary
        ThemeConfig.cNPrimary = data.cNPrimary
        ThemeConfig.themeColor = data.themeColor
        ThemeConfig.secondaryThemeColor = data.secondaryThemeColor
        ThemeConfig.primaryTextColor = data.primaryTextColor
        ThemeConfig.secondaryTextColor = data.secondaryTextColor
        ThemeConfig.themeBackgroundColor = data.themeBackgroundColor
        ThemeConfig.labelContainerColor = data.labelContainerColor
        ThemeConfig.bookInfoInputColor = data.bookInfoInputColor

        // 容器设置
        ThemeConfig.containerOpacity = data.containerOpacity

        // 分割线设置
        ThemeConfig.enableItemDivider = data.enableItemDivider
        ThemeConfig.itemDividerWidth = data.itemDividerWidth
        ThemeConfig.itemDividerLength = data.itemDividerLength
        ThemeConfig.itemDividerColor = data.itemDividerColor

        // 模糊设置
        ThemeConfig.enableBlur = data.enableBlur
        ThemeConfig.enableProgressiveBlur = data.enableProgressiveBlur
        ThemeConfig.topBarBlurRadius = data.topBarBlurRadius
        ThemeConfig.bottomBarBlurRadius = data.bottomBarBlurRadius
        ThemeConfig.topBarBlurAlpha = data.topBarBlurAlpha
        ThemeConfig.bottomBarBlurAlpha = data.bottomBarBlurAlpha
        ThemeConfig.bottomBarLensRadius = data.bottomBarLensRadius

        // 透明度设置
        ThemeConfig.topBarOpacity = data.topBarOpacity
        ThemeConfig.bottomBarOpacity = data.bottomBarOpacity

        // 标签颜色
        ThemeConfig.enableCustomTagColors = data.enableCustomTagColors
        ThemeConfig.customTagColorsJson = data.customTagColorsJson

        // 主界面设置
        ThemeConfig.showDiscovery = data.showDiscovery
        ThemeConfig.showRss = data.showRss
        ThemeConfig.showStatusBar = data.showStatusBar
        ThemeConfig.swipeAnimation = data.swipeAnimation
        ThemeConfig.showBottomView = data.showBottomView
        ThemeConfig.useFloatingBottomBar = data.useFloatingBottomBar
        ThemeConfig.useFloatingBottomBarLiquidGlass = data.useFloatingBottomBarLiquidGlass
        ThemeConfig.tabletInterface = data.tabletInterface
        ThemeConfig.labelVisibilityMode = data.labelVisibilityMode
        ThemeConfig.defaultHomePage = data.defaultHomePage

        // 导航栏图标
        ThemeConfig.navIconBookshelf = data.navIconBookshelf
        ThemeConfig.navIconExplore = data.navIconExplore
        ThemeConfig.navIconRss = data.navIconRss
        ThemeConfig.navIconMy = data.navIconMy

        // Miuix 设置
        ThemeConfig.useMiuixMonet = data.useMiuixMonet

        // 其他
        ThemeConfig.useFlexibleTopAppBar = data.useFlexibleTopAppBar
        ThemeConfig.bgImageBlurring = data.bgImageBlurring
        ThemeConfig.bgImageNBlurring = data.bgImageNBlurring
    }

    /**
     * 导出主题到JSON字符串
     */
    fun exportToJson(): String {
        val data = exportFromCurrent()
        return EXPORT_GSON.toJson(data)
    }

    /**
     * 从JSON字符串导入主题
     */
    fun importFromJson(json: String): Boolean {
        return try {
            val data = GSON.fromJson(json, ThemeExportData::class.java)
            applyToThemeConfig(data)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 从文件URI导入主题
     */
    fun importFromUri(context: Context, uri: Uri): Boolean {
        return try {
            val json = context.contentResolver.openInputStream(uri)?.use {
                it.bufferedReader().readText()
            } ?: return false
            importFromJson(json)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 导出主题到文件
     */
    fun exportToFile(context: Context, uri: Uri): Boolean {
        return try {
            val json = exportToJson()
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(json.toByteArray())
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

/**
 * 主题导出数据类
 */
data class ThemeExportData(
    // 基础主题设置
    val appTheme: String = "0",
    val themeMode: String = "0",
    val isPureBlack: Boolean = false,
    val composeEngine: String = "material",
    val paletteStyle: String = "tonalSpot",
    val materialVersion: String = "material3",
    val customMode: String? = "tonalSpot",
    val customContrast: String = "Default",
    val launcherIcon: String = "ic_launcher",
    val isPredictiveBackEnabled: Boolean = true,
    val fontScale: Int = 10,

    // 深度个性化颜色
    val enableDeepPersonalization: Boolean = false,
    val cPrimary: Int = 0,
    val cNPrimary: Int = 0,
    val themeColor: Int = 0,
    val secondaryThemeColor: Int = 0,
    val primaryTextColor: Int = 0,
    val secondaryTextColor: Int = 0,
    val themeBackgroundColor: Int = 0,
    val labelContainerColor: Int = 0,
    val bookInfoInputColor: Int = 0,

    // 容器设置
    val containerOpacity: Int = 100,

    // 分割线设置
    val enableItemDivider: Boolean = false,
    val itemDividerWidth: Float = 1f,
    val itemDividerLength: Float = 80f,
    val itemDividerColor: Int = 0,

    // 模糊设置
    val enableBlur: Boolean = false,
    val enableProgressiveBlur: Boolean = false,
    val topBarBlurRadius: Int = 24,
    val bottomBarBlurRadius: Int = 8,
    val topBarBlurAlpha: Int = 73,
    val bottomBarBlurAlpha: Int = 40,
    val bottomBarLensRadius: Float = 24f,

    // 透明度设置
    val topBarOpacity: Int = 100,
    val bottomBarOpacity: Int = 100,

    // 标签颜色
    val enableCustomTagColors: Boolean = false,
    val customTagColorsJson: String? = null,

    // 主界面设置
    val showDiscovery: Boolean = true,
    val showRss: Boolean = true,
    val showStatusBar: Boolean = true,
    val swipeAnimation: Boolean = true,
    val showBottomView: Boolean = true,
    val useFloatingBottomBar: Boolean = false,
    val useFloatingBottomBarLiquidGlass: Boolean = false,
    val tabletInterface: String = "auto",
    val labelVisibilityMode: String = "auto",
    val defaultHomePage: String = "bookshelf",

    // 导航栏图标
    val navIconBookshelf: String = "",
    val navIconExplore: String = "",
    val navIconRss: String = "",
    val navIconMy: String = "",

    // Miuix 设置
    val useMiuixMonet: Boolean = false,

    // 其他
    val useFlexibleTopAppBar: Boolean = true,
    val bgImageBlurring: Int = 0,
    val bgImageNBlurring: Int = 0
)

/**
 * 已保存的主题
 */
data class SavedTheme(
    val name: String,
    val data: ThemeExportData
)
