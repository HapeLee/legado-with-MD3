package io.legado.app.help.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import io.legado.app.ui.config.coverConfig.CoverConfig
import io.legado.app.ui.config.mainConfig.MainConfig
import io.legado.app.ui.config.themeConfig.ThemeConfig
import io.legado.app.utils.FileUtils
import io.legado.app.utils.externalFiles
import splitties.init.appCtx
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.lang.reflect.Type
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ThemePackConfig {

    private const val CONFIG_FILE = "theme.json"
    private const val DIR_NAME = "theme_packs"
    private val baseDir get() = File(appCtx.filesDir, DIR_NAME)

    private val PACK_GSON: Gson by lazy {
        GsonBuilder()
            .registerTypeAdapter(ThemePack::class.java, ThemePackSerializer())
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create()
    }

    private val _packList = mutableListOf<ThemePack>()
    val packList: List<ThemePack> get() = _packList

    init {
        loadAll()
    }

    private fun loadAll() {
        _packList.clear()
        baseDir.mkdirs()
        baseDir.listFiles()?.forEach { dir ->
            if (dir.isDirectory) {
                val configFile = File(dir, CONFIG_FILE)
                if (configFile.exists()) {
                    kotlin.runCatching {
                        val json = configFile.readText()
                        val pack = PACK_GSON.fromJson(json, ThemePack::class.java)
                        if (pack.name.isNotEmpty()) {
                            pack.folderName = dir.name
                            _packList.add(pack)
                        }
                    }
                }
            }
        }
    }

    fun reload() {
        loadAll()
    }

    fun getPackDir(pack: ThemePack): File {
        return File(baseDir, pack.folderName)
    }

    fun savePack(pack: ThemePack) {
        val dir = getPackDir(pack)
        dir.mkdirs()
        val configFile = File(dir, CONFIG_FILE)
        val json = PACK_GSON.toJson(pack)
        FileUtils.createFileIfNotExist(configFile.absolutePath).writeText(json)
    }

    fun deletePack(pack: ThemePack) {
        val dir = getPackDir(pack)
        if (dir.exists()) {
            dir.deleteRecursively()
        }
        _packList.remove(pack)
    }

    fun createFromCurrent(name: String): ThemePack {
        val folderName = System.currentTimeMillis().toString()
        val pack = ThemePack(
            name = name,
            folderName = folderName,
            md3Primary = ThemeConfig.cMD3Primary,
            md3OnPrimary = ThemeConfig.cMD3OnPrimary,
            md3PrimaryContainer = ThemeConfig.cMD3PrimaryContainer,
            md3OnPrimaryContainer = ThemeConfig.cMD3OnPrimaryContainer,
            md3Secondary = ThemeConfig.cMD3Secondary,
            md3OnSecondary = ThemeConfig.cMD3OnSecondary,
            md3SecondaryContainer = ThemeConfig.cMD3SecondaryContainer,
            md3Tertiary = ThemeConfig.cMD3Tertiary,
            md3Error = ThemeConfig.cMD3Error,
            md3Surface = ThemeConfig.cMD3Surface,
            md3OnSurface = ThemeConfig.cMD3OnSurface,
            md3Background = ThemeConfig.cMD3Background,
            md3Outline = ThemeConfig.cMD3Outline,
            md3SurfaceContainerLow = ThemeConfig.cMD3SurfaceContainerLow,
            md3SurfaceVariant = ThemeConfig.cMD3SurfaceVariant,
            topBarColor = ThemeConfig.cTopBarColor,
            navBarColor = ThemeConfig.cNavBarColor,
            fontColor = ThemeConfig.cFontColor,
            bgColor = ThemeConfig.cBgColor,
            bookInfoInputColor = ThemeConfig.cBookInfoInputColor,
            appFontPath = ThemeConfig.appFontPath,
            enableContainerBorder = ThemeConfig.enableContainerBorder,
            containerBorderWidth = ThemeConfig.containerBorderWidth,
            containerBorderStyle = ThemeConfig.containerBorderStyle,
            containerBorderDashWidth = ThemeConfig.containerBorderDashWidth,
            containerBorderColor = ThemeConfig.containerBorderColor,
            enableItemDivider = ThemeConfig.enableItemDivider,
            itemDividerWidth = ThemeConfig.itemDividerWidth,
            itemDividerLength = ThemeConfig.itemDividerLength,
            itemDividerColor = ThemeConfig.itemDividerColor,
            enableCustomTagColors = ThemeConfig.enableCustomTagColors,
            customTagColorsJson = ThemeConfig.customTagColorsJson,
            enableBlur = ThemeConfig.enableBlur,
            topBarBlurRadius = ThemeConfig.topBarBlurRadius,
            bottomBarBlurRadius = ThemeConfig.bottomBarBlurRadius,
            topBarBlurAlpha = ThemeConfig.topBarBlurAlpha,
            bottomBarBlurAlpha = ThemeConfig.bottomBarBlurAlpha,
            bgImageLight = ThemeConfig.bgImageLight,
            bgImageDark = ThemeConfig.bgImageDark,
            bgImageBlurring = ThemeConfig.bgImageBlurring,
            bgImageNBlurring = ThemeConfig.bgImageNBlurring,
            navIconBookshelf = MainConfig.navIconBookshelf,
            navIconExplore = MainConfig.navIconExplore,
            navIconRss = MainConfig.navIconRss,
            navIconMy = MainConfig.navIconMy,
            readBgStr = ReadBookConfig.durConfig.bgStr,
            readBgStrNight = ReadBookConfig.durConfig.bgStrNight,
            readBgStrEInk = ReadBookConfig.durConfig.bgStrEInk,
            readBgType = ReadBookConfig.durConfig.bgType,
            readBgTypeNight = ReadBookConfig.durConfig.bgTypeNight,
            readBgTypeEInk = ReadBookConfig.durConfig.bgTypeEInk,
            coverTextColor = CoverConfig.coverTextColor,
            coverShadowColor = CoverConfig.coverShadowColor,
            coverTextColorN = CoverConfig.coverTextColorN,
            coverShadowColorN = CoverConfig.coverShadowColorN,
            coverShowName = CoverConfig.coverShowName,
            coverShowAuthor = CoverConfig.coverShowAuthor,
            coverShowNameN = CoverConfig.coverShowNameN,
            coverShowAuthorN = CoverConfig.coverShowAuthorN,
            coverDefaultColor = CoverConfig.coverDefaultColor,
            coverShowShadow = CoverConfig.coverShowShadow,
            coverShowStroke = CoverConfig.coverShowStroke,
            coverInfoOrientation = CoverConfig.coverInfoOrientation,
            defaultCover = CoverConfig.defaultCover,
            defaultCoverDark = CoverConfig.defaultCoverDark
        )

        val dir = File(baseDir, folderName)
        dir.mkdirs()

        copyResourceFile(pack.appFontPath, dir, "app_font")
        copyResourceFile(pack.bgImageLight, dir, "bg_light")
        copyResourceFile(pack.bgImageDark, dir, "bg_dark")
        copyResourceFile(pack.navIconBookshelf, dir, "nav_bookshelf")
        copyResourceFile(pack.navIconExplore, dir, "nav_explore")
        copyResourceFile(pack.navIconRss, dir, "nav_rss")
        copyResourceFile(pack.navIconMy, dir, "nav_my")

        copyReadBgImage(pack.readBgType, pack.readBgStr, dir, "read_bg_day")
        copyReadBgImage(pack.readBgTypeNight, pack.readBgStrNight, dir, "read_bg_night")
        copyReadBgImage(pack.readBgTypeEInk, pack.readBgStrEInk, dir, "read_bg_eink")

        copyCoverImages(pack.defaultCover, dir, "cover_day")
        copyCoverImages(pack.defaultCoverDark, dir, "cover_night")

        savePack(pack)
        _packList.add(pack)
        return pack
    }

    fun applyPack(pack: ThemePack) {
        val dir = getPackDir(pack)

        PersonalizationThemeConfig.applyConfig(PersonalizationThemeConfig.Config(
            themeName = "",
            md3Primary = pack.md3Primary,
            md3OnPrimary = pack.md3OnPrimary,
            md3PrimaryContainer = pack.md3PrimaryContainer,
            md3OnPrimaryContainer = pack.md3OnPrimaryContainer,
            md3Secondary = pack.md3Secondary,
            md3OnSecondary = pack.md3OnSecondary,
            md3SecondaryContainer = pack.md3SecondaryContainer,
            md3Tertiary = pack.md3Tertiary,
            md3Error = pack.md3Error,
            md3Surface = pack.md3Surface,
            md3OnSurface = pack.md3OnSurface,
            md3Background = pack.md3Background,
            md3Outline = pack.md3Outline,
            md3SurfaceContainerLow = pack.md3SurfaceContainerLow,
            md3SurfaceVariant = pack.md3SurfaceVariant,
            topBarColor = pack.topBarColor,
            navBarColor = pack.navBarColor,
            fontColor = pack.fontColor,
            bgColor = pack.bgColor,
            bookInfoInputColor = pack.bookInfoInputColor,
            appFontPath = restoreResourcePath(dir, "app_font", pack.appFontPath),
            enableContainerBorder = pack.enableContainerBorder,
            containerBorderWidth = pack.containerBorderWidth,
            containerBorderStyle = pack.containerBorderStyle,
            containerBorderDashWidth = pack.containerBorderDashWidth,
            containerBorderColor = pack.containerBorderColor,
            enableItemDivider = pack.enableItemDivider,
            itemDividerWidth = pack.itemDividerWidth,
            itemDividerLength = pack.itemDividerLength,
            itemDividerColor = pack.itemDividerColor,
            enableCustomTagColors = pack.enableCustomTagColors,
            customTagColorsJson = pack.customTagColorsJson,
            enableBlur = pack.enableBlur,
            topBarBlurRadius = pack.topBarBlurRadius,
            bottomBarBlurRadius = pack.bottomBarBlurRadius,
            topBarBlurAlpha = pack.topBarBlurAlpha,
            bottomBarBlurAlpha = pack.bottomBarBlurAlpha,
            bottomBarLensRadius = pack.bottomBarLensRadius
        ))

        ThemeConfig.bgImageLight = restoreResourcePath(dir, "bg_light", pack.bgImageLight)
        ThemeConfig.bgImageDark = restoreResourcePath(dir, "bg_dark", pack.bgImageDark)
        ThemeConfig.bgImageBlurring = pack.bgImageBlurring
        ThemeConfig.bgImageNBlurring = pack.bgImageNBlurring

        MainConfig.navIconBookshelf = restoreResourcePath(dir, "nav_bookshelf", pack.navIconBookshelf) ?: ""
        MainConfig.navIconExplore = restoreResourcePath(dir, "nav_explore", pack.navIconExplore) ?: ""
        MainConfig.navIconRss = restoreResourcePath(dir, "nav_rss", pack.navIconRss) ?: ""
        MainConfig.navIconMy = restoreResourcePath(dir, "nav_my", pack.navIconMy) ?: ""

        val readConfig = ReadBookConfig.durConfig
        readConfig.bgType = pack.readBgType
        readConfig.bgTypeNight = pack.readBgTypeNight
        readConfig.bgTypeEInk = pack.readBgTypeEInk
        readConfig.bgStr = restoreReadBg(dir, "read_bg_day", pack.readBgType, pack.readBgStr)
        readConfig.bgStrNight = restoreReadBg(dir, "read_bg_night", pack.readBgTypeNight, pack.readBgStrNight)
        readConfig.bgStrEInk = restoreReadBg(dir, "read_bg_eink", pack.readBgTypeEInk, pack.readBgStrEInk)

        CoverConfig.coverTextColor = pack.coverTextColor
        CoverConfig.coverShadowColor = pack.coverShadowColor
        CoverConfig.coverTextColorN = pack.coverTextColorN
        CoverConfig.coverShadowColorN = pack.coverShadowColorN
        CoverConfig.coverShowName = pack.coverShowName
        CoverConfig.coverShowAuthor = pack.coverShowAuthor
        CoverConfig.coverShowNameN = pack.coverShowNameN
        CoverConfig.coverShowAuthorN = pack.coverShowAuthorN
        CoverConfig.coverDefaultColor = pack.coverDefaultColor
        CoverConfig.coverShowShadow = pack.coverShowShadow
        CoverConfig.coverShowStroke = pack.coverShowStroke
        CoverConfig.coverInfoOrientation = pack.coverInfoOrientation
        CoverConfig.defaultCover = restoreCoverImages(dir, "cover_day", pack.defaultCover)
        CoverConfig.defaultCoverDark = restoreCoverImages(dir, "cover_night", pack.defaultCoverDark)
    }

    fun exportToZip(pack: ThemePack, destFile: File) {
        val dir = getPackDir(pack)
        if (!dir.exists()) return
        ZipOutputStream(FileOutputStream(destFile)).use { zos ->
            dir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val entryName = file.relativeTo(dir).path.replace('\\', '/')
                    zos.putNextEntry(ZipEntry(entryName))
                    FileInputStream(file).use { fis ->
                        fis.copyTo(zos)
                    }
                    zos.closeEntry()
                }
            }
        }
    }

    fun importFromZip(zipFile: File): ThemePack? {
        val folderName = System.currentTimeMillis().toString()
        val dir = File(baseDir, folderName)
        dir.mkdirs()

        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(dir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val configFile = File(dir, CONFIG_FILE)
        if (!configFile.exists()) {
            dir.deleteRecursively()
            return null
        }

        return kotlin.runCatching {
            val json = configFile.readText()
            val pack = PACK_GSON.fromJson(json, ThemePack::class.java)
            pack.folderName = folderName
            _packList.add(pack)
            pack
        }.getOrNull()
    }

    private fun copyResourceFile(sourcePath: String?, destDir: File, prefix: String) {
        if (sourcePath.isNullOrEmpty()) return
        val sourceFile = File(sourcePath)
        if (!sourceFile.exists()) return
        val ext = sourceFile.extension.ifEmpty { "dat" }
        val destFile = File(destDir, "$prefix.$ext")
        sourceFile.copyTo(destFile, overwrite = true)
    }

    private fun restoreResourcePath(packDir: File, prefix: String, originalPath: String?): String? {
        if (originalPath.isNullOrEmpty()) return null
        val candidates = packDir.listFiles { file ->
            file.name.startsWith(prefix) && file.isFile
        }
        if (candidates.isNullOrEmpty()) return null
        val sourceFile = candidates.first()
        val targetDir = File(appCtx.filesDir, "theme_pack_resources")
        targetDir.mkdirs()
        val targetFile = File(targetDir, "${System.currentTimeMillis()}_${sourceFile.name}")
        sourceFile.copyTo(targetFile, overwrite = true)
        return targetFile.absolutePath
    }

    private fun restoreReadBg(packDir: File, prefix: String, bgType: Int, originalBgStr: String): String {
        if (originalBgStr.isEmpty()) return originalBgStr
        val candidates = packDir.listFiles { file ->
            file.name.startsWith(prefix) && file.isFile
        }
        if (candidates.isNullOrEmpty()) return originalBgStr
        val sourceFile = candidates.first()
        val bgName = sourceFile.name
        val bgDir = File(appCtx.externalFiles, "bg")
        bgDir.mkdirs()
        val targetFile = File(bgDir, bgName)
        if (!targetFile.exists()) {
            sourceFile.copyTo(targetFile, overwrite = true)
        }
        return bgName
    }

    private fun resolveReadBgPath(bgStr: String): String? {
        if (bgStr.isEmpty()) return null
        return if (bgStr.contains(File.separator)) {
            bgStr
        } else {
            FileUtils.getPath(appCtx.externalFiles, "bg", bgStr)
        }
    }

    private fun copyReadBgImage(bgType: Int, bgStr: String, destDir: File, prefix: String) {
        if (bgStr.isEmpty()) return
        when (bgType) {
            1 -> {
                kotlin.runCatching {
                    val ext = bgStr.substringAfterLast(".", "jpg")
                    val destFile = File(destDir, "$prefix.$ext")
                    appCtx.assets.open("bg${File.separator}$bgStr").use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
            2 -> {
                val bgPath = resolveReadBgPath(bgStr)
                copyResourceFile(bgPath, destDir, prefix)
            }
        }
    }

    private fun copyCoverImages(coverPaths: String, destDir: File, prefix: String) {
        val paths = coverPaths.split(",").filter { it.isNotBlank() }
        paths.forEachIndexed { index, path ->
            copyResourceFile(path, destDir, "${prefix}_$index")
        }
    }

    private fun restoreCoverImages(packDir: File, prefix: String, originalPaths: String): String {
        if (originalPaths.isEmpty()) return ""
        val originalList = originalPaths.split(",").filter { it.isNotBlank() }
        val candidates = packDir.listFiles { file ->
            file.name.startsWith(prefix + "_") && file.isFile
        }
        if (candidates.isNullOrEmpty()) return originalPaths
        val sortedCandidates = candidates.sortedBy { file ->
            file.name.substringAfterLast("_").substringBefore(".").toIntOrNull() ?: 0
        }
        val coverDir = File(appCtx.externalFiles, "covers")
        coverDir.mkdirs()
        val restoredPaths = mutableListOf<String>()
        sortedCandidates.forEach { sourceFile ->
            val targetFile = File(coverDir, sourceFile.name)
            if (!targetFile.exists()) {
                sourceFile.copyTo(targetFile, overwrite = true)
            }
            restoredPaths.add(targetFile.absolutePath)
        }
        return if (restoredPaths.isEmpty()) originalPaths else restoredPaths.joinToString(",")
    }

    data class ThemePack(
        var name: String = "",
        @Transient
        var folderName: String = "",
        var md3Primary: Int = 0,
        var md3OnPrimary: Int = 0,
        var md3PrimaryContainer: Int = 0,
        var md3OnPrimaryContainer: Int = 0,
        var md3Secondary: Int = 0,
        var md3OnSecondary: Int = 0,
        var md3SecondaryContainer: Int = 0,
        var md3Tertiary: Int = 0,
        var md3Error: Int = 0,
        var md3Surface: Int = 0,
        var md3OnSurface: Int = 0,
        var md3Background: Int = 0,
        var md3Outline: Int = 0,
        var md3SurfaceContainerLow: Int = 0,
        var md3SurfaceVariant: Int = 0,
        var topBarColor: Int = 0,
        var navBarColor: Int = 0,
        var fontColor: Int = 0,
        var bgColor: Int = 0,
        var bookInfoInputColor: Int = 0,
        var appFontPath: String? = null,
        var enableContainerBorder: Boolean = false,
        var containerBorderWidth: Float = 1f,
        var containerBorderStyle: String = "solid",
        var containerBorderDashWidth: Float = 4f,
        var containerBorderColor: Int = 0,
        var enableItemDivider: Boolean = true,
        var itemDividerWidth: Float = 1f,
        var itemDividerLength: Float = 80f,
        var itemDividerColor: Int = 0,
        var enableCustomTagColors: Boolean = false,
        var customTagColorsJson: String? = null,
        var enableBlur: Boolean = false,
        var topBarBlurRadius: Int = 24,
        var bottomBarBlurRadius: Int = 8,
        var topBarBlurAlpha: Int = 73,
        var bottomBarBlurAlpha: Int = 40,
        var bottomBarLensRadius: Float = 24f,
        var bgImageLight: String? = null,
        var bgImageDark: String? = null,
        var bgImageBlurring: Int = 0,
        var bgImageNBlurring: Int = 0,
        var navIconBookshelf: String = "",
        var navIconExplore: String = "",
        var navIconRss: String = "",
        var navIconMy: String = "",
        var readBgStr: String = "#EEEEEE",
        var readBgStrNight: String = "#000000",
        var readBgStrEInk: String = "#FFFFFF",
        var readBgType: Int = 0,
        var readBgTypeNight: Int = 0,
        var readBgTypeEInk: Int = 0,
        var coverTextColor: Int = -16777216,
        var coverShadowColor: Int = -16777216,
        var coverTextColorN: Int = -1,
        var coverShadowColorN: Int = -1,
        var coverShowName: Boolean = true,
        var coverShowAuthor: Boolean = true,
        var coverShowNameN: Boolean = true,
        var coverShowAuthorN: Boolean = true,
        var coverDefaultColor: Boolean = true,
        var coverShowShadow: Boolean = false,
        var coverShowStroke: Boolean = true,
        var coverInfoOrientation: String = "0",
        var defaultCover: String = "",
        var defaultCoverDark: String = ""
    )
}

class ThemePackSerializer : JsonSerializer<ThemePackConfig.ThemePack>,
    JsonDeserializer<ThemePackConfig.ThemePack> {

    private val COLOR_FIELDS = setOf(
        "md3Primary", "md3OnPrimary", "md3PrimaryContainer", "md3OnPrimaryContainer",
        "md3Secondary", "md3OnSecondary", "md3SecondaryContainer",
        "md3Tertiary", "md3Error", "md3Surface", "md3OnSurface",
        "md3Background", "md3Outline", "md3SurfaceContainerLow", "md3SurfaceVariant",
        "topBarColor", "navBarColor", "fontColor", "bgColor", "bookInfoInputColor",
        "containerBorderColor", "itemDividerColor"
    )

    override fun serialize(
        src: ThemePackConfig.ThemePack,
        typeOfSrc: Type,
        context: JsonSerializationContext
    ): JsonElement {
        val obj = JsonObject()
        obj.addProperty("name", src.name)
        obj.addProperty("md3Primary", src.md3Primary.toHexString())
        obj.addProperty("md3OnPrimary", src.md3OnPrimary.toHexString())
        obj.addProperty("md3PrimaryContainer", src.md3PrimaryContainer.toHexString())
        obj.addProperty("md3OnPrimaryContainer", src.md3OnPrimaryContainer.toHexString())
        obj.addProperty("md3Secondary", src.md3Secondary.toHexString())
        obj.addProperty("md3OnSecondary", src.md3OnSecondary.toHexString())
        obj.addProperty("md3SecondaryContainer", src.md3SecondaryContainer.toHexString())
        obj.addProperty("md3Tertiary", src.md3Tertiary.toHexString())
        obj.addProperty("md3Error", src.md3Error.toHexString())
        obj.addProperty("md3Surface", src.md3Surface.toHexString())
        obj.addProperty("md3OnSurface", src.md3OnSurface.toHexString())
        obj.addProperty("md3Background", src.md3Background.toHexString())
        obj.addProperty("md3Outline", src.md3Outline.toHexString())
        obj.addProperty("md3SurfaceContainerLow", src.md3SurfaceContainerLow.toHexString())
        obj.addProperty("md3SurfaceVariant", src.md3SurfaceVariant.toHexString())
        obj.addProperty("topBarColor", src.topBarColor.toHexString())
        obj.addProperty("navBarColor", src.navBarColor.toHexString())
        obj.addProperty("fontColor", src.fontColor.toHexString())
        obj.addProperty("bgColor", src.bgColor.toHexString())
        obj.addProperty("bookInfoInputColor", src.bookInfoInputColor.toHexString())
        obj.addProperty("appFontPath", src.appFontPath)
        obj.addProperty("enableContainerBorder", src.enableContainerBorder)
        obj.addProperty("containerBorderWidth", src.containerBorderWidth)
        obj.addProperty("containerBorderStyle", src.containerBorderStyle)
        obj.addProperty("containerBorderDashWidth", src.containerBorderDashWidth)
        obj.addProperty("containerBorderColor", src.containerBorderColor.toHexString())
        obj.addProperty("enableItemDivider", src.enableItemDivider)
        obj.addProperty("itemDividerWidth", src.itemDividerWidth)
        obj.addProperty("itemDividerLength", src.itemDividerLength)
        obj.addProperty("itemDividerColor", src.itemDividerColor.toHexString())
        obj.addProperty("enableCustomTagColors", src.enableCustomTagColors)
        obj.addProperty("customTagColorsJson", src.customTagColorsJson)
        obj.addProperty("enableBlur", src.enableBlur)
        obj.addProperty("topBarBlurRadius", src.topBarBlurRadius)
        obj.addProperty("bottomBarBlurRadius", src.bottomBarBlurRadius)
        obj.addProperty("topBarBlurAlpha", src.topBarBlurAlpha)
        obj.addProperty("bottomBarBlurAlpha", src.bottomBarBlurAlpha)
        obj.addProperty("bottomBarLensRadius", src.bottomBarLensRadius)
        obj.addProperty("bgImageLight", src.bgImageLight)
        obj.addProperty("bgImageDark", src.bgImageDark)
        obj.addProperty("bgImageBlurring", src.bgImageBlurring)
        obj.addProperty("bgImageNBlurring", src.bgImageNBlurring)
        obj.addProperty("navIconBookshelf", src.navIconBookshelf)
        obj.addProperty("navIconExplore", src.navIconExplore)
        obj.addProperty("navIconRss", src.navIconRss)
        obj.addProperty("navIconMy", src.navIconMy)
        obj.addProperty("readBgStr", src.readBgStr)
        obj.addProperty("readBgStrNight", src.readBgStrNight)
        obj.addProperty("readBgStrEInk", src.readBgStrEInk)
        obj.addProperty("readBgType", src.readBgType)
        obj.addProperty("readBgTypeNight", src.readBgTypeNight)
        obj.addProperty("readBgTypeEInk", src.readBgTypeEInk)
        obj.addProperty("coverTextColor", src.coverTextColor.toHexString())
        obj.addProperty("coverShadowColor", src.coverShadowColor.toHexString())
        obj.addProperty("coverTextColorN", src.coverTextColorN.toHexString())
        obj.addProperty("coverShadowColorN", src.coverShadowColorN.toHexString())
        obj.addProperty("coverShowName", src.coverShowName)
        obj.addProperty("coverShowAuthor", src.coverShowAuthor)
        obj.addProperty("coverShowNameN", src.coverShowNameN)
        obj.addProperty("coverShowAuthorN", src.coverShowAuthorN)
        obj.addProperty("coverDefaultColor", src.coverDefaultColor)
        obj.addProperty("coverShowShadow", src.coverShowShadow)
        obj.addProperty("coverShowStroke", src.coverShowStroke)
        obj.addProperty("coverInfoOrientation", src.coverInfoOrientation)
        obj.addProperty("defaultCover", src.defaultCover)
        obj.addProperty("defaultCoverDark", src.defaultCoverDark)
        return obj
    }

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): ThemePackConfig.ThemePack {
        val obj = json.asJsonObject
        return ThemePackConfig.ThemePack(
            name = obj.getString("name", "") ?: "",
            md3Primary = obj.getInt("md3Primary", 0),
            md3OnPrimary = obj.getInt("md3OnPrimary", 0),
            md3PrimaryContainer = obj.getInt("md3PrimaryContainer", 0),
            md3OnPrimaryContainer = obj.getInt("md3OnPrimaryContainer", 0),
            md3Secondary = obj.getInt("md3Secondary", 0),
            md3OnSecondary = obj.getInt("md3OnSecondary", 0),
            md3SecondaryContainer = obj.getInt("md3SecondaryContainer", 0),
            md3Tertiary = obj.getInt("md3Tertiary", 0),
            md3Error = obj.getInt("md3Error", 0),
            md3Surface = obj.getInt("md3Surface", 0),
            md3OnSurface = obj.getInt("md3OnSurface", 0),
            md3Background = obj.getInt("md3Background", 0),
            md3Outline = obj.getInt("md3Outline", 0),
            md3SurfaceContainerLow = obj.getInt("md3SurfaceContainerLow", 0),
            md3SurfaceVariant = obj.getInt("md3SurfaceVariant", 0),
            topBarColor = obj.getInt("topBarColor", 0),
            navBarColor = obj.getInt("navBarColor", 0),
            fontColor = obj.getInt("fontColor", 0),
            bgColor = obj.getInt("bgColor", 0),
            bookInfoInputColor = obj.getInt("bookInfoInputColor", 0),
            appFontPath = obj.getString("appFontPath", null),
            enableContainerBorder = obj.getBoolean("enableContainerBorder", false) ?: false,
            containerBorderWidth = obj.getFloat("containerBorderWidth", 1f),
            containerBorderStyle = obj.getString("containerBorderStyle", "solid") ?: "solid",
            containerBorderDashWidth = obj.getFloat("containerBorderDashWidth", 4f),
            containerBorderColor = obj.getInt("containerBorderColor", 0),
            enableItemDivider = obj.getBoolean("enableItemDivider", true) ?: true,
            itemDividerWidth = obj.getFloat("itemDividerWidth", 1f),
            itemDividerLength = obj.getFloat("itemDividerLength", 80f),
            itemDividerColor = obj.getInt("itemDividerColor", 0),
            enableCustomTagColors = obj.getBoolean("enableCustomTagColors", false) ?: false,
            customTagColorsJson = obj.getString("customTagColorsJson", null),
            enableBlur = obj.getBoolean("enableBlur", false) ?: false,
            topBarBlurRadius = obj.getInt("topBarBlurRadius", 24),
            bottomBarBlurRadius = obj.getInt("bottomBarBlurRadius", 8),
            topBarBlurAlpha = obj.getInt("topBarBlurAlpha", 73),
            bottomBarBlurAlpha = obj.getInt("bottomBarBlurAlpha", 40),
            bottomBarLensRadius = obj.getFloat("bottomBarLensRadius", 24f),
            bgImageLight = obj.getString("bgImageLight", null),
            bgImageDark = obj.getString("bgImageDark", null),
            bgImageBlurring = obj.getInt("bgImageBlurring", 0),
            bgImageNBlurring = obj.getInt("bgImageNBlurring", 0),
            navIconBookshelf = obj.getString("navIconBookshelf", "") ?: "",
            navIconExplore = obj.getString("navIconExplore", "") ?: "",
            navIconRss = obj.getString("navIconRss", "") ?: "",
            navIconMy = obj.getString("navIconMy", "") ?: "",
            readBgStr = obj.getString("readBgStr", "#EEEEEE") ?: "#EEEEEE",
            readBgStrNight = obj.getString("readBgStrNight", "#000000") ?: "#000000",
            readBgStrEInk = obj.getString("readBgStrEInk", "#FFFFFF") ?: "#FFFFFF",
            readBgType = obj.getInt("readBgType", 0),
            readBgTypeNight = obj.getInt("readBgTypeNight", 0),
            readBgTypeEInk = obj.getInt("readBgTypeEInk", 0),
            coverTextColor = obj.getInt("coverTextColor", -16777216),
            coverShadowColor = obj.getInt("coverShadowColor", -16777216),
            coverTextColorN = obj.getInt("coverTextColorN", -1),
            coverShadowColorN = obj.getInt("coverShadowColorN", -1),
            coverShowName = obj.getBoolean("coverShowName", true) ?: true,
            coverShowAuthor = obj.getBoolean("coverShowAuthor", true) ?: true,
            coverShowNameN = obj.getBoolean("coverShowNameN", true) ?: true,
            coverShowAuthorN = obj.getBoolean("coverShowAuthorN", true) ?: true,
            coverDefaultColor = obj.getBoolean("coverDefaultColor", true) ?: true,
            coverShowShadow = obj.getBoolean("coverShowShadow", false) ?: false,
            coverShowStroke = obj.getBoolean("coverShowStroke", true) ?: true,
            coverInfoOrientation = obj.getString("coverInfoOrientation", "0") ?: "0",
            defaultCover = obj.getString("defaultCover", "") ?: "",
            defaultCoverDark = obj.getString("defaultCoverDark", "") ?: ""
        )
    }

    private fun Int.toHexString(): String = "#${String.format("%08X", this)}"

    private fun JsonObject.getString(name: String, default: String?): String? {
        val elem = get(name)
        return when {
            elem == null || elem.isJsonNull -> default
            elem.isJsonPrimitive -> {
                val prim = elem.asJsonPrimitive
                if (prim.isString) prim.asString
                else if (prim.isNumber) prim.asString
                else default
            }
            else -> default
        }
    }

    private fun JsonObject.getInt(name: String, default: Int): Int {
        val elem = get(name)
        return when {
            elem == null || elem.isJsonNull -> default
            elem.isJsonPrimitive -> {
                val prim = elem.asJsonPrimitive
                if (prim.isNumber) prim.asNumber.toInt()
                else if (prim.isString) parseColorString(prim.asString)
                else default
            }
            else -> default
        }
    }

    private fun JsonObject.getFloat(name: String, default: Float): Float {
        val elem = get(name)
        return when {
            elem == null || elem.isJsonNull -> default
            elem.isJsonPrimitive -> {
                val prim = elem.asJsonPrimitive
                if (prim.isNumber) prim.asNumber.toFloat()
                else default
            }
            else -> default
        }
    }

    private fun JsonObject.getBoolean(name: String, default: Boolean?): Boolean? {
        val elem = get(name)
        return when {
            elem == null || elem.isJsonNull -> default
            elem.isJsonPrimitive -> {
                val prim = elem.asJsonPrimitive
                if (prim.isBoolean) prim.asBoolean
                else default
            }
            else -> default
        }
    }

    private fun parseColorString(colorStr: String): Int {
        return kotlin.runCatching {
            android.graphics.Color.parseColor(colorStr)
        }.getOrDefault(0)
    }
}
