package io.legado.app.help.config

import io.legado.app.utils.LogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

/**
 * DataStore 写入序列化队列，所有经 [AppConfigStore] 的异步写入按提交顺序串行落盘。
 */
object SettingsWriter {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val writeDispatcher = Dispatchers.IO.limitedParallelism(1)

    private val pendingCount = AtomicInteger(0)

    /** 当前排队的写入任务数（含正在执行的那一个）。*/
    val pendingWriteCount: Int get() = pendingCount.get()

    /**
     * 串行写队列：所有经 [AppConfigStore] 的写入按提交顺序落盘。
     * 写入异常由提交的 block 负责处理；[AppConfigStore] 会移除对应 pending overlay，
     * 使读取回退到最近一次已落盘快照。
     */
    fun launchWrite(block: suspend () -> Unit): Job {
        pendingCount.incrementAndGet()
        return scope.launch(writeDispatcher) {
            try {
                val elapsed = measureTime { runCatching { block() } }
                if (elapsed > 500.milliseconds) {
                    LogUtils.d(
                        "SettingsWriter",
                        "单次 edit 耗时 ${elapsed.inWholeMilliseconds}ms, 队列剩余 ${pendingCount.get() - 1}"
                    )
                }
            } finally {
                pendingCount.decrementAndGet()
            }
        }
    }

    /**
     * 等待所有排队的写入落盘完成（最长等待 [timeoutMs] 毫秒）。
     * 用于 restart 等必须确保"写入已持久化"的场景。
     */
    suspend fun awaitPendingWrites(timeoutMs: Long = 3000) {
        withTimeoutOrNull(timeoutMs) {
            while (pendingCount.get() > 0) {
                delay(50)
            }
        }
    }
}
