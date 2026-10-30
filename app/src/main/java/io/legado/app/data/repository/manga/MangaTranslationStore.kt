package io.legado.app.data.repository.manga

import android.util.AtomicFile
import io.legado.app.domain.gateway.MangaTranslationPage
import io.legado.app.domain.gateway.MangaTranslationStatus
import io.legado.app.domain.gateway.MangaTranslationTask
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** All calls are serialized by the queue except commit, which also synchronizes against clear. */
internal class MangaTranslationStore(private val root: File) {
    data class Record(
        val bookUrl: String,
        val generation: Long = 0,
        val allowLegacy: Boolean = true,
        val showTranslation: Boolean = true,
        val tasks: List<MangaTranslationTask> = emptyList(),
    )

    private val generations = mutableMapOf<String, Long>()
    private fun directory(book: String) = File(root, "books/${digest(book)}")
    fun pages(record: Record) = File(directory(record.bookUrl), "generation-${record.generation}")
    fun legacy(key: String) = File(root, "pages/$key.png")

    @Synchronized
    fun load(book: String): Record {
        val atomic = AtomicFile(File(directory(book), "queue.json"))
        val record = if (!atomic.baseFile.exists() && !File(atomic.baseFile.path + ".bak").exists()) Record(book)
        else {
            // AtomicFile recovers interrupted writes; never guess ownership of flat legacy files.
            val json = JSONObject(atomic.openRead().bufferedReader().use { it.readText() })
            check(json.getString("book") == book)
            val tasks = json.getJSONArray("tasks")
            Record(book, json.getLong("generation"), json.getBoolean("legacy"), json.getBoolean("show"),
                List(tasks.length()) { index ->
                    val item = tasks.getJSONObject(index)
                    val status = MangaTranslationStatus.valueOf(item.getString("status"))
                    MangaTranslationTask(MangaTranslationPage(item.getString("key"), item.getString("url"),
                        item.getString("chapter"), item.getInt("chapterIndex"), item.getInt("pageIndex"),
                        item.getString("subject")),
                        if (status == MangaTranslationStatus.RUNNING) MangaTranslationStatus.PENDING else status,
                        item.optString("result").takeIf { uri -> runCatching {
                            val file = File(java.net.URI(uri))
                            file.isFile && file.canonicalFile.startsWith(
                                File(directory(book), "generation-${json.getLong("generation")}").canonicalFile)
                        }.getOrDefault(false) })
                })
        }
        generations[book] = record.generation
        // A process can die while an old decoder is keeping a cleared generation alive.
        directory(book).listFiles().orEmpty().filter {
            it.isDirectory && it.name.startsWith("generation-") && it != pages(record)
        }.forEach(MangaTranslationFiles::retire)
        return record.copy(tasks = record.tasks.map {
            if (it.status == MangaTranslationStatus.DONE && it.resultUri == null)
                it.copy(status = MangaTranslationStatus.PENDING) else it
        })
    }

    @Synchronized
    fun save(record: Record) {
        directory(record.bookUrl).mkdirs()
        val json = JSONObject().put("book", record.bookUrl).put("generation", record.generation)
            .put("legacy", record.allowLegacy).put("show", record.showTranslation)
            .put("tasks", JSONArray().apply {
                record.tasks.forEach { task -> put(JSONObject().put("key", task.page.key)
                    .put("url", task.page.imageUrl).put("chapter", task.page.chapterId)
                    .put("chapterIndex", task.page.chapterIndex).put("pageIndex", task.page.pageIndex)
                    .put("subject", task.page.chapterSubject).put("status", task.status.name)
                    .put("result", task.resultUri.orEmpty())) }
            })
        val checkpoint = File(directory(record.bookUrl), "queue.json")
        val temporary = File(directory(record.bookUrl), "queue.pending")
        try {
            temporary.outputStream().use { stream ->
                stream.write(json.toString().toByteArray())
                stream.fd.sync()
            }
            // Explicit atomic replacement also reports rename failure instead of only logging it.
            Files.move(temporary.toPath(), checkpoint.toPath(), StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING)
        } finally { temporary.delete() }
        generations[record.bookUrl] = record.generation
    }

    @Synchronized
    fun bookUrls(): List<String> = File(root, "books").listFiles().orEmpty().mapNotNull { directory ->
        val checkpoint = AtomicFile(File(directory, "queue.json"))
        if (!checkpoint.baseFile.exists() && !File(checkpoint.baseFile.path + ".bak").exists()) null
        else checkpoint.openRead().bufferedReader().use { JSONObject(it.readText()).getString("book") }
    }

    @Synchronized
    fun retireAllImages() {
        File(root, "books").listFiles().orEmpty().forEach { book ->
            book.listFiles().orEmpty().filter { it.isDirectory && it.name.startsWith("generation-") }
                .forEach(MangaTranslationFiles::retire)
        }
        MangaTranslationFiles.retire(File(root, "pages"))
    }

    @Synchronized
    fun clear(record: Record): Record {
        val next = Record(record.bookUrl, record.generation + 1, allowLegacy = false, showTranslation = false)
        save(next)
        return next
    }

    /** Same lock as generation invalidation; no old task can recreate a cleared output. */
    @Synchronized
    fun commit(record: Record, temporary: File, output: File): Boolean {
        if (generations[record.bookUrl] != record.generation) return false
        Files.move(temporary.toPath(), output.toPath(), StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING)
        return true
    }

    companion object {
        fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
