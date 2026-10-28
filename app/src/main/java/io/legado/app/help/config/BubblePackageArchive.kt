package io.legado.app.help.config

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

internal data class ExtractedBubblePackage(
    val manifestFile: File,
    val entryCount: Int,
    val extractedBytes: Long
)

internal object BubblePackageArchive {
    private const val MANIFEST_NAME = "bubble.json"
    private const val MAX_ARCHIVE_BYTES = 256L * 1024L * 1024L
    private const val MAX_ENTRIES = 2_048
    private const val MAX_SINGLE_FILE_BYTES = 128L * 1024L * 1024L
    private const val MAX_TOTAL_BYTES = 512L * 1024L * 1024L
    private const val MAX_MANIFEST_BYTES = 512L * 1024L
    private const val MAX_COMPRESSION_RATIO = 250L

    fun extract(zipFile: File, destination: File): ExtractedBubblePackage {
        require(zipFile.isFile) { "bubble package ZIP does not exist" }
        if (zipFile.length() > MAX_ARCHIVE_BYTES) {
            throw IOException("bubble package exceeds the 256 MiB safety limit")
        }
        require(!destination.exists()) { "bubble extraction directory already exists" }
        check(destination.mkdirs()) { "failed to create bubble extraction directory" }

        var entryCount = 0
        var totalBytes = 0L
        var manifest: File? = null
        val paths = HashSet<String>()
        try {
            ZipFile(zipFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    entryCount++
                    if (entryCount > MAX_ENTRIES) throw IOException("bubble package contains too many entries")
                    val relativePath = validateEntryName(entry.name)
                    val pathKey = relativePath.lowercase(Locale.ROOT)
                    if (!paths.add(pathKey)) throw IOException("bubble package contains duplicate paths")
                    val target = File(destination, relativePath).canonicalFile
                    val root = destination.canonicalFile
                    if (target != root && target.parentFile != root && !target.toPath().startsWith(root.toPath())) {
                        throw IOException("bubble package entry escapes extraction directory")
                    }
                    if (entry.isDirectory) {
                        check(target.mkdirs() || target.isDirectory) { "failed to create bubble package directory" }
                        continue
                    }
                    target.parentFile?.let { parent ->
                        check(parent.mkdirs() || parent.isDirectory) { "failed to create bubble package directory" }
                    }
                    var fileBytes = 0L
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(target).use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                fileBytes += count.toLong()
                                totalBytes += count.toLong()
                                if (fileBytes > MAX_SINGLE_FILE_BYTES) {
                                    throw IOException("bubble package entry is too large")
                                }
                                if (totalBytes > MAX_TOTAL_BYTES) {
                                    throw IOException("bubble package expands beyond the allowed size")
                                }
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    val compressedSize = entry.compressedSize
                    if (fileBytes > 0L && compressedSize >= 0L &&
                        fileBytes / compressedSize.coerceAtLeast(1L) > MAX_COMPRESSION_RATIO
                    ) {
                        throw IOException("bubble package entry has an unsafe compression ratio")
                    }
                    if (target.name.equals(MANIFEST_NAME, ignoreCase = false)) {
                        if (manifest != null) throw IOException("bubble package contains multiple $MANIFEST_NAME files")
                        if (fileBytes > MAX_MANIFEST_BYTES) throw IOException("bubble package manifest is too large")
                        manifest = target
                    }
                }
            }
            val manifestFile = manifest ?: throw IOException("bubble package is missing $MANIFEST_NAME")
            return ExtractedBubblePackage(manifestFile, entryCount, totalBytes)
        } catch (error: Throwable) {
            destination.deleteRecursively()
            throw error
        }
    }

    /**
     * 只看条目名、不落地任何数据：压缩包里有没有 [MANIFEST_NAME]。
     *
     * 系统文件关联分派靠它把气泡包和「正好也是 zip 的书/备份」区分开——
     * 后者的解压产物会被 `bookFileRegex` 过滤掉，不能走先解压再分派的路。
     */
    fun containsManifest(input: InputStream): Boolean = runCatching {
        ZipInputStream(input.buffered()).use { zip ->
            generateSequence { runCatching { zip.nextEntry }.getOrNull() }
                .any { !it.isDirectory && File(it.name).name.equals(MANIFEST_NAME, true) }
        }
    }.getOrDefault(false)

    private fun validateEntryName(rawName: String): String {
        if (rawName.isBlank() || '\u0000' in rawName || '\\' in rawName) {
            throw IOException("bubble package contains an invalid path")
        }
        if (rawName.startsWith('/') || DRIVE_PREFIX.matches(rawName)) {
            throw IOException("bubble package contains an absolute path")
        }
        val isDirectory = rawName.endsWith('/')
        val normalized = rawName.trimEnd('/')
        val segments = normalized.split('/')
        if (segments.any { it.isEmpty() || it == "." || it == ".." }) {
            throw IOException("bubble package contains an unsafe path")
        }
        return if (isDirectory) "$normalized/" else normalized
    }

    private val DRIVE_PREFIX = Regex("^[A-Za-z]:.*")
}
