package io.legado.app.data.repository

import io.legado.app.data.entities.Book
import io.legado.app.domain.gateway.DictionaryGateway
import io.legado.app.domain.model.BookDictionary
import io.legado.app.domain.model.DictPair
import io.legado.app.domain.model.TranslationDictionaryPolicy
import io.legado.app.help.book.BookHelp
import io.legado.app.utils.GSON
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

class DictionaryRepositoryImpl : DictionaryGateway {

    private companion object {
        const val DICT_FILE_NAME = "translation_dictionary.json"
        val bookLocks = ConcurrentHashMap<String, Any>()
    }

    private fun getDictFile(book: Book): File {
        val cacheDir = BookHelp.cachePath
        val bookFolder = File(cacheDir, book.getFolderName())
        return File(bookFolder, DICT_FILE_NAME)
    }

    private fun getBookLock(book: Book): Any =
        bookLocks.computeIfAbsent(getDictFile(book).absolutePath) { Any() }

    override fun getBookDictionaries(book: Book): BookDictionary {
        return synchronized(getBookLock(book)) {
            readDictionary(book)
        }
    }

    private fun readDictionary(book: Book): BookDictionary {
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

    override fun mergeDiscoveredPairs(book: Book, newPairs: List<DictPair>): BookDictionary {
        return synchronized(getBookLock(book)) {
            val existing = readDictionary(book)
            @Suppress("USELESS_ELVIS")
            val existingPairs = existing.pairs ?: emptyList()
            val mergedPairs = TranslationDictionaryPolicy.mergeDiscoveredPairs(existingPairs, newPairs)
            if (mergedPairs == existingPairs) return@synchronized existing
            val updated = existing.copy(pairs = mergedPairs, updatedAt = System.currentTimeMillis())
            saveDictionary(book, updated)
            updated
        }
    }

    private fun saveDictionary(book: Book, dictionary: BookDictionary) {
        val dictFile = getDictFile(book)
        dictFile.parentFile?.mkdirs()
        val tempFile = File.createTempFile("$DICT_FILE_NAME.", ".tmp", dictFile.parentFile)
        try {
            tempFile.writeText(GSON.toJson(dictionary))
            try {
                Files.move(
                    tempFile.toPath(),
                    dictFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    tempFile.toPath(),
                    dictFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        } finally {
            tempFile.delete()
        }
    }

    override fun clearBookDictionary(book: Book) {
        synchronized(getBookLock(book)) {
            val dictFile = getDictFile(book)
            if (dictFile.exists()) {
                dictFile.delete()
            }
        }
    }
}
