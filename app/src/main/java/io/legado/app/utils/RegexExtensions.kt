package io.legado.app.utils

import com.script.ScriptBindings
import com.script.rhino.RhinoScriptEngine
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.SearchBook
import io.legado.app.exception.RegexTimeoutException
import io.legado.app.help.CrashHandler
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import splitties.init.appCtx

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
        try {
            withTimeout(timeout) {
                val timeoutContext = currentCoroutineContext()
                // 在 IO 线程执行匹配，让 runBlocking 事件循环空出来触发超时；
                // runInterruptible 保证超时取消时安全中断该线程（不会把中断标记泄漏给线程池）。
                runInterruptible(IO) {
                    val pattern = regex.toPattern()
                    val matcher = pattern.matcher(InterruptibleCharSequence(charSequence))
                    val stringBuffer = StringBuffer()
                    try {
                        while (matcher.find()) {
                            if (isJs) {
                                val jsResult = RhinoScriptEngine.run {
                                    val bindings = ScriptBindings()
                                    bindings["result"] = matcher.group()
                                    bindings["chapter"] = chapter
                                    bindings["book"] = book
                                    eval(
                                        replacement1,
                                        getRuntimeScope(bindings),
                                        timeoutContext
                                    )
                                }.toString()
                                val quotedResult = jsResult.quoteReplacementJs()
                                matcher.appendReplacement(stringBuffer, quotedResult)
                            } else {
                                matcher.appendReplacement(stringBuffer, replacement1)
                            }
                        }
                        matcher.appendTail(stringBuffer)
                    } catch (e: RegexInterruptedException) {
                        // 中断由超时取消触发，转成 InterruptedException，
                        // 交由 runInterruptible 归一化为协程取消 -> withTimeout 抛出超时。
                        throw InterruptedException()
                    }
                    stringBuffer.toString()
                }
            }
        } catch (e: TimeoutCancellationException) {
            val timeoutMsg = "替换超时\n替换规则$regex\n替换内容:$charSequence"
            val exception = RegexTimeoutException(timeoutMsg)
            appCtx.longToastOnUi(timeoutMsg)
            CrashHandler.saveCrashInfo2File(exception)
            throw exception
        }
    }
}

/**
 * charAt() 中检测线程中断标记的 CharSequence 包装。
 *
 * java.util.regex 的回溯匹配会反复读取输入字符，因此在字符读取处检查中断，
 * 就能让陷入灾难性回溯的 matcher.find() 及时抛出并结束，无需重启进程。
 * 采样检测（每 1024 次读取查一次）避免正常替换时逐字符做 native 调用的开销。
 */
private class InterruptibleCharSequence(private val delegate: CharSequence) : CharSequence {

    private var counter = 0

    override val length: Int get() = delegate.length

    override fun get(index: Int): Char {
        if ((counter++ and CHECK_MASK) == 0 && Thread.currentThread().isInterrupted) {
            throw RegexInterruptedException()
        }
        return delegate[index]
    }

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
        InterruptibleCharSequence(delegate.subSequence(startIndex, endIndex))

    override fun toString(): String = delegate.toString()

    private companion object {
        const val CHECK_MASK = 0x3FF
    }
}

/**
 * 由 [InterruptibleCharSequence] 在检测到线程中断时抛出。无栈以降低开销。
 */
private class RegexInterruptedException : RuntimeException() {
    override fun fillInStackTrace(): Throwable = this
}
