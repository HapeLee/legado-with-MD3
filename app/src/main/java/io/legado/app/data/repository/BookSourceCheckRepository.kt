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
import io.legado.app.domain.gateway.CheckSourceSettings
import io.legado.app.domain.gateway.CheckSourceSettingsGateway
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
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import org.mozilla.javascript.WrappedException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.util.concurrent.Executors
import kotlin.coroutines.coroutineContext

class BookSourceCheckRepository(
    private val bookSourceRepository: BookSourceRepository,
    private val settingsGateway: CheckSourceSettingsGateway,
) : BookSourceCheckGateway {
    private val _state = MutableStateFlow(BookSourceCheckState())
    override val state = _state.asStateFlow()

    override suspend fun check(sourceIds: Set<String>, keyword: String) {
        if (sourceIds.isEmpty() || _state.value.isRunning) return
        val settings = settingsGateway.currentSettings
        _state.value = BookSourceCheckState(
            isRunning = true,
            total = sourceIds.size,
            results = sourceIds.associateWith {
                BookSourceCheckResult(BookSourceCheckStatus.Pending)
            },
        )
        val dispatcher = Executors.newFixedThreadPool(settings.threadCount)
            .asCoroutineDispatcher()
        var completedNormally = false
        try {
            coroutineScope {
                sourceIds.map { id ->
                    async(dispatcher) {
                        runCatching { checkOne(id, keyword, settings) }
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

    private suspend fun checkOne(
        sourceId: String,
        keyword: String,
        settings: CheckSourceSettings,
    ) {
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
            withTimeout(settings.timeoutMillis) {
                checkSource(source, keyword, settings)
            }
        }
        result.onSuccess {
            source.respondTime = System.currentTimeMillis() - startedAt
        }.onFailure { error ->
            coroutineContext.ensureActive()
            checkFailureGroup(error)?.let { source.addGroup(it) }
            if (settings.writeSourceComment) source.addErrorComment(error)
            source.respondTime = settings.timeoutMillis + System.currentTimeMillis() - startedAt
        }
        val saveResult = runCatching { bookSourceRepository.updateCheckResult(source) }
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

    private suspend fun checkSource(
        source: BookSource,
        keyword: String,
        settings: CheckSourceSettings,
    ) {
        source.updateGroups { removeInvalidGroups() }
        if (settings.writeSourceComment) source.updateGroups { removeErrorComment() }
        if (settings.checkDomain) checkDomainReachable(source)
        if (settings.quickMode) {
            checkBranchesConcurrently(source, keyword, settings)
        } else {
            checkSearchBranch(source, keyword, settings)
            checkDiscoveryBranch(source, settings)
        }
        source.getInvalidGroupNames().takeIf { it.isNotBlank() }
            ?.let { throw NoStackTraceException(it) }
    }

    /**
     * 快速模式：搜索与发现并发执行，任一分支失败即整体判失败。
     */
    private suspend fun checkBranchesConcurrently(
        source: BookSource,
        keyword: String,
        settings: CheckSourceSettings,
    ) = supervisorScope {
        listOf(
            async { runCatching { checkSearchBranch(source, keyword, settings) } },
            async { runCatching { checkDiscoveryBranch(source, settings) } },
        ).awaitAll().firstNotNullOfOrNull { it.exceptionOrNull() }?.let { throw it }
    }

    /**
     * 校验源地址端口连通性，避免对已下线的域名跑完整规则。
     */
    private suspend fun checkDomainReachable(source: BookSource) {
        val domain = source.bookSourceUrl
        if (!domain.startsWith("http", ignoreCase = true)) {
            throw NoStackTraceException("源地址不是http链接")
        }
        if (isDomainReachable(domain)) {
            source.removeGroupSafe(GROUP_DOMAIN_INVALID)
        } else {
            source.addGroupSafe(GROUP_DOMAIN_INVALID)
            throw NoStackTraceException("源地址不可访问")
        }
    }

    private suspend fun isDomainReachable(domain: String): Boolean {
        val reachable = runCatching {
            withTimeout(DOMAIN_PROBE_TIMEOUT_MILLIS) {
                val uri = URI(domain.substringBefore("#"))
                val host = uri.host ?: return@withTimeout false
                val port = resolveProbePort(uri.scheme, uri.port)
                Socket().use { socket ->
                    socket.connect(
                        InetSocketAddress(host, port),
                        DOMAIN_PROBE_CONNECT_TIMEOUT_MILLIS,
                    )
                    true
                }
            }
        }.getOrDefault(false)
        // 取消时不能把源判成域名失效
        coroutineContext.ensureActive()
        return reachable
    }

    private suspend fun checkSearchBranch(
        source: BookSource,
        keyword: String,
        settings: CheckSourceSettings,
    ) {
        if (!settings.checkSearch) return
        val word = source.getCheckKeyword(keyword)
        if (source.searchUrl.isNullOrBlank()) {
            source.addGroupSafe(GROUP_SEARCH_URL_EMPTY)
            return
        }
        source.removeGroupSafe(GROUP_SEARCH_URL_EMPTY)
        // 拿到第一条就停：批量校验只需要判定可用性，不必解析整页结果
        val books = WebBook.searchBookAwait(source, word, shouldBreak = { it > 0 })
        if (books.isEmpty()) {
            source.addGroupSafe(GROUP_SEARCH_INVALID)
            return
        }
        source.removeGroupSafe(GROUP_SEARCH_INVALID)
        checkBook(books.first().toBook(), source, true, settings)
    }

    private suspend fun checkDiscoveryBranch(
        source: BookSource,
        settings: CheckSourceSettings,
    ) {
        if (!settings.checkDiscovery || source.exploreUrl.isNullOrBlank()) return
        val url = source.exploreKinds().firstOrNull { !it.url.isNullOrBlank() }?.url
        if (url.isNullOrBlank()) {
            source.addGroupSafe(GROUP_DISCOVERY_URL_EMPTY)
            return
        }
        source.removeGroupSafe(GROUP_DISCOVERY_URL_EMPTY)
        val books = WebBook.exploreBookAwait(source, url, shouldBreak = { it > 0 })
        if (books.isEmpty()) {
            source.addGroupSafe(GROUP_DISCOVERY_INVALID)
            return
        }
        source.removeGroupSafe(GROUP_DISCOVERY_INVALID)
        checkBook(books.first().toBook(), source, false, settings)
    }

    private suspend fun checkBook(
        book: Book,
        source: BookSource,
        searchBook: Boolean,
        settings: CheckSourceSettings,
    ) {
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
            val type = if (searchBook) TYPE_SEARCH else TYPE_DISCOVERY
            bookFailureGroup(type, error)?.let { source.addGroupSafe(it) } ?: throw error
        }.onSuccess {
            val type = if (searchBook) TYPE_SEARCH else TYPE_DISCOVERY
            source.removeGroupSafe("${type}目录失效")
            source.removeGroupSafe("${type}正文失效")
        }
    }

    /**
     * 快速模式下两个分支并发改写同一个源的分组与注释，字符串字段的读改写必须串行。
     */
    private inline fun BookSource.updateGroups(block: BookSource.() -> Unit) {
        synchronized(this) { block() }
    }

    private fun BookSource.addGroupSafe(group: String) {
        updateGroups { addGroup(group) }
    }

    private fun BookSource.removeGroupSafe(group: String) {
        updateGroups { removeGroup(group) }
    }
}

internal const val TYPE_SEARCH = "搜索"
internal const val TYPE_DISCOVERY = "发现"
internal const val GROUP_TIMEOUT = "校验超时"
internal const val GROUP_JS_INVALID = "js失效"
internal const val GROUP_SITE_INVALID = "网站失效"
internal const val GROUP_DOMAIN_INVALID = "域名失效"
internal const val GROUP_SEARCH_INVALID = "${TYPE_SEARCH}失效"
internal const val GROUP_DISCOVERY_INVALID = "${TYPE_DISCOVERY}失效"
internal const val GROUP_SEARCH_URL_EMPTY = "${TYPE_SEARCH}链接规则为空"
internal const val GROUP_DISCOVERY_URL_EMPTY = "${TYPE_DISCOVERY}规则为空"

private const val DOMAIN_PROBE_TIMEOUT_MILLIS = 2_000L
private const val DOMAIN_PROBE_CONNECT_TIMEOUT_MILLIS = 1_600

/**
 * 域名探测端口：显式端口优先，否则按 scheme 取默认端口。
 * 必须识别 https，否则 443-only 的站点会被误判成「域名失效」。
 */
internal fun resolveProbePort(scheme: String?, port: Int): Int =
    port.takeIf { it > 0 } ?: if (scheme.equals("https", ignoreCase = true)) 443 else 80

/**
 * 单个书源校验失败后的分组判定。`null` 表示不额外标记分组
 * （`NoStackTraceException` 已经携带具体失效分组）。
 */
internal fun checkFailureGroup(error: Throwable): String? = when (error) {
    is TimeoutCancellationException -> GROUP_TIMEOUT
    is ScriptException, is WrappedException -> GROUP_JS_INVALID
    !is NoStackTraceException -> GROUP_SITE_INVALID
    else -> null
}

/**
 * 详情/目录/正文校验失败后的分组判定。`null` 表示向上抛出，交给外层归类。
 */
internal fun bookFailureGroup(type: String, error: Throwable): String? = when (error) {
    is ContentEmptyException -> "${type}正文失效"
    is TocEmptyException -> "${type}目录失效"
    else -> null
}

private fun Throwable?.displayMessage(): String? =
    this?.localizedMessage?.takeIf { it.isNotBlank() }
        ?: this?.javaClass?.simpleName?.takeIf { it.isNotBlank() }
