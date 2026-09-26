package io.legado.app.help.book

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalCoroutinesApi::class)
class BookImageFileStoreTest {
    private val image = byteArrayOf(1, 2, 3, 4)
    private val store = BookImageFileStore { it.readBytes().contentEquals(image) }

    private fun withDirectory(block: (File) -> Unit) {
        val root = Files.createTempDirectory("book-image-test").toFile()
        try {
            block(root)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `explicit index survives restart and protects shared original from online eviction`() = withDirectory { root ->
        val file = root.resolve("page.jpg").apply { writeBytes(image) }
        store.setOnline(file, true)
        store.retainDownload(file, "3.hash")
        store.retainDownload(file, "4.hash")
        val restarted = BookImageFileStore { true }
        assertTrue(restarted.hasDownloadChapter(file, "3.hash"))
        assertTrue(restarted.hasDownloadChapter(file, "4.hash"))
        assertFalse(restarted.hasDownloadChapter(file, "5.hash"))
        // 即使遗留在线标记重新出现，也必须复核显式下载用途。
        root.resolve(".online").mkdirs()
        root.resolve(".online/${file.name}").writeText("")
        val online = root.resolve("online.jpg").apply { writeBytes(image) }
        restarted.setOnline(online, true)
        restarted.trimOnlineCache(root, image.size.toLong())
        assertTrue(online.isFile)
        restarted.trimOnlineCache(root, 0)
        assertFalse(online.isFile)
        assertTrue(file.isFile)
        assertArrayEquals(image, file.readBytes())
    }

    @Test
    fun `corrupt download index protects bytes and explicit retry repairs its reference`() = withDirectory { root ->
        val file = root.resolve("page.jpg").apply { writeBytes(image) }
        val index = root.resolve(".downloads/page.jpg")
        index.parentFile!!.mkdirs()
        index.writeText("chapter.bad=\\uQQQQ")
        assertTrue(store.isExplicitDownload(file))
        assertFalse(store.hasDownloadChapter(file, "1.hash"))
        store.retainDownload(file, "1.hash")
        assertTrue(store.hasDownloadChapter(file, "1.hash"))
        store.setOnline(file, true)
        assertFalse(store.isOnline(file))
        assertArrayEquals(image, file.readBytes())
    }

    @Test
    fun `partial explicit download is retained but is not valid content`() = withDirectory { root ->
        val file = root.resolve("missing.jpg")
        store.retainDownload(file, "1.hash")
        assertTrue(store.isExplicitDownload(file))
        assertFalse(store.isValid(file))
    }

    @Test
    fun `directory migration waits for readers of both source and target`() = withDirectory { root ->
        val source = root.resolve("old").apply { mkdirs() }
        val target = root.resolve("new").apply { mkdirs() }
        var moves = 0
        val move: (File, File) -> Boolean = { _, _ -> moves++; true }
        store.pin(source.resolve("page.jpg")).use {
            assertFalse(store.moveIfIdle(source, target, move))
        }
        store.pin(target.resolve("page.jpg")).use {
            assertFalse(store.moveIfIdle(source, target, move))
        }
        assertEquals(0, moves)
        assertTrue(store.moveIfIdle(source, target, move))
        assertEquals(1, moves)
    }

    @Test
    fun `eviction snapshots access times once even while markers are touched during sorting`() =
        withDirectory { root ->
            val files =
                (0..79).map { index -> root.resolve("page$index.jpg").apply { writeBytes(image) } }
            files.forEach { store.setOnline(it, true) }
            val reads = mutableMapOf<String, Int>()
            val concurrentStore = BookImageFileStore(readAccessTime = { marker ->
                val count = reads.getOrDefault(marker.name, 0) + 1
                reads[marker.name] = count
                // 旧实现比较器反复读文件属性，本检查能确定性暴露该问题。
                check(count == 1) { "Access time read again during sorting" }
                val snapshot = marker.name.removePrefix("page").removeSuffix(".jpg").toLong()
                marker.setLastModified(100_000L - snapshot)
                snapshot
            }) { true }
            concurrentStore.trimOnlineCache(root, image.size * 3L)
            assertEquals(80, reads.size)
            assertTrue(reads.values.all { it == 1 })
            assertEquals(
                files.takeLast(3).map { it.name },
                files.filter { it.exists() }.map { it.name })
        }

    @Test
    fun `online budget evicts least recently used files but preserves history and retained files`() = withDirectory { root ->
        val old = root.resolve("old.jpg").apply { writeBytes(image) }
        val recent = root.resolve("recent.jpg").apply { writeBytes(image) }
        val history = root.resolve("history.jpg").apply { writeBytes(image) }
        val retained = root.resolve("retained.jpg").apply { writeBytes(image) }
        store.setOnline(old, true)
        store.setOnline(recent, true)
        store.setOnline(retained, true)
        store.setOnline(retained, false)
        root.resolve(".online/old.jpg").setLastModified(1_000)
        root.resolve(".online/recent.jpg").setLastModified(2_000)
        // 模拟重启：用途和最近访问时间不依赖进程内的 map。
        BookImageFileStore { true }.trimOnlineCache(root, image.size.toLong())
        assertFalse(old.exists())
        assertFalse(root.resolve(".online/old.jpg").exists())
        assertTrue(recent.exists())
        assertTrue(history.exists())
        assertTrue(retained.exists())
    }

    @Test
    fun `active online file survives zero budget until last reader releases it`() = withDirectory { root ->
        val target = root.resolve("active.jpg").apply { writeBytes(image) }
        store.setOnline(target, true)
        val request = store.pin(target)
        val tiles = store.pin(target)
        request.close()
        request.close()
        store.trimOnlineCache(root, 0)
        assertTrue(target.exists())
        assertFalse(store.deleteIfIdle(root, File::deleteRecursively))
        tiles.close()
        store.trimOnlineCache(root, 0)
        assertFalse(target.exists())
    }

    @Test
    fun `orphan online marker is cleaned even below budget`() = withDirectory { root ->
        val missing = root.resolve("missing.jpg")
        store.setOnline(missing, true)
        store.trimOnlineCache(root, 1_024)
        assertFalse(root.resolve(".online/missing.jpg").exists())
    }

    @Test
    fun `synchronous publication is protected from cleanup during validation`() = withDirectory { root ->
        val target = root.resolve("page.jpg")
        lateinit var validatingStore: BookImageFileStore
        validatingStore = BookImageFileStore {
            assertFalse(validatingStore.deleteIfIdle(root, File::deleteRecursively))
            it.readBytes().contentEquals(image)
        }
        image.inputStream().use { validatingStore.write(target, it) }
        assertArrayEquals(image, target.readBytes())
        assertTrue(validatingStore.deleteIfIdle(root, File::deleteRecursively))
    }

    @Test
    fun `cleanup cannot delete an active target or its parent directory`() = runTest {
        val root = Files.createTempDirectory("book-image-cleanup").toFile()
        try {
            val target = root.resolve("images/page.jpg").apply {
                requireNotNull(parentFile).mkdirs()
                writeBytes(image)
            }
            store.withFileLock(target) {
                assertFalse(store.deleteIfIdle(target))
                assertFalse(store.deleteIfIdle(root, File::deleteRecursively))
                assertArrayEquals(image, target.readBytes())
                val unrelated = root.resolve("other.jpg").apply { writeBytes(image) }
                assertTrue(store.deleteIfIdle(unrelated))
            }
            assertTrue(store.deleteIfIdle(root, File::deleteRecursively))
            assertFalse(root.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `queued writer protects target until it finishes`() = runTest {
        val root = Files.createTempDirectory("book-image-cleanup-waiter").toFile()
        try {
            val target = root.resolve("page.jpg").apply { writeBytes(image) }
            val ownerStarted = CompletableDeferred<Unit>()
            val ownerRelease = CompletableDeferred<Unit>()
            val waiterStarted = CompletableDeferred<Unit>()
            val waiterRelease = CompletableDeferred<Unit>()
            val owner = launch {
                store.withFileLock(target) {
                    ownerStarted.complete(Unit)
                    ownerRelease.await()
                }
            }
            ownerStarted.await()
            val waiter = launch {
                store.withFileLock(target) {
                    waiterStarted.complete(Unit)
                    waiterRelease.await()
                }
            }
            runCurrent()
            ownerRelease.complete(Unit)
            owner.join()
            assertFalse(store.deleteIfIdle(target))
            waiterStarted.await()
            waiterRelease.complete(Unit)
            waiter.join()
            assertTrue(store.deleteIfIdle(target))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `publish exposes only the complete validated image and replaces atomically`() = withDirectory { root ->
        val oldImage = byteArrayOf(5, 6, 7, 8)
        val target = root.resolve("image.jpg").apply { writeBytes(oldImage) }
        var observedOldFile = false
        val validatingStore = BookImageFileStore {
            assertArrayEquals(oldImage, target.readBytes())
            observedOldFile = true
            it.readBytes().contentEquals(image)
        }
        image.inputStream().use { validatingStore.write(target, it) }
        assertTrue(observedOldFile)
        assertArrayEquals(image, target.readBytes())
        assertEquals(listOf(target.name), root.listFiles()!!.map { it.name })
    }

    @Test
    fun `failed transfer leaves no visible image or temporary file`() = withDirectory { root ->
        val target = root.resolve("image.jpg")
        val brokenStream = object : InputStream() {
            private var reads = 0
            override fun read(): Int {
                if (reads++ < 2) return 1
                throw IOException("connection lost")
            }
        }
        try {
            brokenStream.use { store.write(target, it) }
            error("Expected transfer failure")
        } catch (_: IOException) {
            assertFalse(target.exists())
            assertTrue(root.listFiles()!!.isEmpty())
        }
    }

    @Test
    fun `invalid bytes cannot replace an existing valid image`() = withDirectory { root ->
        val target = root.resolve("image.jpg").apply { writeBytes(image) }
        try {
            byteArrayOf(9).inputStream().use { store.write(target, it) }
            error("Expected validation failure")
        } catch (_: IOException) {
            assertTrue(store.isValid(target))
            assertEquals(1, root.listFiles()!!.size)
        }
    }

    @Test
    fun `cancellation before publication preserves previous image and cleans temporary file`() = withDirectory { root ->
        val target = root.resolve("image.jpg").apply { writeBytes(image) }
        var checks = 0
        try {
            image.inputStream().use { input ->
                store.write(target, input) {
                    if (++checks == 3) throw CancellationException("cancelled")
                }
            }
            error("Expected cancellation")
        } catch (_: CancellationException) {
            assertArrayEquals(image, target.readBytes())
            assertEquals(1, root.listFiles()!!.size)
        }
    }

    @Test
    fun `existence is insufficient and failed data can be replaced on retry`() = withDirectory { root ->
        val target = root.resolve("image.jpg").apply { writeBytes(byteArrayOf(9)) }
        assertFalse(store.isValid(target))
        image.inputStream().use { store.write(target, it) }
        assertTrue(store.isValid(target))
        target.writeBytes(byteArrayOf())
        assertFalse(store.isValid(target))
        assertFalse(store.isValid(root))
    }

    @Test
    fun `concurrent consumers recheck validity under the same file lock`() = runTest {
        val root = Files.createTempDirectory("book-image-concurrent").toFile()
        try {
            val target = root.resolve("image.jpg")
            val acquired = CompletableDeferred<Unit>()
            val finishTransfer = CompletableDeferred<Unit>()
            var transfers = 0
            val owner = async {
                store.withFileLock(target) {
                    if (!store.isValid(target)) {
                        transfers++
                        acquired.complete(Unit)
                        finishTransfer.await()
                        image.inputStream().use { store.write(target, it) }
                    }
                }
            }
            acquired.await()
            val waiter = async {
                store.withFileLock(target) {
                    if (!store.isValid(target)) transfers++
                }
            }
            runCurrent()
            finishTransfer.complete(Unit)
            awaitAll(owner, waiter)
            assertEquals(1, transfers)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `finishing owner and cancelling waiter cannot create a second lock`() = runTest {
        val target = File("book-image-lock-test.jpg")
        val acquired = CompletableDeferred<Unit>()
        val releaseOwner = CompletableDeferred<Unit>()
        val waiterAcquired = CompletableDeferred<Unit>()
        val releaseWaiter = CompletableDeferred<Unit>()
        val owner = launch {
            store.withFileLock(target) {
                acquired.complete(Unit)
                releaseOwner.await()
            }
        }
        acquired.await()
        val cancelledWaiter = launch { store.withFileLock(target) { error("Cancelled waiter entered") } }
        val waiter = launch {
            store.withFileLock(target) {
                waiterAcquired.complete(Unit)
                releaseWaiter.await()
            }
        }
        runCurrent()
        cancelledWaiter.cancelAndJoin()
        releaseOwner.complete(Unit)
        owner.join()
        waiterAcquired.await()
        var entered = false
        val newcomer = launch { store.withFileLock(target) { entered = true } }
        runCurrent()
        assertFalse(entered)
        releaseWaiter.complete(Unit)
        waiter.join()
        newcomer.join()
        assertTrue(entered)
        // 最后一位退出后，同一身份仍可以重新获取。
        store.withFileLock(target) { assertTrue(entered) }
    }
}
