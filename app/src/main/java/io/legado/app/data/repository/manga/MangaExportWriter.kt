package io.legado.app.data.repository.manga

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal data class MangaExportPage(
    val chapterNumber: Int,
    val chapterTitle: String,
    val pageNumber: Int,
    val openStream: () -> InputStream,
)

internal data class PreparedMangaExportPage(val page: MangaExportPage, val path: String)

internal class MangaExportPageException(val page: MangaExportPage, cause: Exception) : IOException(cause)

/** Both destinations use the same ordered pages and copy original encoded bytes without decoding. */
internal object MangaExportWriter {
    suspend fun prepare(pages: List<MangaExportPage>): List<PreparedMangaExportPage> {
        val chapterWidth = maxOf(4, pages.maxOfOrNull { it.chapterNumber }?.toString()?.length ?: 0)
        val pageWidth = maxOf(4, pages.maxOfOrNull { it.pageNumber }?.toString()?.length ?: 0)
        return pages.map { page ->
            currentCoroutineContext().ensureActive()
            try {
                val extension = page.openStream().use(::imageExtension)
                PreparedMangaExportPage(
                    page,
                    "${page.chapterNumber.toString().padStart(chapterWidth, '0')}_${safeName(page.chapterTitle)}/" +
                        "${page.pageNumber.toString().padStart(pageWidth, '0')}.$extension",
                )
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                throw MangaExportPageException(page, error)
            }
        }
    }

    suspend fun writeFolder(
        pages: List<PreparedMangaExportPage>,
        openOutput: (path: String) -> OutputStream,
        onProgress: (Int, Int) -> Unit,
    ) {
        pages.forEachIndexed { index, prepared ->
            copyPage(prepared) { input -> openOutput(prepared.path).use { copy(input, it) } }
            onProgress(index + 1, pages.size)
        }
    }

    suspend fun writeCbz(
        pages: List<PreparedMangaExportPage>,
        output: OutputStream,
        onProgress: (Int, Int) -> Unit,
    ) {
        ZipOutputStream(output.buffered()).use { zip ->
            // Images are already compressed; avoid spending CPU recompressing them.
            zip.setLevel(Deflater.NO_COMPRESSION)
            pages.forEachIndexed { index, prepared ->
                copyPage(prepared) { input ->
                    zip.putNextEntry(ZipEntry(prepared.path))
                    copy(input, zip)
                    zip.closeEntry()
                }
                onProgress(index + 1, pages.size)
            }
        }
    }

    private suspend fun copyPage(
        prepared: PreparedMangaExportPage,
        write: suspend (InputStream) -> Unit,
    ) {
        currentCoroutineContext().ensureActive()
        try {
            prepared.page.openStream().buffered().use { input ->
                input.mark(64)
                if (imageExtension(input) != prepared.path.substringAfterLast('.')) {
                    throw IOException("Image format changed during export")
                }
                input.reset()
                write(input)
            }
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            throw MangaExportPageException(prepared.page, error)
        }
    }

    private suspend fun copy(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) return
            if (count > 0) output.write(buffer, 0, count)
        }
    }

    // Cache suffixes may be guessed from URLs; use encoded bytes for exported extensions.
    private fun imageExtension(input: InputStream): String {
        val bytes = ByteArray(64)
        var size = 0
        while (size < bytes.size) {
            val count = input.read(bytes, size, bytes.size - size)
            if (count < 0) break
            if (count == 0) throw IOException("Image stream made no progress")
            size += count
        }
        fun matches(offset: Int, vararg signature: Int) = size >= offset + signature.size &&
            signature.indices.all { bytes[offset + it].toInt() and 0xff == signature[it] }
        fun text(offset: Int, value: String) = matches(offset, *value.map(Char::code).toIntArray())
        return when {
            matches(0, 0xff, 0xd8, 0xff) -> "jpg"
            matches(0, 137, 80, 78, 71, 13, 10, 26, 10) -> "png"
            text(0, "GIF87a") || text(0, "GIF89a") -> "gif"
            text(0, "RIFF") && text(8, "WEBP") -> "webp"
            text(0, "BM") -> "bmp"
            text(4, "ftyp") && (text(8, "avif") || text(8, "avis") ||
                (16..size - 4 step 4).any { text(it, "avif") || text(it, "avis") }) -> "avif"
            else -> throw IOException("Unsupported or invalid comic image")
        }
    }

    fun safeName(value: String): String = value
        .replace(Regex("[\\p{Cntrl}<>:\"/\\\\|?*]"), "_")
        .take(100).trim().trimEnd('.', ' ')
        .ifBlank { "_" }

    fun uniqueName(base: String, suffix: String, existing: Set<String>): String {
        var name = "$base$suffix"
        var number = 1
        while (existing.any { it.equals(name, ignoreCase = true) }) {
            name = "$base (${number++})$suffix"
        }
        return name
    }
}
