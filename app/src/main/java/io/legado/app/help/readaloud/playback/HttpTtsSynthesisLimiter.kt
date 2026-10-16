package io.legado.app.help.readaloud.playback

import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/** 每个进程共用一份。配额持续到响应流被消费/关闭，不能在收到响应头时释放。 */
class HttpTtsSynthesisLimiter {
    private class Quota {
        var active = 0
        val changes = MutableStateFlow(0L)
    }

    private val quotas = mutableMapOf<String, Quota>()

    suspend fun acquire(sourceId: String, limit: Int): Closeable {
        val quota = synchronized(quotas) { quotas.getOrPut(sourceId) { Quota() } }
        while (true) {
            val version = synchronized(quota) {
                if (quota.active < limit.coerceIn(1, 8)) {
                    quota.active++
                    val released = AtomicBoolean()
                    return Closeable {
                        if (released.compareAndSet(false, true)) synchronized(quota) {
                            quota.active--
                            quota.changes.value++
                        }
                    }
                }
                quota.changes.value
            }
            quota.changes.first { it != version }
        }
    }

    companion object {
        val shared = HttpTtsSynthesisLimiter()
    }
}
