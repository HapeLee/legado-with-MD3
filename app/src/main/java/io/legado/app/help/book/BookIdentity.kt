package io.legado.app.help.book

import io.legado.app.data.entities.Book
import java.text.Normalizer
import java.util.Locale

object BookIdentity {

    private const val WORK_PREFIX = "work:"

    fun key(book: Book?): String {
        book ?: return ""
        return key(
            name = book.name,
            author = book.getRealAuthor().ifBlank { book.author },
            fallback = book.bookUrl
        )
    }

    fun key(name: String?, author: String?, fallback: String? = null): String {
        val normalizedName = normalize(name)
        val normalizedAuthor = normalize(author)
        return when {
            normalizedName.isNotBlank() && normalizedAuthor.isNotBlank() ->
                "$WORK_PREFIX$normalizedAuthor/$normalizedName"
            normalizedName.isNotBlank() ->
                "$WORK_PREFIX$normalizedName"
            else -> fallback.orEmpty()
        }
    }

    fun legacyKeys(book: Book?): List<String> {
        book ?: return emptyList()
        val key = key(book)
        return listOf(book.bookUrl)
            .filter { it.isNotBlank() && it != key }
            .distinct()
    }

    fun isWorkKey(value: String?): Boolean {
        return value.orEmpty().startsWith(WORK_PREFIX)
    }

    private fun normalize(value: String?): String {
        return Normalizer.normalize(value.orEmpty(), Normalizer.Form.NFKC)
            .trim()
            .replace(Regex("\\s+"), "")
            .lowercase(Locale.ROOT)
    }
}

fun Book.characterBookKey(): String = BookIdentity.key(this)
