package io.legato.kazusa.model.webBook

import io.legato.kazusa.R
import io.legato.kazusa.constant.AppPattern
import io.legato.kazusa.data.appDb
import io.legato.kazusa.data.entities.Book
import io.legato.kazusa.data.entities.BookChapter
import io.legato.kazusa.data.entities.BookSource
import io.legato.kazusa.data.entities.rule.ContentRule
import io.legato.kazusa.exception.ContentEmptyException
import io.legato.kazusa.exception.NoStackTraceException
import io.legato.kazusa.help.book.BookHelp
import io.legato.kazusa.help.config.AppConfig
import io.legato.kazusa.model.Debug
import io.legato.kazusa.model.analyzeRule.AnalyzeRule
import io.legato.kazusa.model.analyzeRule.AnalyzeRule.Companion.setChapter
import io.legato.kazusa.model.analyzeRule.AnalyzeRule.Companion.setCoroutineContext
import io.legato.kazusa.model.analyzeRule.AnalyzeRule.Companion.setNextChapterUrl
import io.legato.kazusa.model.analyzeRule.AnalyzeUrl
import io.legato.kazusa.utils.HtmlFormatter
import io.legato.kazusa.utils.NetworkUtils
import io.legato.kazusa.utils.mapAsync
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.flow
import org.apache.commons.text.StringEscapeUtils
import splitties.init.appCtx
import kotlin.coroutines.coroutineContext

/**
 * 获取正文
 */
object BookContent {

    @Throws(Exception::class)
    suspend fun analyzeContent(
        bookSource: BookSource,
        book: Book,
        bookChapter: BookChapter,
        baseUrl: String,
        redirectUrl: String,
        body: String?,
        nextChapterUrl: String?,
        needSave: Boolean = true
    ): String {
        body ?: throw NoStackTraceException(
            appCtx.getString(R.string.error_get_web_content, baseUrl)
        )
        Debug.log(bookSource.bookSourceUrl, "≡获取成功:${baseUrl}")
        Debug.log(bookSource.bookSourceUrl, body, state = 40)
        val mNextChapterUrl = if (nextChapterUrl.isNullOrEmpty()) {
            appDb.bookChapterDao.getChapter(book.bookUrl, bookChapter.index + 1)?.url
                ?: appDb.bookChapterDao.getChapter(book.bookUrl, 0)?.url
        } else {
            nextChapterUrl
        }
        val contentList = arrayListOf<String>()
        val nextUrlList = arrayListOf(redirectUrl)
        val contentRule = bookSource.getContentRule()
        val analyzeRule = AnalyzeRule(book, bookSource)
        analyzeRule.setContent(body, baseUrl)
        analyzeRule.setRedirectUrl(redirectUrl)
        analyzeRule.setCoroutineContext(coroutineContext)
        analyzeRule.setChapter(bookChapter)
        analyzeRule.setNextChapterUrl(mNextChapterUrl)
        coroutineContext.ensureActive()
        var contentData = analyzeContent(
            book, baseUrl, redirectUrl, body, contentRule, bookChapter, bookSource, mNextChapterUrl
        )
        contentList.add(contentData.first)
        if (contentData.second.size == 1) {
            var nextUrl = contentData.second[0]
            while (nextUrl.isNotEmpty() && !nextUrlList.contains(nextUrl)) {
                if (!mNextChapterUrl.isNullOrEmpty()
                    && NetworkUtils.getAbsoluteURL(redirectUrl, nextUrl)
                    == NetworkUtils.getAbsoluteURL(redirectUrl, mNextChapterUrl)
                ) break
                nextUrlList.add(nextUrl)
                coroutineContext.ensureActive()
                val analyzeUrl = AnalyzeUrl(
                    mUrl = nextUrl,
                    source = bookSource,
                    ruleData = book,
                    coroutineContext = coroutineContext
                )
                val res = analyzeUrl.getStrResponseAwait() //控制并发访问
                res.body?.let { nextBody ->
                    contentData = analyzeContent(
                        book, nextUrl, res.url, nextBody, contentRule,
                        bookChapter, bookSource, mNextChapterUrl,
                        printLog = false
                    )
                    nextUrl =
                        if (contentData.second.isNotEmpty()) contentData.second[0] else ""
                    contentList.add(contentData.first)
                    Debug.log(bookSource.bookSourceUrl, "第${contentList.size}页完成")
                }
            }
            Debug.log(bookSource.bookSourceUrl, "◇本章总页数:${nextUrlList.size}")
        } else if (contentData.second.size > 1) {
            Debug.log(bookSource.bookSourceUrl, "◇并发解析正文,总页数:${contentData.second.size}")
            flow {
                for (urlStr in contentData.second) {
                    emit(urlStr)
                }
            }.mapAsync(AppConfig.threadCount) { urlStr ->
                val analyzeUrl = AnalyzeUrl(
                    mUrl = urlStr,
                    source = bookSource,
                    ruleData = book,
                    coroutineContext = coroutineContext
                )
                val res = analyzeUrl.getStrResponseAwait() //控制并发访问
                analyzeContent(
                    book, urlStr, res.url, res.body!!, contentRule,
                    bookChapter, bookSource, mNextChapterUrl,
                    getNextPageUrl = false,
                    printLog = false
                ).first
            }.collect {
                coroutineContext.ensureActive()
                contentList.add(it)
            }
        }
        var contentStr = contentList.joinToString("\n")
        val titleRule = contentRule.title //先正文再章节名称
        if (!titleRule.isNullOrBlank()) {
            var title = analyzeRule.runCatching {
                getString(titleRule)
            }.onFailure {
                Debug.log(bookSource.bookSourceUrl, "获取标题出错, ${it.localizedMessage}")
            }.getOrNull()
            if (!title.isNullOrBlank()) {
                val matchResult = AppPattern.imgRegex.find(title)
                if (matchResult != null) {
                    matchResult.groupValues[1]
                    val (group1,group2) = matchResult.destructured
                    title = if (group1 != "") {
                        group1
                    } else {
                        bookChapter.title
                    }
                    bookChapter.reviewImg = group2
                }
                bookChapter.title = title
                bookChapter.titleMD5 = null
                appDb.bookChapterDao.update(bookChapter)
            }
        }
        //全文替换
        val replaceRegex = contentRule.replaceRegex
        if (!replaceRegex.isNullOrEmpty()) {
            contentStr = contentStr.split(AppPattern.LFRegex).joinToString("\n") { it.trim() }
            contentStr = analyzeRule.getString(replaceRegex, contentStr)
            contentStr = contentStr.split(AppPattern.LFRegex).joinToString("\n") { "　　$it" }
        }
        Debug.log(bookSource.bookSourceUrl, "┌获取章节名称")
        Debug.log(bookSource.bookSourceUrl, "└${bookChapter.title}")
        Debug.log(bookSource.bookSourceUrl, "┌获取正文内容")
        Debug.log(bookSource.bookSourceUrl, "└\n$contentStr")
        if (!bookChapter.isVolume && contentStr.isBlank()) {
            throw ContentEmptyException("内容为空")
        }
        if (needSave) {
            BookHelp.saveContent(bookSource, book, bookChapter, contentStr)
        }
        return contentStr
    }

    @Throws(Exception::class)
    private suspend fun analyzeContent(
        book: Book,
        baseUrl: String,
        redirectUrl: String,
        body: String,
        contentRule: ContentRule,
        chapter: BookChapter,
        bookSource: BookSource,
        nextChapterUrl: String?,
        getNextPageUrl: Boolean = true,
        printLog: Boolean = true
    ): Pair<String, List<String>> {
        val analyzeRule = AnalyzeRule(book, bookSource)
        analyzeRule.setContent(body, baseUrl)
        analyzeRule.setCoroutineContext(coroutineContext)
        val rUrl = analyzeRule.setRedirectUrl(redirectUrl)
        analyzeRule.setNextChapterUrl(nextChapterUrl)
        val nextUrlList = arrayListOf<String>()
        analyzeRule.setChapter(chapter)
        //获取正文
        var content = analyzeRule.getString(contentRule.content, unescape = false)
        content = HtmlFormatter.formatKeepImg(content, rUrl)
        if (content.indexOf('&') > -1) {
            content = StringEscapeUtils.unescapeHtml4(content)
        }
        //获取下一页链接
        if (getNextPageUrl) {
            val nextUrlRule = contentRule.nextContentUrl
            if (!nextUrlRule.isNullOrEmpty()) {
                Debug.log(bookSource.bookSourceUrl, "┌获取正文下一页链接", printLog)
                analyzeRule.getStringList(nextUrlRule, isUrl = true)?.let {
                    nextUrlList.addAll(it)
                }
                Debug.log(bookSource.bookSourceUrl, "└" + nextUrlList.joinToString("，"), printLog)
            }
        }
        return Pair(content, nextUrlList)
    }
}
