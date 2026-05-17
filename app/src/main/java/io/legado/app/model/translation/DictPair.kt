package io.legado.app.model.translation

import io.legado.app.data.entities.Book
import io.legado.app.help.book.BookHelp
import io.legado.app.utils.GSON
import java.io.File

/**
 * A pair of original text and its translation, used for maintaining
 * consistent terminology across multiple translation chunks.
 */
data class DictPair(
    val original: String,
    val translation: String
)

/**
 * Collection of dictionary pairs with metadata.
 */
data class BookDictionary(
    val bookUrl: String,
    val pairs: List<DictPair> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Manages book-specific translation dictionaries for consistent terminology.
 * Dictionaries are stored as JSON files in the book cache directory.
 */
object Translation {

    private const val DICT_FILE_NAME = "translation_dictionary.json"

    /**
     * Get the dictionary file for a book.
     */
    fun getDictFile(book: Book): File {
        val cacheDir = BookHelp.cachePath
        val bookFolder = File(cacheDir, book.getFolderName())
        return File(bookFolder, DICT_FILE_NAME)
    }

    /**
     * Load existing dictionary for a book.
     * Returns empty dictionary if none exists.
     */
    fun getBookDictionaries(book: Book): BookDictionary {
        val dictFile = getDictFile(book)
        return if (dictFile.exists()) {
            try {
                GSON.fromJson(dictFile.readText(), BookDictionary::class.java)
            } catch (e: Exception) {
                BookDictionary(book.bookUrl)
            }
        } else {
            BookDictionary(book.bookUrl)
        }
    }

    /**
     * Update the dictionary for a book with new term pairs.
     * Merges with existing dictionary, avoiding duplicates.
     */
    fun updateBookDic(book: Book, newPairs: List<DictPair>) {
        val existingDict = getBookDictionaries(book)
        val updatedPairs = existingDict.pairs.toMutableList()

        for (newPair in newPairs) {
            val existingIndex = updatedPairs.indexOfFirst {
                it.original == newPair.original
            }
            if (existingIndex >= 0) {
                // Update existing pair's translation
                updatedPairs[existingIndex] = newPair
            } else {
                // Add new pair
                updatedPairs.add(newPair)
            }
        }

        val updatedDict = existingDict.copy(
            pairs = updatedPairs,
            updatedAt = System.currentTimeMillis()
        )

        saveDictionary(book, updatedDict)
    }

    /**
     * Save dictionary to file.
     */
    private fun saveDictionary(book: Book, dictionary: BookDictionary) {
        val dictFile = getDictFile(book)
        dictFile.parentFile?.mkdirs()
        dictFile.writeText(GSON.toJson(dictionary))
    }

    /**
     * Clear dictionary for a book.
     */
    fun clearBookDictionary(book: Book) {
        val dictFile = getDictFile(book)
        if (dictFile.exists()) {
            dictFile.delete()
        }
    }
}