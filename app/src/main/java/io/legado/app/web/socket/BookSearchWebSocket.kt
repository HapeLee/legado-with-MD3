package io.legado.app.web.socket

import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.domain.model.BookSearchScope
import io.legado.app.domain.usecase.BookSearchControl
import io.legado.app.domain.usecase.BookSearchRequest
import io.legado.app.domain.usecase.SearchBooksUseCase
import io.legado.app.domain.usecase.SearchRunEvent
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.config.otherConfig.OtherConfig
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.isJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import splitties.init.appCtx
import java.io.IOException

class BookSearchWebSocket(handshakeRequest: NanoHTTPD.IHTTPSession) :
    NanoWSD.WebSocket(handshakeRequest),
    CoroutineScope by MainScope() {

    private val normalClosure = NanoWSD.WebSocketFrame.CloseCode.NormalClosure
    private val searchBooksUseCase: SearchBooksUseCase by lazy { GlobalContext.get().get() }
    private val searchControl = BookSearchControl()
    private val sentBookUrls = linkedSetOf<String>()
    private var searchJob: Job? = null

    private val SEARCH_FINISH = "Search finish"

    override fun onOpen() {
        launch(IO) {
            kotlin.runCatching {
                while (isOpen) {
                    ping("ping".toByteArray())
                    delay(30000)
                }
            }
        }
    }

    override fun onClose(
        code: NanoWSD.WebSocketFrame.CloseCode,
        reason: String,
        initiatedByRemote: Boolean
    ) {
        searchJob?.cancel()
        cancel()
    }

    override fun onMessage(message: NanoWSD.WebSocketFrame) {
        launch(IO) {
            kotlin.runCatching {
                if (!message.textPayload.isJson()) {
                    send("数据必须为Json格式")
                    close(normalClosure, SEARCH_FINISH, false)
                    return@launch
                }
                val searchMap =
                    GSON.fromJsonObject<Map<String, String>>(message.textPayload).getOrNull()
                if (searchMap != null) {
                    val key = searchMap["key"]?.trim()
                    if (key.isNullOrBlank()) {
                        send(appCtx.getString(R.string.cannot_empty))
                        close(normalClosure, SEARCH_FINISH, false)
                        return@launch
                    }
                    startSearch(key)
                }
            }
        }
    }

    override fun onPong(pong: NanoWSD.WebSocketFrame) {

    }

    override fun onException(exception: IOException) {

    }

    private fun startSearch(key: String) {
        searchJob?.cancel()
        sentBookUrls.clear()
        searchControl.resume()
        searchJob = launch(IO) {
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
                                    send(GSON.toJson(newBooks))
                                }
                            }

                            is SearchRunEvent.Finished -> close(normalClosure, SEARCH_FINISH, false)
                        }
                    }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Throwable) {
                close(normalClosure, exception.toString(), false)
            }
        }
    }
}
