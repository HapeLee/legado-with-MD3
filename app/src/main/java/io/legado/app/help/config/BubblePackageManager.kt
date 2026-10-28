package io.legado.app.help.config

import androidx.annotation.Keep
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.compress.ZipUtils
import io.legado.app.utils.externalFiles
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getFile
import io.legado.app.utils.normalizeFileName
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * 段评气泡包：`bubblePackages/<dirName>/bubble.json` 描述一套气泡外观。
 *
 * 包里有 SVG 模板（`${num}` / `${color}` 占位）、尺寸缩放、日间/夜间 × 普通/强调四色，
 * 以及可被模板引用的 `asset://` 字体或图片资源。
 */
object BubblePackageManager {

    const val BUILTIN_DIR_NAME = "builtin_default"
    const val DEFAULT_EMPHASIS_COLOR = "#FF0000"
    const val DEFAULT_NORMAL_COLOR = "#808080"
    const val MIN_SIZE_SCALE = 0.5f
    const val MAX_SIZE_SCALE = 1.5f
    private const val packageFileName = "bubble.json"
    private const val defaultBubblePath =
        "M44 48 Q48 48 48 44 L48 20 Q48 16 44 16 L20 16 Q16 16 16 20 L16 24 S16 28 10 30 Q6 32 10 34 Q16 36 16 38 L16 44 Q16 48 20 48 Z"

    @Volatile
    private var cachedEntry: Entry? = null

    @Volatile
    private var cachedDirName: String? = null

    private val importMutex = Mutex()

    val rootDir: File
        get() = appCtx.externalFiles.getFile("bubblePackages")

    private val tempDir: File
        get() = rootDir.getFile("temp").apply { mkdirs() }

    @Keep
    data class Config(
        var name: String,
        var dirName: String = "",
        var svgTemplate: String = "",
        var sizeScale: Float = 1f,
        var dayNormalColor: String? = null,
        var dayEmphasisColor: String? = null,
        var nightNormalColor: String? = null,
        var nightEmphasisColor: String? = null,
        var updatedAt: Long = System.currentTimeMillis(),
        var formatVersion: Int = 1,
        var resources: List<PackageResource> = emptyList()
    )

    data class Entry(
        val config: Config,
        val source: Source,
        val dirName: String,
        val localDir: File? = null
    )

    enum class Source { BUILTIN, LOCAL }

    fun builtinConfig(): Config {
        return Config(
            name = "内置段评气泡",
            dirName = BUILTIN_DIR_NAME,
            svgTemplate = defaultSvgTemplate(),
            sizeScale = 1f,
            dayNormalColor = DEFAULT_NORMAL_COLOR,
            dayEmphasisColor = DEFAULT_EMPHASIS_COLOR,
            nightNormalColor = DEFAULT_NORMAL_COLOR,
            nightEmphasisColor = DEFAULT_EMPHASIS_COLOR,
            updatedAt = 0L
        )
    }

    fun builtinEntry(): Entry {
        return Entry(
            config = builtinConfig(),
            source = Source.BUILTIN,
            dirName = BUILTIN_DIR_NAME
        )
    }

    fun activeDirName(): String {
        return ReadBookConfig.reviewBubblePackage.ifBlank { BUILTIN_DIR_NAME }
    }

    fun currentEntry(): Entry {
        val dirName = activeDirName()
        cachedEntry?.takeIf { cachedDirName == dirName }?.let { return it }
        val entry = if (dirName == BUILTIN_DIR_NAME) {
            builtinEntry()
        } else {
            runCatching { readEntry(localDir(dirName)) }.getOrNull() ?: builtinEntry()
        }
        cachedDirName = dirName
        cachedEntry = entry
        return entry
    }

    fun invalidateCurrentEntry() {
        cachedEntry = null
        cachedDirName = null
    }

    /** 按目录名取包；内置包不会落在磁盘上。IO 线程调用。 */
    fun entryOf(dirName: String): Entry? {
        if (dirName.isBlank() || dirName == BUILTIN_DIR_NAME) return builtinEntry()
        return runCatching { readEntry(localDir(dirName)) }.getOrNull()
    }

    /** 备份：把本地包目录（除 temp 与隐藏目录）复制进备份目录。 */
    fun copyToBackup(destination: File) {
        if (!rootDir.exists()) return
        check(destination.mkdirs() || destination.isDirectory) { "failed to create backup directory" }
        rootDir.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith('.') && it.name != "temp" }
            ?.forEach { dir ->
                runCatching { dir.copyRecursively(File(destination, dir.name), overwrite = true) }
            }
    }

    /** 恢复：把备份里的包合并回本地；校验不通过的包不保留。 */
    fun restoreFromBackup(source: File) {
        if (!source.isDirectory) return
        val parent = rootDir.apply { mkdirs() }.canonicalFile
        source.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith('.') }
            ?.forEach { dir ->
                val target = File(parent, dir.name).canonicalFile
                if (target.parentFile != parent) return@forEach
                runCatching {
                    dir.copyRecursively(target, overwrite = true)
                    requireNotNull(readEntry(target)) { "bubble package manifest is invalid" }
                }.onFailure {
                    deletePathBestEffort(target)
                }
            }
        invalidateCurrentEntry()
    }

    suspend fun loadEntries(): List<Entry> = withContext(IO) {
        listOf(builtinEntry()) + loadLocal().sortedWith(
            compareByDescending<Entry> { it.config.updatedAt }
                .thenBy { it.config.name }
                .thenBy { it.dirName }
        )
    }

    fun addOrUpdate(config: Config, oldEntry: Entry? = null): Entry {
        val normalized = normalizeConfig(config)
        val name = normalized.name.trim().ifBlank { "段评气泡" }
        val editableOldEntry = oldEntry?.takeIf {
            it.dirName.isNotBlank() && it.dirName != BUILTIN_DIR_NAME
        }
        val dirName = if (editableOldEntry != null) {
            editableOldEntry.dirName
        } else {
            val baseDirName = normalized.dirName.ifBlank { name.normalizeFileName() }
                .ifBlank { "bubble_${System.currentTimeMillis()}" }
            uniqueDirName(baseDirName)
        }
        val dir = localDir(dirName)
        val createdDirectory = !dir.exists()
        check(dir.mkdirs() || dir.isDirectory) { "failed to create bubble package directory" }
        val next = normalized.copy(
            name = name,
            dirName = dirName,
            updatedAt = System.currentTimeMillis()
        )
        return try {
            val validated = validateImportedConfig(next, dir)
            val manifest = File(dir, packageFileName)
            val json = GSON.toJson(validated)
            AtomicTextFileStore(manifest).writeVerified(json) { storedJson ->
                runCatching {
                    val stored = GSON.fromJsonObject<Config>(storedJson).getOrThrow()
                    validateImportedConfig(stored, dir).dirName == dirName
                }.getOrDefault(false)
            }
            invalidateCurrentEntry()
            Entry(validated, Source.LOCAL, dirName, localDir = dir)
        } catch (error: Throwable) {
            if (createdDirectory) deletePathBestEffort(dir)
            throw error
        }
    }

    fun deleteLocal(entry: Entry) {
        if (entry.source == Source.BUILTIN || entry.dirName == BUILTIN_DIR_NAME) return
        FileUtils.delete(entry.localDir ?: localDir(entry.dirName), deleteRootDir = true)
        invalidateCurrentEntry()
    }

    suspend fun exportZip(entry: Entry): File = withContext(IO) {
        if (entry.source == Source.BUILTIN) {
            throw IllegalArgumentException("内置气泡不可导出")
        }
        val dir = entry.localDir ?: localDir(entry.dirName)
        val zipFile = tempDir.getFile("${entry.dirName}.zip")
        if (zipFile.exists()) zipFile.delete()
        if (!ZipUtils.zipFile(dir.absolutePath, zipFile.absolutePath) ||
            !zipFile.exists() || zipFile.length() <= 0L
        ) {
            throw IllegalStateException("气泡导出失败")
        }
        zipFile
    }

    suspend fun importZip(zipFile: File): Entry = withContext(IO) {
        importMutex.withLock {
            val unzipDir = tempDir.getFile("import_${UUID.randomUUID()}")
            try {
                val extracted = BubblePackageArchive.extract(zipFile, unzipDir)
                val packageFile = extracted.manifestFile
                val config = readImportedConfig(packageFile, packageFile.parentFile)
                val dirName = uniqueDirName(safeImportedDirName(config))
                val parentDir = rootDir.apply { mkdirs() }.canonicalFile
                val targetDir = File(parentDir, dirName).canonicalFile
                require(targetDir.parentFile == parentDir) { "bubble directory escapes package root" }
                val stagingDir = File(parentDir, ".$dirName.staging-${UUID.randomUUID()}")
                val backupDir = File(parentDir, ".$dirName.backup-${UUID.randomUUID()}")
                try {
                    val sourceDir = requireNotNull(packageFile.parentFile)
                    check(sourceDir.copyRecursively(stagingDir, overwrite = false)) {
                        "failed to stage imported bubble package"
                    }
                    val next = config.copy(dirName = dirName, updatedAt = System.currentTimeMillis())
                    File(stagingDir, packageFileName).writeText(GSON.toJson(next))
                    val verified = readImportedConfig(File(stagingDir, packageFileName), stagingDir)
                    require(verified.dirName == dirName) { "staged bubble package identity mismatch" }

                    val installed = BubbleDirectoryTransaction().install(
                        targetDir,
                        stagingDir,
                        backupDir
                    ) { installedDir ->
                        val installedConfig =
                            readImportedConfig(File(installedDir, packageFileName), installedDir)
                        require(installedConfig.dirName == dirName) {
                            "installed bubble package identity mismatch"
                        }
                        Entry(installedConfig, Source.LOCAL, dirName, localDir = installedDir)
                    }
                    invalidateCurrentEntry()
                    installed
                } finally {
                    deletePathBestEffort(stagingDir)
                }
            } finally {
                deletePathBestEffort(unzipDir)
            }
        }
    }

    /** 当前包的外观参数；渲染与测量都走这里，保证占位与绘制一致。 */
    fun currentConfig(): Config = currentEntry().config

    private fun readImportedConfig(file: File, resourceRoot: File? = null): Config {
        require(file.isFile) { "bubble package manifest is missing" }
        require(file.length() in 1..512L * 1024L) { "bubble package manifest is empty or too large" }
        val raw = GSON.fromJsonObject<Config>(file.readText()).getOrThrow()
        return validateImportedConfig(raw, resourceRoot)
    }

    private fun validateImportedConfig(config: Config, resourceRoot: File? = null): Config {
        val normalized = normalizeConfig(config)
        require(normalized.name.length <= 200) { "bubble package name is too long" }
        require(normalized.svgTemplate.length <= 512 * 1024) { "bubble SVG template is too large" }
        BubbleSvgPolicy.validate(normalized.svgTemplate)
        if (resourceRoot == null) return normalized
        val resources = PackageResourcePolicy.validateFiles(resourceRoot, normalized.resources)
        BubbleSvgPolicy.packageReferences(normalized.svgTemplate).forEach { reference ->
            val image = runCatching {
                PackageResourcePolicy.resolve(
                    resourceRoot,
                    resources,
                    reference,
                    PackageResourcePolicy.TYPE_IMAGE
                )
            }.getOrNull()
            val font = if (image == null) runCatching {
                PackageResourcePolicy.resolve(
                    resourceRoot,
                    resources,
                    reference,
                    PackageResourcePolicy.TYPE_FONT
                )
            }.getOrNull() else null
            val file = image ?: font
            requireNotNull(file) { "bubble package resource is missing: $reference" }
            PackageResourcePolicy.validateResolvedFile(
                file,
                if (image != null) PackageResourcePolicy.TYPE_IMAGE else PackageResourcePolicy.TYPE_FONT,
                reference
            )
        }
        val hasPackageResources = resources.isNotEmpty() ||
            BubbleSvgPolicy.packageReferences(normalized.svgTemplate).isNotEmpty()
        return normalized.copy(
            formatVersion = if (hasPackageResources) 2 else normalized.formatVersion.coerceIn(1, 2),
            resources = resources
        )
    }

    private fun safeImportedDirName(config: Config): String {
        val requested = config.dirName.trim()
        if (requested.isNotEmpty()) {
            require(requested != "." && requested != "..") { "invalid bubble directory" }
            require(!requested.startsWith('.')) { "bubble directory must not be hidden" }
            require(!File(requested).isAbsolute && '/' !in requested && '\\' !in requested) {
                "bubble directory must be a single path segment"
            }
        }
        return requested.ifBlank { config.name.normalizeFileName() }
            .ifBlank { "bubble_${System.currentTimeMillis()}" }
    }

    private fun deletePathBestEffort(file: File) {
        runCatching {
            if (file.exists() && !file.deleteRecursively()) {
                throw IOException("failed to delete ${file.absolutePath}")
            }
        }
    }

    private fun loadLocal(): List<Entry> {
        if (!rootDir.exists()) return emptyList()
        return rootDir.listFiles()
            ?.filter {
                it.isDirectory &&
                    !it.name.startsWith('.') &&
                    it.name !in setOf("temp", BUILTIN_DIR_NAME)
            }
            ?.mapNotNull(::readEntry)
            .orEmpty()
    }

    private fun readEntry(dir: File): Entry? {
        val parent = rootDir.apply { mkdirs() }.canonicalFile
        val canonicalDir = dir.canonicalFile
        if (canonicalDir.parentFile != parent || canonicalDir.name.startsWith('.')) return null
        val file = File(canonicalDir, packageFileName)
        return runCatching {
            AtomicTextFileStore(file).recoverInterruptedCommit()
            if (!file.exists()) return@runCatching null
            val config = readImportedConfig(file, canonicalDir)
            val dirName = canonicalDir.name
            Entry(
                config.copy(dirName = dirName),
                Source.LOCAL,
                dirName,
                localDir = canonicalDir
            )
        }.getOrNull()
    }

    private fun normalizeConfig(config: Config): Config {
        val version = config.formatVersion.takeIf { it > 0 } ?: 1
        require(version in 1..2) { "unsupported bubble package version" }
        val size = config.sizeScale.takeIf { it.isFinite() } ?: 1f
        val resources = PackageResourcePolicy.normalize(config.resources.orEmpty())
        return config.copy(
            name = config.name.trim().ifBlank { "段评气泡" },
            svgTemplate = config.svgTemplate.ifBlank { defaultSvgTemplate() },
            formatVersion = version,
            resources = resources,
            sizeScale = size.coerceIn(MIN_SIZE_SCALE, MAX_SIZE_SCALE),
            dayNormalColor = normalizeColorOrBlank(config.dayNormalColor),
            dayEmphasisColor = normalizeColorOrBlank(config.dayEmphasisColor),
            nightNormalColor = normalizeColorOrBlank(config.nightNormalColor),
            nightEmphasisColor = normalizeColorOrBlank(config.nightEmphasisColor)
        )
    }

    private fun normalizeColorOrBlank(value: String?): String? {
        val color = value?.trim().orEmpty()
        if (color.isBlank()) return null
        return if (color.startsWith("#")) color else "#$color"
    }

    private fun uniqueDirName(preferred: String): String {
        val clean = preferred.normalizeFileName().ifBlank { "bubble_${System.currentTimeMillis()}" }
        if (!localDir(clean).exists()) return clean
        for (index in 2..999) {
            val candidate = "${clean}_$index"
            if (!localDir(candidate).exists()) return candidate
        }
        return "${clean}_${System.currentTimeMillis()}"
    }

    private fun localDir(dirName: String): File {
        val parent = rootDir.apply { mkdirs() }.canonicalFile
        val directory = File(parent, dirName).canonicalFile
        require(directory.parentFile == parent && !directory.name.startsWith('.')) {
            "bubble directory escapes package root"
        }
        return directory
    }

    private fun defaultSvgTemplate(): String {
        return """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64">
              <path d="$defaultBubblePath" fill="none" stroke="${'$'}{color}" stroke-width="3.2" stroke-linejoin="round" stroke-linecap="round"/>
              <text x="32" y="32" dy=".35em" text-anchor="middle" font-family="sans-serif" font-size="15" font-weight="600" fill="${'$'}{color}">${'$'}{num}</text>
            </svg>
        """.trimIndent()
    }
}
