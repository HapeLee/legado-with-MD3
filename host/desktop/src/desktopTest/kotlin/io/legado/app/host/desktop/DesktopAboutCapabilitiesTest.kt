package io.legado.app.host.desktop

import io.legado.app.feature.about.CrashLogEntry
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * M5-1c 把「关于」页的平台能力抽成契约之后，「desktop 没有这个能力」这件事必须在测试里被钉住。
 *
 * 与 `DesktopImportJsonEditorTest`（M2-1）同形、同因：`AboutDiagnostics` 的几个方法都有
 * **看似合理**的降级返回值（`listCrashLogs` 返回空列表 = 「没有崩溃日志」、
 * `readCrashLog` 返回 failure、`createHeapDump` 返回 `false` = 「没找到堆转储」），
 * `BundledTextReader.readText` 的 `null` 更是本契约**正常**的失败语义（文件不存在）。
 * 所以「desktop 不支持」极容易被顺手写成「返回空/返回 false 让它别崩」——那会把平台缺口
 * 伪装成内容问题。AGENTS.md 要求平台能力缺失显式建模；这个用例就是那条纪律的可执行形式。
 *
 * ⚠️ 本片的六个方法里五个是 `suspend`，所以不能直接用 `kotlin.test.assertFailsWith`
 * （它的 block 是**非挂起**的 `() -> Unit`）——用下面的 [unsupportedError] 承接。
 */
class DesktopAboutCapabilitiesTest {

    @Test
    fun `desktop 的关于页平台能力显式不可用而不是静默降级`() = runBlocking {
        val errors = listOf(
            unsupportedError { DesktopAppUpdateChecker.check() },
            unsupportedError { DesktopAboutDiagnostics.listCrashLogs() },
            unsupportedError { DesktopAboutDiagnostics.readCrashLog(CrashLogEntry("id", "name")) },
            unsupportedError { DesktopAboutDiagnostics.clearCrashLogs() },
            unsupportedError { DesktopAboutDiagnostics.saveLogs() },
            unsupportedError { DesktopAboutDiagnostics.createHeapDump() },
            unsupportedError { DesktopBundledTextReader.readText("privacyPolicy.md") },
        )

        // 异常信息要指明缺的是哪一族契约，而不是一句泛化的 "not implemented"。
        errors.forEach { error ->
            assertTrue(
                error.message.orEmpty().contains("关于"),
                "异常信息应指明缺失的契约：${error.message}"
            )
        }
    }

    private suspend fun unsupportedError(block: suspend () -> Unit): Throwable =
        try {
            block()
            fail("desktop 侧应当显式抛 UnsupportedOperationException，而不是降级返回")
        } catch (e: UnsupportedOperationException) {
            e
        }
}
