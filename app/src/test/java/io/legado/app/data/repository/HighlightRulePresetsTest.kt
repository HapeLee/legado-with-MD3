package io.legado.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

class HighlightRulePresetsTest {

    @Test
    fun presetIdsAreUnique() {
        val ids = HighlightRulePresets.rules.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun everyPresetPatternCompiles() {
        HighlightRulePresets.rules.forEach { rule ->
            try {
                Pattern.compile(rule.pattern)
            } catch (e: PatternSyntaxException) {
                throw AssertionError("预设「${rule.name}」的正则无法编译：${e.description}")
            }
        }
    }

    /** 示例文本进编辑弹层就是预览样本，自己都匹配不上说明正则或示例写错了。 */
    @Test
    fun everyPresetMatchesItsOwnSample() {
        HighlightRulePresets.rules.forEach { rule ->
            assertTrue(
                "预设「${rule.name}」匹配不到自己的示例：${rule.sampleText}",
                Pattern.compile(rule.pattern).matcher(rule.sampleText).find(),
            )
        }
    }

    @Test
    fun presetsShareOneBackgroundTint() {
        val colors = HighlightRulePresets.rules.map { it.bgColor }
        assertTrue(colors.all { it != null })
        assertEquals(1, colors.distinct().size)
    }

    /**
     * 「重点强调」的示例里带一段裸 `[^\n*_]{1,40}`，会匹配任意一行正文、把整页涂成高亮。
     * 这里钉死：普通叙述句不命中，成对标记仍然命中。
     */
    @Test
    fun emphasisPresetDoesNotPaintOrdinaryProse() {
        val pattern = Pattern.compile(
            HighlightRulePresets.rules.single { it.id == "emphasis_default" }.pattern
        )
        listOf(
            "他停了一下，看了看窗外。",
            "夜色很深，巷口的路灯忽明忽暗。",
        ).forEach { line ->
            assertFalse(
                "「重点强调」不该匹配普通正文：$line",
                pattern.matcher(line).find(),
            )
        }
        assertTrue(pattern.matcher("**这是重点内容**，需要特别注意。").find())
    }
}
