package io.legado.app.help.config

import android.content.Context
import android.net.Uri
import androidx.annotation.Keep
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import io.legado.app.ui.config.coverConfig.CoverConfig
import io.legado.app.ui.config.themeConfig.ThemeConfig
import io.legado.app.utils.EncoderUtils
import io.legado.app.utils.GSON
import splitties.init.appCtx
import java.io.File
import java.io.FileOutputStream

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
                    val data = parseThemeData(json)
                        ?: error("不支持的主题配置格式")
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

    fun uniqueSavedThemeName(name: String): String {
        if (_savedThemes.none { it.name == name }) return name
        var index = 2
        while (_savedThemes.any { it.name == "$name $index" }) index++
        return "$name $index"
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
     * 删除已保存的主题
     */
    fun deleteSavedTheme(theme: SavedTheme) {
        val file = File(baseDir, "${theme.name}.json")
        if (file.exists() && !file.delete()) {
            error("Failed to delete saved theme: ${theme.name}")
        }
        _savedThemes.removeAll { it.name == theme.name }
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
    fun exportFromCurrent(includeEmbeddedAssets: Boolean = true): ThemeExportData {
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
            themeColorNight = ThemeConfig.themeColorNight,
            secondaryThemeColorNight = ThemeConfig.secondaryThemeColorNight,
            primaryTextColorNight = ThemeConfig.primaryTextColorNight,
            secondaryTextColorNight = ThemeConfig.secondaryTextColorNight,
            themeBackgroundColorNight = ThemeConfig.themeBackgroundColorNight,
            labelContainerColorNight = ThemeConfig.labelContainerColorNight,
            bookInfoInputColor = ThemeConfig.bookInfoInputColor,
            bookInfoFollowCoverColor = ThemeConfig.bookInfoFollowCoverColor,
            bookInfoBackgroundBlur = ThemeConfig.bookInfoBackgroundBlur,

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
            showHome = ThemeConfig.showHome,
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
            mainNavigationOrder = ThemeConfig.mainNavigationOrder,

            // 导航栏图标
            navIconHome = ThemeConfig.navIconHome,
            navIconBookshelf = ThemeConfig.navIconBookshelf,
            navIconExplore = ThemeConfig.navIconExplore,
            navIconRss = ThemeConfig.navIconRss,
            navIconMy = ThemeConfig.navIconMy,

            // Miuix 设置
            useMiuixMonet = ThemeConfig.useMiuixMonet,

            // 其他
            useFlexibleTopAppBar = ThemeConfig.useFlexibleTopAppBar,
            bgImageLight = ThemeConfig.bgImageLight,
            bgImageDark = ThemeConfig.bgImageDark,
            bgImageBlurring = ThemeConfig.bgImageBlurring,
            bgImageNBlurring = ThemeConfig.bgImageNBlurring,
            appFontPath = ThemeConfig.appFontPath,

            // 封面配置 (CoverConfig)
            coverLoadOnlyWifi = CoverConfig.loadCoverOnlyWifi,
            coverUseDefault = CoverConfig.useDefaultCover,
            coverShowShadow = CoverConfig.coverShowShadow,
            coverShowStroke = CoverConfig.coverShowStroke,
            coverDefaultColor = CoverConfig.coverDefaultColor,
            coverDefaultImage = CoverConfig.defaultCover,
            coverTextColor = CoverConfig.coverTextColor,
            coverShadowColor = CoverConfig.coverShadowColor,
            coverShowName = CoverConfig.coverShowName,
            coverShowAuthor = CoverConfig.coverShowAuthor,
            coverDefaultImageDark = CoverConfig.defaultCoverDark,
            coverTextColorN = CoverConfig.coverTextColorN,
            coverShadowColorN = CoverConfig.coverShadowColorN,
            coverShowNameN = CoverConfig.coverShowNameN,
            coverShowAuthorN = CoverConfig.coverShowAuthorN,
            coverInfoOrientation = CoverConfig.coverInfoOrientation,
            assets = if (includeEmbeddedAssets) exportAssets() else null
        )
    }

    /**
     * 将相关的图片、字体资源转换为Base64
     */
    private fun exportAssets(): Map<String, String> {
        val assets = mutableMapOf<String, String>()
        val assetPaths = mapOf(
            "bgImageLight" to ThemeConfig.bgImageLight,
            "bgImageDark" to ThemeConfig.bgImageDark,
            "navIconHome" to ThemeConfig.navIconHome,
            "navIconBookshelf" to ThemeConfig.navIconBookshelf,
            "navIconExplore" to ThemeConfig.navIconExplore,
            "navIconRss" to ThemeConfig.navIconRss,
            "navIconMy" to ThemeConfig.navIconMy,
            "appFontPath" to ThemeConfig.appFontPath
        )

        assetPaths.forEach { (key, path) ->
            if (!path.isNullOrBlank()) {
                try {
                    val file = if (path.startsWith("content://")) {
                        null // TODO: 处理 content uri
                    } else {
                        File(path)
                    }
                    if (file?.exists() == true) {
                        assets[key] = EncoderUtils.base64Encode(file.readBytes())
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // 处理封面图集（可能包含多个逗号分隔的路径）
        exportCoverAssets(assets, "coverDefaultImage", CoverConfig.defaultCover)
        exportCoverAssets(assets, "coverDefaultImageDark", CoverConfig.defaultCoverDark)

        return assets
    }

    /**
     * 导出封面图集资源（支持多个逗号分隔的路径）
     */
    private fun exportCoverAssets(assets: MutableMap<String, String>, keyPrefix: String, paths: String?) {
        if (paths.isNullOrBlank()) return
        val pathList = paths.split(",").filter { it.isNotBlank() }
        pathList.forEachIndexed { index, path ->
            try {
                val file = if (path.startsWith("content://")) {
                    null
                } else {
                    File(path)
                }
                if (file?.exists() == true) {
                    val key = if (pathList.size == 1) keyPrefix else "${keyPrefix}_$index"
                    assets[key] = EncoderUtils.base64Encode(file.readBytes())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 将导出数据应用到当前配置
     */
    internal fun applyToThemeConfig(
        data: ThemeExportData,
        applyEmbeddedCoverAssets: Boolean = true,
    ): AppliedThemeAssets {
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
        ThemeConfig.themeColorNight = data.themeColorNight
        ThemeConfig.secondaryThemeColorNight = data.secondaryThemeColorNight
        ThemeConfig.primaryTextColorNight = data.primaryTextColorNight
        ThemeConfig.secondaryTextColorNight = data.secondaryTextColorNight
        ThemeConfig.themeBackgroundColorNight = data.themeBackgroundColorNight
        ThemeConfig.labelContainerColorNight = data.labelContainerColorNight
        ThemeConfig.bookInfoInputColor = data.bookInfoInputColor
        ThemeConfig.bookInfoFollowCoverColor = data.bookInfoFollowCoverColor
        ThemeConfig.bookInfoBackgroundBlur = data.bookInfoBackgroundBlur

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
        ThemeConfig.showHome = data.showHome
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
        ThemeConfig.mainNavigationOrder = data.mainNavigationOrder

        // 导航栏图标
        ThemeConfig.navIconHome = data.navIconHome
        ThemeConfig.navIconBookshelf = data.navIconBookshelf
        ThemeConfig.navIconExplore = data.navIconExplore
        ThemeConfig.navIconRss = data.navIconRss
        ThemeConfig.navIconMy = data.navIconMy

        // Miuix 设置
        ThemeConfig.useMiuixMonet = data.useMiuixMonet

        // 其他
        ThemeConfig.useFlexibleTopAppBar = data.useFlexibleTopAppBar
        ThemeConfig.bgImageLight = data.bgImageLight
        ThemeConfig.bgImageDark = data.bgImageDark
        ThemeConfig.bgImageBlurring = data.bgImageBlurring
        ThemeConfig.bgImageNBlurring = data.bgImageNBlurring
        ThemeConfig.appFontPath = data.appFontPath

        // 封面配置 (CoverConfig)
        CoverConfig.loadCoverOnlyWifi = data.coverLoadOnlyWifi
        CoverConfig.useDefaultCover = data.coverUseDefault
        CoverConfig.coverShowShadow = data.coverShowShadow
        CoverConfig.coverShowStroke = data.coverShowStroke
        CoverConfig.coverDefaultColor = data.coverDefaultColor
        CoverConfig.defaultCover = data.coverDefaultImage
        CoverConfig.coverTextColor = data.coverTextColor
        CoverConfig.coverShadowColor = data.coverShadowColor
        CoverConfig.coverShowName = data.coverShowName
        CoverConfig.coverShowAuthor = data.coverShowAuthor
        CoverConfig.defaultCoverDark = data.coverDefaultImageDark
        CoverConfig.coverTextColorN = data.coverTextColorN
        CoverConfig.coverShadowColorN = data.coverShadowColorN
        CoverConfig.coverShowNameN = data.coverShowNameN
        CoverConfig.coverShowAuthorN = data.coverShowAuthorN
        CoverConfig.coverInfoOrientation = data.coverInfoOrientation

        // 应用嵌入的资源
        val embeddedAssets = data.assets?.let { assets ->
            applyAssets(
                assets = assets,
                applyCoverAssets = applyEmbeddedCoverAssets,
            )
        } ?: AppliedThemeAssets()
        return AppliedThemeAssets(
            lightCoverPaths = embeddedAssets.lightCoverPaths.ifEmpty {
                data.coverDefaultImage.toCoverPaths()
            },
            darkCoverPaths = embeddedAssets.darkCoverPaths.ifEmpty {
                data.coverDefaultImageDark.toCoverPaths()
            },
        )
    }

    private fun applyAssets(
        assets: Map<String, String>,
        applyCoverAssets: Boolean,
    ): AppliedThemeAssets {
        val coverPaths = mutableMapOf<String, MutableList<String>>()

        assets.forEach { (key, base64) ->
            if (base64.isBlank()) return@forEach
            if (!applyCoverAssets && key.startsWith("coverDefaultImage")) return@forEach
            try {
                val bytes = EncoderUtils.base64DecodeToByteArray(base64)
                val destFile = when {
                    key.startsWith("bgImage") -> {
                        val baseDir = appCtx.getExternalFilesDir(null) ?: appCtx.filesDir
                        val folder = File(baseDir, key)
                        folder.mkdirs()
                        File(folder, "theme_asset_${System.currentTimeMillis()}.jpg")
                    }

                    key.startsWith("navIcon") -> {
                        val folder = File(appCtx.filesDir, "nav_icons")
                        folder.mkdirs()
                        File(folder, "${key.removePrefix("navIcon").lowercase()}.png")
                    }

                    key == "appFontPath" -> {
                        val folder = File(appCtx.filesDir, "fonts")
                        folder.mkdirs()
                        File(folder, "theme_font.ttf")
                    }

                    key.startsWith("coverDefaultImage") -> {
                        val baseDir = appCtx.getExternalFilesDir(null) ?: appCtx.filesDir
                        val folder = File(baseDir, "covers")
                        folder.mkdirs()
                        File(folder, "${key}_${System.currentTimeMillis()}.jpg")
                    }

                    else -> null
                }

                destFile?.let { file ->
                    FileOutputStream(file).use { it.write(bytes) }
                    val path = file.absolutePath
                    when {
                        key == "bgImageLight" -> ThemeConfig.bgImageLight = path
                        key == "bgImageDark" -> ThemeConfig.bgImageDark = path
                        key == "navIconHome" -> ThemeConfig.navIconHome = path
                        key == "navIconBookshelf" -> ThemeConfig.navIconBookshelf = path
                        key == "navIconExplore" -> ThemeConfig.navIconExplore = path
                        key == "navIconRss" -> ThemeConfig.navIconRss = path
                        key == "navIconMy" -> ThemeConfig.navIconMy = path
                        key == "appFontPath" -> ThemeConfig.appFontPath = path
                        key.startsWith("coverDefaultImage") -> {
                            // 判断是日间还是夜间封面
                            val baseKey = if (key.removePrefix("coverDefaultImage").startsWith("Dark")) {
                                "coverDefaultImageDark"
                            } else {
                                "coverDefaultImage"
                            }
                            coverPaths.getOrPut(baseKey) { mutableListOf() }.add(path)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 将收集到的封面路径合并为逗号分隔的字符串
        coverPaths["coverDefaultImage"]?.let { paths ->
            CoverConfig.defaultCover = paths.joinToString(",")
        }
        coverPaths["coverDefaultImageDark"]?.let { paths ->
            CoverConfig.defaultCoverDark = paths.joinToString(",")
        }
        return AppliedThemeAssets(
            lightCoverPaths = coverPaths["coverDefaultImage"].orEmpty(),
            darkCoverPaths = coverPaths["coverDefaultImageDark"].orEmpty(),
        )
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
        return importFromJsonWithAssets(json) != null
    }

    internal fun importFromJsonWithAssets(json: String): AppliedThemeAssets? {
        return try {
            val data = parseThemeData(json) ?: return null
            applyToThemeConfig(data)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun String.toCoverPaths(): List<String> =
        split(",").map(String::trim).filter(String::isNotEmpty)

    private fun parseThemeData(json: String): ThemeExportData? {
        val root = JsonParser.parseString(json).asJsonObject
        return when {
            root.has("appTheme") && root.has("themeMode") ->
                GSON.fromJson(root, ThemeExportData::class.java)

            root.has("a") && root.has("A") && root.has("y0") ->
                parseObfuscatedThemeV1(root)

            else -> null
        }
    }

    /**
     * 兼容曾由 R8 混淆字段名导出的主题配置。
     * 该映射对应加入首页开关、但尚未加入导航顺序和日夜独立配色的旧格式。
     */
    private fun parseObfuscatedThemeV1(root: JsonObject): ThemeExportData {
        val assetsType = object : TypeToken<Map<String, String>>() {}.type
        return ThemeExportData(
            appTheme = root.string("a", "0"),
            themeMode = root.string("b", "0"),
            isPureBlack = root.boolean("c"),
            composeEngine = root.string("d", "material"),
            paletteStyle = root.string("e", "tonalSpot"),
            materialVersion = root.string("f", "material3"),
            customMode = root.nullableString("g"),
            customContrast = root.string("h", "Default"),
            launcherIcon = root.string("i", "ic_launcher"),
            isPredictiveBackEnabled = root.boolean("j", true),
            fontScale = root.int("k", 10),
            enableDeepPersonalization = root.boolean("l"),
            cPrimary = root.int("m"),
            cNPrimary = root.int("n"),
            themeColor = root.int("o"),
            secondaryThemeColor = root.int("p"),
            primaryTextColor = root.int("q"),
            secondaryTextColor = root.int("r"),
            themeBackgroundColor = root.int("s"),
            labelContainerColor = root.int("t"),
            bookInfoInputColor = root.int("u"),
            containerOpacity = root.int("v", 100),
            enableItemDivider = root.boolean("w"),
            itemDividerWidth = root.float("x", 1f),
            itemDividerLength = root.float("y", 80f),
            itemDividerColor = root.int("z"),
            enableBlur = root.boolean("A"),
            enableProgressiveBlur = root.boolean("B"),
            topBarBlurRadius = root.int("C", 24),
            bottomBarBlurRadius = root.int("D", 8),
            topBarBlurAlpha = root.int("E", 73),
            bottomBarBlurAlpha = root.int("F", 40),
            bottomBarLensRadius = root.float("G", 24f),
            topBarOpacity = root.int("H", 100),
            bottomBarOpacity = root.int("I", 100),
            enableCustomTagColors = root.boolean("J"),
            customTagColorsJson = root.nullableString("K"),
            showHome = root.boolean("L", true),
            showDiscovery = root.boolean("M", true),
            showRss = root.boolean("N", true),
            showStatusBar = root.boolean("O", true),
            swipeAnimation = root.boolean("P", true),
            showBottomView = root.boolean("Q", true),
            useFloatingBottomBar = root.boolean("R"),
            useFloatingBottomBarLiquidGlass = root.boolean("S"),
            tabletInterface = root.string("T", "auto"),
            labelVisibilityMode = root.string("U", "auto"),
            defaultHomePage = root.string("V", "bookshelf"),
            navIconHome = root.string("W"),
            navIconBookshelf = root.string("X"),
            navIconExplore = root.string("Y"),
            navIconRss = root.string("Z"),
            navIconMy = root.string("a0"),
            useMiuixMonet = root.boolean("b0"),
            useFlexibleTopAppBar = root.boolean("c0", true),
            bgImageLight = root.nullableString("d0"),
            bgImageDark = root.nullableString("e0"),
            bgImageBlurring = root.int("f0"),
            bgImageNBlurring = root.int("g0"),
            appFontPath = root.nullableString("h0"),
            coverLoadOnlyWifi = root.boolean("i0"),
            coverUseDefault = root.boolean("j0"),
            coverShowShadow = root.boolean("k0"),
            coverShowStroke = root.boolean("l0", true),
            coverDefaultColor = root.boolean("m0", true),
            coverDefaultImage = root.string("n0"),
            coverTextColor = root.int("o0", -16777216),
            coverShadowColor = root.int("p0", -16777216),
            coverShowName = root.boolean("q0", true),
            coverShowAuthor = root.boolean("r0", true),
            coverDefaultImageDark = root.string("s0"),
            coverTextColorN = root.int("t0", -1),
            coverShadowColorN = root.int("u0", -1),
            coverShowNameN = root.boolean("v0", true),
            coverShowAuthorN = root.boolean("w0", true),
            coverInfoOrientation = root.string("x0", "0"),
            assets = root.get("y0")?.takeUnless { it.isJsonNull }?.let {
                GSON.fromJson(it, assetsType)
            },
        )
    }

    private fun JsonObject.string(key: String, default: String = ""): String =
        get(key)?.takeUnless { it.isJsonNull }?.asString ?: default

    private fun JsonObject.nullableString(key: String): String? =
        get(key)?.takeUnless { it.isJsonNull }?.asString

    private fun JsonObject.boolean(key: String, default: Boolean = false): Boolean =
        get(key)?.takeUnless { it.isJsonNull }?.asBoolean ?: default

    private fun JsonObject.int(key: String, default: Int = 0): Int =
        get(key)?.takeUnless { it.isJsonNull }?.asInt ?: default

    private fun JsonObject.float(key: String, default: Float = 0f): Float =
        get(key)?.takeUnless { it.isJsonNull }?.asFloat ?: default

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
@Keep
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
    val themeColorNight: Int = 0,
    val secondaryThemeColorNight: Int = 0,
    val primaryTextColorNight: Int = 0,
    val secondaryTextColorNight: Int = 0,
    val themeBackgroundColorNight: Int = 0,
    val labelContainerColorNight: Int = 0,
    val bookInfoInputColor: Int = 0,
    val bookInfoFollowCoverColor: Boolean = true,
    val bookInfoBackgroundBlur: String = ThemeConfig.BOOK_INFO_BACKGROUND_BLUR_ON,

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
    val showHome: Boolean = true,
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
    val mainNavigationOrder: String = "home,bookshelf,explore,rss,my",

    // 导航栏图标
    val navIconHome: String = "",
    val navIconBookshelf: String = "",
    val navIconExplore: String = "",
    val navIconRss: String = "",
    val navIconMy: String = "",

    // Miuix 设置
    val useMiuixMonet: Boolean = false,

    // 其他
    val useFlexibleTopAppBar: Boolean = true,
    val bgImageLight: String? = null,
    val bgImageDark: String? = null,
    val bgImageBlurring: Int = 0,
    val bgImageNBlurring: Int = 0,
    val appFontPath: String? = null,
    val selectedCoverAlbumId: String? = null,

    // 封面配置 (CoverConfig)
    val coverLoadOnlyWifi: Boolean = false,
    val coverUseDefault: Boolean = false,
    val coverShowShadow: Boolean = false,
    val coverShowStroke: Boolean = true,
    val coverDefaultColor: Boolean = true,
    val coverDefaultImage: String = "",
    val coverTextColor: Int = -16777216,
    val coverShadowColor: Int = -16777216,
    val coverShowName: Boolean = true,
    val coverShowAuthor: Boolean = true,
    val coverDefaultImageDark: String = "",
    val coverTextColorN: Int = -1,
    val coverShadowColorN: Int = -1,
    val coverShowNameN: Boolean = true,
    val coverShowAuthorN: Boolean = true,
    val coverInfoOrientation: String = "0",

    // 嵌入的二进制资源 (Base64)
    val assets: Map<String, String>? = null
)

/**
 * 已保存的主题
 */
@Keep
data class SavedTheme(
    val name: String,
    val data: ThemeExportData,
    val packageRootPath: String? = null,
    val packageManifest: ThemePackageManifest? = null,
)

internal data class AppliedThemeAssets(
    val lightCoverPaths: List<String> = emptyList(),
    val darkCoverPaths: List<String> = emptyList(),
)
