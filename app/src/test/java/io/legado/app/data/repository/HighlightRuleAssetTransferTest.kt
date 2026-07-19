package io.legado.app.data.repository

import io.legado.app.data.entities.HighlightRule
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightRuleAssetTransferTest {

    @Test
    fun `导出时复制高亮背景和字体并改写为包内文件名`() {
        val root = Files.createTempDirectory("highlight-rule-export").toFile()
        val sourceDir = root.resolve("source").apply { mkdirs() }
        val exportDir = root.resolve("export").apply { mkdirs() }
        val background = sourceDir.resolve("bubble.9.png").apply { writeText("background") }
        val font = sourceDir.resolve("dialog.ttf").apply { writeText("font") }
        val rule = HighlightRule(
            id = "dialog",
            bgImage = background.absolutePath,
            fontPath = font.absolutePath,
        )

        val result = HighlightRuleAssetTransfer.prepareExport(
            rules = listOf(rule),
            exportDir = exportDir,
            copyAsset = { source, target ->
                source.toFile().copyTo(target, overwrite = true)
                true
            },
        )

        val exportedRule = result.rules.single()
        assertEquals("highlight_rule_bg_0_bubble.9.png", exportedRule.bgImage)
        assertEquals("highlight_rule_font_0_dialog.ttf", exportedRule.fontPath)
        assertEquals(setOf(exportedRule.bgImage, exportedRule.fontPath), result.files.map { it.name }.toSet())
        assertTrue(result.files.all { it.exists() })
    }

    @Test
    fun `导入时恢复包内资源并清空不可读取的旧设备路径`() {
        val root = Files.createTempDirectory("highlight-rule-import").toFile()
        val importDir = root.resolve("import").apply { mkdirs() }
        val backgroundDir = root.resolve("background").apply { mkdirs() }
        val fontDir = root.resolve("font").apply { mkdirs() }
        importDir.resolve("highlight_rule_bg_0_bubble.png").writeText("background")
        importDir.resolve("highlight_rule_font_0_dialog.ttf").writeText("font")
        val bundledRule = HighlightRule(
            id = "bundled",
            bgImage = "highlight_rule_bg_0_bubble.png",
            fontPath = "highlight_rule_font_0_dialog.ttf",
        )
        val staleRule = HighlightRule(
            id = "stale",
            bgImage = "/data/user/0/other.app/files/missing.png",
            fontPath = "content://other.app/fonts/missing.ttf",
        )

        val restored = HighlightRuleAssetTransfer.restoreImport(
            rules = listOf(bundledRule, staleRule),
            importDir = importDir,
            backgroundDir = backgroundDir,
            fontDir = fontDir,
            isReadableBackgroundReference = { false },
        )

        assertEquals(backgroundDir.resolve("highlight_rule_bg_0_bubble.png").absolutePath, restored[0].bgImage)
        assertEquals(fontDir.resolve("highlight_rule_font_0_dialog.ttf").absolutePath, restored[0].fontPath)
        assertNull(restored[1].bgImage)
        assertNull(restored[1].fontPath)
    }

    @Test
    fun `加载旧规则时清除不可读取资源但保留有效引用`() {
        val validRule = HighlightRule(
            id = "valid",
            bgImage = "assets://bg/dialog.png",
            fontPath = "/fonts/dialog.ttf",
        )
        val staleRule = HighlightRule(
            id = "stale",
            bgImage = "/data/user/0/other.app/files/missing.png",
            fontPath = "content://other.app/fonts/missing.ttf",
        )

        val cleaned = HighlightRuleAssetTransfer.clearUnreadableReferences(
            rules = listOf(validRule, staleRule),
            isReadableBackgroundReference = { it.startsWith("assets://") },
            isReadableFontReference = { it == "/fonts/dialog.ttf" },
        )

        assertEquals(validRule, cleaned[0])
        assertNull(cleaned[1].bgImage)
        assertNull(cleaned[1].fontPath)
    }

    private fun String.toFile() = java.io.File(this)
}
