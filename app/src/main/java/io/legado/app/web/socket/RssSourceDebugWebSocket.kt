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
 * web端订阅源调试
 */
class RssSourceDebugWebSocket(private val session: DefaultWebSocketServerSession) :
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
                        if (tag.isNullOrBlank()) {
                            session.send(appCtx.getString(R.string.cannot_empty))
                            session.close(CloseReason(CloseReason.Codes.NORMAL, "调试结束"))
                            break
                        }
                        appDb.rssSourceDao.getByKey(tag)?.let {
                            Debug.callback = this@RssSourceDebugWebSocket
                            Debug.startDebug(this, it)
                        }
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
