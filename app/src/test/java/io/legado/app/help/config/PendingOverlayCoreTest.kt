package io.legado.app.help.config

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * 覆盖 PR-1 合入门禁要求的 pending overlay 三个竞态时序：
 * 1. 写入后立即读；2. 写入中 collector 回灌旧值；3. 落盘完成后回灌新值。
 */
class PendingOverlayCoreTest {

    private data class CoupledSettings(
        val eInk: Boolean = false,
        val gray: Boolean = false,
    )

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
            persistAll = { values ->
                val mutable = dsState.toMutablePreferences()
                values.forEach { (key, value) -> mutable.setPrefValue(key, value) }
                dsState = mutable.toPreferences()
                dsState
            },
        )

        /** 执行队列中下一个落盘任务（模拟 SettingsWriter 串行队列跑完一个 Job） */
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
        // 落盘完成后新值仍由 overlay 保护，读取已是新值
        assertEquals(2, h.read("k"))
        // collector 携带落盘后的新状态回灌，确认后 overlay 移除
        h.core.onEmission(h.dsState)
        assertEquals(2, h.read("k"))
        // overlay 已清空：此后外部写入（如 Restore）的回灌可正常覆盖
        h.core.onEmission(mutablePreferencesOf(intPreferencesKey("k") to 5))
        assertEquals(5, h.read("k"))
    }

    @Test
    fun `落盘完成后处理到旧回灌-读值不回滚`() {
        val h = Harness(mutablePreferencesOf(intPreferencesKey("k") to 1))
        h.core.put("k", 2)
        h.completeNextWrite()
        // collector 此时才处理一条落盘前排队的旧回灌，读值不得回滚（否则 observe
        // 订阅方会收到 2→1→2 的幻影变更）
        h.core.onEmission(mutablePreferencesOf(intPreferencesKey("k") to 1))
        assertEquals(2, h.read("k"))
        // 含新值的回灌到达后 overlay 才移除，外部写入随后可正常覆盖
        h.core.onEmission(h.dsState)
        assertEquals(2, h.read("k"))
        h.core.onEmission(mutablePreferencesOf(intPreferencesKey("k") to 7))
        assertEquals(7, h.read("k"))
    }

    @Test
    fun `批量写入立即生效-单次落盘-回灌确认后清空 overlay`() {
        val h = Harness(mutablePreferencesOf(intPreferencesKey("a") to 1))
        h.core.putAll(mapOf("a" to 2, "b" to "x"))
        // 写入内存立即可读，且只排一个落盘任务
        assertEquals(2, h.read("a"))
        assertEquals("x", h.core.preferencesFlow.value.compatDsString("b"))
        assertEquals(1, h.writeQueue.size)
        h.completeNextWrite()
        assertEquals(2, h.dsState.compatDsInt("a"))
        assertEquals("x", h.dsState.compatDsString("b"))
        // 回灌确认后 overlay 清空，外部回灌可覆盖
        h.core.onEmission(h.dsState)
        h.core.onEmission(mutablePreferencesOf(intPreferencesKey("a") to 9))
        assertEquals(9, h.read("a"))
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

    @Test
    fun `写入异常时-overlay仍被清空且回退到磁盘旧值`() {
        var throwError = false
        var dsState: Preferences = mutablePreferencesOf(intPreferencesKey("k") to 1)
        val writeQueue = ArrayDeque<suspend () -> Unit>()
        val core = PendingOverlayCore(
            initial = dsState,
            launchWrite = { writeQueue += it },
            persist = { key, value ->
                if (throwError) {
                    throw java.io.IOException("Disk full")
                }
                val mutable = dsState.toMutablePreferences()
                mutable.setPrefValue(key, value)
                dsState = mutable.toPreferences()
                dsState
            },
            persistAll = { error("unused") },
        )
        core.put("k", 2)
        assertEquals(2, core.preferencesFlow.value.compatDsInt("k"))

        throwError = true
        try {
            runBlocking { writeQueue.removeFirst().invoke() }
        } catch (e: Exception) {
            // Expected
        }

        // 验证 overlay 已被清除，读取回退到快照中的旧值 1
        assertEquals(1, core.preferencesFlow.value.compatDsInt("k"))
    }

    @Test
    fun `同key连续写入-第一次失败第二次成功-最终正确应用第二次的值`() {
        var firstWrite = true
        var dsState: Preferences = mutablePreferencesOf(intPreferencesKey("k") to 1)
        val writeQueue = ArrayDeque<suspend () -> Unit>()
        val core = PendingOverlayCore(
            initial = dsState,
            launchWrite = { writeQueue += it },
            persist = { key, value ->
                if (firstWrite) {
                    firstWrite = false
                    throw java.io.IOException("Disk full on first write")
                }
                val mutable = dsState.toMutablePreferences()
                mutable.setPrefValue(key, value)
                dsState = mutable.toPreferences()
                dsState
            },
            persistAll = { error("unused") },
        )
        // 第一次写入 2 (失败)
        core.put("k", 2)
        // 第二次写入 3 (成功)
        core.put("k", 3)

        assertEquals(3, core.preferencesFlow.value.compatDsInt("k"))

        // 执行第一个任务（失败）
        try {
            runBlocking { writeQueue.removeFirst().invoke() }
        } catch (e: Exception) {
            // Expected
        }

        // 此时因为 pending 中最新的 write 对象是 3 的写入，且 3 的任务还没跑完，
        // 即使 2 的写入任务失败清理了，读取也应该保持最新值 3 (来自 overlay)，而不是回退到 1
        assertEquals(3, core.preferencesFlow.value.compatDsInt("k"))

        // 执行第二个任务（成功）
        runBlocking { writeQueue.removeFirst().invoke() }

        // 第二个成功落盘，读取为 3（overlay 待回灌确认后清空）
        assertEquals(3, core.preferencesFlow.value.compatDsInt("k"))
        assertEquals(3, dsState.compatDsInt("k"))
    }

    @Test
    fun `等待批量写入时落盘异常会回滚并传给调用方`() = runBlocking {
        val initial = mutablePreferencesOf(intPreferencesKey("k") to 1)
        val writeQueue = ArrayDeque<suspend () -> Unit>()
        val core = PendingOverlayCore(
            initial = initial,
            launchWrite = { writeQueue += it },
            persist = { _, _ -> error("unused") },
            persistAll = { throw java.io.IOException("disk full") },
        )

        val result = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { core.putAllAndAwait(mapOf("k" to 2)) }
        }
        assertEquals(2, core.preferencesFlow.value.compatDsInt("k"))

        writeQueue.removeFirst().invoke()

        assertEquals("disk full", result.await().exceptionOrNull()?.message)
        assertEquals(1, core.preferencesFlow.value.compatDsInt("k"))
    }

    @Test
    fun `原子 transform 串行读取 overlay 并保持跨字段不变式`() {
        val core = PendingOverlayCore(
            initial = CoupledSettings().toPreferences(),
            launchWrite = {},
            persist = { _, _ -> error("不会执行落盘") },
            persistAll = { error("不会执行落盘") },
        )
        val firstEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val first = thread {
            core.atomicUpdate(
                read = { it.toCoupledSettings() },
                toPrefMap = { it.toPrefMap() },
            ) {
                firstEntered.countDown()
                releaseFirst.await()
                it.copy(eInk = true, gray = false)
            }
        }
        firstEntered.await()

        val secondStarted = CountDownLatch(1)
        val secondEntered = CountDownLatch(1)
        val second = thread {
            secondStarted.countDown()
            core.atomicUpdate(
                read = { it.toCoupledSettings() },
                toPrefMap = { it.toPrefMap() },
            ) {
                secondEntered.countDown()
                it.copy(eInk = false, gray = true)
            }
        }
        secondStarted.await()
        val secondRacedWithFirst = secondEntered.await(200, TimeUnit.MILLISECONDS)
        releaseFirst.countDown()
        first.join()
        second.join()

        assertFalse(secondRacedWithFirst)
        assertEquals(
            CoupledSettings(eInk = false, gray = true),
            core.preferencesFlow.value.toCoupledSettings(),
        )
    }

    @Test
    fun `等待原子 transform 落盘失败会回滚并传给调用方`() = runBlocking {
        val initial = mutablePreferencesOf(intPreferencesKey("k") to 1)
        val writeQueue = ArrayDeque<suspend () -> Unit>()
        val core = PendingOverlayCore(
            initial = initial,
            launchWrite = { writeQueue += it },
            persist = { _, _ -> error("unused") },
            persistAll = { throw java.io.IOException("atomic disk full") },
        )

        val result = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching {
                core.atomicUpdateAndAwait<Int>(
                    read = { preferences -> preferences.compatDsInt("k") ?: 0 },
                    toPrefMap = { value -> mapOf("k" to value) },
                    transform = { it + 1 },
                )
            }
        }
        assertEquals(2, core.preferencesFlow.value.compatDsInt("k"))

        writeQueue.removeFirst().invoke()

        assertEquals("atomic disk full", result.await().exceptionOrNull()?.message)
        assertEquals(1, core.preferencesFlow.value.compatDsInt("k"))
    }

    @Test
    fun `等待原子 transform 无差量时不入队并立即返回`() = runBlocking {
        val core = PendingOverlayCore(
            initial = mutablePreferencesOf(intPreferencesKey("k") to 1),
            launchWrite = { error("无差量不应入队") },
            persist = { _, _ -> error("unused") },
            persistAll = { error("unused") },
        )

        core.atomicUpdateAndAwait(
            read = { preferences -> preferences.compatDsInt("k") ?: 0 },
            toPrefMap = { value -> mapOf("k" to value) },
            transform = { it },
        )

        assertEquals(1, core.preferencesFlow.value.compatDsInt("k"))
    }

    private fun Preferences.toCoupledSettings(): CoupledSettings = CoupledSettings(
        eInk = compatDsBoolean("eInk") ?: false,
        gray = compatDsBoolean("gray") ?: false,
    )

    private fun CoupledSettings.toPrefMap(): Map<String, Any?> = mapOf(
        "eInk" to eInk,
        "gray" to gray,
    )

    private fun CoupledSettings.toPreferences(): Preferences = mutablePreferencesOf(
        booleanPreferencesKey("eInk") to eInk,
        booleanPreferencesKey("gray") to gray,
    )
}
