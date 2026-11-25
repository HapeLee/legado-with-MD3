package io.legado.app.data.repository

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import coil3.ImageLoader
import io.legado.app.constant.BookType
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.domain.gateway.BookshelfSettingsGateway
import io.legado.app.domain.gateway.CoverSettingsGateway
import io.legado.app.domain.model.LocalBookshelfSort
import io.legado.app.domain.model.settings.BookshelfSettings
import io.legado.app.domain.model.settings.CoverSettings
import io.legado.app.model.localBook.contentDescriptorByteSize
import io.legado.app.model.localBook.localBookFileSize
import io.legado.app.support.InMemoryAppDatabaseFixture
import io.legado.app.ui.main.bookshelf.BookShelfItem
import io.legado.app.ui.main.bookshelf.BookshelfCoverPreloader
import io.legado.app.utils.FileDoc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import splitties.init.injectAsAppCtx
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [26])
class BookshelfLocalSortTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `size sort uses file bytes and recursively counts only comic images`() = withRepository { database, repository, _ ->
        val empty = book(folder.newFile("empty.txt"))
        val smallFile = folder.newFile("小书 #1.txt").apply { writeText("小书", Charsets.UTF_8) }
        val small = book(smallFile).copy(bookUrl = smallFile.toURI().toASCIIString())
        val archive = book(folder.newFile("comic.cbz").apply { writeBytes(ByteArray(7)) })
            .copy(type = BookType.local or BookType.image or BookType.archive)
        val comicFolder = folder.newFolder("comic")
        comicFolder.resolve("image.png").writeBytes(ByteArray(4))
        comicFolder.resolve("volume").apply { mkdir() }.resolve("image.jpg").writeBytes(ByteArray(5))
        comicFolder.resolve("ignored.txt").writeBytes(ByteArray(4000))
        val comic = book(comicFolder).copy(type = BookType.local or BookType.image)
        val large = book(folder.newFile("large.txt").apply { writeBytes(ByteArray(12000)) })
        val missing = book(File(folder.root, "missing.txt")).copy(durChapterTime = 10)
        database.bookDao.insert(missing, large, comic, archive, small, empty)

        val metrics = repository.metricsOf(database.bookDao.flowBookShelfLocal())
            .await { it.size == 5 }
        val localItems = database.bookDao.flowBookShelfLocal().first()
        val group = localGroup(LocalBookshelfSort.FILE_SIZE)

        assertEquals(
            urls(empty, small, archive, comic, large, missing),
            repository.sortBooks(localItems, group, sort = 0, sortOrder = 0, localMetrics = metrics).map { it.bookUrl }
        )
        assertEquals(
            urls(large, comic, archive, small, empty, missing),
            repository.sortBooks(localItems, group, sort = 0, sortOrder = 1, localMetrics = metrics).map { it.bookUrl }
        )
    }

    @Test
    fun `image book can be a single file instead of a directory`() = withRepository { _, _, _ ->
        val image = folder.newFile("page.png").apply { writeBytes(ByteArray(23)) }
        val provider = registerMangaSizeProvider(image)

        assertEquals(23L, localBookFileSize(image.absolutePath, true))
        assertEquals(23L, contentDescriptorByteSize(Uri.parse(provider.document("fallback.png"))))
        assertEquals(23L, localBookFileSize(provider.document("fallback.png"), true))
        assertEquals(0L, localBookFileSize(provider.document("empty.png"), true))
    }

    @Test
    fun `manga size uses a descriptor when the provider omits page size`() = withRepository { _, _, _ ->
        val image = folder.newFile("page.png").apply { writeBytes(ByteArray(23)) }
        val provider = registerMangaSizeProvider(image)

        assertEquals(30L, localBookFileSize(provider.tree, true))

        provider.includeUnknownPage = true
        assertNull(localBookFileSize(provider.tree, true))
    }

    @Test
    fun `size sort caches across reading updates and restarts and rereads on refresh or reimport`() =
        withRepository { database, repository, settings ->
            val image = folder.newFile("page.png").apply { writeBytes(ByteArray(23)) }
            val provider = registerMangaSizeProvider(image)
            val safBook = book(image).copy(
                bookUrl = provider.document("known.png"),
                originName = "known.png",
                type = BookType.local or BookType.image,
                latestChapterTime = 100L,
            )
            val changingFile = folder.newFile("changing.txt").apply { writeBytes(ByteArray(3)) }
            val changing = book(changingFile).copy(latestChapterTime = 100L)
            database.bookDao.insert(safBook, changing)

            val initial = repository.metricsOf(database.bookDao.flowBookShelfLocal())
                .await { it[safBook.bookUrl] == 7L && it[changing.bookUrl] == 3L }
            assertEquals(7L, initial[safBook.bookUrl])
            val queriesAfterInitial = provider.queryCount.get()
            assertTrue(queriesAfterInitial > 0)

            // 模拟阅读进度更新和改名：等过防抖窗口确认不重扫本地文件与 SAF
            changingFile.writeBytes(ByteArray(15))
            database.bookDao.update(
                safBook.copy(name = "Renamed", durChapterTime = 999L),
                changing.copy(name = "Updated title", durChapterTime = 888L),
            )
            delay(350)
            val afterProgress = repository.metricsOf(database.bookDao.flowBookShelfLocal()).first()
            assertEquals(7L, afterProgress[safBook.bookUrl])
            assertEquals(3L, afterProgress[changing.bookUrl])
            assertEquals(queriesAfterInitial, provider.queryCount.get())

            // 验证磁盘缓存不落明文 bookUrl，且新建 Repository 实例（模拟冷启动）命中缓存零 SAF 扫描
            val cacheFile = File(folder.root, "metrics-cache.json")
            assertTrue(cacheFile.isFile)
            assertFalse(cacheFile.readText().contains(safBook.bookUrl))
            assertFalse(cacheFile.readText().contains("known.png"))
            assertFalse(cacheFile.readText().contains("changing.txt"))

            val restartedScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val restartedRepo = BookshelfRepository(database, settings, cacheFile, restartedScope)
            try {
                val restarted = restartedRepo.metricsOf(database.bookDao.flowBookShelfLocal())
                    .await { it[safBook.bookUrl] == 7L && it[changing.bookUrl] == 3L }
                assertEquals(7L, restarted[safBook.bookUrl])
                assertEquals(queriesAfterInitial, provider.queryCount.get())
            } finally {
                restartedScope.coroutineContext[Job]?.cancelAndJoin()
            }

            // 重新导入（latestChapterTime 变化）只重测该书
            database.bookDao.update(changing.copy(latestChapterTime = 200L))
            val afterReimport = repository.metricsOf(database.bookDao.flowBookShelfLocal())
                .await { it[changing.bookUrl] == 15L }
            assertEquals(15L, afterReimport[changing.bookUrl])
            assertEquals(queriesAfterInitial, provider.queryCount.get())

            // 显式刷新后重测外部改过的文件
            changingFile.writeBytes(ByteArray(25))
            repository.invalidateLocalMetrics(LocalBookshelfSort.FILE_SIZE)
            val afterRefresh = repository.metricsOf(database.bookDao.flowBookShelfLocal())
                .await { it[changing.bookUrl] == 25L }
            assertEquals(25L, afterRefresh[changing.bookUrl])
        }

    @Test
    fun `mixed group keeps online books in original slots and reorders only local books in place`() =
        withRepository { database, repository, _ ->
            val onlineA = onlineBook("online-a", name = "A在线", durChapterTime = 500)
            val onlineC = onlineBook("online-c", name = "C在线", durChapterTime = 300)
            val onlineE = onlineBook("online-e", name = "E在线", durChapterTime = 100)
            val localB = book(folder.newFile("b.txt").apply { writeBytes(ByteArray(300)) })
                .copy(name = "B本地", durChapterTime = 400)
            val localD = book(folder.newFile("d.txt").apply { writeBytes(ByteArray(100)) })
                .copy(name = "D本地", durChapterTime = 200)
            val localF = book(File(folder.root, "missing.txt"))
                .copy(name = "F未知本地", durChapterTime = 50)
            database.bookDao.insert(onlineA, onlineC, onlineE, localB, localD, localF)

            val metrics = repository.metricsOf(database.bookDao.flowBookShelfLocal())
                .await { it[localB.bookUrl] == 300L && it[localD.bookUrl] == 100L }
            val allItems = database.bookDao.flowBookShelf().first()

            // 分组单独设为按文件大小，全局是按书名升序（2）：
            // 基础排序位置：0=A在线, 1=B本地, 2=C在线, 3=D本地, 4=E在线, 5=F未知本地
            // 在线书 0/2/4 不动，本地槽位 1/3/5 按大小升序填入 D(100), B(300), F(未知)
            val customGroup = BookGroup(groupId = BookGroup.IdAll, bookSort = LocalBookshelfSort.FILE_SIZE)
            val sortedAsc = repository.sortBooks(allItems, customGroup, sort = 2, sortOrder = 0, localMetrics = metrics)
            assertEquals(urls(onlineA, localD, onlineC, localB, onlineE, localF), sortedAsc.map { it.bookUrl })

            // 全局本身设为按文件大小降序（回落基础排序为最近阅读时间降序）：
            // 基础排序位置：0=A在线(500), 1=B本地(400), 2=C在线(300), 3=D本地(200), 4=E在线(100), 5=F未知(50)
            // 本地槽位 1/3/5 按大小降序填入 B(300), D(100), F(未知)
            val followGlobalGroup = BookGroup(groupId = BookGroup.IdAll, bookSort = -1)
            val sortedDesc = repository.sortBooks(
                allItems, followGlobalGroup, sort = LocalBookshelfSort.FILE_SIZE, sortOrder = 1, localMetrics = metrics
            )
            assertEquals(urls(onlineA, localB, onlineC, localD, onlineE, localF), sortedDesc.map { it.bookUrl })

            // 纯在线书不受影响
            val onlineOnly = allItems.filterNot { it.isLocal }
            val sortedOnlineOnly = repository.sortBooks(
                onlineOnly, customGroup, sort = 2, sortOrder = 0, localMetrics = metrics
            )
            assertEquals(urls(onlineA, onlineC, onlineE), sortedOnlineOnly.map { it.bookUrl })
        }

    @Test
    fun `private group local books are measured and live removals never stay stale`() =
        withRepository { database, repository, _ ->
            val privateGroup = BookGroup(groupId = 1L, groupName = "Private", isPrivate = true, bookSort = LocalBookshelfSort.FILE_SIZE)
            val fileA = folder.newFile("priv-a.txt").apply { writeBytes(ByteArray(500)) }
            val fileB = folder.newFile("priv-b.txt").apply { writeBytes(ByteArray(100)) }
            val bookA = book(fileA).copy(group = 1L)
            val bookB = book(fileB).copy(group = 1L)
            database.bookGroupDao.insert(privateGroup)
            database.bookDao.insert(bookA, bookB)

            // 与书架生产路径一致：指标只来自该分组自己的书单
            val combined = repository.flowBooksWithLocalMetrics(
                database.bookDao.flowBookShelfByGroup(privateGroup.groupId),
                LocalBookshelfSort.FILE_SIZE,
            ).map { (items, metrics) ->
                repository.sortBooks(items, privateGroup, sort = 0, sortOrder = 0, localMetrics = metrics)
                    .map { it.bookUrl }
            }

            assertEquals(
                urls(bookB, bookA),
                withTimeout(20_000) { combined.first { it == urls(bookB, bookA) } }
            )

            // 删除一本书后，书架组合流立刻移除该书，不会滞留旧快照
            database.bookDao.delete(bookB)
            assertEquals(
                urls(bookA),
                withTimeout(20_000) { combined.first { it == urls(bookA) } }
            )
        }

    @Test
    fun `cover preload does not scan uncached local files before the shelf opens`() =
        withRepository { database, repository, shelfSettings ->
            val image = folder.newFile("cover.png")
            val provider = registerMangaSizeProvider(image)
            val coverPath = image.toURI().toASCIIString()
            val book = book(image).copy(bookUrl = provider.document("known.png"), coverUrl = coverPath)
            database.bookDao.insert(book)
            database.bookGroupDao.insert(localGroup(LocalBookshelfSort.FILE_SIZE))
            shelfSettings.update { it.copy(saveTabPosition = BookGroup.IdLocal) }
            val app = RuntimeEnvironment.getApplication()
            val loader = ImageLoader.Builder(app).build()
            try {
                val coverSettings = object : CoverSettingsGateway {
                    override val settings = MutableStateFlow(CoverSettings())
                    override val currentSettings get() = settings.value
                    override suspend fun update(transform: (CoverSettings) -> CoverSettings) = settings.update(transform)
                }
                val preloader = BookshelfCoverPreloader(
                    app, loader, BookRepository(database.bookDao, database.bookChapterDao, database),
                    BookGroupRepository(database.bookGroupDao), repository,
                    shelfSettings, coverSettings, PrivateContentRepository(database.bookDao),
                )

                assertEquals(0, withTimeout(20_000) { preloader.preloadCurrentGroupFirstScreen() })
                assertEquals(0, provider.queryCount.get())

                // 指标落盘缓存后，tryGetCachedMetrics 直接命中缓存且零 SAF 查询
                val measured = repository.metricsOf(database.bookDao.flowBookShelfLocal())
                    .await { it[book.bookUrl] == 7L }
                assertEquals(7L, measured[book.bookUrl])
                val queriesAfterMeasure = provider.queryCount.get()
                val shelfItems = database.bookDao.flowBookShelfLocal().first()
                assertEquals(
                    mapOf(book.bookUrl to 7L),
                    repository.tryGetCachedMetrics(LocalBookshelfSort.FILE_SIZE, shelfItems),
                )
                assertEquals(queriesAfterMeasure, provider.queryCount.get())
            } finally {
                loader.shutdown()
            }
        }

    @Test
    fun `refresh discards a stale read that finishes after the cache was cleared`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        // 第一次读取模拟阻塞在 SAF 查询里的慢读取：刷新清空缓存后才返回旧值 4
        val reader: suspend (String, Boolean) -> Long? = { _, _ ->
            if (calls.incrementAndGet() == 1) {
                entered.countDown()
                release.await(20, TimeUnit.SECONDS)
                4L
            } else {
                16L
            }
        }
        withRepository(fileSizeReader = reader) { database, repository, _ ->
            val target = book(folder.newFile("race.txt"))
            database.bookDao.insert(target)
            val latest = MutableStateFlow<Map<String, Long>?>(null)
            val collector = launch(Dispatchers.Default) {
                repository.metricsOf(database.bookDao.flowBookShelfLocal()).collect { latest.value = it }
            }
            try {
                assertTrue(withContext(Dispatchers.IO) { entered.await(20, TimeUnit.SECONDS) })
                repository.invalidateLocalMetrics(LocalBookshelfSort.FILE_SIZE)
                // 先放行阻塞在刷新前的旧读取，确认旧值 4 不会回填缓存，下一轮扫描会继续读取新值 16
                release.countDown()
                withTimeout(20_000) { latest.first { it?.get(target.bookUrl) == 16L } }

                assertEquals(16L, latest.value?.get(target.bookUrl))
                val items = database.bookDao.flowBookShelfLocal().first()
                assertEquals(
                    mapOf(target.bookUrl to 16L),
                    repository.tryGetCachedMetrics(LocalBookshelfSort.FILE_SIZE, items),
                )
                assertEquals(2, calls.get())
            } finally {
                release.countDown()
                collector.cancelAndJoin()
            }
        }
    }

    @Test
    fun `cache file never stores plain file names and drops the legacy plaintext format`() =
        withRepository { database, repository, _ ->
            val cacheFile = File(folder.root, "metrics-cache.json")
            cacheFile.writeText(
                """{"version":1,"fileSize":{"abc":{"v":1,"s":"1|私密旧书.txt|0"}}}""",
                Charsets.UTF_8,
            )
            val secret = book(folder.newFile("私密书名.txt").apply { writeBytes(ByteArray(11)) })
            database.bookDao.insert(secret)

            val metrics = repository.metricsOf(database.bookDao.flowBookShelfLocal())
                .await { it[secret.bookUrl] == 11L }
            assertEquals(11L, metrics[secret.bookUrl])

            val text = cacheFile.readText(Charsets.UTF_8)
            assertTrue(text.contains("\"version\":2"))
            assertFalse(text.contains("私密书名"))
            assertFalse(text.contains("私密旧书"))
            assertFalse(text.contains(secret.bookUrl))
        }

    @Test
    fun `reimporting a manga directory rereads size even when the directory time is unchanged`() =
        withRepository { database, repository, _ ->
            val image = folder.newFile("page.png").apply { writeBytes(ByteArray(23)) }
            val provider = registerMangaSizeProvider(image)
            val safBook = book(image).copy(
                bookUrl = provider.document("known.png"),
                originName = "known.png",
                type = BookType.local or BookType.image,
                latestChapterTime = 100L,
            )
            database.bookDao.insert(safBook)

            val comicDir = folder.newFolder("同名漫画")
            val page = comicDir.resolve("001.png").apply { writeBytes(ByteArray(4)) }
            val fixedTime = 1_700_000_000_000L
            assertTrue(comicDir.setLastModified(fixedTime))
            val importRepository = BookImportRepository(database, repository)
            fun directoryDoc() = FileDoc(
                name = comicDir.name,
                isDir = true,
                size = 0L,
                lastModified = comicDir.lastModified(),
                uri = Uri.Builder().path(comicDir.absolutePath).build(),
            )

            val book = importRepository.importMangaDirectory(directoryDoc())
            val localBooks = database.bookDao.flowBookShelfLocal()
            val initial = repository.metricsOf(localBooks)
                .await { it[book.bookUrl] == 4L && it[safBook.bookUrl] == 7L }
            assertEquals(4L, initial[book.bookUrl])
            val safQueriesAfterInitial = provider.queryCount.get()

            // 原地覆盖图片：大小变化但目录修改时间保持不变，书籍校验戳也不变
            page.writeBytes(ByteArray(9))
            assertTrue(comicDir.setLastModified(fixedTime))
            assertEquals(fixedTime, comicDir.lastModified())
            delay(350)
            assertEquals(4L, repository.metricsOf(localBooks).first()[book.bookUrl])

            // 直接调用 LocalBook.importMangaDirectory 也应按书籍失效缓存，且不影响其他已缓存的 SAF 书籍
            io.legado.app.model.localBook.LocalBook.importMangaDirectory(directoryDoc())
            assertEquals(fixedTime, database.bookDao.getBook(book.bookUrl)?.latestChapterTime)
            assertEquals(9L, repository.metricsOf(localBooks).await { it[book.bookUrl] == 9L }[book.bookUrl])
            assertEquals(safQueriesAfterInitial, provider.queryCount.get())

            // 通过 BookImportRepository.importMangaDirectory 重新导入同样生效
            page.writeBytes(ByteArray(14))
            assertTrue(comicDir.setLastModified(fixedTime))
            importRepository.importMangaDirectory(directoryDoc())
            assertEquals(14L, repository.metricsOf(localBooks).await { it[book.bookUrl] == 14L }[book.bookUrl])
            assertEquals(safQueriesAfterInitial, provider.queryCount.get())
        }

    @Test
    fun `size sort only measures local books inside the subscribed group and reuses shared cache`() {
        val readUrls = java.util.Collections.synchronizedList(mutableListOf<String>())
        val reader: suspend (String, Boolean) -> Long? = { url, isMangaDir ->
            readUrls.add(url)
            localBookFileSize(url, isMangaDir)
        }
        withRepository(fileSizeReader = reader) { database, repository, _ ->
            val image = folder.newFile("page.png").apply { writeBytes(ByteArray(23)) }
            val provider = registerMangaSizeProvider(image)
            val localGroup = BookGroup(groupId = 1L, groupName = "本地A", bookSort = LocalBookshelfSort.FILE_SIZE)
            val onlineGroup = BookGroup(groupId = 2L, groupName = "纯在线", bookSort = LocalBookshelfSort.FILE_SIZE)
            val otherGroup = BookGroup(groupId = 4L, groupName = "其他组", bookSort = 0)
            database.bookGroupDao.insert(localGroup, onlineGroup, otherGroup)

            val localA = book(folder.newFile("group-a.txt").apply { writeBytes(ByteArray(42)) })
                .copy(group = localGroup.groupId)
            val otherSaf = book(image).copy(
                bookUrl = provider.tree,
                originName = "manga",
                type = BookType.local or BookType.image,
                group = otherGroup.groupId,
            )
            val online = onlineBook("online-only").copy(group = onlineGroup.groupId)
            database.bookDao.insert(localA, otherSaf, online)

            val (onlineBooks, onlineMetrics) = withTimeout(20_000) {
                repository.flowBooksWithLocalMetrics(
                    database.bookDao.flowBookShelfByGroup(onlineGroup.groupId),
                    LocalBookshelfSort.FILE_SIZE,
                ).first { (books, _) -> books.isNotEmpty() }
            }
            assertEquals(listOf(online.bookUrl), onlineBooks.map { it.bookUrl })
            assertTrue(onlineMetrics.isEmpty())
            delay(300)
            assertTrue(readUrls.isEmpty())
            assertEquals(0, provider.queryCount.get())

            // 订阅本地分组 A：只扫描 localA，不扫描其他分组的 SAF 漫画目录
            val localAMetrics = withTimeout(20_000) {
                repository.flowBooksWithLocalMetrics(
                    database.bookDao.flowBookShelfByGroup(localGroup.groupId),
                    LocalBookshelfSort.FILE_SIZE,
                ).map { it.second }.first { it[localA.bookUrl] == 42L }
            }
            assertEquals(mapOf(localA.bookUrl to 42L), localAMetrics)
            assertEquals(listOf(localA.bookUrl), readUrls.toList())
            assertEquals(0, provider.queryCount.get())

            // 再订阅包含全部本地书的分组：localA 命中同一份缓存不重读，仅新增扫描 otherSaf
            val allLocalMetrics = repository.metricsOf(database.bookDao.flowBookShelfLocal())
                .await { it[localA.bookUrl] == 42L && it[otherSaf.bookUrl] == 30L }
            assertEquals(42L, allLocalMetrics[localA.bookUrl])
            assertEquals(30L, allLocalMetrics[otherSaf.bookUrl])
            assertEquals(listOf(localA.bookUrl, otherSaf.bookUrl), readUrls.toList())
            assertTrue(provider.queryCount.get() > 0)
        }
    }

    private fun BookshelfRepository.metricsOf(books: Flow<List<BookShelfItem>>): Flow<Map<String, Long>> =
        flowLocalMetrics(LocalBookshelfSort.FILE_SIZE, books)

    private fun withRepository(
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        fileSizeReader: suspend (String, Boolean) -> Long? = ::localBookFileSize,
        test: suspend CoroutineScope.(AppDatabase, BookshelfRepository, TestSettings) -> Unit,
    ) = runBlocking {
        val context = RuntimeEnvironment.getApplication().apply { injectAsAppCtx() }
        InMemoryAppDatabaseFixture(context).use { fixture ->
            val database = fixture.database
            val settings = TestSettings()
            val cacheFile = File(folder.root, "metrics-cache.json")
            val repository = BookshelfRepository(
                database = database,
                settingsGateway = settings,
                cacheFile = cacheFile,
                scope = scope,
                fileSizeReader = fileSizeReader,
            )
            try {
                test(database, repository, settings)
            } finally {
                scope.coroutineContext[Job]?.cancelAndJoin()
            }
        }
    }

    private class TestSettings : BookshelfSettingsGateway {
        override val settings = MutableStateFlow(BookshelfSettings(bookshelfSortOrder = 0))
        override val currentSettings get() = settings.value

        override suspend fun update(transform: (BookshelfSettings) -> BookshelfSettings) {
            settings.update(transform)
        }
    }

    private fun registerMangaSizeProvider(image: File): MangaSizeProvider {
        val provider = MangaSizeProvider(image)
        provider.attachInfo(RuntimeEnvironment.getApplication(), ProviderInfo().apply {
            authority = provider.authority
            exported = true
        })
        ShadowContentResolver.registerProviderInternal(provider.authority, provider)
        return provider
    }

    private class MangaSizeProvider(private val image: File) : ContentProvider() {
        val authority = "test.manga.size"
        val tree = DocumentsContract.buildTreeDocumentUri(authority, "manga").toString()
        val queryCount = AtomicInteger()
        var includeUnknownPage = false

        fun document(id: String) = DocumentsContract.buildDocumentUri(authority, id).toString()

        override fun onCreate() = true

        override fun getType(uri: Uri): String = if (DocumentsContract.getDocumentId(uri) == "manga") {
            DocumentsContract.Document.MIME_TYPE_DIR
        } else {
            "image/png"
        }

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor {
            queryCount.incrementAndGet()
            val columns = requireNotNull(projection).map { it }.toTypedArray()
            val ids = if (uri.lastPathSegment == "children") {
                listOf("known.png", "fallback.png") + if (includeUnknownPage) listOf("unknown.png") else emptyList()
            } else {
                listOf(DocumentsContract.getDocumentId(uri))
            }
            return MatrixCursor(columns).apply {
                for (id in ids) {
                    addRow(columns.map { column ->
                        when (column) {
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME -> id
                            DocumentsContract.Document.COLUMN_MIME_TYPE -> if (id == "manga") {
                                DocumentsContract.Document.MIME_TYPE_DIR
                            } else {
                                "image/png"
                            }
                            DocumentsContract.Document.COLUMN_LAST_MODIFIED -> 0L
                            DocumentsContract.Document.COLUMN_SIZE -> when (id) {
                                "known.png" -> 7L
                                "empty.png" -> 0L
                                else -> null
                            }
                            else -> null
                        }
                    })
                }
            }
        }

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            if (DocumentsContract.getDocumentId(uri) != "fallback.png") {
                throw FileNotFoundException(uri.toString())
            }
            return ParcelFileDescriptor.open(image, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        override fun insert(uri: Uri, values: ContentValues?): Uri = throw UnsupportedOperationException()
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
            throw UnsupportedOperationException()
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
            throw UnsupportedOperationException()
    }

    private suspend fun Flow<Map<String, Long>>.await(predicate: (Map<String, Long>) -> Boolean = { true }): Map<String, Long> =
        withTimeout(20_000) { first(predicate) }

    private fun urls(vararg books: Book) = books.map { it.bookUrl }

    private fun localGroup(sort: Int) = BookGroup(groupId = BookGroup.IdLocal, bookSort = sort)

    private fun book(file: File) = Book(
        bookUrl = file.absolutePath,
        name = file.nameWithoutExtension,
        origin = BookType.localTag,
        originName = file.name,
        type = BookType.local or BookType.text,
        latestChapterTime = 0,
        durChapterTime = 0,
    )

    private fun onlineBook(url: String, name: String = url, durChapterTime: Long = 0L) = Book(
        bookUrl = url,
        name = name,
        origin = "https://source.test",
        originName = "TestSource",
        type = BookType.text,
        latestChapterTime = 0,
        durChapterTime = durChapterTime,
    )
}
