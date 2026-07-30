package io.legado.app.data.repository

import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import io.legado.app.constant.AppLog
import io.legado.app.help.DefaultData
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ReadStyleResolver
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.compress.ZipUtils
import io.legado.app.utils.createFolderReplace
import io.legado.app.utils.externalCache
import io.legado.app.utils.externalFiles
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getFile
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.printOnDebug
import splitties.init.appCtx
import java.io.File
import java.io.InputStream

class ReadStyleRepository(
    private val highlightRuleRepository: HighlightRuleRepository,
) {

    val configFilePath: String =
        FileUtils.getPath(appCtx.filesDir, ReadBookConfig.configFileName)
    val shareConfigFilePath: String =
        FileUtils.getPath(appCtx.filesDir, ReadBookConfig.shareConfigFileName)

    fun readConfigs(): List<ReadBookConfig.Config> {
        val configFile = File(configFilePath)
        if (configFile.exists()) {
            try {
                return GSON.fromJsonArray<ReadBookConfig.Config>(configFile.readText()).getOrThrow()
            } catch (e: Exception) {
                AppLog.put("读取排版配置文件出错", e)
            }
        }
        return DefaultData.readConfigs
    }

    fun readShareConfig(fallbackConfig: ReadBookConfig.Config): ReadBookConfig.Config {
        val configFile = File(shareConfigFilePath)
        if (configFile.exists()) {
            try {
                return GSON.fromJsonObject<ReadBookConfig.Config>(configFile.readText()).getOrThrow()
            } catch (e: Exception) {
                e.printOnDebug()
            }
        }
        return fallbackConfig
    }

    fun save(
        configs: List<ReadBookConfig.Config>,
        shareConfig: ReadBookConfig.Config
    ) {
        // 原子写：两条保存通道（配置 store 的兜底保存与 ReadStyleSaveQueue）会并发落到这里，
        // 「删掉再写」既有杀进程丢配置的窗口，也会让两者互相看到对方写了一半的文件。
        FileUtils.writeTextAtomic(configFilePath, GSON.toJson(configs))
        FileUtils.writeTextAtomic(shareConfigFilePath, GSON.toJson(shareConfig))
    }

    fun getAllPicBgStr(configs: List<ReadBookConfig.Config>): ArrayList<String> {
        val list = arrayListOf<String>()
        configs.forEach {
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

    fun clearBgAndCache(configs: List<ReadBookConfig.Config>) {
        val bgs = hashSetOf<String>()
        configs.forEach { config ->
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
        FileUtils.delete(FileUtils.getPath(appCtx.externalCache, "readConfig.zip"))
    }

    fun saveBackgroundImage(inputStream: InputStream, displayName: String?): String {
        val bgDir = appCtx.externalFiles.getFile("bg")
        bgDir.mkdirs()
        val safeName = displayName
            ?.let { File(it).name }
            ?.takeIf { it.isNotBlank() }
            ?: "read_bg.jpg"
        val baseName = File(safeName).nameWithoutExtension.ifBlank { "read_bg" }
        val extension = File(safeName).extension.ifBlank { "jpg" }
        val bgFile = File(bgDir, "${baseName}_${System.currentTimeMillis()}.$extension")
        if (!FileUtils.writeInputStream(bgFile, inputStream)) {
            error("save read background image failed")
        }
        return bgFile.absolutePath
    }

    fun export(config: ReadBookConfig.Config): ByteArray {
        val exportDir = appCtx.externalCache.getFile("readConfigExport")
        exportDir.createFolderReplace()
        var exportConfig = config.copy(
            highlightRules = ArrayList(config.highlightRules.map { it.copy() })
        )
        val exportFiles = arrayListOf<File>()

        exportConfig = addBackgroundFile(exportDir, exportConfig, 0, exportFiles)
        exportConfig = addBackgroundFile(exportDir, exportConfig, 1, exportFiles)
        exportConfig = addBackgroundFile(exportDir, exportConfig, 2, exportFiles)
        exportConfig = exportConfig.copy(
            textFont = addAssetFile(exportDir, exportConfig.textFont, exportFiles),
            titleFont = addAssetFile(exportDir, exportConfig.titleFont, exportFiles),
        )
        HighlightRuleAssetTransfer.prepareExport(
            rules = exportConfig.highlightRules,
            exportDir = exportDir,
            copyAsset = ::copyRuleAsset,
        ).let { result ->
            exportConfig = exportConfig.copy(highlightRules = ArrayList(result.rules))
            exportFiles.addAll(result.files)
        }

        val configFile = exportDir.getFile(ReadBookConfig.configFileName)
        configFile.writeText(GSON.toJson(exportConfig))
        exportFiles.add(configFile)

        val zipFile = appCtx.externalCache.getFile("readConfig.zip")
        FileUtils.delete(zipFile)
        ZipUtils.zipFiles(exportFiles, zipFile)
        return zipFile.readBytes()
    }

    fun import(byteArray: ByteArray): ReadBookConfig.Config {
        val configZipPath = FileUtils.getPath(appCtx.externalCache, "readConfig.zip")
        FileUtils.delete(configZipPath)
        val zipFile = FileUtils.createFileIfNotExist(configZipPath)
        zipFile.writeBytes(byteArray)
        val configDir = appCtx.externalCache.getFile("readConfig")
        configDir.createFolderReplace()
        ZipUtils.unZipToPath(zipFile, configDir)
        val configFile = configDir.getFile(ReadBookConfig.configFileName)
        var config: ReadBookConfig.Config =
            GSON.fromJsonObject<ReadBookConfig.Config>(configFile.readText()).getOrThrow()

        config = config.copy(
            textFont = importFont(configDir, config.textFont),
            titleFont = importFont(configDir, config.titleFont),
            bgStr = importBackground(configDir, config.bgType, config.bgStr),
            bgStrNight = importBackground(configDir, config.bgTypeNight, config.bgStrNight),
            bgStrEInk = importBackground(configDir, config.bgTypeEInk, config.bgStrEInk),
        )

        if (config.highlightRules.isNotEmpty()) {
            val restored = HighlightRuleAssetTransfer.restoreImport(
                rules = config.highlightRules,
                importDir = configDir,
                backgroundDir = File(appCtx.filesDir, "bg_images"),
                fontDir = appCtx.externalFiles.getFile("font"),
                isReadableBackgroundReference = appCtx::isReadableHighlightBackground,
                isReadableFontReference = appCtx::isReadableHighlightFont,
            )
            val targetConfigName = config.name.ifBlank { null }
            val highlightRules = restored.map { rule ->
                if (targetConfigName.isNullOrBlank()) {
                    rule.copy(configName = null)
                } else {
                    rule.copyWithNewId().copy(configName = listOf(targetConfigName).toJsonArray())
                }
            }
            config = config.copy(highlightRules = ArrayList(highlightRules))
            highlightRuleRepository.saveForConfig(highlightRules, targetConfigName)
        }
        // 预热 Config 内部的颜色记忆化缓存，让返回的实例首次绘制时不必再解析一遍字符串。
        // 必须排在最后一次 copy 之后——copy 会把缓存重置回未初始化。
        config.curTextColor()
        config.curTextAccentColor()
        config.curTextShadowColor()
        return config
    }

    /**
     * 背景是图片（type 2）时把它落到 bg 目录并改写成绝对路径；是颜色（type 0）时
     * 只做一次解析校验——解析不了就让导入整体失败，而不是导进一份显示不出来的配置。
     */
    private fun importBackground(configDir: File, bgType: Int, bgStr: String): String {
        if (bgType != 2) {
            if (bgType == 0) {
                bgStr.toColorInt()
            }
            return bgStr
        }
        val bgName = FileUtils.getName(bgStr)
        val bgPath = FileUtils.getPath(appCtx.externalFiles, "bg", bgName)
        if (!FileUtils.exist(bgPath)) {
            val bgFile = configDir.getFile(bgName)
            if (bgFile.exists()) {
                bgFile.copyTo(File(bgPath))
            }
        }
        return bgPath
    }

    private fun addBackgroundFile(
        exportDir: File,
        config: ReadBookConfig.Config,
        bgIndex: Int,
        exportFiles: MutableList<File>
    ): ReadBookConfig.Config {
        val sourcePath = ReadStyleResolver.backgroundPath(config, bgIndex) ?: return config
        val exportedName = addAssetFile(exportDir, sourcePath, exportFiles)
        if (exportedName.isBlank()) {
            return config
        }
        return when (bgIndex) {
            0 -> config.copy(bgStr = exportedName)
            1 -> config.copy(bgStrNight = exportedName)
            2 -> config.copy(bgStrEInk = exportedName)
            else -> config
        }
    }

    private fun addAssetFile(
        exportDir: File,
        sourcePath: String,
        exportFiles: MutableList<File>
    ): String {
        if (sourcePath.isBlank()) {
            return ""
        }
        val source = File(sourcePath)
        if (!source.exists() || !source.isFile) {
            return ""
        }
        val target = exportDir.getFile(source.name)
        source.copyTo(target, overwrite = true)
        if (exportFiles.none { it.absolutePath == target.absolutePath }) {
            exportFiles.add(target)
        }
        return target.name
    }

    private fun copyRuleAsset(sourcePath: String, target: File): Boolean {
        return runCatching {
            target.parentFile?.mkdirs()
            if (sourcePath.isContentScheme()) {
                appCtx.contentResolver.openInputStream(sourcePath.toUri())?.use { input ->
                    target.outputStream().use(input::copyTo)
                } ?: return@runCatching false
            } else {
                val source = File(sourcePath)
                if (!source.isFile) return@runCatching false
                source.copyTo(target, overwrite = true)
            }
            true
        }.getOrDefault(false)
    }

    private fun importFont(configDir: File, fontName: String): String {
        if (fontName.isEmpty()) {
            return ""
        }
        val fontPath = FileUtils.getPath(appCtx.externalFiles, "font", fontName)
        val fontFile = configDir.getFile(fontName)
        return if (fontFile.exists()) {
            if (!FileUtils.exist(fontPath)) {
                fontFile.copyTo(File(fontPath))
            }
            fontPath
        } else {
            ""
        }
    }
}
