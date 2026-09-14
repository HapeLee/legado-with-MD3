package io.legado.app.help

import io.legado.app.data.entities.BaseSource
import io.legado.app.data.rate.ConcurrentRateRegistry
import io.legado.app.data.rate.ConcurrentRecord
import io.legado.app.exception.ConcurrentException
import kotlinx.coroutines.delay

/**
 * 并发率限流器。
 *
 * M2-4b：`ConcurrentRecord` 与登记表已下沉 `:core:data` 的 [ConcurrentRateRegistry]，
 * 好让 `BaseSource.putConcurrent` 不再经 `SourceRuntimeProvider`。本类只保留运行时逻辑
 * （等待/抛 `ConcurrentException`，用到 `Thread.sleep` 等 JVM-only API，仍属 `:app`）。
 */
class ConcurrentRateLimiter(source: BaseSource?) {

    private val concurrentRate = source?.concurrentRate
    private val key = source?.getKey()
    /**
     * 开始访问,并发判断
     */
    @Throws(ConcurrentException::class)
    private fun fetchStart(): ConcurrentRecord? {
        if (concurrentRate.isNullOrEmpty() || concurrentRate == "0") {
            return null
        }
        val key = key ?: return null
        var isNewRecord = false
        val fetchRecord = ConcurrentRateRegistry.records.computeIfAbsent(key) {
            isNewRecord = true
            val rateIndex = concurrentRate.indexOf("/")
            if (rateIndex > 0) {
                val accessLimit = concurrentRate.take(rateIndex).toIntOrNull() ?: 1
                val interval = concurrentRate.substring(rateIndex + 1).toIntOrNull() ?: 0
                ConcurrentRecord(System.currentTimeMillis(), accessLimit, interval, 1)
            } else {
                ConcurrentRecord(
                    System.currentTimeMillis(),
                    1,
                    concurrentRate.toIntOrNull() ?: 0,
                    1
                )
            }
        }
        if (isNewRecord) return fetchRecord
        val waitTime: Long = synchronized(fetchRecord) {
            //并发控制为 次数/毫秒 , 非并发实际为1/毫秒
            val nextTime = fetchRecord.time + fetchRecord.interval.toLong()
            val nowTime = System.currentTimeMillis()
            if (nowTime >= nextTime) {
                //已经过了限制时间,重置开始时间
                fetchRecord.time = nowTime
                fetchRecord.frequency = 1
                return@synchronized 0
            }
            if (fetchRecord.frequency < fetchRecord.accessLimit) {
                fetchRecord.frequency++
                return@synchronized 0
            } else {
                return@synchronized nextTime - nowTime
            }
        }
        if (waitTime > 0) {
            throw ConcurrentException(
                "根据并发率还需等待${waitTime}毫秒才可以访问",
                waitTime = waitTime
            )
        }
        return fetchRecord
    }

    /**
     * 获取并发记录，若处于并发限制状态下则会等待
     */
    suspend fun getConcurrentRecord(): ConcurrentRecord? {
        while (true) {
            try {
                return fetchStart()
            } catch (e: ConcurrentException) {
                delay(e.waitTime)
            }
        }
    }

    fun getConcurrentRecordBlocking(): ConcurrentRecord? {
        while (true) {
            try {
                return fetchStart()
            } catch (e: ConcurrentException) {
                Thread.sleep(e.waitTime)
            }
        }
    }

    suspend inline fun <T> withLimit(block: () -> T): T {
        getConcurrentRecord()
        return block()
    }

    inline fun <T> withLimitBlocking(block: () -> T): T {
        getConcurrentRecordBlocking()
        return block()
    }

}
