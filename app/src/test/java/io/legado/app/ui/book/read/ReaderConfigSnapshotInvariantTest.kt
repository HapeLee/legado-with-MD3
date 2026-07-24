package io.legado.app.ui.book.read

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

/**
 * Track E · E0 —— 排版快照的完备性不变式。
 *
 * 排版底座 `ReadBookConfig.Config` 是可变全局、无 flow，所以 `ReadBookViewModel` 只能在每次
 * 写入后手工重建 [ReadBookStyleConfig] / [ReadSheetConfigUiState] 两份快照。失败模式是
 * 「给 UiState 加了字段，却忘了在 build 函数里赋值」⇒ 该项永远是默认值，弹层显示旧值。
 *
 * 两个 build 函数是 `ReadBookViewModel` 的 private 成员、且 VM 需要大量 Koin 依赖才能构造，
 * 因此这里做源码扫描而非实例断言。Track E · E2 把它们改成 `ReadStyleSnapshot` 上的纯函数
 * 之后，本测试应改写为真正的实例断言（构造快照 → 派生 → 逐字段比对）。
 */
class ReaderConfigSnapshotInvariantTest {

    @Test
    fun `buildSheetConfig 覆盖 ReadSheetConfigUiState 的每个字段`() {
        assertAllFieldsAssigned(
            stateType = ReadSheetConfigUiState::class,
            functionSignature = "private fun buildSheetConfig(): ReadSheetConfigUiState",
        )
    }

    @Test
    fun `buildStyleConfig 覆盖 ReadBookStyleConfig 的每个字段`() {
        assertAllFieldsAssigned(
            stateType = ReadBookStyleConfig::class,
            functionSignature = "private fun buildStyleConfig(): ReadBookStyleConfig",
        )
    }

    private fun assertAllFieldsAssigned(stateType: KClass<*>, functionSignature: String) {
        val body = viewModelSource().functionBodyAfter(functionSignature)
        val assigned = NAMED_ARGUMENT.findAll(body).map { it.groupValues[1] }.toSet()
        val missing = stateType.primaryConstructor
            ?.parameters
            ?.mapNotNull { it.name }
            ?.filterNot { it in assigned }
            .orEmpty()

        assertTrue(
            "${stateType.simpleName} 的以下字段没有在 $functionSignature 里被赋值，" +
                "它们会永远保持默认值：\n" +
                missing.joinToString("\n") { "  - $it" },
            missing.isEmpty(),
        )
    }

    /** 取签名之后第一个 `(` 起、括号配平为止的文本。 */
    private fun String.functionBodyAfter(signature: String): String {
        val signatureIndex = indexOf(signature)
        require(signatureIndex >= 0) {
            "在 ReadBookViewModel.kt 里找不到 `$signature`——函数被改名或改签名了，请同步本测试"
        }
        val open = indexOf('(', signatureIndex + signature.length)
        require(open >= 0) { "`$signature` 之后找不到参数列表起始括号" }
        var depth = 0
        for (index in open until length) {
            when (this[index]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return substring(open + 1, index)
                }
            }
        }
        error("`$signature` 的括号未配平")
    }

    private fun viewModelSource(): String =
        mainSourceFile("io/legado/app/ui/book/read/ReadBookViewModel.kt").readText()

    private companion object {
        /** 只认行首缩进后的具名实参，避免把嵌套调用里的 `a = b` 也算进来。 */
        val NAMED_ARGUMENT = Regex("""^\s*([A-Za-z][A-Za-z0-9_]*)\s*=[^=]""", RegexOption.MULTILINE)

        fun mainSourceFile(relativePath: String): File {
            var directory: File? = File("").absoluteFile
            while (directory != null) {
                for (prefix in listOf("src/main/java", "app/src/main/java")) {
                    val candidate = File(directory, "$prefix/$relativePath")
                    if (candidate.isFile) return candidate
                }
                directory = directory.parentFile
            }
            error("从 ${File("").absolutePath} 向上找不到 $relativePath")
        }
    }
}
