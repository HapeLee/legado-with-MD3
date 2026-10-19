package io.legado.app.data.repository

import io.legado.app.data.appDb
import io.legado.app.data.dao.SearchContentHistoryDao
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.SearchContentHistory
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.gateway.ReadSettingsGateway
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.book.isLocal
import io.legado.app.ui.book.searchContent.SearchResult
import io.legado.app.utils.ChineseUtils
import io.legado.app.utils.RegexDeadlineSequence
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

    /**
     * 每次 [beginSearch] / [clearSession] 自增。被取消的旧搜索可能还卡在不可中断的
     * `BookHelp.getContent` 里，回到主流程后不能再用旧结果覆盖新会话的缓存。
     * 主线程递增、Default 线程读取，用 volatile 保证可见性。
     */
    @Volatile
    private var searchGeneration = 0

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
        book: Book?,
        query: String,
        replaceEnabled: Boolean,
        regexReplace: Boolean,
    ) {
        searchGeneration += 1
        // 书还没加载好时只做代次失效，不要用空 bookUrl 覆盖可恢复的最近会话。
        if (book == null) return
        cacheResults(book, query, replaceEnabled, regexReplace, emptyList())
    }

    fun clearSession(bookUrl: String) {
        if (lastSearchSession?.bookUrl == bookUrl) {
            searchGeneration += 1
            lastSearchResults = null
            lastQueryKey = null
            lastSearchSession = null
        }
    }

    fun getCache(
        book: Book?,
        query: String,
        replaceEnabled: Boolean,
        regexReplace: Boolean
    ): List<SearchResult>? {
        val key = searchKey(book, query, replaceEnabled, regexReplace)
        return if (lastQueryKey == key) lastSearchResults else null
    }

    fun search(book: Book, query: String, replaceEnabled: Boolean, regexReplace: Boolean): Flow<List<SearchResult>> = flow {
        // 冷流在收集时才拿到当前代次：收集前若已被新的 beginSearch 取代，本代次结果不再落缓存。
        val generation = searchGeneration
        val chapters = appDb.bookChapterDao.getChapterList(book.bookUrl)
        val totalChapters = chapters.size
        val contentProcessor = ContentProcessor.get(book.name, book.origin)
        val chineseConverterType = readSettingsGateway?.currentSettings?.chineseConverterType ?: 0
        val defaultReplaceEnabled = otherSettingsGateway?.currentSettings?.replaceEnableDefault ?: true
        val cacheChapterNames = BookHelp.getChapterFiles(book).toHashSet()

        val allResults = mutableListOf<SearchResult>()
        fun publishCache(results: List<SearchResult>) {
            if (generation == searchGeneration) {
                cacheResults(book, query, replaceEnabled, regexReplace, results)
            }
        }

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
                    publishCache(allResults)
                    emit(ArrayList(allResults))
                }
            }
        }
        publishCache(allResults)
        emit(ArrayList(allResults))
    }.flowOn(Dispatchers.Default)

    private fun cacheResults(
        book: Book?,
        query: String,
        replaceEnabled: Boolean,
        regexReplace: Boolean,
        results: List<SearchResult>,
    ) {
        val cachedResults = ArrayList(results)
        lastSearchResults = cachedResults
        lastQueryKey = searchKey(book, query, replaceEnabled, regexReplace)
        lastSearchSession = SearchSession(
            bookUrl = book?.bookUrl.orEmpty(),
            query = query,
            replaceEnabled = replaceEnabled,
            regexReplace = regexReplace,
            results = cachedResults,
        )
    }

    /**
     * 缓存键必须覆盖所有会改变正文文本的输入：标题模式、简繁转换、净化开关。
     * 否则切换这些设置后重新进入搜索会复用旧结果，连带复用旧偏移。
     */
    private fun searchKey(
        book: Book?,
        query: String,
        replaceEnabled: Boolean,
        regexReplace: Boolean
    ): String {
        val chineseConverterType = readSettingsGateway?.currentSettings?.chineseConverterType ?: 0
        val defaultReplaceEnabled =
            otherSettingsGateway?.currentSettings?.replaceEnableDefault ?: true
        val useReplaceRule = book?.getUseReplaceRule(defaultReplaceEnabled) ?: defaultReplaceEnabled
        return buildString {
            append(book?.bookUrl).append('|')
            append(query).append('|')
            append(replaceEnabled).append('|')
            append(regexReplace).append('|')
            append(titleModeProvider()).append('|')
            append(chineseConverterType).append('|')
            append(useReplaceRule)
        }
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

        // 不修改 DAO 返回的实体：缓存文件名、替换规则的标题匹配都还要用原始标题。
        val resultChapterTitle = when (chineseConverterType) {
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
            // 标题净化必须与阅读页一致（阅读页不看"搜索替换"开关）。若这里受开关影响，
            // 标题长度不同会让本章所有正文命中整体位移，直达下标就会落到另一个同词上。
            val title = chapter.getDisplayTitle(
                contentProcessor.getTitleReplaceRules(),
                useReplace = book.getUseReplaceRule(defaultReplaceEnabled),
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
                chapterTitle = resultChapterTitle,
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
        // 空关键词下 indexOf 恒返回 0（死循环）、正则匹配每个位置；调用方负责挡掉空白查询。
        if (pattern.isEmpty()) return positions
        if (regexReplace) { // 正则表达式搜索
            val regex = runCatching { Regex(pattern) }.getOrNull() ?: return positions
            // 正则无法被协程取消，只能靠字符访问时的截止时间抛出来打断指数回溯。
            val guarded = RegexDeadlineSequence(content, REGEX_SEARCH_TIMEOUT_MILLIS)
            var match = regex.find(guarded)
            while (match != null) {
                val start = match.range.first
                val length = match.range.last + 1 - start
                // 零宽匹配（`a*`、`(?=x)` 等）拼不出合法选区，focus 会落到 anchor 前一位。
                if (length > 0) {
                    positions.add(SearchMatch(start, length))
                }
                match = match.next()
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
        var po1 = (queryIndexInContent - length).coerceAtLeast(0)
        var po2 = (queryIndexInContent + matchLength + length).coerceAtMost(content.length)
        // 不要在代理对中间切片段：resultText 里出现半个 emoji 会渲染成乱码。
        if (po1 in 1 until content.length && content[po1].isLowSurrogate()) po1 -= 1
        if (po2 in 1 until content.length && content[po2 - 1].isHighSurrogate()) po2 += 1
        val queryIndexInResult = queryIndexInContent - po1
        val newText = content.substring(po1, po2)
        return queryIndexInResult to newText
    }

    private data class SearchMatch(
        val position: Int,
        val length: Int,
    )

}

private const val REGEX_SEARCH_TIMEOUT_MILLIS = 3_000L
