package io.legado.app.data.repository.manga

import java.io.Closeable
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Shared by preview requests and region decoders. Retired generations cannot gain new readers. */
object MangaTranslationFiles {
    private val readers = mutableMapOf<String, Int>()
    private val retired = mutableSetOf<File>()

    @Synchronized
    fun pin(file: File): Closeable {
        val path = file.canonicalPath
        check(retired.none { file.canonicalFile.startsWith(it) }) { "译图已失效" }
        check(file.isFile) { "译图不存在" }
        readers[path] = (readers[path] ?: 0) + 1
        val released = AtomicBoolean()
        return Closeable {
            if (released.compareAndSet(false, true)) synchronized(this) {
                val remaining = (readers[path] ?: 1) - 1
                if (remaining == 0) readers.remove(path) else readers[path] = remaining
                collectRetired()
            }
        }
    }

    @Synchronized
    fun retire(directory: File) {
        retired.add(directory.canonicalFile)
        collectRetired()
    }

    // Keep tombstones for the process lifetime: a delayed Coil request must not reopen an old URI.
    private fun collectRetired() {
        retired.forEach { directory ->
            if (readers.keys.none { File(it).startsWith(directory) }) directory.deleteRecursively()
        }
    }
}
