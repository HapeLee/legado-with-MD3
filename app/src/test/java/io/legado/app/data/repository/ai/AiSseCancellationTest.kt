package io.legado.app.data.repository.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.ServerSocket
import kotlin.concurrent.thread

/**
 * [readSseData] 的两条口径：解析结果一个都不能变，取消必须真能把阻塞中的 socket 读叫醒。
 *
 * 用真 `ServerSocket` 起一个「发完就沉默」的服务端，是为了走到只在真实连接上才存在的那条路：
 * AI 分配点了取消没反应，卡的就是正文这一段的读，不是头部到达之前的那一段。
 */
class AiSseCancellationTest {

    /** 取消进行中的流式读：协程要在取消后立刻退出，而不是等服务器再发一个字节。 */
    @Test
    fun cancelWakesAStalledStream() {
        val server = SseServer()
        server.enqueue("data: hello\n\n")
        val collected = mutableListOf<String>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val client = OkHttpClient()
        try {
            val response = client.newCall(server.request()).execute()
            val job = scope.launch {
                response.readSseData { delta ->
                    synchronized(collected) { collected += delta }
                }
            }
            awaitDelivered(collected)
            job.cancel()
            runBlocking {
                try {
                    withTimeout(CANCEL_GRACE_MS) { job.join() }
                } catch (e: Throwable) {
                    fail("取消之后这条流还卡在 socket 读上：协程等不到退出")
                }
            }
            assertTrue("取消后协程仍在跑", !job.isActive)
            assertEquals(listOf("hello"), synchronized(collected) { collected.toList() })
        } finally {
            scope.cancel()
            client.dispatcher.executorService.shutdownNow()
            server.shutdown()
        }
    }

    /** 空行成帧、`[DONE]` 收尾、多行合并：改造前后一个字节都不该差。 */
    @Test
    fun keepsTheSseFraming() {
        val server = SseServer()
        server.enqueue("data: one\n\n")
        server.enqueue("data: a\ndata: b\n\n")
        server.enqueue("data: [DONE]\n\n")
        assertEquals(listOf("one", "a\nb"), server.collectAll())
    }

    /** 服务器没发 `[DONE]` 就断开：缓冲区里剩下那一条仍然要交给调用方。 */
    @Test
    fun flushesThePendingPayloadWhenTheStreamEndsEarly() {
        val server = SseServer()
        server.enqueue("data: one\n\n")
        server.enqueue("data: last\n")
        server.endAfterQueue()
        assertEquals(listOf("one", "last"), server.collectAll())
    }

    /**
     * 一次性的最小 SSE 服务端：按 [enqueue] 的顺序写正文，队列空了就沉默。
     * 沉默 = 连接挂着但一个字节都不发，正是「AI 连上了但回答停在半路」那一态。
     *
     * 收的一边必须一直读：客户端的 POST 有几百字节堆在接收队列里没人取，
     * 这时 `close()` 会发 RST 而不是 FIN，已经写出去的响应头会跟着整条被丢掉，
     * 测试就会在「还没进入正文读取」那一步先炸（`readResponseHeaders` 报连接中止）。
     */
    private class SseServer {

        private val server = ServerSocket(0)
        private val pending = ArrayDeque<String>()
        private var drainThenClose = false

        @Volatile
        private var stopped = false

        init {
            thread(isDaemon = true, name = "sse-test-server") {
                runCatching {
                    server.use { listening ->
                        listening.accept().use { socket ->
                            thread(isDaemon = true, name = "sse-test-drain") {
                                runCatching { socket.getInputStream().use { it.readBytes() } }
                            }
                            val out = socket.getOutputStream()
                            out.write(SSE_HEADERS.toByteArray())
                            out.flush()
                            while (!stopped) {
                                val chunk = nextChunk()
                                if (chunk == null) {
                                    if (drainThenClose) break
                                    Thread.sleep(POLL_MS)
                                    continue
                                }
                                out.write(chunk.toByteArray())
                                out.flush()
                            }
                        }
                    }
                }
            }
        }

        fun enqueue(chunk: String) = synchronized(pending) { pending += chunk }

        /** 正文发完就断开连接（模拟没等到 `[DONE]` 的收尾）。 */
        fun endAfterQueue() {
            drainThenClose = true
        }

        fun request(): Request = Request.Builder()
            .url("http://127.0.0.1:${server.localPort}/sse")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()

        /** 走完一整条流：起连接、读到自然结束，返回收到的载荷。 */
        fun collectAll(): List<String> {
            val client = OkHttpClient()
            return try {
                val response = client.newCall(request()).execute()
                val collected = mutableListOf<String>()
                runBlocking {
                    withTimeout(DELIVERY_GRACE_MS) {
                        response.readSseData { delta -> collected += delta }
                    }
                }
                collected
            } finally {
                client.dispatcher.executorService.shutdownNow()
                shutdown()
            }
        }

        fun shutdown() {
            stopped = true
            runCatching { server.close() }
        }

        private fun nextChunk(): String? = synchronized(pending) {
            if (pending.isEmpty()) null else pending.removeFirst()
        }
    }

    /** 等第一条 data 落到调用方手里，确认已经进到正文读取阶段（不是卡在头之前）。 */
    private fun awaitDelivered(collected: List<String>) {
        val deadline = System.currentTimeMillis() + DELIVERY_GRACE_MS
        while (System.currentTimeMillis() < deadline) {
            if (synchronized(collected) { collected.isNotEmpty() }) return
            Thread.sleep(POLL_MS)
        }
        fail("测试服务端没能把第一个事件送到读的一边")
    }

    private companion object {

        const val SSE_HEADERS = "HTTP/1.1 200 OK\r\n" +
            "Content-Type: text/event-stream\r\n" +
            "Connection: keep-alive\r\n\r\n"

        /** 沉默服务端的轮询间隔。 */
        const val POLL_MS = 20L

        /** 取消到协程退出的容忍时间：真正生效的关闭是毫秒级，这里全是给 CI 的余量。 */
        const val CANCEL_GRACE_MS = 5_000L

        /** 本地回环上一次投递（或一整条流读完）的容忍时间。 */
        const val DELIVERY_GRACE_MS = 10_000L
    }
}
