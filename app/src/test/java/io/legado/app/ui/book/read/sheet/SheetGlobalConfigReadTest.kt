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
 * R4.6 之前这条断言留了白名单，装的全是静态选项表（tip 取值表、页眉页脚模式枚举）——
 * 它们不随配置变化，读了不会陈旧，但挂在 `ReadBookConfig` 上就逼着护栏开口子。
 * 现在它们已搬到唯一消费方旁边，**本断言不再有例外**。
 */
class SheetGlobalConfigReadTest {

    @Test
    fun `设置弹层不直读可变的 ReadBookConfig 字段`() {
        val offenders = sheetSources().flatMap { file ->
            val text = file.readText()
            val qualified = GLOBAL_ACCESS.findAll(text)
                .map { "${file.name} → ReadBookConfig.${it.groupValues[1]}" }
            // 单独抓 import：`import ...ReadBookConfig.tipNames` 之后成员可以裸写，
            // 上面那条按 `ReadBookConfig.` 找的正则一个都看不见。
            val imported = CONFIG_IMPORT.findAll(text)
                .map { "${file.name} → import ${it.groupValues[0].removePrefix("import ")}" }
            (qualified + imported).toList()
        }.distinct().sorted()

        assertEquals(
            "以下弹层代码引用了全局排版配置，请改从 state.sheetConfig / state.styleConfig 取值：\n" +
                offenders.joinToString("\n") { "  - $it" } + "\n" +
                "静态选项表（下拉项的取值/显示名、模式枚举）不要往 ReadBookConfig 上挂——" +
                "R4.6 已经把它们搬到唯一消费方旁边（HeaderFooterPage.kt 底部的 tipTypeValues/" +
                "headerModes/footerModes），本断言不再留白名单。",
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
        val CONFIG_IMPORT = Regex(
            """^import io\.legado\.app\.help\.config\.ReadBookConfig\b.*$""",
            RegexOption.MULTILINE,
        )
    }
}
