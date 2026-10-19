package io.legado.app.help.readaloud.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * 听书下载进度的「收尾」与「不丢更新」。
 *
 * 这两条都是用户在界面上直接看到的状态：`running` 收不回来，按钮就永久禁用、取消也没反应；
 * 章级与句级更新互相覆盖，进度条会回退。两者都不依赖 Android，可以直接在 JVM 上钉住。
 */
class ReadAloudAudioStoreProgressTest {

    @Test
    fun `beginDownload resets leftovers from the previous run`() {
        ReadAloudAudioStore.beginDownload("book-a", 3)
        ReadAloudAudioStore.updateProgress {
            it.copy(chapterDone = 2, sentenceDone = 10, sentenceTotal = 10, failed = 4)
        }

        ReadAloudAudioStore.beginDownload("book-b", 5)

        val progress = ReadAloudAudioStore.progress.value
        assertTrue(progress.running)
        assertEquals("book-b", progress.bookUrl)
        assertEquals(5, progress.chapterTotal)
        assertEquals(0, progress.chapterDone)
        assertEquals(0, progress.sentenceDone)
        assertEquals(0, progress.failed)
    }

    @Test
    fun `finishProgress always clears running`() {
        ReadAloudAudioStore.beginDownload("book-a", 1)
        // 取消、服务销毁、异常三条路径最后都只做这一件事：running 必须落地
        ReadAloudAudioStore.finishProgress()
        ReadAloudAudioStore.finishProgress()

        val progress = ReadAloudAudioStore.progress.value
        assertFalse(progress.running)
        assertEquals(1, progress.chapterTotal)
    }

    @Test
    fun `concurrent chapter and sentence updates do not lose each other`() {
        val threads = 4
        val perThread = 250
        ReadAloudAudioStore.beginDownload("book-a", 1)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        val failures = AtomicInteger()
        repeat(threads) {
            thread {
                try {
                    start.await()
                    repeat(perThread) {
                        ReadAloudAudioStore.updateProgress {
                            it.copy(sentenceDone = it.sentenceDone + 1)
                        }
                    }
                } catch (e: Throwable) {
                    failures.incrementAndGet()
                } finally {
                    done.countDown()
                }
            }
        }
        start.countDown()
        assertTrue(done.await(10, TimeUnit.SECONDS))
        assertEquals(0, failures.get())
        // 「读 value 再 copy 回去」在并发下会少加：这里钉住 update 的原子性
        assertEquals(threads * perThread, ReadAloudAudioStore.progress.value.sentenceDone)
    }
}
