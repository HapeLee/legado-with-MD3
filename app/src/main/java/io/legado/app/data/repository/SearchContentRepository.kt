package io.legado.app.data.repository

import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.SearchContentHistory
import io.legado.app.data.dao.SearchContentHistoryDao
import io.legado.app.domain.gateway.ReadSettingsGateway
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.book.isLocal
import io.legado.app.ui.book.searchContent.SearchResult
import io.legado.app.utils.ChineseUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class SearchContentRepository(
    private val titleModeProvider: () -> Int = { 0 },
    private val historyDao: SearchContentHistoryDao? = null,
    private val readSettingsGateway: ReadSettingsGateway? = null,
    private val otherSettingsGateway: OtherSettingsGateway? = null,
) {

    private val effectiveHistoryDao: SearchContentHistoryDao
        get() = historyDao ?: appDb.searchContentHistoryDao

    fun observeHistory(book: Book?, onlyThisBook: Boolean): Flow<List<SearchContentHistory>> =
        if (onlyThisBook && book != null) {
            effectiveHistoryDao.getByBook(book.name, book.author)
        } else {
            effectiveHistoryDao.getAll()
        }

    suspend fun saveHistory(book: Book, query: String) {
        val history = effectiveHistoryDao.get(book.name, book.author, query)
            ?: SearchContentHistory(bookName = book.name, bookAuthor = book.author, query = query)
        history.time = System.currentTimeMillis()
        effectiveHistoryDao.insert(history)
    }

    suspend fun deleteHistory(id: Long) = effectiveHistoryDao.delete(id)

    suspend fun clearHistory(book: Book?, onlyThisBook: Boolean) {
        if (onlyThisBook && book != null) {
            effectiveHistoryDao.deleteByBook(book.name, book.author)
        } else {
            effectiveHistoryDao.deleteAll()
        }
    }

    private var lastSearchResults: List<SearchResult>? = null
    private var lastQueryKey: String? = null
    private var lastSearchSession: SearchSession? = null

    data class SearchSession(
        val bookUrl: String,
        val query: String,
        val replaceEnabled: Boolean,
        val regexReplace: Boolean,
        val results: List<SearchResult>,
    )

    fun getLastSession(bookUrl: String): SearchSession? {
        return lastSearchSession?.takeIf { it.bookUrl == bookUrl }
    }

    fun beginSearch(
        bookUrl: String,
        query: String,
        replaceEnabled: Boolean,
        regexReplace: Boolean,
    ) {
        cacheResults(bookUrl, query, replaceEnabled, regexReplace, emptyList())
    }

    fun clearSession(bookUrl: String) {
        if (lastSearchSession?.bookUrl == bookUrl) {
            lastSearchResults = null
            lastQueryKey = null
            lastSearchSession = null
        }
    }

    fun getCache(
        bookUrl: String,
        query: String,
        replaceEnabled: Boolean,
        regexReplace: Boolean
    ): List<SearchResult>? {
        val key = searchKey(bookUrl, query, replaceEnabled, regexReplace)
        return if (lastQueryKey == key) lastSearchResults else null
    }

    fun search(book: Book, query: String, replaceEnabled: Boolean, regexReplace: Boolean): Flow<List<SearchResult>> = flow {
        val chapters = appDb.bookChapterDao.getChapterList(book.bookUrl)
        val totalChapters = chapters.size
        val contentProcessor = ContentProcessor.get(book.name, book.origin)
        val chineseConverterType = readSettingsGateway?.currentSettings?.chineseConverterType ?: 0
        val defaultReplaceEnabled = otherSettingsGateway?.currentSettings?.replaceEnableDefault ?: true
        val cacheChapterNames = BookHelp.getChapterFiles(book).toHashSet()

        val allResults = mutableListOf<SearchResult>()

        for (bookChapter in chapters) {

            currentCoroutineContext().ensureActive()

            if (book.isLocal || cacheChapterNames.contains(bookChapter.getFileName())) {
                val chapterResults = searchChapter(
                    query,
                    book,
                    bookChapter,
                    contentProcessor,
                    replaceEnabled,
                    regexReplace,
                    chineseConverterType,
                    defaultReplaceEnabled,
                ).map {
                    if (totalChapters > 0) {
                        it.copy(progressPercent = (bookChapter.index + 1).toFloat() / totalChapters * 100f)
                    } else {
                        it
                    }
                }

                if (chapterResults.isNotEmpty()) {
                    allResults.addAll(chapterResults)
                    cacheResults(book.bookUrl, query, replaceEnabled, regexReplace, allResults)
                    emit(ArrayList(allResults))
                }
            }
        }
        cacheResults(book.bookUrl, query, replaceEnabled, regexReplace, allResults)
        emit(ArrayList(allResults))
    }.flowOn(Dispatchers.Default)

    private fun cacheResults(
        bookUrl: String,
        query: String,
        replaceEnabled: Boolean,
        regexReplace: Boolean,
        results: List<SearchResult>,
    ) {
        val cachedResults = ArrayList(results)
        lastSearchResults = cachedResults
        lastQueryKey = searchKey(bookUrl, query, replaceEnabled, regexReplace)
        lastSearchSession = SearchSession(
            bookUrl = bookUrl,
            query = query,
            replaceEnabled = replaceEnabled,
            regexReplace = regexReplace,
            results = cachedResults,
        )
    }

    private fun searchKey(
        bookUrl: String,
        query: String,
        replaceEnabled: Boolean,
        regexReplace: Boolean
    ): String {
        return "$bookUrl-$query-$replaceEnabled-$regexReplace-${titleModeProvider()}"
    }

    private suspend fun searchChapter(
        query: String,
        book: Book,
        chapter: BookChapter,
        contentProcessor: ContentProcessor,
        replaceEnabled: Boolean,
        regexReplace: Boolean,
        chineseConverterType: Int,
        defaultReplaceEnabled: Boolean,
    ): List<SearchResult> {
        val searchResultsWithinChapter: MutableList<SearchResult> = mutableListOf()
        val chapterContent = BookHelp.getContent(book, chapter) ?: return searchResultsWithinChapter

        chapter.title = when (chineseConverterType) {
            1 -> ChineseUtils.t2s(chapter.title)
            2 -> ChineseUtils.s2t(chapter.title)
            else -> chapter.title
        }

        val bodyContent = contentProcessor.getContent(
            book,
            chapter,
            chapterContent,
            includeTitle = false,
            useReplace = replaceEnabled
        )
        val includeTitle = titleModeProvider() != 2 ||
                chapter.isVolume ||
                bodyContent.textList.isEmpty()
        val mContent = if (includeTitle) {
            val title = chapter.getDisplayTitle(
                contentProcessor.getTitleReplaceRules(),
                useReplace = replaceEnabled && book.getUseReplaceRule(defaultReplaceEnabled),
                chineseConverterType = chineseConverterType,
            )
            listOf(title).plus(bodyContent.textList).joinToString("\n")
        } else {
            bodyContent.toString()
        }

        val matches = searchPosition(mContent, query, regexReplace)

        matches.forEachIndexed { index, match ->
            val construct = getResultAndQueryIndex(mContent, match.position, match.length)
            val result = SearchResult(
                bookUrl = book.bookUrl,
                resultCountWithinChapter = index,
                resultText = construct.second,
                chapterTitle = chapter.title,
                query = query,
                chapterIndex = chapter.index,
                queryIndexInResult = construct.first,
                queryIndexInChapter = match.position,
                matchLength = match.length,
                isRegex = regexReplace
            )
            searchResultsWithinChapter.add(result)
        }
        return searchResultsWithinChapter
    }

    private fun searchPosition(content: String, pattern: String, regexReplace: Boolean): List<SearchMatch> {
        val positions: MutableList<SearchMatch> = mutableListOf()
        if (regexReplace) { // 正则表达式搜索
            try {
                Regex(pattern).findAll(content).forEach { match ->
                    positions.add(SearchMatch(match.range.first, match.value.length))
                }
            } catch (e: Exception) {
                return positions
            }
        } else {
            var index = content.indexOf(pattern)
            while (index >= 0) {
                positions.add(SearchMatch(index, pattern.length))
                index = content.indexOf(pattern, index + pattern.length)
            }
        }
        return positions
    }

    private fun getResultAndQueryIndex(
        content: String,
        queryIndexInContent: Int,
        matchLength: Int
    ): Pair<Int, String> {
        val length = 12
        var po1 = queryIndexInContent - length
        var po2 = queryIndexInContent + matchLength + length
        if (po1 < 0) {
            po1 = 0
        }
        if (po2 > content.length) {
            po2 = content.length
        }
        val queryIndexInResult = queryIndexInContent - po1
        val newText = content.substring(po1, po2)
        return queryIndexInResult to newText
    }

    private data class SearchMatch(
        val position: Int,
        val length: Int,
    )

}
