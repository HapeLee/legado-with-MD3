package io.legado.app.model.localBook

import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import splitties.init.appCtx
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes

internal suspend fun localBookFileSize(bookUrl: String, mayBeMangaDirectory: Boolean): Long? = try {
    if (bookUrl.startsWith("content://")) {
        contentByteSize(bookUrl.toUri(), mayBeMangaDirectory)
    } else {
        val file = localFile(bookUrl)
        if (mayBeMangaDirectory && file.isDirectory) {
            LocalMangaFiles.directoryByteSize(file)
        } else {
            localFileByteSize(file)
        }
    }
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    null
}

internal fun localFileByteSize(file: File): Long? {
    if (!file.canRead()) return null
    val attributes = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
    return attributes.size().takeIf { attributes.isRegularFile }
}

private suspend fun contentByteSize(uri: Uri, mayBeMangaDirectory: Boolean): Long? {
    val resolver = appCtx.contentResolver
    if (mayBeMangaDirectory) {
        val isTree = DocumentsContract.isTreeUri(uri)
        val isTreeRoot = isTree && uri.pathSegments.size == 2
        val mimeType = if (isTreeRoot) DocumentsContract.Document.MIME_TYPE_DIR else {
            try {
                resolver.getType(uri)
            } catch (_: SecurityException) {
                null
            }
        }
        val directory = when {
            mimeType == DocumentsContract.Document.MIME_TYPE_DIR -> readableMangaDirectoryUri(uri)
            mimeType == null && isTree -> try {
                readableMangaDirectoryUri(uri)
            } catch (_: FileNotFoundException) {
                null
            }
            else -> null
        }
        if (directory != null) return LocalMangaFiles.directoryByteSize(directory)
    }

    val metadataSize = try {
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (column < 0 || !cursor.moveToFirst() || cursor.isNull(column)) null
            else cursor.getLong(column).takeIf { it >= 0L }
        }
    } catch (_: IllegalArgumentException) {
        null
    }
    return metadataSize ?: contentDescriptorByteSize(uri)
}

internal fun contentDescriptorByteSize(uri: Uri): Long? = appCtx.contentResolver
    .openAssetFileDescriptor(uri, "r")?.use { it.length.takeIf { length -> length >= 0L } }
