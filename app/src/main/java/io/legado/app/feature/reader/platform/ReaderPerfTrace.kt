package io.legado.app.feature.reader.platform

import android.os.Build
import android.os.Trace
import java.util.concurrent.atomic.AtomicInteger

/**
 * Small, reader-local Perfetto vocabulary. Keep sections coarse: the point is to explain the
 * path to the first readable page, not to trace every Canvas draw or Compose recomposition.
 */
internal object ReaderPerfTrace {
    private val nextAsyncCookie = AtomicInteger()
    inline fun <T> section(name: String, block: () -> T): T {
        Trace.beginSection("reader.$name")
        return try {
            block()
        } finally {
            Trace.endSection()
        }
    }

    suspend fun <T> suspendSection(name: String, block: suspend () -> T): T {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return block()
        val cookie = nextAsyncCookie.incrementAndGet()
        Trace.beginAsyncSection("reader.$name", cookie)
        return try {
            block()
        } finally {
            Trace.endAsyncSection("reader.$name", cookie)
        }
    }

    /**
     * 归因打点。`marker` 会被放在 composable 体内，每次重组都执行；未开 tracing 时必须尽早
     * 返回，否则每条 marker 都是两次 `Trace` 静态调用乘以重组次数。
     * 注意：API < 29 没有 `Trace.isEnabled()`，这里随之整体跳过（与 suspendSection 一致）。
     */
    fun marker(name: String) {
        if (!isEnabled()) return
        Trace.beginSection("reader.$name")
        Trace.endSection()
    }

    fun isEnabled(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && Trace.isEnabled()

    /** Aggregate costs from an interleaved operation without tracing every paragraph. */
    fun counter(name: String, value: Long) {
        if (isEnabled()) Trace.setCounter("reader.$name", value)
    }
}
