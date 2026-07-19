package io.legado.app.data.repository

import android.content.Context
import androidx.core.net.toUri
import io.legado.app.data.entities.HighlightRule
import io.legado.app.utils.isContentScheme
import java.io.File

internal object HighlightRuleAssetTransfer {

    data class ExportResult(
        val rules: List<HighlightRule>,
        val files: List<File>,
    )

    fun prepareExport(
        rules: List<HighlightRule>,
        exportDir: File,
        copyAsset: (source: String, target: File) -> Boolean,
    ): ExportResult {
        val files = ArrayList<File>()
        val portableRules = rules.mapIndexed { index, rule ->
            rule.copy(
                bgImage = exportReference(
                    reference = rule.bgImage,
                    prefix = "highlight_rule_bg_$index",
                    exportDir = exportDir,
                    files = files,
                    copyAsset = copyAsset,
                ),
                fontPath = exportReference(
                    reference = rule.fontPath,
                    prefix = "highlight_rule_font_$index",
                    exportDir = exportDir,
                    files = files,
                    copyAsset = copyAsset,
                ),
            )
        }
        return ExportResult(portableRules, files)
    }

    fun restoreImport(
        rules: List<HighlightRule>,
        importDir: File,
        backgroundDir: File,
        fontDir: File,
        isReadableBackgroundReference: (String) -> Boolean,
        isReadableFontReference: (String) -> Boolean = isReadableBackgroundReference,
    ): List<HighlightRule> {
        return rules.map { rule ->
            rule.copy(
                bgImage = restoreReference(
                    reference = rule.bgImage,
                    importDir = importDir,
                    targetDir = backgroundDir,
                    isReadableReference = isReadableBackgroundReference,
                ),
                fontPath = restoreReference(
                    reference = rule.fontPath,
                    importDir = importDir,
                    targetDir = fontDir,
                    isReadableReference = isReadableFontReference,
                ),
            )
        }
    }

    fun clearUnreadableReferences(
        rules: List<HighlightRule>,
        isReadableBackgroundReference: (String) -> Boolean,
        isReadableFontReference: (String) -> Boolean,
    ): List<HighlightRule> {
        return rules.map { rule ->
            rule.copy(
                bgImage = rule.bgImage?.takeIf(isReadableBackgroundReference),
                fontPath = rule.fontPath?.takeIf(isReadableFontReference),
            )
        }
    }

    private fun exportReference(
        reference: String?,
        prefix: String,
        exportDir: File,
        files: MutableList<File>,
        copyAsset: (source: String, target: File) -> Boolean,
    ): String? {
        val source = reference?.takeIf { it.isNotBlank() } ?: return null
        if (source.startsWith("assets://")) return source
        val sourceName = File(source).name.ifBlank { "asset" }
        val targetName = "${prefix}_${sourceName.safeFileName()}"
        val target = File(exportDir, targetName)
        return if (copyAsset(source, target)) {
            files.add(target)
            targetName
        } else {
            null
        }
    }

    private fun restoreReference(
        reference: String?,
        importDir: File,
        targetDir: File,
        isReadableReference: (String) -> Boolean,
    ): String? {
        val source = reference?.takeIf { it.isNotBlank() } ?: return null
        if (source.startsWith("assets://") || isReadableReference(source)) return source
        val bundledFile = File(importDir, File(source).name)
        if (!bundledFile.isFile) return null
        targetDir.mkdirs()
        val targetFile = File(targetDir, bundledFile.name)
        if (!targetFile.exists() || targetFile.length() != bundledFile.length()) {
            bundledFile.copyTo(targetFile, overwrite = true)
        }
        return targetFile.absolutePath
    }

    private fun String.safeFileName(): String {
        return replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "asset" }
    }
}

internal fun Context.isReadableHighlightBackground(path: String): Boolean {
    if (path.isContentScheme()) {
        return runCatching {
            contentResolver.openAssetFileDescriptor(path.toUri(), "r")?.use { true } ?: false
        }.getOrDefault(false)
    }
    if (File(path).isFile) return true
    val assetPath = path.removePrefix("assets://").let {
        if (it.startsWith("bg/")) it else "bg/$it"
    }
    return runCatching { assets.open(assetPath).use { true } }.getOrDefault(false)
}

internal fun Context.isReadableHighlightFont(path: String): Boolean {
    if (path.isContentScheme()) {
        return runCatching {
            contentResolver.openAssetFileDescriptor(path.toUri(), "r")?.use { true } ?: false
        }.getOrDefault(false)
    }
    return File(path).isFile
}
