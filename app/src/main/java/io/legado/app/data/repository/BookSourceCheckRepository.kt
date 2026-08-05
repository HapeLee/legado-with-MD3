package io.legado.app.data.repository

import com.script.ScriptException
import io.legado.app.constant.BookSourceType
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSource
import io.legado.app.domain.gateway.BookSourceCheckGateway
import io.legado.app.domain.gateway.BookSourceCheckState
import io.legado.app.domain.gateway.CheckSourceSettingsGateway
import io.legado.app.domain.gateway.DownloadCacheSettingsGateway
import io.legado.app.exception.ContentEmptyException
import io.legado.app.exception.NoStackTraceException
import io.legado.app.exception.TocEmptyException
import io.legado.app.help.source.exploreKinds
import io.legado.app.model.webBook.WebBook
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeout
import org.mozilla.javascript.WrappedException
import java.util.concurrent.Executors
import kotlin.coroutines.coroutineContext

class BookSourceCheckRepository(
    private val bookSourceRepository: BookSourceRepository,
    private val settingsGateway: CheckSourceSettingsGateway,
    private val downloadCacheSettingsGateway: DownloadCacheSettingsGateway,
) : BookSourceCheckGateway {
    private val _state = MutableStateFlow(BookSourceCheckState())
    override val state = _state.asStateFlow()
    override suspend fun check(sourceIds: Set<String>, keyword: String) {
        if (sourceIds.isEmpty() || _state.value.isRunning) return
        _state.value = BookSourceCheckState(isRunning = true, total = sourceIds.size)
        val dispatcher = Executors.newFixedThreadPool(
            downloadCacheSettingsGateway.currentSettings.threadCount.coerceAtLeast(1)
        ).asCoroutineDispatcher()
        try {
            coroutineScope {
                sourceIds.map { id ->
                    async(dispatcher) { checkOne(id, keyword.ifBlank { "我的" }) }
                }.awaitAll()
            }
        } finally {
            dispatcher.close()
            _state.update { it.copy(isRunning = false, currentSourceName = "") }
        }
    }

    private suspend fun checkOne(sourceId: String, keyword: String) {
        val source = bookSourceRepository.getBookSource(sourceId) ?: return
        val startedAt = System.currentTimeMillis()
        updateResult(sourceId, source.bookSourceName, "开始校验")
        val result = runCatching {
            withTimeout(settingsGateway.currentSettings.timeoutMillis) {
                checkSource(source, keyword)
            }
        }
        result.onSuccess {
            source.respondTime = System.currentTimeMillis() - startedAt
            updateResult(sourceId, source.bookSourceName, "校验成功", completed = true)
        }.onFailure { error ->
            coroutineContext.ensureActive()
            when (error) {
                is TimeoutCancellationException -> source.addGroup("校验超时")
                is ScriptException, is WrappedException -> source.addGroup("js失效")
                !is NoStackTraceException -> source.addGroup("网站失效")
            }
            source.addErrorComment(error)
            source.respondTime =
                settingsGateway.currentSettings.timeoutMillis + System.currentTimeMillis() - startedAt
            updateResult(
                sourceId,
                source.bookSourceName,
                "校验失败:${error.localizedMessage}",
                completed = true
            )
        }
        bookSourceRepository.updateSources(source)
    }

    private fun updateResult(
        id: String,
        name: String,
        message: String,
        completed: Boolean = false
    ) {
        _state.update { state ->
            state.copy(
                completed = state.completed + if (completed) 1 else 0,
                currentSourceName = name,
                results = state.results + (id to message),
            )
        }
    }

    private suspend fun checkSource(source: BookSource, keyword: String) {
        val settings = settingsGateway.currentSettings
        source.removeInvalidGroups()
        source.removeErrorComment()
        if (settings.checkSearch) {
            val word = source.getCheckKeyword(keyword)
            if (source.searchUrl.isNullOrBlank()) source.addGroup("搜索链接规则为空")
            else {
                source.removeGroup("搜索链接规则为空")
                val books = WebBook.searchBookAwait(source, word)
                if (books.isEmpty()) source.addGroup("搜索失效")
                else {
                    source.removeGroup("搜索失效"); checkBook(books.first().toBook(), source, true)
                }
            }
        }
        if (settings.checkDiscovery && !source.exploreUrl.isNullOrBlank()) {
            val url = source.exploreKinds().firstOrNull { !it.url.isNullOrBlank() }?.url
            if (url.isNullOrBlank()) source.addGroup("发现规则为空")
            else {
                source.removeGroup("发现规则为空")
                val books = WebBook.exploreBookAwait(source, url)
                if (books.isEmpty()) source.addGroup("发现失效")
                else {
                    source.removeGroup("发现失效"); checkBook(books.first().toBook(), source, false)
                }
            }
        }
        source.getInvalidGroupNames().takeIf { it.isNotBlank() }
            ?.let { throw NoStackTraceException(it) }
    }

    private suspend fun checkBook(book: Book, source: BookSource, searchBook: Boolean) {
        val settings = settingsGateway.currentSettings
        runCatching {
            if (!settings.checkInfo) return
            if (book.tocUrl.isBlank()) WebBook.getBookInfoAwait(source, book)
            if (!settings.checkCategory || source.bookSourceType == BookSourceType.file) return
            val toc = WebBook.getChapterListAwait(source, book).getOrThrow().asSequence()
                .filter { !(it.isVolume && it.url.startsWith(it.title)) }.take(2).toList()
            val nextUrl = toc.getOrNull(1)?.url ?: toc.first().url
            if (settings.checkContent) WebBook.getContentAwait(
                source,
                book,
                toc.first(),
                nextUrl,
                false
            )
        }.onFailure { error ->
            val type = if (searchBook) "搜索" else "发现"
            when (error) {
                is ContentEmptyException -> source.addGroup("${type}正文失效")
                is TocEmptyException -> source.addGroup("${type}目录失效")
                else -> throw error
            }
        }.onSuccess {
            val type = if (searchBook) "搜索" else "发现"
            source.removeGroup("${type}目录失效")
            source.removeGroup("${type}正文失效")
        }
    }
}
