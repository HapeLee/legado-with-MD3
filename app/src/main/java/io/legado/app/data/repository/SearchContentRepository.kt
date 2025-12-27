package io.legado.app.data.repository

import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.book.isLocal
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.book.searchContent.SearchResult
import io.legado.app.utils.ChineseUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class SearchContentRepository {

    fun search(book: Book, query: String, replaceEnabled: Boolean, regexReplace: Boolean): Flow<List<SearchResult>> = flow {
        val chapters = appDb.bookChapterDao.getChapterList(book.bookUrl)
        val totalChapters = chapters.size
        val contentProcessor = ContentProcessor.get(book.name, book.origin)
        val cacheChapterNames = BookHelp.getChapterFiles(book).toHashSet()

        val allResults = mutableListOf<SearchResult>()
        var lastEmitTime = System.currentTimeMillis()

        for (bookChapter in chapters) {

            currentCoroutineContext().ensureActive()

            if (book.isLocal || cacheChapterNames.contains(bookChapter.getFileName())) {
                val chapterResults = searchChapter(
                    query,
                    book,
                    bookChapter,
                    contentProcessor,
                    replaceEnabled,
                    regexReplace
                ).map {
                    if (totalChapters > 0) {
                        it.copy(progressPercent = (bookChapter.index + 1).toFloat() / totalChapters * 100f)
                    } else {
                        it
                    }
                }

                if (chapterResults.isNotEmpty()) {
                    allResults.addAll(chapterResults)

                    val now = System.currentTimeMillis()
                    if (now - lastEmitTime > 350L) {
                        emit(ArrayList(allResults))
                        lastEmitTime = now
                    }
                }
            }
        }
        emit(ArrayList(allResults))
    }.flowOn(Dispatchers.Default)

    private suspend fun searchChapter(
        query: String,
        book: Book,
        chapter: BookChapter,
        contentProcessor: ContentProcessor,
        replaceEnabled: Boolean,
        regexReplace: Boolean
    ): List<SearchResult> {
        val searchResultsWithinChapter: MutableList<SearchResult> = mutableListOf()
        val chapterContent = BookHelp.getContent(book, chapter) ?: return searchResultsWithinChapter

        chapter.title = when (AppConfig.chineseConverterType) {
            1 -> ChineseUtils.t2s(chapter.title)
            2 -> ChineseUtils.s2t(chapter.title)
            else -> chapter.title
        }

        val mContent = contentProcessor.getContent(
            book, chapter, chapterContent, useReplace = replaceEnabled
        ).toString()

        val positions = searchPosition(mContent, query, regexReplace)

        positions.forEachIndexed { index, position ->
            val construct = getResultAndQueryIndex(mContent, position, query)
            val result = SearchResult(
                resultCountWithinChapter = index,
                resultText = construct.second,
                chapterTitle = chapter.title,
                query = query,
                chapterIndex = chapter.index,
                queryIndexInResult = construct.first,
                queryIndexInChapter = position,
                isRegex = regexReplace
            )
            searchResultsWithinChapter.add(result)
        }
        return searchResultsWithinChapter
    }

    private fun searchPosition(content: String, pattern: String, regexReplace: Boolean): List<Int> {
        val position: MutableList<Int> = mutableListOf()
        if (regexReplace) { // 正则表达式搜索
            try {
                Regex(pattern).findAll(content).forEach { match ->
                    position.add(match.range.first)
                }
            } catch (e: Exception) {
                return position
            }
        } else {
            var index = content.indexOf(pattern)
            while (index >= 0) {
                position.add(index)
                index = content.indexOf(pattern, index + pattern.length)
            }
        }
        return position
    }

    private fun getResultAndQueryIndex(
        content: String,
        queryIndexInContent: Int,
        query: String
    ): Pair<Int, String> {
        val length = 12
        var po1 = queryIndexInContent - length
        var po2 = queryIndexInContent + query.length + length
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

}
