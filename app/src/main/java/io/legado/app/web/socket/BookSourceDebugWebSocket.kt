package io.legado.app.web.socket

import io.ktor.server.websocket.*
import io.ktor.websocket.*
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.model.Debug
import io.legado.app.utils.*
import kotlinx.coroutines.*
import splitties.init.appCtx

/**
 * web端书源调试
 */
class BookSourceDebugWebSocket(private val session: DefaultWebSocketServerSession) :
    CoroutineScope by session {

    private var debugSession: Debug.Session? = null

    suspend fun handle() {
        try {
            for (frame in session.incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    if (!text.isJson()) {
                        session.send("数据必须为Json格式")
                        session.close(CloseReason(CloseReason.Codes.NORMAL, "调试结束"))
                        break
                    }
                    val debugBean = GSON.fromJsonObject<Map<String, String>>(text).getOrNull()
                    if (debugBean != null) {
                        val tag = debugBean["tag"]
                        val key = debugBean["key"]
                        if (tag.isNullOrBlank() || key.isNullOrBlank()) {
                            session.send(appCtx.getString(R.string.cannot_empty))
                            session.close(CloseReason(CloseReason.Codes.NORMAL, "调试结束"))
                            break
                        }
                        appDb.bookSourceDao.getBookSource(tag)?.let {
                            val current = Debug.startDebug(this, it, key)
                            debugSession = current
                            current.events.collect { event ->
                                if (!event.kind.isSourcePayload) session.send(event.message)
                                if (event.kind.isTerminal) {
                                    session.close(CloseReason(CloseReason.Codes.NORMAL, "调试结束"))
                                }
                            }
                        }
                    } else {
                        session.send("数据必须为Json格式")
                        session.close(CloseReason(CloseReason.Codes.NORMAL, "调试结束"))
                        break
                    }
                }
            }
        } catch (e: Exception) {
            e.printOnDebug()
        } finally {
            debugSession?.cancel()
            debugSession = null
        }
    }
}
