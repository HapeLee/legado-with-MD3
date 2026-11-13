package io.legado.app.ui.widget.components.menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 第三十九轮：`SelectionItemCard` 的 `dropdownContent` 公共插槽 + 收编后的结构契约。
 *
 * 这是三入口迁移里唯一一处**组件 API**：`SelectionItemCard` / `SelectionItemCardContent` /
 * `ReorderableSelectionItem` 三个对外函数都暴露同一个 `ColumnScope.(onDismiss) -> Unit`
 * 插槽，6 个业务调用点各自传菜单项并自己决定是否 `dismiss()`。
 *
 * 盘点结论：插槽签名与 `AppAnchoredPanel.content` 同构，因此只把卡片内部的弹层分派收编到
 * `externalTrigger` 兼容路径，公共 API 与 6 个调用点零改动；菜单项的 dismiss 仍由调用点
 * 持有，入口的 `onExpandedChange(false)` 等价于旧 `showMenu = false`。
 *
 * 静态断言不证明四引擎弹层落点（玻璃锚点由 `MochiOverlaysAndBarsTest` 覆盖）与 TalkBack。
 */
class SelectionItemCardMenuContractTest {

    /** 手画容器归零；单入口、页面自持开合、公共插槽直接复用。 */
    @Test
    fun cardUsesSingleAnchoredPanelWithoutChangingPublicApi() {
        val source = codeOf(CARD)
        assertEquals(
            "卡片仍手画 RoundDropdownMenu",
            0,
            Regex("RoundDropdownMenu\\(").findAll(source).count(),
        )
        assertEquals(
            "应只有一个统一入口弹层",
            1,
            Regex("AppAnchoredPanel\\(").findAll(source).count(),
        )
        assertTrue("开合未由 showMenu 驱动", source.contains("expanded = showMenu"))
        assertTrue("开合回调未收口", source.contains("onExpandedChange = { showMenu = it }"))
        assertTrue("未保留既有按钮外观", source.contains("externalTrigger = true"))
        assertTrue("公共插槽未直接复用", source.contains("content = dropdownContent"))
        assertTrue("菜单项仍应只在传插槽时出现", source.contains("if (dropdownContent != null) {"))
    }

    /** 三个对外函数共用一个插槽签名，两处包装函数只做转发。 */
    @Test
    fun dropdownContentIsOneSlotOnThreeOverloads() {
        val source = codeOf(CARD)
        assertEquals(
            "三个对外组件都应保留同一插槽签名",
            3,
            Regex(
                "dropdownContent: @Composable \\(ColumnScope\\.\\(onDismiss: \\(\\) -> Unit\\) -> Unit\\)\\? = null"
            ).findAll(source).count(),
        )
        assertEquals(
            "两处包装函数应只转发插槽",
            2,
            Regex("dropdownContent = dropdownContent,").findAll(source).count(),
        )
    }

    /** 6 个业务调用点仍传 `dropdownContent`，且每项都使用传入的关闭回调。 */
    @Test
    fun sixCallersKeepPassingDropdownContentAndDismissCallback() {
        CALLERS.forEach { path ->
            val source = codeOf(path)
            assertTrue("$path 未传 dropdownContent", source.contains("dropdownContent = {"))
            assertTrue(
                "$path 未使用传入的 dismiss 回调",
                Regex("dismiss\\(\\)|onDismiss\\(\\)").containsMatchIn(source),
            )
        }
    }

    /** ⋮ 按钮的外观与无障碍名称由卡片自己给，点击只交给入口展开。 */
    @Test
    fun moreButtonKeepsItsOwnAppearanceAndName() {
        val source = codeOf(CARD)
        assertTrue("⋮ 按钮被换成别的控件", source.contains("SmallPlainButton("))
        assertTrue("按钮点击未交给入口", source.contains("onClick = { open() }"))
        assertTrue("⋮ 图标丢失", source.contains("icon = AppIcons.MoreVert"))
        assertTrue(
            "无障碍名称丢失",
            source.contains("contentDescription = moreContentDescription ?: defaultMoreDescription"),
        )
        assertTrue(
            "默认无障碍名称丢失",
            source.contains("defaultMoreDescription = stringResource(R.string.more_menu)"),
        )
    }

    private fun codeOf(path: String): String = sourceOf(path)
        .lineSequence()
        .filterNot { line ->
            val trimmed = line.trimStart()
            trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")
        }
        .joinToString("\n")

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
        const val CARD = "io/legado/app/ui/widget/components/card/SelectionItemCard.kt"
        val CALLERS = listOf(
            "io/legado/app/ui/widget/components/changeSource/ChangeSourceSheet.kt",
            "io/legado/app/ui/rss/subscription/RuleSubScreen.kt",
            "io/legado/app/ui/rss/favorites/RssFavoritesScreen.kt",
            "io/legado/app/ui/replace/ReplaceRuleScreen.kt",
            "io/legado/app/ui/main/bookshelf/autoGroup/AiAutoGroupSheet.kt",
            "io/legado/app/ui/book/read/sheet/ChangeChapterSourceSheet.kt",
        )
    }
}
