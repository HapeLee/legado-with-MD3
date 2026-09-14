package io.legado.app.host.desktop

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * M2-1 把导入编辑能力从全局 Provider 改成显式注入之后，「desktop 没有这个能力」这件事
 * 也必须在测试里被钉住。
 *
 * 为什么值得单独立一个用例：`ImportJsonEditor` 的三个方法都有**看似合理**的降级返回值
 * （`fieldsOf` 返回 null 的语义是「这个对象不可编辑」、两个 `withXxx` 返回 null 表示
 * 「本次编辑无效」），所以「desktop 不支持」很容易被顺手写成"返回 null 让它别崩"。
 * 那会把「平台缺口」伪装成「内容不可编辑」——用户看到的是「该对象不支持编辑」，
 * 而不是「桌面端还没做」。AGENTS.md 要求平台能力缺失显式建模；这个用例就是那条纪律的
 * 可执行形式：谁把它改成静默降级，谁就得先改这条断言。
 */
class DesktopImportJsonEditorTest {

    @Test
    fun desktopImportJsonEditorFailsLoudlyInsteadOfSilentlyDegrading() {
        val editor = DesktopImportJsonEditor

        val fieldsError = assertFailsWith<UnsupportedOperationException> {
            editor.fieldsOf(mapOf("key" to "value"))
        }
        val textError = assertFailsWith<UnsupportedOperationException> {
            editor.withEditedText(mapOf("key" to "value"), "key", "next")
        }
        val boolError = assertFailsWith<UnsupportedOperationException> {
            editor.withBoolean(mapOf("key" to true), "key", false)
        }

        // 异常信息要指明缺的是哪个契约，而不是一句泛化的 "not implemented"。
        listOf(fieldsError, textError, boolError).forEach { error ->
            assertTrue(
                error.message.orEmpty().contains("ImportJsonEditor"),
                "异常信息应指明缺失的契约：${error.message}"
            )
        }
    }
}
