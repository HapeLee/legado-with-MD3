package io.legado.app.data.repository

import com.script.ScriptException
import io.legado.app.constant.BookSourceType
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSource
import io.legado.app.domain.gateway.BookSourceCheckGateway
import io.legado.app.domain.gateway.BookSourceCheckFailure
import io.legado.app.domain.gateway.BookSourceCheckResult
import io.legado.app.domain.gateway.BookSourceCheckState
import io.legado.app.domain.gateway.BookSourceCheckStatus
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
        _state.value = BookSourceCheckState(
            isRunning = true,
            total = sourceIds.size,
            results = sourceIds.associateWith {
                BookSourceCheckResult(BookSourceCheckStatus.Pending)
            },
        )
        val dispatcher = Executors.newFixedThreadPool(
            downloadCacheSettingsGateway.currentSettings.threadCount.coerceAtLeast(1)
        ).asCoroutineDispatcher()
        var completedNormally = false
        try {
            coroutineScope {
                sourceIds.map { id ->
                    async(dispatcher) {
                        runCatching { checkOne(id, keyword.ifBlank { "我的" }) }
                            .onFailure { error ->
                                coroutineContext.ensureActive()
                                updateResult(
                                    id = id,
                                    name = "",
                                    status = BookSourceCheckStatus.Failed,
                                    failure = BookSourceCheckFailure.CheckFailed,
                                    detail = error.displayMessage(),
                                )
                            }
                    }
                }.awaitAll()
            }
            completedNormally = true
        } finally {
            dispatcher.close()
            val unfinishedStatus = if (completedNormally) {
                BookSourceCheckStatus.Failed
            } else {
                BookSourceCheckStatus.Cancelled
            }
            _state.update { state ->
                val results = state.results.mapValues { (_, result) ->
                    if (result.status.isTerminal) result
                    else BookSourceCheckResult(
                        status = unfinishedStatus,
                        failure = BookSourceCheckFailure.Incomplete
                            .takeIf { completedNormally },
                    )
                }
                state.copy(
                    isRunning = false,
                    completed = results.values.count { it.status.isTerminal },
                    currentSourceName = "",
                    results = results,
                )
            }
        }
    }

    private suspend fun checkOne(sourceId: String, keyword: String) {
        val source = bookSourceRepository.getBookSource(sourceId)
        if (source == null) {
            updateResult(
                id = sourceId,
                name = "",
                status = BookSourceCheckStatus.Failed,
                failure = BookSourceCheckFailure.SourceMissing,
            )
            return
        }
        val startedAt = System.currentTimeMillis()
        updateResult(
            sourceId,
            source.bookSourceName,
            BookSourceCheckStatus.Running,
        )
        val result = runCatching {
            withTimeout(settingsGateway.currentSettings.timeoutMillis) {
                checkSource(source, keyword)
            }
        }
        result.onSuccess {
            source.respondTime = System.currentTimeMillis() - startedAt
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
        }
        val saveResult = runCatching { bookSourceRepository.updateSources(source) }
        coroutineContext.ensureActive()
        when {
            result.isFailure -> updateResult(
                sourceId,
                source.bookSourceName,
                BookSourceCheckStatus.Failed,
                BookSourceCheckFailure.CheckFailed,
                result.exceptionOrNull().displayMessage(),
            )

            saveResult.isFailure -> updateResult(
                sourceId,
                source.bookSourceName,
                BookSourceCheckStatus.Failed,
                BookSourceCheckFailure.SaveFailed,
                saveResult.exceptionOrNull().displayMessage(),
            )

            else -> updateResult(
                sourceId,
                source.bookSourceName,
                BookSourceCheckStatus.Succeeded,
            )
        }
    }

    private fun updateResult(
        id: String,
        name: String,
        status: BookSourceCheckStatus,
        failure: BookSourceCheckFailure? = null,
        detail: String? = null,
    ) {
        _state.update { state ->
            val previous = state.results[id]
            val completed = when {
                previous?.status?.isTerminal == true -> state.completed
                status.isTerminal -> state.completed + 1
                else -> state.completed
            }
            state.copy(
                completed = completed,
                currentSourceName = name.ifBlank { state.currentSourceName },
                results = state.results + (
                    id to BookSourceCheckResult(status, failure, detail)
                ),
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

private fun Throwable?.displayMessage(): String? =
    this?.localizedMessage?.takeIf { it.isNotBlank() }
        ?: this?.javaClass?.simpleName?.takeIf { it.isNotBlank() }
