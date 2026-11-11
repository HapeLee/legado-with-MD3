package io.legado.app.ui.widget.components.menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * `CloudTtsScreen` 编辑面板的纯单选菜单迁移契约（第十七轮）。
 *
 * 旧实现逐处同构：`MenuButton(当前值) { xxMenu = true }` + `RoundDropdownMenu`，
 * 触发是自带 `onClick` 的 `MediumTonalButton`，无法直接塞进 [AppChoiceDropdown] 的
 * trigger 槽——那会形成内外两层可点节点。
 *
 * 本轮定下的 trigger 形态：`MediumTonalButton(onClick = null)` 纯展示 + `compact = true`，
 * 由入口包装唯一可点节点，四套引擎各自渲染原生单选弹层，trigger 外观与迁移前一致。
 * 与 `SourceEditOptionCard(onClick = null)` / `GlassCard(onClick = null)` 同一约定。
 *
 * 区域菜单**刻意保留**：它混有「自定义区域」这条命令项并紧接着一个文本输入框，
 * 不是纯单选，迁移会改变交互形态，需要单独建基线。
 *
 * 静态契约只保证结构与 ID 一致性，**不证明弹层定位、真机外观或 TalkBack 行为**。
 */
class CloudTtsChoiceMenuContractTest {

    @Test
    fun pureSelectorsUseCompactAppChoiceDropdown() {
        val source = sourceOf(SCREEN)
        assertEquals(
            "纯单选菜单数量（服务商 / 模型 / 风格 / 角色 / 格式）",
            5,
            Regex("AppChoiceDropdown\\(").findAll(source).count(),
        )
        assertEquals(5, Regex("selectedId =").findAll(source).count())
        assertEquals(5, Regex("onSelect =").findAll(source).count())
        assertEquals(5, Regex("compact = true").findAll(source).count())
    }

    @Test
    fun triggersAreDisplayOnlyAndCarryAnchorModifier() {
        val source = sourceOf(SCREEN)
        assertFalse(
            "纯展示 trigger 不应再引入平行组件，沿用 MediumTonalButton(onClick = null)",
            source.contains("MediumTonalButtonDisplay"),
        )
        assertEquals(
            "纯展示 trigger 数量",
            5,
            Regex("onClick = null").findAll(source).count(),
        )
        assertEquals(
            "每个 trigger 都必须透传入口给的锚点 modifier",
            5,
            Regex("modifier = this\\.modifier\\.fillMaxWidth\\(\\)").findAll(source).count(),
        )
    }

    @Test
    fun editorNoLongerOwnsPerMenuExpandedState() {
        val source = sourceOf(SCREEN)
        listOf("providerMenu", "modelMenu", "styleMenu", "roleMenu", "formatMenu").forEach {
            assertFalse("$it 开合状态应交给 AppChoiceDropdown 内部持有", source.contains(it))
        }
    }

    /** 区域菜单是「选项 + 自定义区域命令 + 后续输入框」的混合菜单，本轮刻意不动。 */
    @Test
    fun mixedRegionMenuKeepsRoundDropdownMenu() {
        val source = sourceOf(SCREEN)
        assertEquals(
            "剩余手画 RoundDropdownMenu 数量（仅区域混合菜单）",
            1,
            Regex("RoundDropdownMenu\\(").findAll(source).count(),
        )
        assertTrue("区域菜单丢失", source.contains("var regionMenu by remember"))
        assertTrue("区域自定义命令丢失", source.contains("R.string.cloud_tts_custom_region_action"))
    }

    private fun sourceOf(path: String): String {
        var directory: File? = File("").absoluteFile
        while (directory != null) {
            val candidate = File(directory, "app/src/main/java/$path")
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile
        }
        error("从 ${File("").absolutePath} 向上找不到 $path")
    }

    private companion object {
        const val SCREEN = "io/legado/app/ui/book/readaloud/cloudtts/CloudTtsScreen.kt"
    }
}
