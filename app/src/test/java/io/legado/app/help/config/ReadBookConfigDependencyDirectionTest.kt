package io.legado.app.help.config

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R4.3 —— `ReadBookConfig` 与 `ReadStyleGateway` 之间的依赖必须单向。
 *
 * 原来是个环：`ReadBookConfig`（排版数据的全局存储）持有 `readStyleGateway` 并在
 * `clearMissingTextFont()` 里反过来命令它改样式、落盘；而 gateway 的实现
 * `ReadBookStyleConfigRepository` 又大量读 `ReadBookConfig`。存储反向命令拥有它的领域层，
 * 谁是权威就说不清了，也让两边都没法单独测。
 *
 * 现在方向是单向的：消费方（`ChapterProvider`）直接找 gateway，`ReadBookConfig` 只向下
 * 依赖 `ReadStyleRepository`（文件读写）。
 *
 * 注意 `readSettingsGateway` 不在此列——那是**只读**上游设置，不构成环。
 */
class ReadBookConfigDependencyDirectionTest {

    @Test
    fun `ReadBookConfig 不反向依赖 ReadStyleGateway`() {
        val source = stripComments(
            mainSourceFile("io/legado/app/help/config/ReadBookConfig.kt").readText()
        )
        val violations = buildList {
            if (Regex("""\bReadStyleGateway\b""").containsMatchIn(source)) {
                add("引用了 ReadStyleGateway")
            }
            if (Regex("""\bReadStyleMutation\b""").containsMatchIn(source)) {
                add("引用了 ReadStyleMutation（在向领域层下达样式变更）")
            }
            if (Regex("""\battachGateway\b""").containsMatchIn(source)) {
                add("又出现了 attachGateway 这类事后回填的反向引用")
            }
        }
        assertTrue(
            "ReadBookConfig 又反向依赖排版 gateway 了：${violations.joinToString()}。\n" +
                "排版存储不该命令拥有它的领域层——需要改样式的调用方请自己拿 " +
                "ReadStyleGateway（ChapterProvider.getTypeface 是现成例子）。",
            violations.isEmpty(),
        )
    }

    private companion object {
        fun stripComments(text: String): String = text
            .replace(Regex("""/\*[\s\S]*?\*/"""), "")
            .replace(Regex("""//[^\n]*"""), "")

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
