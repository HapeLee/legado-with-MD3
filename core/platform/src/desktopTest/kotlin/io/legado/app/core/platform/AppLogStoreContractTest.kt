package io.legado.app.core.platform

import java.util.logging.Handler
import java.util.logging.LogRecord
import java.util.logging.Logger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [AppLogStore] 的行为契约（M2-4a：从 `:app` 的 `constant.AppLog` 下沉到共享层）。
 *
 * 三件事必须与迁移前**逐字一致**，否则是静默回退：
 *  1. 环形缓冲**新的在前**，上限 **101** 条。迁移前的判据是 `if (size > 100) removeLast()`
 *     且 trim 发生在 `add` 之前 ⇒ 稳态是 101 而不是 100。这里钉的是实测行为而不是直觉：
 *     改成 100 也是「合理」的，但那已经不是迁移前的行为。
 *  2. `put` **落盘**、`putNotSave` **不落盘**（迁移前 `put` 走 `LogUtils.d`、`putNotSave` 不走）。
 *     落盘断言挂在宿主同名 logger [APP_LOGGER_NAME] 上，同时就守住了「共享层与 `:app` 写进
 *     同一个日志文件」——那是 [PlatformLog] 用同名 `Logger.getLogger` 的全部理由。
 *  3. `putDebug` 受 host 同步过来的 [LogSettings.recordLog] 门控。
 *
 * 住在 `desktopTest`（而非 `commonTest`）是因为要挂 `java.util.logging.Handler` 抓取值；
 * `commonTest` 不能引用 `java.*`。两个 target 的 [PlatformLog] actual 内容相同，测一份即可。
 */
class AppLogStoreContractTest {

    private val logger = Logger.getLogger(APP_LOGGER_NAME)

    private val captured = mutableListOf<String>()

    private val handler = object : Handler() {
        override fun publish(record: LogRecord) {
            captured += record.message
        }

        override fun flush() = Unit

        override fun close() = Unit
    }

    @BeforeTest
    fun setUp() {
        AppLogStore.clear()
        LogSettings.recordLog = false
        captured.clear()
        logger.addHandler(handler)
    }

    @AfterTest
    fun tearDown() {
        logger.removeHandler(handler)
        AppLogStore.clear()
        LogSettings.recordLog = false
    }

    @Test
    fun `最新的日志排在最前`() {
        AppLogStore.put("first")
        AppLogStore.put("second")
        AppLogStore.put("third")
        assertEquals(
            listOf("third", "second", "first"),
            AppLogStore.logs.map { it.second },
        )
    }

    @Test
    fun `环形缓冲稳态上限是 101 条——与迁移前的 trim 顺序一致`() {
        repeat(150) { AppLogStore.put("msg-$it") }
        assertEquals(101, AppLogStore.logs.size)
        // 留下的必然是最新的 101 条：msg-49 .. msg-149
        assertEquals("msg-149", AppLogStore.logs.first().second)
        assertEquals("msg-49", AppLogStore.logs.last().second)
    }

    @Test
    fun `put 会写进宿主同名 logger——落盘通道不丢`() {
        AppLogStore.put("persisted-line")
        assertTrue(captured.any { it == "AppLog persisted-line" })
    }

    @Test
    fun `putNotSave 只进内存、不落盘`() {
        AppLogStore.putNotSave("memory-only")
        assertEquals(listOf("memory-only"), AppLogStore.logs.map { it.second })
        assertFalse(captured.any { it.contains("memory-only") })
    }

    @Test
    fun `putDebug 仅在 recordLog 打开时记录`() {
        LogSettings.recordLog = false
        AppLogStore.putDebug("debug-off")
        assertTrue(AppLogStore.logs.isEmpty())

        LogSettings.recordLog = true
        AppLogStore.putDebug("debug-on")
        assertEquals(listOf("debug-on"), AppLogStore.logs.map { it.second })
    }

    @Test
    fun `clear 清空内存缓冲`() {
        AppLogStore.put("a")
        AppLogStore.put("b")
        AppLogStore.clear()
        assertTrue(AppLogStore.logs.isEmpty())
    }
}
