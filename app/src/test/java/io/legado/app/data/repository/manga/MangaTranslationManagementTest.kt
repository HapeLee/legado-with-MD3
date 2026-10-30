package io.legado.app.data.repository.manga

import android.app.Application
import io.legado.app.domain.gateway.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class MangaTranslationManagementTest {
    @get:Rule val temporary = TemporaryFolder()
    private val page = MangaTranslationPage("page", "image", "chapter", 0, 0, "chapter")
    private open class Processor : MangaPageProcessor {
        override suspend fun translate(record: MangaTranslationStore.Record, page: MangaTranslationPage,
            onProgress: (String) -> Unit): String? = null
        override suspend fun cached(record: MangaTranslationStore.Record, page: MangaTranslationPage): String? = null
        override fun close() = Unit
    }
    @Test fun `clear all invalidates unopened books and legacy cache without deleting models`() = runTest {
        val root = temporary.newFolder()
        val store = MangaTranslationStore(root)
        val unopened = MangaTranslationStore.Record("unopened", tasks = listOf(MangaTranslationTask(page)))
        store.save(unopened)
        val image = store.pages(unopened).apply { mkdirs() }.resolve("old.png").apply { writeText("image") }
        root.resolve("models").mkdirs()
        root.resolve("models/keep.bin").writeText("model")
        root.resolve("pages").mkdirs()
        root.resolve("pages/legacy.png").writeText("legacy")
        val lease = MangaTranslationFiles.pin(image)
        val repository = MangaTranslationRepository(store, Processor(), backgroundScope)
        repository.clearAll()
        assertTrue(store.load("unopened").tasks.isEmpty())
        assertFalse(store.load("unopened").showTranslation)
        assertFalse(store.load("unopened").allowLegacy)
        assertTrue(image.exists())
        lease.close()
        assertFalse(image.exists())
        assertFalse(root.resolve("pages/legacy.png").exists())
        assertTrue(root.resolve("models/keep.bin").exists())
        val delayed = root.resolve("late.tmp").apply { writeText("stale") }
        assertFalse(store.commit(unopened, delayed, image))
    }
    @Test fun `model maintenance waits for native page and rejects resume until complete`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var closed = false
        var deleted = false
        val processor = object : Processor() {
            override suspend fun translate(record: MangaTranslationStore.Record, page: MangaTranslationPage,
                onProgress: (String) -> Unit): String? { entered.complete(Unit); finish.await(); return null }
            override fun close() { closed = true }
        }
        val repository = MangaTranslationRepository(MangaTranslationStore(temporary.newFolder()), processor, backgroundScope)
        repository.enqueueChapter("book", listOf(page), "page")
        repository.resume("book")
        entered.await()
        val maintenance = launch { repository.withModelsPaused { assertTrue(closed); deleted = true } }
        runCurrent()
        assertFalse(deleted)
        assertTrue(runCatching { repository.resume("book") }.isFailure)
        finish.complete(Unit)
        maintenance.join()
        assertTrue(deleted)
        assertTrue(closed)
    }
    @Test fun `cancelled maintenance releases resume guard`() = runTest {
        val repository = MangaTranslationRepository(MangaTranslationStore(temporary.newFolder()), Processor(), backgroundScope)
        val entered = CompletableDeferred<Unit>()
        val maintenance = launch { repository.withModelsPaused { entered.complete(Unit); awaitCancellation() } }
        entered.await()
        maintenance.cancelAndJoin()
        repository.resume("book")
    }

    @Test fun `late page result cannot republish after clear all`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val processor = object : Processor() {
            override suspend fun translate(record: MangaTranslationStore.Record, page: MangaTranslationPage,
                onProgress: (String) -> Unit): String? { entered.complete(Unit); finish.await(); return "file:/old.png" }
        }
        val repository = MangaTranslationRepository(MangaTranslationStore(temporary.newFolder()), processor, backgroundScope)
        repository.enqueueChapter("book", listOf(page), "page")
        repository.resume("book")
        entered.await()
        repository.clearAll()
        finish.complete(Unit)
        runCurrent()
        val state = repository.observeBook("book").first()
        assertTrue(state.tasks.isEmpty())
        assertFalse(state.showTranslation)
    }

}
