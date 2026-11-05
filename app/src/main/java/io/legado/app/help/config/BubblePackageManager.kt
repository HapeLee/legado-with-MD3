package io.legado.app.help.config

import android.net.Uri
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
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/**
 * 段评气泡包管理。
 *
 * 目录契约与上游（Legado-Arc）保持一致，磁盘上已有的气泡包可直接使用：
 * `bubblePackages/<dirName>/bubble.json` + 可选的 `assets/`、`images/` 资源目录。
 */
object BubblePackageManager {

    const val BUILTIN_DIR_NAME = "builtin_default"
    const val DEFAULT_EMPHASIS_COLOR = "#FF0000"
    const val DEFAULT_NORMAL_COLOR = "#808080"
    const val DEFAULT_COLOR = DEFAULT_EMPHASIS_COLOR
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

    val importCacheDir: File
        get() = appCtx.externalFiles.getFile("bubbleImports")

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

    fun activeDirName(): String = BubbleSettings.activeDirName

    suspend fun apply(entry: Entry) {
        BubbleSettings.setActiveDirName(entry.dirName)
        invalidateCurrentEntry()
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
            it.dirName.isNotBlank() &&
                it.dirName != BUILTIN_DIR_NAME &&
                it.source == Source.LOCAL
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

    suspend fun deleteLocal(entry: Entry) {
        if (entry.source == Source.BUILTIN || entry.dirName == BUILTIN_DIR_NAME) return
        FileUtils.delete(entry.localDir ?: localDir(entry.dirName), deleteRootDir = true)
        if (activeDirName() == entry.dirName) {
            BubbleSettings.setActiveDirName(BUILTIN_DIR_NAME)
        }
        invalidateCurrentEntry()
    }

    /** 本地气泡包目录（备份用；已排除内置与 temp/remote_cache）。 */
    suspend fun localPackageDirs(): List<File> = withContext(IO) {
        loadLocal().mapNotNull { it.localDir }
    }

    /**
     * 备份恢复路径：包内容已经在 rootDir 同分区的暂存目录里，跳过解压与解压炸弹校验，
     * 只做清单校验后原子安装。目录名沿用备份里的文件夹名。
     */
    suspend fun installStagedPackage(stagingDir: File): Entry = withContext(IO) {
        importMutex.withLock {
            val dirName = stagingDir.name
            require(isPackageDirName(dirName)) { "invalid bubble directory: $dirName" }
            val config = readImportedConfig(File(stagingDir, packageFileName), stagingDir)
            require(config.dirName.isBlank() || config.dirName == dirName) {
                "bubble package identity mismatch: ${config.dirName}"
            }
            val parentDir = rootDir.apply { mkdirs() }.canonicalFile
            val targetDir = File(parentDir, dirName).canonicalFile
            require(targetDir.parentFile == parentDir) { "bubble directory escapes package root" }
            val backupDir = File(parentDir, ".$dirName.backup-${UUID.randomUUID()}")
            BubbleDirectoryTransaction().install(targetDir, stagingDir, backupDir) { installedDir ->
                val installedConfig =
                    readImportedConfig(File(installedDir, packageFileName), installedDir)
                Entry(
                    installedConfig.copy(dirName = dirName),
                    Source.LOCAL,
                    dirName,
                    localDir = installedDir,
                )
            }.also { invalidateCurrentEntry() }
        }
    }

    /**
     * 备份恢复：用备份里的整棵树替换本地 `bubblePackages`。
     *
     * 整棵目录走 [BubbleDirectoryTransaction]（旧树 → backup，staging → root），任一步失败回滚旧树；
     * 安装前后逐包验清单，坏包不会被装上，也不会留下半套包。目录名沿用备份里的文件夹名。
     *
     * @return 实际安装的包数量
     */
    suspend fun replacePackageTree(stagingRoot: File): Int = replacePackageTree(stagingRoot, rootDir)

    internal suspend fun replacePackageTree(stagingRoot: File, targetRoot: File): Int = withContext(IO) {
        importMutex.withLock {
            val parentDir = (targetRoot.parentFile ?: error("no parent for bubble package root"))
                .apply { mkdirs() }
            val targetDir = File(parentDir, targetRoot.name)
            val stagedDirs = stagingRoot.listFiles()
                ?.filter { it.isDirectory && isPackageDirName(it.name) }
                .orEmpty()
            require(stagedDirs.isNotEmpty()) { "no bubble package in backup" }
            stagedDirs.forEach { dir ->
                readImportedConfig(File(dir, packageFileName), dir)
            }
            val backupDir = File(parentDir, ".${targetRoot.name}.backup-${UUID.randomUUID()}")
            val installedCount = BubbleDirectoryTransaction().install(
                targetDir,
                stagingRoot,
                backupDir
            ) { installedRoot ->
                val installedDirs = installedRoot.listFiles()
                    ?.filter { it.isDirectory && isPackageDirName(it.name) }
                    .orEmpty()
                installedDirs.forEach { dir ->
                    readImportedConfig(File(dir, packageFileName), dir)
                }
                installedDirs.size
            }
            invalidateCurrentEntry()
            val activeDir = activeDirName()
            if (activeDir != BUILTIN_DIR_NAME && !File(targetRoot, activeDir).exists()) {
                BubbleSettings.setActiveDirName(BUILTIN_DIR_NAME)
            }
            installedCount
        }
    }

    private fun isPackageDirName(name: String): Boolean =
        name != BUILTIN_DIR_NAME &&
            name != "temp" &&
            name != "remote_cache" &&
            !name.startsWith('.') &&
            '/' !in name &&
            '\\' !in name

    suspend fun exportZip(entry: Entry): File = withContext(IO) {
        if (entry.source == Source.BUILTIN) {
            throw IllegalArgumentException("内置气泡不可导出")
        }
        val dir = entry.localDir ?: localDir(entry.dirName)
        val zipFile = tempDir.getFile("${entry.dirName}.zip")
        if (zipFile.exists()) zipFile.delete()
        if (!ZipUtils.zipFile(dir, zipFile) || !zipFile.exists() || zipFile.length() <= 0L) {
            throw IllegalStateException("气泡导出失败")
        }
        zipFile
    }

    /** 只读条目名判断这是不是气泡包，供系统文件关联分派用（不落地任何解压数据）。 */
    fun isBubblePackageZip(input: InputStream): Boolean =
        BubblePackageArchive.containsManifest(input)

    /** 从 content:// / file:// 直接导入：先拷到临时文件再走 [importZip]。 */
    suspend fun importZipUri(uri: Uri): Entry = withContext(IO) {
        val target = importCacheDir.getFile("import_${UUID.randomUUID()}.zip")
        val input = appCtx.contentResolver.openInputStream(uri)
            ?: error("无法读取所选文件")
        input.use { source ->
            FileOutputStream(target).use { source.copyTo(it) }
        }
        runCatching { importZip(target) }
            .also { target.delete() }
            .getOrThrow()
    }

    /** 已经拿到字节（网络导入）时的入口：落临时文件后走 [importZip]。 */
    suspend fun importZipBytes(bytes: ByteArray): Entry = withContext(IO) {
        val target = importCacheDir.getFile("import_${UUID.randomUUID()}.zip")
        FileOutputStream(target).use { it.write(bytes) }
        runCatching { importZip(target) }
            .also { target.delete() }
            .getOrThrow()
    }

    suspend fun importZip(zipFile: File): Entry = withContext(IO) {
        importMutex.withLock {
            val unzipDir = tempDir.getFile("import_${UUID.randomUUID()}")
            try {
                val extracted = BubblePackageArchive.extract(zipFile, unzipDir)
                val packageFile = extracted.manifestFile
                val config = readImportedConfig(packageFile, packageFile.parentFile)
                val baseDirName = safeImportedDirName(config)
                val dirName = uniqueDirName(baseDirName)
                val parentDir = rootDir.apply { mkdirs() }.canonicalFile
                val targetDir = File(parentDir, dirName).canonicalFile
                require(targetDir.parentFile == parentDir) { "bubble directory escapes package root" }
                require(!targetDir.exists()) { "bubble package target already exists" }
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
                    it.name !in setOf("temp", "remote_cache", BUILTIN_DIR_NAME)
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

    internal fun normalizeStoredConfig(config: Config): Config = normalizeConfig(config)

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
