package io.legado.app.core.platform

import kotlin.test.Test
import kotlin.test.assertTrue

class ClockContractTest {

    private val clock: Clock = SystemClock

    @Test
    fun `nowMillis is in plausible epoch milliseconds range`() {
        val t = clock.nowMillis()
        // 2020-01-01 ~ 2100-12-31 的 epoch 毫秒区间。
        assertTrue(t in 1_577_836_800_000L..4_102_444_800_000L, "nowMillis=$t 不在 epoch 毫秒区间")
    }

    @Test
    fun `nowNanos is monotonic non-decreasing on consecutive reads`() {
        val a = clock.nowNanos()
        val b = clock.nowNanos()
        assertTrue(b >= a, "nowNanos 倒退: a=$a b=$b")
    }
}
