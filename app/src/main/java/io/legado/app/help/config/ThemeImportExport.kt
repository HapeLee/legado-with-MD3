package io.legado.app.help.config

import androidx.annotation.Keep
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import io.legado.app.domain.model.settings.ThemeExportData
import io.legado.app.utils.EncoderUtils
import io.legado.app.utils.GSON
import splitties.init.appCtx
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * 轻量级主题导入导出系统
 * 将所有主题配置导出为JSON文件，方便分享和备份
 * 支持保存多个命名主题并在之间切换
 */
object ThemeImportExport {

    internal fun withEmbeddedAssets(data: ThemeExportData): ThemeExportData =
        data.copy(assets = exportAssets(data))

    /**
     * 将相关的图片、字体资源转换为Base64
     */
    private fun exportAssets(data: ThemeExportData): Map<String, String> {
        val assets = mutableMapOf<String, String>()
        val assetPaths = mapOf(
            "bgImageLight" to data.bgImageLight,
            "bgImageDark" to data.bgImageDark,
            "navIconHome" to data.navIconHome,
            "navIconBookshelf" to data.navIconBookshelf,
            "navIconExplore" to data.navIconExplore,
            "navIconRss" to data.navIconRss,
            "navIconMy" to data.navIconMy,
            "appFontPath" to data.appFontPath,
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
        exportCoverAssets(assets, "coverDefaultImage", data.coverDefaultImage)
        exportCoverAssets(assets, "coverDefaultImageDark", data.coverDefaultImageDark)

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

    internal fun prepareForApply(
        data: ThemeExportData,
        applyEmbeddedCoverAssets: Boolean = true,
    ): PreparedTheme {
        val embeddedAssets = data.assets?.let { assets ->
            applyAssets(
                assets = assets,
                applyCoverAssets = applyEmbeddedCoverAssets,
            )
        } ?: AppliedThemeAssets()
        val appliedAssets = AppliedThemeAssets(
            lightCoverPaths = embeddedAssets.lightCoverPaths.ifEmpty {
                data.coverDefaultImage.toCoverPaths()
            },
            darkCoverPaths = embeddedAssets.darkCoverPaths.ifEmpty {
                data.coverDefaultImageDark.toCoverPaths()
            },
            paths = embeddedAssets.paths,
            createdFiles = embeddedAssets.createdFiles,
        )
        return PreparedTheme(
            data = data.copy(
                bgImageLight = embeddedAssets.paths["bgImageLight"] ?: data.bgImageLight,
                bgImageDark = embeddedAssets.paths["bgImageDark"] ?: data.bgImageDark,
                navIconHome = embeddedAssets.paths["navIconHome"] ?: data.navIconHome,
                navIconBookshelf = embeddedAssets.paths["navIconBookshelf"]
                    ?: data.navIconBookshelf,
                navIconExplore = embeddedAssets.paths["navIconExplore"] ?: data.navIconExplore,
                navIconRss = embeddedAssets.paths["navIconRss"] ?: data.navIconRss,
                navIconMy = embeddedAssets.paths["navIconMy"] ?: data.navIconMy,
                appFontPath = embeddedAssets.paths["appFontPath"] ?: data.appFontPath,
                coverDefaultImage = appliedAssets.lightCoverPaths.joinToString(","),
                coverDefaultImageDark = appliedAssets.darkCoverPaths.joinToString(","),
                assets = null,
            ),
            appliedAssets = appliedAssets,
        )
    }

    private fun applyAssets(
        assets: Map<String, String>,
        applyCoverAssets: Boolean,
    ): AppliedThemeAssets {
        val coverPaths = mutableMapOf<String, MutableList<String>>()
        val paths = mutableMapOf<String, String>()
        val createdFiles = mutableListOf<File>()

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
                        File(folder, "theme_asset_${UUID.randomUUID()}.jpg")
                    }

                    key.startsWith("navIcon") -> {
                        val folder = File(appCtx.filesDir, "nav_icons")
                        folder.mkdirs()
                        File(
                            folder,
                            "theme_${key.removePrefix("navIcon").lowercase()}_${UUID.randomUUID()}.png",
                        )
                    }

                    key == "appFontPath" -> {
                        val folder = File(appCtx.filesDir, "fonts")
                        folder.mkdirs()
                        File(folder, "theme_font_${UUID.randomUUID()}.ttf")
                    }

                    key.startsWith("coverDefaultImage") -> {
                        val baseDir = appCtx.getExternalFilesDir(null) ?: appCtx.filesDir
                        val folder = File(baseDir, "covers")
                        folder.mkdirs()
                        File(folder, "${key}_${UUID.randomUUID()}.jpg")
                    }

                    else -> null
                }

                destFile?.let { file ->
                    createdFiles += file
                    FileOutputStream(file).use { it.write(bytes) }
                    val path = file.absolutePath
                    if (key.startsWith("coverDefaultImage")) {
                            // 判断是日间还是夜间封面
                            val baseKey = if (key.removePrefix("coverDefaultImage").startsWith("Dark")) {
                                "coverDefaultImageDark"
                            } else {
                                "coverDefaultImage"
                            }
                            coverPaths.getOrPut(baseKey) { mutableListOf() }.add(path)
                    } else {
                        paths[key] = path
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return AppliedThemeAssets(
            lightCoverPaths = coverPaths["coverDefaultImage"].orEmpty(),
            darkCoverPaths = coverPaths["coverDefaultImageDark"].orEmpty(),
            paths = paths,
            createdFiles = createdFiles,
        )
    }

    internal fun prepareLegacyJson(json: String): PreparedTheme? {
        return try {
            val data = parseThemeData(json) ?: return null
            prepareForApply(data)
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

    internal fun parseLegacyThemeData(json: String): ThemeExportData? = parseThemeData(json)

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

}

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
    val paths: Map<String, String> = emptyMap(),
    val createdFiles: List<File> = emptyList(),
)

internal data class PreparedTheme(
    val data: ThemeExportData,
    val appliedAssets: AppliedThemeAssets,
)
