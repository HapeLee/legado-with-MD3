package io.legado.app.ui.book.read.sheet

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Track E · E3 —— 阅读设置弹层不得读可变全局排版配置。
 *
 * 弹层的显示值必须来自 `ReadBookUiState` 的快照（`sheetConfig` / `styleConfig`），
 * 否则会出现「改完关掉再打开还是旧值」——组合期直读 `ReadBookConfig` 的可变字段时，
 * 上游变化不会触发重组；即使 seed 进 `remember` 也只在首次组合读一次。
 *
 * 例外是编译期常量与静态枚举元数据（tip 类型码、下拉项名称/取值），它们不随配置变化，
 * 见 [ALLOWED_MEMBERS]。
 */
class SheetGlobalConfigReadTest {

    @Test
    fun `设置弹层不直读可变的 ReadBookConfig 字段`() {
        val offenders = sheetSources().flatMap { file ->
            GLOBAL_ACCESS.findAll(file.readText())
                .map { it.groupValues[1] }
                .filterNot { it in ALLOWED_MEMBERS }
                .map { "${file.name} → ReadBookConfig.$it" }
        }.distinct().sorted()

        assertEquals(
            "以下弹层代码直读了可变全局配置，请改从 state.sheetConfig / state.styleConfig 取值" +
                "（若确为常量或静态元数据，加进 ALLOWED_MEMBERS 并说明理由）：\n" +
                offenders.joinToString("\n") { "  - $it" },
            emptyList<String>(),
            offenders,
        )
    }

    private fun sheetSources(): List<File> =
        sheetDirectory().listFiles { f: File -> f.isFile && f.extension == "kt" }
            .orEmpty()
            .sortedBy(File::getName)

    private fun sheetDirectory(): File {
        val relativePath = "io/legado/app/ui/book/read/sheet"
        var directory: File? = File("").absoluteFile
        while (directory != null) {
            for (prefix in listOf("src/main/java", "app/src/main/java")) {
                val candidate = File(directory, "$prefix/$relativePath")
                if (candidate.isDirectory) return candidate
            }
            directory = directory.parentFile
        }
        error("从 ${File("").absolutePath} 向上找不到 $relativePath")
    }

    private companion object {
        val GLOBAL_ACCESS = Regex("""\bReadBookConfig\.([A-Za-z][A-Za-z0-9_]*)""")

        /** 编译期常量与静态元数据——不随配置变化，读它们不会产生陈旧显示。 */
        val ALLOWED_MEMBERS = setOf(
            "tipNone",
            "tipCustom",
            "tipNames",
            "tipValues",
            "getHeaderModes",
            "getFooterModes",
        )
    }
}
