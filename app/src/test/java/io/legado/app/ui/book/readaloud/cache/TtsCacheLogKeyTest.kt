package io.legado.app.ui.book.readaloud.cache

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `AppLog` 是「毫秒时间戳 + 头插 + 满了丢尾巴」的环形表，同一毫秒内重复的同一条日志
 * 在朗读日志页会撞成同一个 LazyColumn key，Compose 直接抛
 * `IllegalArgumentException: Key … was already used`。
 * 这里守住 key 的两个性质：唯一，而且尽量保持稳定。
 */
class TtsCacheLogKeyTest {

    private fun entry(timestamp: Long, message: String) =
        TtsLogEntryUi(timestamp = timestamp, message = message, hasError = false, fullContent = message)

    @Test
    fun `same message logged twice in one millisecond still gets distinct keys`() {
        val logs = listOf(entry(1_790_963_401_997L, "TTS 预合成完成"), entry(1_790_963_401_997L, "TTS 预合成完成"))

        val keys = ttsLogKeys(logs)

        assertEquals(2, keys.toSet().size)
        assertEquals(keys[0], "1790963401997:${"TTS 预合成完成".hashCode()}")
        assertEquals(keys[1], "${keys[0]}#2")
    }

    @Test
    fun `distinct entries keep the plain timestamp key`() {
        val logs = listOf(entry(1L, "a"), entry(2L, "a"), entry(2L, "b"))

        val keys = ttsLogKeys(logs)

        assertEquals(listOf("1:${"a".hashCode()}", "2:${"a".hashCode()}", "2:${"b".hashCode()}"), keys)
    }

    @Test
    fun `a newly logged line does not renumber the lines already on screen`() {
        val old = listOf(entry(1_000L, "第一条"), entry(1_000L, "第一条"), entry(900L, "更早"))
        val oldKeys = ttsLogKeys(old)

        val withNew = ttsLogKeys(listOf(entry(2_000L, "TTS 引擎切换")) + old)

        assertEquals(oldKeys, withNew.drop(1))
        assertEquals(withNew.size, withNew.toSet().size)
    }

    @Test
    fun `dropping the oldest line keeps the remaining keys`() {
        val logs = listOf(entry(3L, "x"), entry(3L, "x"), entry(1L, "最旧的"))

        val full = ttsLogKeys(logs)
        val trimmed = ttsLogKeys(logs.dropLast(1))

        assertEquals(full.take(2), trimmed)
        assertEquals(trimmed.size, trimmed.toSet().size)
    }
}
