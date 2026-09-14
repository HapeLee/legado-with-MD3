package io.legado.app.data.rate

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * [ConcurrentRateRegistry] 的行为契约（M2-4b：从 `:app` `help.ConcurrentRateLimiter` 的
 * companion 下沉到共享层，原实现零测试）。
 *
 * 守的是**解析语义与迁移前逐字一致**：`concurrentRate` 是书源里写的 `"次数/间隔"` 或纯数字
 * `"间隔"`（此时次数按 1）。非法值必须**保持原记录不动**，而不是把并发限制清成 0/1 ——
 * 后者会让所有源退化成无节流，是更坏的失败方式。写法来自用户手写书源，五花八门，
 * 所以 0 / 负数 / 非数字 / 缺斜杠 这些边界都要钉住。
 *
 * 登记表是全局 object，故每个用例前后都清空，避免用例间互相污染。
 */
class ConcurrentRateRegistryTest {

    private val key = "source-key"

    @BeforeTest
    fun setUp() {
        ConcurrentRateRegistry.records.clear()
    }

    @AfterTest
    fun tearDown() {
        ConcurrentRateRegistry.records.clear()
    }

    @Test
    fun `次数斜杠间隔——分别解析为 accessLimit 与 interval，frequency 从 0 起`() {
        ConcurrentRateRegistry.update(key, "3/10")
        val record = assertNotNull(ConcurrentRateRegistry.records[key])
        assertEquals(3, record.accessLimit)
        assertEquals(10, record.interval)
        assertEquals(0, record.frequency)
    }

    @Test
    fun `纯数字——视为 1 次每 interval`() {
        ConcurrentRateRegistry.update(key, "10")
        val record = assertNotNull(ConcurrentRateRegistry.records[key])
        assertEquals(1, record.accessLimit)
        assertEquals(10, record.interval)
    }

    @Test
    fun `同一 key 再次更新沿用首次的 time 与 frequency`() {
        ConcurrentRateRegistry.update(key, "3/10")
        val first = assertNotNull(ConcurrentRateRegistry.records[key])
        ConcurrentRateRegistry.update(key, "5/20")
        val second = assertNotNull(ConcurrentRateRegistry.records[key])
        // time 只在首次登记时取系统时间，之后一直沿用——节流窗口不能因为改配置就重置。
        assertEquals(first.time, second.time)
        assertEquals(first.frequency, second.frequency)
        assertEquals(5, second.accessLimit)
        assertEquals(20, second.interval)
    }

    @Test
    fun `次数或间隔非正时保持原记录不变`() {
        ConcurrentRateRegistry.update(key, "3/10")
        val before = assertNotNull(ConcurrentRateRegistry.records[key])
        ConcurrentRateRegistry.update(key, "0/10")  // accessLimit <= 0
        ConcurrentRateRegistry.update(key, "-1/10") // accessLimit <= 0
        ConcurrentRateRegistry.update(key, "3/0")   // interval <= 0
        val after = assertNotNull(ConcurrentRateRegistry.records[key])
        assertEquals(before.accessLimit, after.accessLimit)
        assertEquals(before.interval, after.interval)
    }

    @Test
    fun `非数字保持原记录不变，且从未设过的 key 不落表`() {
        // compute 在非法输入时返回原值；原值为 null ⇒ 不落表，而不是写入一条 0/0 记录。
        ConcurrentRateRegistry.update(key, "abc")
        assertNull(ConcurrentRateRegistry.records[key])

        ConcurrentRateRegistry.update(key, "3/10")
        val before = assertNotNull(ConcurrentRateRegistry.records[key])
        ConcurrentRateRegistry.update(key, "abc")
        val after = assertNotNull(ConcurrentRateRegistry.records[key])
        assertEquals(before.accessLimit, after.accessLimit)
        assertEquals(before.interval, after.interval)
    }
}
