package io.legado.app.data.repository

import io.legado.app.data.dao.BookSourceDao
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.help.source.SourceHelp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class BookSourceRepository(private val bookSourceDao: BookSourceDao) {

    fun flowAll(): Flow<List<BookSourcePart>> {
        return bookSourceDao.flowAll()
    }

    fun flowEnabled(): Flow<List<BookSourcePart>> {
        return bookSourceDao.flowEnabled()
    }

    fun flowGroups(): Flow<List<String>> {
        return bookSourceDao.flowGroups()
    }

    fun flowHomepageModules(): Flow<List<BookSource>> {
        return bookSourceDao.flowHomepageModules()
    }

    fun flowExploreSources(): Flow<List<BookSource>> {
        return bookSourceDao.flowExploreSources()
    }

    fun flowExploreSourceParts(): Flow<List<BookSourcePart>> {
        return bookSourceDao.flowExploreSourceParts()
    }

    suspend fun getBookSource(sourceUrl: String): BookSource? {
        return withContext(Dispatchers.IO) {
            bookSourceDao.getBookSource(sourceUrl)
        }
    }

    suspend fun getBookSourcePart(sourceUrl: String): BookSourcePart? {
        return withContext(Dispatchers.IO) {
            bookSourceDao.getBookSourcePart(sourceUrl)
        }
    }

    fun getBookSourceSync(sourceUrl: String): BookSource? {
        return bookSourceDao.getBookSource(sourceUrl)
    }

    fun has(bookSourceUrl: String): Boolean {
        return bookSourceDao.has(bookSourceUrl)
    }

    suspend fun getBookSourceAddBook(baseUrl: String): BookSource? {
        return withContext(Dispatchers.IO) {
            bookSourceDao.getBookSourceAddBook(baseUrl)
        }
    }

    suspend fun getHasBookUrlPattern(): List<BookSourcePart> {
        return withContext(Dispatchers.IO) {
            bookSourceDao.hasBookUrlPattern
        }
    }

    suspend fun getAllEnabledPart(): List<BookSourcePart> {
        return withContext(Dispatchers.IO) {
            bookSourceDao.allEnabledPart
        }
    }

    suspend fun getAllPart(): List<BookSourcePart> {
        return withContext(Dispatchers.IO) {
            bookSourceDao.allPart
        }
    }

    suspend fun moveToEdge(sources: List<BookSourcePart>, toTop: Boolean) =
        withContext(Dispatchers.IO) {
            val selected = sources.sortedBy { it.customOrder }
            val start = if (toTop) {
                bookSourceDao.minOrder - selected.size
            } else {
                bookSourceDao.maxOrder + 1
            }
            bookSourceDao.upOrder(
                selected.mapIndexed { index, part -> part.copy(customOrder = start + index) }
            )
        }

    suspend fun getAllTextEnabledPart(): List<BookSourcePart> {
        return withContext(Dispatchers.IO) {
            bookSourceDao.allTextEnabledPart
        }
    }

    suspend fun topSources(sources: List<BookSourcePart>) = withContext(Dispatchers.IO) {
        val minOrder = bookSourceDao.minOrder - 1
        val reorderedSources = sources.sortedBy { it.customOrder }.mapIndexed { index, source ->
            source.copy(customOrder = minOrder - index)
        }
        bookSourceDao.upOrder(reorderedSources)
    }

    suspend fun bottomSources(sources: List<BookSourcePart>) = withContext(Dispatchers.IO) {
        val maxOrder = bookSourceDao.maxOrder + 1
        val reorderedSources = sources.sortedBy { it.customOrder }.mapIndexed { index, source ->
            source.copy(customOrder = maxOrder + index)
        }
        bookSourceDao.upOrder(reorderedSources)
    }

    suspend fun deleteSourceParts(sources: List<BookSourcePart>) = withContext(Dispatchers.IO) {
        SourceHelp.deleteBookSourceParts(sources)
    }

    suspend fun deleteSource(sourceUrl: String) = withContext(Dispatchers.IO) {
        SourceHelp.deleteBookSource(sourceUrl)
    }

    suspend fun disableSource(sourceUrl: String) = withContext(Dispatchers.IO) {
        bookSourceDao.getBookSource(sourceUrl)?.let { source ->
            source.enabled = false
            bookSourceDao.update(source)
        }
    }

    suspend fun moveSourceToTop(sourceUrl: String): Int? = withContext(Dispatchers.IO) {
        bookSourceDao.moveToTop(sourceUrl)
    }

    suspend fun moveSourceToBottom(sourceUrl: String): Int? = withContext(Dispatchers.IO) {
        bookSourceDao.moveToBottom(sourceUrl)
    }

    suspend fun updateSources(vararg sources: BookSource) = withContext(Dispatchers.IO) {
        bookSourceDao.update(*sources)
    }

    suspend fun updateOrder(sources: List<BookSourcePart>) = withContext(Dispatchers.IO) {
        bookSourceDao.upOrder(sources)
    }

    suspend fun setEnabled(enabled: Boolean, sources: List<BookSourcePart>) =
        withContext(Dispatchers.IO) {
            bookSourceDao.enable(enabled, sources)
        }

    suspend fun setEnabled(sourceUrl: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        bookSourceDao.enable(sourceUrl, enabled)
    }

    suspend fun setExploreEnabled(enabled: Boolean, sources: List<BookSourcePart>) =
        withContext(Dispatchers.IO) {
            bookSourceDao.enableExplore(enabled, sources)
        }

    suspend fun updateGroups(sources: List<BookSourcePart>) = withContext(Dispatchers.IO) {
        bookSourceDao.upGroup(sources)
    }

    suspend fun getAll(): List<BookSource> = withContext(Dispatchers.IO) {
        bookSourceDao.all
    }

    suspend fun getAllEnabled(): List<BookSource> = withContext(Dispatchers.IO) {
        bookSourceDao.allEnabled
    }

    suspend fun getAllDisabled(): List<BookSource> = withContext(Dispatchers.IO) {
        bookSourceDao.allDisabled
    }

    suspend fun getAllLogin(): List<BookSource> = withContext(Dispatchers.IO) {
        bookSourceDao.allLogin
    }

    suspend fun getAllNoGroup(): List<BookSource> = withContext(Dispatchers.IO) {
        bookSourceDao.allNoGroup
    }

    suspend fun getAllExploreEnabled(): List<BookSource> = withContext(Dispatchers.IO) {
        bookSourceDao.allEnabledExplore
    }

    suspend fun getAllExploreDisabled(): List<BookSource> = withContext(Dispatchers.IO) {
        bookSourceDao.allDisabledExplore
    }

    suspend fun getByGroup(group: String): List<BookSource> = withContext(Dispatchers.IO) {
        bookSourceDao.getByGroup(group)
    }

    suspend fun searchByGroup(group: String): List<BookSource> = withContext(Dispatchers.IO) {
        bookSourceDao.groupSearch(group)
    }

    suspend fun search(searchKey: String): List<BookSource> = withContext(Dispatchers.IO) {
        bookSourceDao.search(searchKey)
    }

    suspend fun getNoGroup(): List<BookSource> = withContext(Dispatchers.IO) {
        bookSourceDao.noGroup
    }

    suspend fun delete(source: BookSource) = withContext(Dispatchers.IO) {
        bookSourceDao.delete(source)
    }

    suspend fun insert(source: BookSource) = withContext(Dispatchers.IO) {
        bookSourceDao.insert(source)
    }
}
