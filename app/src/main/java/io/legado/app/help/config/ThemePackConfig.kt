package io.legado.app.help.config

import android.net.Uri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import io.legado.app.ui.config.coverConfig.CoverConfig
import io.legado.app.ui.config.mainConfig.MainConfig
import io.legado.app.ui.config.themeConfig.ThemeConfig
import io.legado.app.utils.FileUtils
import io.legado.app.utils.externalFiles
import splitties.init.appCtx
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
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
            appearance = ThemeAppearanceConfig.fromCurrent(),
            bgImageLight = ThemeConfig.bgImageLight,
            bgImageDark = ThemeConfig.bgImageDark,
            bgImageBlurring = ThemeConfig.bgImageBlurring,
            bgImageNBlurring = ThemeConfig.bgImageNBlurring,
            navIconBookshelf = MainConfig.navIconBookshelf,
            navIconExplore = MainConfig.navIconExplore,
            navIconRss = MainConfig.navIconRss,
            navIconMy = MainConfig.navIconMy,
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

        copyResourceFile(pack.appearance.appFontPath, dir, "app_font")
        copyResourceFile(pack.bgImageLight, dir, "bg_light")
        copyResourceFile(pack.bgImageDark, dir, "bg_dark")
        copyResourceFile(pack.navIconBookshelf, dir, "nav_bookshelf")
        copyResourceFile(pack.navIconExplore, dir, "nav_explore")
        copyResourceFile(pack.navIconRss, dir, "nav_rss")
        copyResourceFile(pack.navIconMy, dir, "nav_my")

        copyCoverImages(pack.defaultCover, dir, "cover_day")
        copyCoverImages(pack.defaultCoverDark, dir, "cover_night")

        savePack(pack)
        _packList.add(pack)
        return pack
    }

    fun applyPack(pack: ThemePack) {
        val dir = getPackDir(pack)
        val restoredAppearance = pack.appearance.copy(
            appFontPath = restoreResourcePath(dir, "app_font", pack.appearance.appFontPath)
        )
        restoredAppearance.applyToThemeConfig()

        ThemeConfig.bgImageLight = restoreResourcePath(dir, "bg_light", pack.bgImageLight)
        ThemeConfig.bgImageDark = restoreResourcePath(dir, "bg_dark", pack.bgImageDark)
        ThemeConfig.bgImageBlurring = pack.bgImageBlurring
        ThemeConfig.bgImageNBlurring = pack.bgImageNBlurring

        MainConfig.navIconBookshelf = restoreResourcePath(dir, "nav_bookshelf", pack.navIconBookshelf) ?: ""
        MainConfig.navIconExplore = restoreResourcePath(dir, "nav_explore", pack.navIconExplore) ?: ""
        MainConfig.navIconRss = restoreResourcePath(dir, "nav_rss", pack.navIconRss) ?: ""
        MainConfig.navIconMy = restoreResourcePath(dir, "nav_my", pack.navIconMy) ?: ""

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

        return kotlin.runCatching {
            ZipInputStream(FileInputStream(zipFile)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val outFile = resolveZipOutputFile(dir, entry)
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

            val json = configFile.readText()
            val pack = PACK_GSON.fromJson(json, ThemePack::class.java)
            pack.folderName = folderName
            _packList.add(pack)
            pack
        }.onFailure {
            dir.deleteRecursively()
        }.getOrNull()
    }

    private fun resolveZipOutputFile(dir: File, entry: ZipEntry): File {
        val outFile = File(dir, entry.name).canonicalFile
        val canonicalDir = dir.canonicalFile
        if (outFile.path == canonicalDir.path ||
            outFile.path.startsWith(canonicalDir.path + File.separator)
        ) {
            return outFile
        }
        throw IllegalArgumentException("Invalid theme pack entry: ${entry.name}")
    }

    private fun copyResourceFile(sourcePath: String?, destDir: File, prefix: String) {
        if (sourcePath.isNullOrEmpty()) return
        val sourceUri = Uri.parse(sourcePath)
        val sourceName = sourceUri.lastPathSegment ?: sourcePath
        val ext = sourceName.substringAfterLast(".", "dat").ifBlank { "dat" }
        val destFile = File(destDir, "$prefix.$ext")

        if (sourceUri.scheme == "content") {
            appCtx.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            return
        }

        val sourceFile = File(sourcePath)
        if (!sourceFile.exists()) return
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

    private fun copyCoverImages(coverPaths: String, destDir: File, prefix: String) {
        val paths = coverPaths.split(",").filter { it.isNotBlank() }
        paths.forEachIndexed { index, path ->
            copyResourceFile(path, destDir, "${prefix}_$index")
        }
    }

    private fun restoreCoverImages(packDir: File, prefix: String, originalPaths: String): String {
        if (originalPaths.isEmpty()) return ""
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
            sourceFile.copyTo(targetFile, overwrite = true)
            restoredPaths.add(targetFile.absolutePath)
        }
        return if (restoredPaths.isEmpty()) originalPaths else restoredPaths.joinToString(",")
    }

    data class ThemePack(
        var name: String = "",
        @Transient
        var folderName: String = "",
        var appearance: ThemeAppearanceConfig = ThemeAppearanceConfig(),
        var bgImageLight: String? = null,
        var bgImageDark: String? = null,
        var bgImageBlurring: Int = 0,
        var bgImageNBlurring: Int = 0,
        var navIconBookshelf: String = "",
        var navIconExplore: String = "",
        var navIconRss: String = "",
        var navIconMy: String = "",
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
    ) {
        val themeColor: Int get() = appearance.themeColor
        val appFontPath: String? get() = appearance.appFontPath
    }
}

