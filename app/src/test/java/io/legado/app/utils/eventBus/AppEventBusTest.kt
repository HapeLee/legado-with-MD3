package io.legado.app.utils.eventBus

import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AppEventBus] 的语义基线。
 *
 * 语义说明（与被替换的 LiveEventBus 的差异）：
 * - LiveEventBus 的 `observeForever(observer)` 会在 `post` 调用栈内**内联**回调观察者；
 *   本总线基于 Flow，`trySend` 只把值放入 channel，下游 collector 在下一个调度点才收到。
 *   因此**投递到 collector 是异步的**。
 * - 受影响的只有 `observeEvent` 的两个调用点（NOTIFY_MAIN → setupSystemBar、
 *   READ_ALOUD_PLAY → newReadAloud），二者都是「触发动作」型，post 后不立即读状态，
 *   延迟一帧无行为差异。**其余 100+ 处调用点原本就走 callbackFlow + trySend，行为不变。**
 *
 * 这组测试锁住的是真正会被破坏的不变式：顺序、粘性、扇入、取消、tag 隔离。
 */
class AppEventBusTest {

    @Test
    fun `按发送顺序投递且不丢事件`() = runBlocking {
        val received = mutableListOf<String>()
        val job = AppEventBus.observe<String>("order").onEach { received += it }.launchIn(this)
        delay(20) // 等 callbackFlow 完成监听注册

        AppEventBus.post("order", "a")
        AppEventBus.post("order", "b")
        AppEventBus.post("order", "c")
        delay(20)

        assertEquals(listOf("a", "b", "c"), received)
        job.cancel()
    }

    @Test
    fun `非粘性订阅收不到订阅前的值`() = runBlocking {
        AppEventBus.post("plain", "before")

        val received = mutableListOf<String>()
        val job = AppEventBus.observe<String>("plain").onEach { received += it }.launchIn(this)
        delay(20)

        AppEventBus.post("plain", "after")
        delay(20)

        assertEquals(listOf("after"), received)
        job.cancel()
    }

    @Test
    fun `粘性订阅先收到最后一次值`() = runBlocking {
        AppEventBus.post("sticky", "last")

        val received = mutableListOf<String>()
        val job = AppEventBus.observeSticky<String>("sticky").onEach { received += it }.launchIn(this)
        delay(20)

        AppEventBus.post("sticky", "newer")
        delay(20)

        assertEquals(listOf("last", "newer"), received)
        job.cancel()
    }

    @Test
    fun `observeAll 扇入多个 tag 且过滤未订阅的 tag`() = runBlocking {
        val received = mutableListOf<String>()
        val job = AppEventBus.observeAll<String>(listOf("t1", "t2"))
            .onEach { received += it }
            .launchIn(this)
        delay(20)

        AppEventBus.post("t1", "one")
        AppEventBus.post("t2", "two")
        AppEventBus.post("t3", "ignored")
        delay(20)

        assertEquals(listOf("one", "two"), received)
        job.cancel()
    }

    @Test
    fun `取消订阅后不再收到事件`() = runBlocking {
        val received = mutableListOf<String>()
        val job = launch {
            AppEventBus.observe<String>("cancel").onEach { received += it }.collect { }
        }
        delay(20)
        AppEventBus.post("cancel", "a")
        delay(20)
        assertEquals(listOf("a"), received)

        job.cancel()
        job.join()
        AppEventBus.post("cancel", "b")
        delay(20)

        assertEquals(listOf("a"), received)
    }

    @Test
    fun `无订阅者时 post 不抛异常且值被记录为粘性`() = runBlocking {
        AppEventBus.post("lonely", 42)

        val received = mutableListOf<Int>()
        val job = AppEventBus.observeSticky<Int>("lonely").onEach { received += it }.launchIn(this)
        delay(20)

        assertTrue(received.contains(42))
        job.cancel()
    }

    @Test
    fun `同 tag 多订阅者都会收到`() = runBlocking {
        val first = mutableListOf<String>()
        val second = mutableListOf<String>()
        val j1 = AppEventBus.observe<String>("multi").onEach { first += it }.launchIn(this)
        val j2 = AppEventBus.observe<String>("multi").onEach { second += it }.launchIn(this)
        delay(20)

        AppEventBus.post("multi", "x")
        delay(20)

        assertEquals(listOf("x"), first)
        assertEquals(listOf("x"), second)
        j1.cancel()
        j2.cancel()
    }
}
