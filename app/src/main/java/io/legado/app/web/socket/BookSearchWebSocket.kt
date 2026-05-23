package io.legado.app.web.socket

import io.ktor.server.websocket.*
import io.ktor.websocket.*
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.domain.model.BookSearchScope
import io.legado.app.domain.usecase.BookSearchControl
import io.legado.app.domain.usecase.BookSearchRequest
import io.legado.app.domain.usecase.SearchBooksUseCase
import io.legado.app.domain.usecase.SearchRunEvent
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.config.otherConfig.OtherConfig
import io.legado.app.utils.*
import kotlinx.coroutines.*
import org.koin.core.context.GlobalContext
import splitties.init.appCtx

class BookSearchWebSocket(private val session: DefaultWebSocketServerSession) : CoroutineScope by session {

    private val searchBooksUseCase: SearchBooksUseCase by lazy { GlobalContext.get().get() }
    private val searchControl = BookSearchControl()
    private val sentBookUrls = linkedSetOf<String>()
    private var searchJob: Job? = null

    private val SEARCH_FINISH = "Search finish"

    suspend fun handle() {
        try {
            for (frame in session.incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    if (!text.isJson()) {
                        session.send("数据必须为Json格式")
                        session.close(CloseReason(CloseReason.Codes.NORMAL, SEARCH_FINISH))
                        break
                    }
                    val searchMap = GSON.fromJsonObject<Map<String, String>>(text).getOrNull()
                    if (searchMap != null) {
                        val key = searchMap["key"]?.trim()
                        if (key.isNullOrBlank()) {
                            session.send(appCtx.getString(R.string.cannot_empty))
                            session.close(CloseReason(CloseReason.Codes.NORMAL, SEARCH_FINISH))
                            break
                        }
                        startSearch(key)
                    }
                }
            }
        } catch (e: Exception) {
            e.printOnDebug()
        } finally {
            searchJob?.cancel()
        }
    }

    private fun startSearch(key: String) {
        searchJob?.cancel()
        sentBookUrls.clear()
        searchControl.resume()
        searchJob = launch(Dispatchers.IO) {
            try {
                searchBooksUseCase
                    .execute(
                        BookSearchRequest(
                            keyword = key,
                            page = 1,
                            scope = BookSearchScope(AppConfig.searchScope),
                            precision = appCtx.getPrefBoolean(PreferKey.precisionSearch),
                            concurrency = OtherConfig.threadCount,
                        ),
                        searchControl
                    )
                    .collect { event ->
                        when (event) {
                            SearchRunEvent.Started -> Unit
                            is SearchRunEvent.Progress -> {
                                val newBooks = event.upsertBooks.filter { sentBookUrls.add(it.bookUrl) }
                                if (newBooks.isNotEmpty()) {
                                    session.send(GSON.toJson(newBooks))
                                }
                            }

                            is SearchRunEvent.Finished -> session.close(CloseReason(CloseReason.Codes.NORMAL, SEARCH_FINISH))
                        }
                    }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Throwable) {
                session.close(CloseReason(CloseReason.Codes.INTERNAL_ERROR, exception.toString()))
            }
        }
    }
}
