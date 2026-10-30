package io.legado.app.data.repository.manga

import android.app.Application
import io.legado.app.data.AppDatabase
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.gateway.MangaTranslationBookState
import io.legado.app.domain.gateway.MangaTranslationGateway
import io.legado.app.domain.gateway.MangaTranslationPage
import io.legado.app.domain.gateway.MangaTranslationStatus
import io.legado.app.domain.gateway.MangaTranslationTask
import io.legado.app.domain.gateway.TranslationSettingsGateway
import io.legado.app.domain.gateway.MangaSettingsGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

/** One application-owned serial worker. Reader lifetimes control dispatch, never native teardown. */
class MangaTranslationRepository internal constructor(
    private val store: MangaTranslationStore,
    private val processor: MangaPageProcessor,
    private val scope: CoroutineScope,
) : MangaTranslationGateway {
    constructor(application: Application, database: AppDatabase, profiles: AiProfileGateway,
                ai: AiTextGateway, settings: TranslationSettingsGateway, mangaSettings: MangaSettingsGateway) : this(
        MangaTranslationStore(File(application.filesDir, "manga_translation")),
        application, database, profiles, ai, settings, mangaSettings,
    )

    private constructor(store: MangaTranslationStore, application: Application, database: AppDatabase,
                        profiles: AiProfileGateway, ai: AiTextGateway, settings: TranslationSettingsGateway,
                        mangaSettings: MangaSettingsGateway) : this(
        store, MangaPageTranslator(application, database, profiles, ai, settings, store, mangaSettings),
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )

    private class Book(var record: MangaTranslationStore.Record) {
        val state = MutableStateFlow(MangaTranslationBookState(tasks = record.tasks.map { it.copy(resultUri = null) },
            showTranslation = record.showTranslation))
        val validated = mutableSetOf<String>()
        @Volatile var active = false
        var chapter: String? = null
        var preferred: String? = null
        var failureId = 0L
        var failureMessage: String? = null
    }
    private val mutex = Mutex()
    private val books = ConcurrentHashMap<String, Book>()
    private val readerEpochs = mutableMapOf<String, Long>()
    private val lifetimeLock = Any()
    private var activeBook: String? = null
    private var worker: Job? = null
    private var modelMaintenance = false
    private var configurationEpoch = 0L
    private val maintenanceMutex = Mutex()

    private fun book(url: String) = books.getOrPut(url) { Book(store.load(url)) }
    private fun publish(book: Book, message: String? = book.state.value.message) {
        book.state.value = MangaTranslationBookState(book.record.tasks.map {
            if (it.page.key in book.validated) it else it.copy(resultUri = null)
        }, book.active,
            book.record.showTranslation, message, book.failureId, book.failureMessage)
    }
    private fun save(book: Book) { store.save(book.record); publish(book) }

    override fun observeBook(bookUrl: String): Flow<MangaTranslationBookState> = flow {
        val state = withContext(Dispatchers.IO) { mutex.withLock { book(bookUrl).state } }
        emitAll(state)
    }

    override suspend fun enqueueChapter(bookUrl: String, pages: List<MangaTranslationPage>, preferredKey: String?) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val book = book(bookUrl)
                if (pages.isEmpty()) return@withLock
                book.chapter = pages.first().chapterId
                book.preferred = preferredKey
                val incoming = pages.distinctBy { it.chapterId to it.pageIndex }
                val existing = book.record.tasks.associateBy { it.page.chapterId to it.page.pageIndex }
                // Completed pages revalidate their original/config key without reloading the engine.
                val replacements = incoming.map { page ->
                    val old = existing[page.chapterId to page.pageIndex]
                    if (old?.status == MangaTranslationStatus.RUNNING && old.page == page) old
                    else MangaTranslationTask(page)
                }
                val keys = incoming.mapTo(hashSetOf()) { it.chapterId to it.pageIndex }
                book.record = book.record.copy(tasks = replacements + book.record.tasks.filter { (it.page.chapterId to it.page.pageIndex) !in keys })
                save(book)
                if (book.active) startWorker()
            }
        }

    override suspend fun resume(bookUrl: String) {
        val epoch = synchronized(lifetimeLock) { readerEpochs[bookUrl] ?: 0L }
        withContext(Dispatchers.IO) {
            mutex.withLock {
                check(!modelMaintenance) { "模型正在维护，请稍后开启翻译" }
                val book = book(bookUrl)
                val attached = synchronized(lifetimeLock) {
                    if ((readerEpochs[bookUrl] ?: 0L) != epoch) false
                    else { book.active = true; true }
                }
                if (!attached) return@withLock
                activeBook?.takeIf { it != bookUrl }?.let { previous ->
                    book(previous).also { it.active = false; publish(it) }
                }
                book.record = book.record.copy(tasks = book.record.tasks.map {
                    if (it.status == MangaTranslationStatus.FAILED) it.copy(status = MangaTranslationStatus.PENDING) else it
                })
                book.failureMessage = null
                activeBook = bookUrl
                publish(book, null)
                startWorker()
            }
        }
    }

    override suspend fun pause(bookUrl: String) = withContext(Dispatchers.IO) {
        mutex.withLock { book(bookUrl).also { it.active = false; publish(it, null) }; Unit }
    }

    override suspend fun prioritize(bookUrl: String, pageKey: String) = withContext(Dispatchers.IO) {
        mutex.withLock { book(bookUrl).preferred = pageKey }
    }

    override suspend fun setShowTranslation(bookUrl: String, show: Boolean) = withContext(Dispatchers.IO) {
        mutex.withLock { book(bookUrl).also { it.record = it.record.copy(showTranslation = show); save(it) } }
        Unit
    }

    override suspend fun withModelsPaused(action: suspend () -> Unit) = maintenanceMutex.withLock {
        val running = mutex.withLock {
            modelMaintenance = true
            books.values.forEach { it.active = false; publish(it, null) }
            worker
        }
        try {
            running?.join()
            action()
        } finally {
            withContext(kotlinx.coroutines.NonCancellable) { mutex.withLock { modelMaintenance = false } }
        }
    }

    override suspend fun clearAll() = withContext(Dispatchers.IO) {
        mutex.withLock {
            (store.bookUrls() + books.keys).distinct().forEach { url ->
                val book = book(url)
                val oldDirectory = store.pages(book.record)
                book.active = false
                book.record = store.clear(book.record)
                book.validated.clear()
                book.failureMessage = null
                publish(book, null)
                MangaTranslationFiles.retire(oldDirectory)
            }
            store.retireAllImages()
        }
    }

    override suspend fun clearBook(bookUrl: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val book = book(bookUrl)
            val oldDirectory = store.pages(book.record)
            book.active = false
            book.record = store.clear(book.record)
            book.validated.clear()
            book.failureMessage = null
            publish(book, null)
            MangaTranslationFiles.retire(oldDirectory)
        }
    }

    override suspend fun validateLoadedPages(bookUrl: String, pages: List<MangaTranslationPage>) {
        // Cache inspection must never delay a pause/clear intent behind a native operation.
        scope.launch {
            try { inspectLoadedPages(bookUrl, pages) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                mutex.withLock {
                    books[bookUrl]?.let { book ->
                        book.failureId++
                        book.failureMessage = error.localizedMessage ?: "翻译缓存读取失败"
                        publish(book)
                    }
                }
            }
        }
    }

    override suspend fun invalidateConfiguration() = mutex.withLock {
        check(modelMaintenance) { "翻译配置只能在队列暂停后更新" }
        configurationEpoch++
        books.values.forEach { book ->
            book.validated.clear()
            book.record = book.record.copy(tasks = book.record.tasks.map {
                // Retain the checkpoint URI only as a trigger for cached() revalidation.
                // publish() hides it until validated against the new configuration.
                it.copy(status = MangaTranslationStatus.PENDING)
            })
            save(book)
        }
    }

    private suspend fun inspectLoadedPages(bookUrl: String, pages: List<MangaTranslationPage>) {
        val (snapshot, epoch) = mutex.withLock { book(bookUrl).record to configurationEpoch }
        val loaded = pages.associateBy { it.chapterId to it.pageIndex }
        snapshot.tasks.filter { task -> task.resultUri != null &&
            (task.page.chapterId to task.page.pageIndex) in loaded }.forEach { task ->
            val page = loaded.getValue(task.page.chapterId to task.page.pageIndex)
            val uri = try { processor.cached(snapshot, page) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
            mutex.withLock {
                val book = book(bookUrl)
                if (configurationEpoch == epoch && book.record.generation == snapshot.generation && task in book.record.tasks) {
                    book.validated.add(page.key)
                    book.record = book.record.copy(tasks = book.record.tasks.map {
                        if (it == task) it.copy(page = page, resultUri = uri,
                            status = if (uri == null) MangaTranslationStatus.PENDING else MangaTranslationStatus.DONE) else it
                    })
                    save(book)
                }
            }
        }
    }

    private fun startWorker() {
        if (modelMaintenance || worker?.isActive == true) return
        worker = scope.launch {
            try {
                while (true) {
                    val next = mutex.withLock {
                        val url = activeBook ?: return@withLock null
                        val book = book(url)
                        if (!book.active) return@withLock null
                        val task = book.record.tasks.filter { it.status == MangaTranslationStatus.PENDING }
                            .minWithOrNull(compareBy<MangaTranslationTask>(
                                { if (it.page.chapterId == book.chapter) 0 else 1 },
                                { if (it.page.key == book.preferred) 0 else 1 },
                                { it.page.chapterIndex }, { it.page.pageIndex })) ?: return@withLock null
                        book.record = book.record.copy(tasks = book.record.tasks.map {
                            if (it.page.key == task.page.key) it.copy(status = MangaTranslationStatus.RUNNING) else it
                        })
                        save(book)
                        book.record to task.page
                    } ?: break
                    val (record, page) = next
                    val result = runCatching {
                        processor.translate(record, page) { message ->
                            scope.launch {
                                mutex.withLock {
                                    val book = book(record.bookUrl)
                                    if (book.record.generation == record.generation && book.active &&
                                        book.record.tasks.any { it.page == page && it.status == MangaTranslationStatus.RUNNING }) publish(book, message)
                                }
                            }
                        }
                    }
                    if (result.exceptionOrNull() is CancellationException) throw result.exceptionOrNull()!!
                    mutex.withLock {
                        val book = book(record.bookUrl)
                        if (book.record.generation == record.generation) {
                            book.validated.add(page.key)
                            book.record = book.record.copy(tasks = book.record.tasks.map { task ->
                                if (task.page == page) task.copy(
                                    status = if (result.isFailure) MangaTranslationStatus.FAILED
                                        else if (result.getOrNull() == null) MangaTranslationStatus.SKIPPED else MangaTranslationStatus.DONE,
                                    resultUri = result.getOrNull()) else task
                            })
                            save(book)
                            if (result.isFailure) {
                                if (result.exceptionOrNull() is MangaTranslationPreparationException) book.active = false
                                book.failureId++
                                book.failureMessage = result.exceptionOrNull()?.localizedMessage ?: "翻译失败"
                                publish(book)
                            }
                        } else MangaTranslationFiles.retire(store.pages(record))
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutex.withLock {
                    activeBook?.let { url ->
                        val book = book(url)
                        book.active = false
                        book.record = book.record.copy(tasks = book.record.tasks.map {
                            if (it.status == MangaTranslationStatus.RUNNING) it.copy(status = MangaTranslationStatus.PENDING) else it
                        })
                        book.failureId++
                        book.failureMessage = error.localizedMessage ?: "翻译队列保存失败"
                        publish(book)
                    }
                }
            } finally {
                runCatching { processor.close() }
                mutex.withLock {
                    worker = null
                    activeBook?.let { url ->
                        val book = book(url)
                        if (book.active && book.record.tasks.any { it.status == MangaTranslationStatus.PENDING }) startWorker()
                    }
                }
            }
        }
    }

    override fun detach(bookUrl: String) {
        synchronized(lifetimeLock) {
            readerEpochs[bookUrl] = (readerEpochs[bookUrl] ?: 0L) + 1L
            books[bookUrl]?.active = false
        }
        scope.launch { mutex.withLock { books[bookUrl]?.let { publish(it, null) } } }
    }
}
