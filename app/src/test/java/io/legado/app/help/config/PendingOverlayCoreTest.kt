package io.legado.app.help.config

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 覆盖 PR-1 合入门禁要求的 pending overlay 三个竞态时序：
 * 1. 写入后立即读；2. 写入中 collector 回灌旧值；3. 落盘完成后回灌新值。
 */
class PendingOverlayCoreTest {

    /** 模拟的 DataStore 落盘状态 + 可手动执行的写队列 */
    private class Harness(initial: Preferences) {
        var dsState: Preferences = initial
        val writeQueue = ArrayDeque<suspend () -> Unit>()
        val core = PendingOverlayCore(
            initial = initial,
            launchWrite = { writeQueue += it },
            persist = { key, value ->
                val mutable = dsState.toMutablePreferences()
                mutable.setPrefValue(key, value)
                dsState = mutable.toPreferences()
                dsState
            },
        )

        /** 执行队列中下一个落盘任务（模拟 DsSync 串行队列跑完一个 Job） */
        fun completeNextWrite() = runBlocking { writeQueue.removeFirst().invoke() }

        fun read(key: String): Int? = core.preferencesFlow.value.compatDsInt(key)
    }

    @Test
    fun `写入后立即读到新值-落盘尚未发生`() {
        val h = Harness(mutablePreferencesOf(intPreferencesKey("k") to 1))
        h.core.put("k", 2)
        assertEquals(2, h.read("k"))
        assertEquals(1, h.writeQueue.size)
    }

    @Test
    fun `写入中 collector 回灌旧值不覆盖未落盘的新值`() {
        val h = Harness(mutablePreferencesOf(intPreferencesKey("k") to 1))
        h.core.put("k", 2)
        // 落盘未完成时，collector 携带旧状态回灌
        h.core.onEmission(mutablePreferencesOf(intPreferencesKey("k") to 1))
        assertEquals(2, h.read("k"))
        // 其他 key 的回灌内容仍然生效
        h.core.onEmission(
            mutablePreferencesOf(
                intPreferencesKey("k") to 1,
                intPreferencesKey("other") to 9,
            )
        )
        assertEquals(2, h.read("k"))
        assertEquals(9, h.read("other"))
    }

    @Test
    fun `落盘完成后回灌新值-读取保持新值且 overlay 已清空`() {
        val h = Harness(mutablePreferencesOf(intPreferencesKey("k") to 1))
        h.core.put("k", 2)
        h.completeNextWrite()
        // 落盘完成即采纳 edit 结果，读取已是新值
        assertEquals(2, h.read("k"))
        // collector 随后携带落盘后的新状态回灌
        h.core.onEmission(h.dsState)
        assertEquals(2, h.read("k"))
        // overlay 已清空：此后外部写入（如 Restore）的回灌可正常覆盖
        h.core.onEmission(mutablePreferencesOf(intPreferencesKey("k") to 5))
        assertEquals(5, h.read("k"))
    }

    @Test
    fun `同 key 连续写入-旧写入落盘不移除新写入的 overlay`() {
        val h = Harness(mutablePreferencesOf())
        h.core.put("k", 1)
        h.core.put("k", 2)
        assertEquals(2, h.read("k"))
        // 第一次写入落盘完成，读取仍是第二次写入的值
        h.completeNextWrite()
        assertEquals(2, h.read("k"))
        // 第二次写入落盘完成
        h.completeNextWrite()
        assertEquals(2, h.read("k"))
        assertEquals(2, h.dsState.compatDsInt("k"))
    }

    @Test
    fun `移除写入立即隐藏快照中的旧值`() {
        val h = Harness(mutablePreferencesOf(stringPreferencesKey("k") to "v"))
        h.core.put("k", null)
        assertNull(h.core.preferencesFlow.value.compatDsString("k"))
        h.completeNextWrite()
        assertNull(h.dsState.rawPrefValue("k"))
    }

    @Test
    fun `落盘顺序与提交顺序一致`() {
        val h = Harness(mutablePreferencesOf())
        h.core.put("a", 1)
        h.core.put("b", 2)
        h.core.put("a", 3)
        while (h.writeQueue.isNotEmpty()) h.completeNextWrite()
        assertEquals(3, h.dsState.compatDsInt("a"))
        assertEquals(2, h.dsState.compatDsInt("b"))
        assertEquals(h.dsState, h.core.preferencesFlow.value)
    }
}
