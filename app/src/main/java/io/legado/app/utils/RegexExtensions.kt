package io.legado.app.utils

import androidx.core.os.postDelayed
import com.script.ScriptBindings
import com.script.rhino.RhinoScriptEngine
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.SearchBook
import io.legado.app.exception.RegexTimeoutException
import io.legado.app.help.CrashHandler
import io.legado.app.help.coroutine.Coroutine
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import splitties.init.appCtx
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val handler by lazy { buildMainHandler() }

/**
 * 带有超时检测的正则替换
 */
fun CharSequence.replace(
    regex: Regex,
    replacement: String,
    timeout: Long,
    chapter: BookChapter? = null,
    book: SearchBook? = null
): String {
    val charSequence = this@replace
    val isJs = replacement.startsWith("@js:")
    val replacement1 = if (isJs) replacement.substring(4) else replacement
    val book = if (isJs) {
        book ?: chapter?.bookUrl?.let {
            appDb.searchBookDao.getSearchBook(it) ?: appDb.bookDao.getBook(it)?.toSearchBook()
        }
    } else {
        null
    }
    return runBlocking {
        suspendCancellableCoroutine { block ->
            val coroutine = Coroutine.async(executeContext = IO) {
                try {
                    val pattern = regex.toPattern()
                    val matcher = pattern.matcher(charSequence)
                    val stringBuffer = StringBuffer()
                    while (matcher.find()) {
                        if (isJs) {
                            val jsResult = RhinoScriptEngine.run {
                                val bindings = ScriptBindings()
                                bindings["result"] = matcher.group()
                                bindings["chapter"] = chapter
                                bindings["book"] = book
                                eval(replacement1, bindings)
                            }.toString()
                            val quotedResult = jsResult.quoteReplacementJs()
                            matcher.appendReplacement(stringBuffer, quotedResult)
                        } else {
                            matcher.appendReplacement(stringBuffer, replacement1)
                        }
                    }
                    matcher.appendTail(stringBuffer)
                    block.resume(stringBuffer.toString())
                } catch (e: Exception) {
                    block.resumeWithException(e)
                }
            }
            val timeoutRunnable = handler.postDelayed(timeout) {
                if (coroutine.isActive) {
                    val timeoutMsg =
                        "替换超时,3秒后还未结束将重启应用\n替换规则$regex\n替换内容:$charSequence"
                    val exception = RegexTimeoutException(timeoutMsg)
                    block.cancel(exception)
                    appCtx.longToastOnUi(timeoutMsg)
                    CrashHandler.saveCrashInfo2File(exception)
                    handler.postDelayed(3000) {
                        if (coroutine.isActive) {
                            appCtx.restart()
                        }
                    }
                }
            }
            // 正则结束后立即撤掉看门狗,否则它会在主线程消息队列里存活整个 timeout 窗口,
            // 并钉住整段正文拷贝;规则数量多时会在 3 秒内堆积成百上千份拷贝导致 OOM。
            coroutine.invokeOnCompletion {
                handler.removeCallbacks(timeoutRunnable)
            }
        }
    }
}
