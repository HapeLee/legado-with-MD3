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
    CoroutineScope by session,
    Debug.Callback {

    private val notPrintState = arrayOf(10, 20, 30, 40)

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
                            Debug.callback = this@BookSourceDebugWebSocket
                            Debug.startDebug(this, it, key)
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
            Debug.cancelDebug(true)
        }
    }

    override fun printLog(state: Int, msg: String) {
        if (state in notPrintState) {
            return
        }
        launch(Dispatchers.IO) {
            runCatching {
                session.send(msg)
                if (state == -1 || state == 1000) {
                    Debug.cancelDebug(true)
                    session.close(CloseReason(CloseReason.Codes.NORMAL, "调试结束"))
                }
            }.onFailure {
                it.printOnDebug()
            }
        }
    }

}
