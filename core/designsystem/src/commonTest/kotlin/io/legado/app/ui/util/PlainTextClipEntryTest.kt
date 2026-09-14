package io.legado.app.ui.util

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 探针异常：让「工厂被调用」这条断言不需要真的造出一个 `ClipEntry`（共享层造不出）。 */
private class ProbeStop : RuntimeException()

/**
 * 锁住 [PlainTextClipEntryProvider] 的两条语义（M1-3o）。
 *
 * 为什么只测这些：[ClipEntry] 是 CMP 的 `expect class`，**共享层没有构造器**——这正是
 * [PlainTextClipEntryFactory] 这个 seam 存在的原因，也决定了成功路径（真的写出一个剪贴板
 * 条目）在 `commonTest` 里无法断言。能锁、且值得锁的是两件事：
 *
 * 1. **未注入时抛异常，而不是静默 no-op**（判据同 `ClipboardProvider`）；
 * 2. **[plainTextClipEntry] 把 `label` / `text` 原样透传**给宿主工厂。
 *
 * 用 [ProbeStop] 当哨兵：工厂在记录完参数后立刻抛出，于是断言不必持有 `ClipEntry` 实例。
 */
class PlainTextClipEntryTest {

    @AfterTest
    fun tearDown() {
        PlainTextClipEntryProvider.uninstall()
    }

    @Test
    fun missingFactoryFailsLoudlyInsteadOfDoingNothing() {
        assertFalse(PlainTextClipEntryProvider.isInstalled)

        val error = assertFailsWith<IllegalStateException> {
            plainTextClipEntry("url", "https://example.com")
        }
        assertTrue(
            error.message.orEmpty().contains("PlainTextClipEntryProvider"),
            "异常信息应指出缺的是哪个注入点，实际：" + error.message
        )
    }

    @Test
    fun installAndUninstallToggleIsInstalled() {
        assertFalse(PlainTextClipEntryProvider.isInstalled)

        PlainTextClipEntryProvider.install(neverCalledFactory())
        assertTrue(PlainTextClipEntryProvider.isInstalled)

        PlainTextClipEntryProvider.uninstall()
        assertFalse(PlainTextClipEntryProvider.isInstalled)
    }

    @Test
    fun helperForwardsLabelAndTextVerbatim() {
        var seen: Pair<String, String>? = null
        PlainTextClipEntryProvider.install { label, text ->
            seen = label to text
            throw ProbeStop()
        }

        assertFailsWith<ProbeStop> {
            plainTextClipEntry("httpTTS", """{"a":1}""")
        }
        assertEquals("httpTTS" to """{"a":1}""", seen)
    }

    /** 一个永不返回的工厂：本测试类不需要真的 `ClipEntry`。 */
    private fun neverCalledFactory(): PlainTextClipEntryFactory =
        PlainTextClipEntryFactory { _, _ -> throw ProbeStop() }
}
