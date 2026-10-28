package io.legado.app.help.config

import androidx.annotation.Keep
import java.io.File
import java.util.Locale

@Keep
data class PackageResource(
    val alias: String,
    val path: String,
    val type: String
)

internal object PackageResourcePolicy {

    const val ALIAS_PREFIX = "asset://"
    const val TYPE_IMAGE = "image"
    const val TYPE_FONT = "font"
    const val MAX_RESOURCES = 512
    const val MAX_IMAGE_BYTES = 64L * 1024L * 1024L
    const val MAX_FONT_BYTES = 32L * 1024L * 1024L
    const val MAX_SVG_BYTES = 4L * 1024L * 1024L
    const val MAX_TOTAL_BYTES = 256L * 1024L * 1024L

    private val aliasRegex = Regex("^[A-Za-z][A-Za-z0-9_.-]{0,63}$")
    private val imageExtensions = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "svg")
    private val fontExtensions = setOf("ttf", "otf")

    fun normalize(resources: List<PackageResource>): List<PackageResource> {
        require(resources.size <= MAX_RESOURCES) { "package contains too many resources" }
        val aliases = HashSet<String>()
        val paths = HashSet<String>()
        return resources.map { resource ->
            val alias = resource.alias.trim()
            require(aliasRegex.matches(alias)) { "invalid package resource alias: $alias" }
            require(aliases.add(alias.lowercase(Locale.ROOT))) {
                "duplicate package resource alias: $alias"
            }
            val path = normalizePath(resource.path)
            require(paths.add(path.lowercase(Locale.ROOT))) {
                "duplicate package resource path: $path"
            }
            val type = resource.type.trim().lowercase(Locale.ROOT)
            require(type == TYPE_IMAGE || type == TYPE_FONT) {
                "unsupported package resource type: $type"
            }
            requireExtension(path, type)
            PackageResource(alias = alias, path = path, type = type)
        }
    }

    fun validateFiles(root: File, resources: List<PackageResource>): List<PackageResource> {
        val normalized = normalize(resources)
        var totalBytes = 0L
        normalized.forEach { resource ->
            val file = resolveRelativeFile(root, resource.path)
            require(file.isFile) { "package resource is missing: ${resource.path}" }
            validateResolvedFile(file, resource.type, resource.path)
            totalBytes += file.length()
            require(totalBytes <= MAX_TOTAL_BYTES) { "package resources exceed the safety limit" }
        }
        return normalized
    }

    fun validateResolvedFile(file: File, type: String, label: String = file.name) {
        val extension = file.extension.lowercase(Locale.ROOT)
        val maxBytes = when {
            type == TYPE_FONT -> MAX_FONT_BYTES
            extension == "svg" -> MAX_SVG_BYTES
            else -> MAX_IMAGE_BYTES
        }
        require(file.isFile && file.length() in 1..maxBytes) {
            "package resource is empty or too large: $label"
        }
        val header = file.inputStream().use { input ->
            ByteArray(16).also { input.read(it) }
        }
        val valid = if (type == TYPE_FONT) {
            header.startsWith(0x00, 0x01, 0x00, 0x00) ||
                header.startsWithAscii("OTTO") ||
                header.startsWithAscii("ttcf")
        } else {
            when (extension) {
                "png" -> header.startsWith(0x89, 0x50, 0x4e, 0x47)
                "jpg", "jpeg" -> header.startsWith(0xff, 0xd8)
                "webp" -> header.startsWithAscii("RIFF") && header.copyOfRange(8, 12).startsWithAscii("WEBP")
                "gif" -> header.startsWithAscii("GIF8")
                "bmp" -> header.startsWithAscii("BM")
                "svg" -> file.inputStream().bufferedReader(Charsets.UTF_8).use { reader ->
                    buildString {
                        repeat(16) {
                            val line = reader.readLine() ?: return@repeat
                            append(line)
                        }
                    }.contains("<svg", ignoreCase = true)
                }
                else -> false
            }
        }
        require(valid) { "package resource content does not match its type: $label" }
    }

    fun resolve(
        root: File,
        resources: List<PackageResource>,
        reference: String?,
        expectedType: String
    ): File? {
        val value = reference?.trim().orEmpty()
        if (value.isBlank()) return null
        val path = if (value.startsWith(ALIAS_PREFIX, ignoreCase = true)) {
            val alias = value.substring(ALIAS_PREFIX.length)
            resources.firstOrNull {
                it.alias.equals(alias, ignoreCase = true) &&
                    it.type.equals(expectedType, ignoreCase = true)
            }?.path ?: return null
        } else {
            normalizePathOrNull(value) ?: return null
        }
        requireExtension(path, expectedType)
        return runCatching { resolveRelativeFile(root, path) }
            .getOrNull()
            ?.takeIf { it.isFile }
    }

    fun normalizePath(value: String): String {
        return requireNotNull(normalizePathOrNull(value)) { "invalid package resource path" }
    }

    fun isSafeReference(value: String): Boolean {
        val reference = value.trim()
        if (reference.startsWith(ALIAS_PREFIX, ignoreCase = true)) {
            return aliasRegex.matches(reference.substring(ALIAS_PREFIX.length))
        }
        return normalizePathOrNull(reference) != null
    }

    private fun normalizePathOrNull(value: String): String? {
        val path = value.trim().replace('\\', '/')
        if (path.isBlank() || path.startsWith('/') || DRIVE_PREFIX.matches(path)) return null
        if (path.contains('?') || path.contains('#') || '\u0000' in path) return null
        val segments = path.split('/')
        if (segments.any { it.isBlank() || it == "." || it == ".." }) return null
        if (segments.first().lowercase(Locale.ROOT) !in setOf("assets", "images")) return null
        return segments.joinToString("/")
    }

    private fun resolveRelativeFile(root: File, path: String): File {
        val canonicalRoot = root.canonicalFile
        val file = File(canonicalRoot, path).canonicalFile
        require(file.toPath().startsWith(canonicalRoot.toPath()) && file != canonicalRoot) {
            "package resource escapes its root"
        }
        return file
    }

    private fun requireExtension(path: String, type: String) {
        val extension = path.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val allowed = if (type == TYPE_FONT) fontExtensions else imageExtensions
        require(extension in allowed) { "unsupported package resource file: $path" }
    }

    private fun ByteArray.startsWith(vararg values: Int): Boolean {
        if (size < values.size) return false
        return values.indices.all { index -> this[index].toInt() and 0xff == values[index] }
    }

    private fun ByteArray.startsWithAscii(value: String): Boolean {
        return startsWith(*value.toByteArray(Charsets.US_ASCII).map { it.toInt() and 0xff }.toIntArray())
    }

    private val DRIVE_PREFIX = Regex("^[A-Za-z]:.*")
}
