package io.legado.app.help.bookmark

import android.content.Context
import android.net.Uri
import io.legado.app.data.entities.Bookmark
import io.legado.app.utils.GSON
import io.legado.app.utils.writeToOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object BookmarkExporter {

    fun formatToMarkdown(bookName: String, author: String?, bookmarks: List<Bookmark>): String {
        return buildString {
            append("# $bookName\n")
            if (!author.isNullOrBlank()) append("作者：$author\n")
            append("\n---\n\n")

            bookmarks.forEach {
                append("#### ${it.chapterName}\n")
                if (it.bookText.isNotEmpty()) {
                    val quotedText = it.bookText.replace("\n", "\n> ")
                    append("> $quotedText\n\n")
                }
                if (it.content.isNotBlank()) {
                    append("${it.content}\n\n")
                }
                append("---\n\n")
            }
        }
    }

    suspend fun exportToUri(
        context: Context,
        fileUri: Uri,
        bookmarks: List<Bookmark>,
        isMd: Boolean,
        bookName: String = "",
        author: String? = ""
    ) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(fileUri)?.use { outputStream ->
            if (isMd) {
                val content = formatToMarkdown(bookName, author, bookmarks)
                outputStream.write(content.toByteArray())
            } else {
                GSON.writeToOutputStream(outputStream, bookmarks)
            }
        }
    }
}