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

    fun marker(name: String) {
        Trace.beginSection("reader.$name")
        Trace.endSection()
    }

    fun isEnabled(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && Trace.isEnabled()

    /** Aggregate costs from an interleaved operation without tracing every paragraph. */
    fun counter(name: String, value: Long) {
        if (isEnabled()) Trace.setCounter("reader.$name", value)
    }
}
