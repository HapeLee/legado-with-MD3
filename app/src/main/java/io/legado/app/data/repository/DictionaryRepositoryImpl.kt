package io.legado.app.data.repository

import io.legado.app.data.entities.Book
import io.legado.app.domain.gateway.DictionaryGateway
import io.legado.app.domain.model.BookDictionary
import io.legado.app.domain.model.DictPair
import io.legado.app.help.book.BookHelp
import io.legado.app.utils.GSON
import java.io.File

class DictionaryRepositoryImpl : DictionaryGateway {

    private companion object {
        const val DICT_FILE_NAME = "translation_dictionary.json"
    }

    private fun getDictFile(book: Book): File {
        val cacheDir = BookHelp.cachePath
        val bookFolder = File(cacheDir, book.getFolderName())
        return File(bookFolder, DICT_FILE_NAME)
    }

    override fun getBookDictionaries(book: Book): BookDictionary {
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

    override fun updateBookDic(book: Book, newPairs: List<DictPair>) {
        val existingDict = getBookDictionaries(book)
        @Suppress("SENSELESS_COMPARISON")
        val updatedPairs = (existingDict.pairs ?: emptyList()).toMutableList()

        for (newPair in newPairs) {
            val existingIndex = updatedPairs.indexOfFirst {
                it.original == newPair.original
            }
            if (existingIndex >= 0) {
                updatedPairs[existingIndex] = newPair
            } else {
                updatedPairs.add(newPair)
            }
        }

        val updatedDict = existingDict.copy(
            pairs = updatedPairs,
            updatedAt = System.currentTimeMillis()
        )

        saveDictionary(book, updatedDict)
    }

    private fun saveDictionary(book: Book, dictionary: BookDictionary) {
        val dictFile = getDictFile(book)
        dictFile.parentFile?.mkdirs()
        dictFile.writeText(GSON.toJson(dictionary))
    }

    override fun clearBookDictionary(book: Book) {
        val dictFile = getDictFile(book)
        if (dictFile.exists()) {
            dictFile.delete()
        }
    }
}
