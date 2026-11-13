package io.legado.app.ui.widget.components.menu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Mochi fork `0.2.1-menu3` 接入后的静态契约：三入口的 Mochi 分支必须保持
 * 「入口自己渲染唯一可点节点 + 只把锚点交给库弹层」，不得回到把 trigger 塞进库按钮的写法
 * —— 那是「按下即开 + 大圆按钮外壳」的来源，见 `docs/dev/mochi-menu-extension-spec.md` §11。
 *
 * 「按下不展开 / 松手才展开」无法用 Compose 的 `performTouchInput` 直接表达（注入作用域要求
 * 手势结束时所有指针都已抬起），所以这里锁结构；行为由 `TopBarActionMenuContractTest` 的
 * Mochi 用例（点击后条目出现 + 稳定 ID 回调）与真机截图验收共同保证。
 */
class MochiAnchorMenuContractTest {
    @Test
    fun mochiBranchesUseAnchorOnlyMenus() {
        BRANCHES.forEach { (path, anchorCall) ->
            val source = sourceOf(path)
            LIBRARY_BUTTONS.forEach { button ->
                assertFalse(
                    "$path 又把 trigger 交给了库按钮 $button；会回到按下即开 + 库按钮外壳",
                    source.contains(button),
                )
            }
            assertTrue("$path 缺少锚点式弹层 $anchorCall", source.contains(anchorCall))
            assertTrue("$path 缺少入口可点节点", source.contains("clickable"))
        }
    }

    @Test
    fun mochiIconAnchorGoesThroughSharedTopBarShell() {
        val entry = sourceOf("io/legado/app/ui/widget/components/menu/AppActionMenu.kt")
        assertTrue(
            "Mochi 的 Icon 锚点应走统一顶栏按钮壳，否则顶栏 ⋯ 会退化成裸图标、与同排搜索按钮不一致",
            entry.contains("TopBarActionSurface("),
        )
        assertTrue(
            "Icon / Row 两种锚点应分别渲染：Row 仍由 trigger 自绘",
            entry.contains("AppMenuAnchor.Icon"),
        )
        assertTrue(
            "壳里没有落到 MochiBarIconButton",
            sourceOf("io/legado/app/ui/widget/components/topbar/TopBarButton.kt")
                .contains("MochiBarIconButton("),
        )
    }

    @Test
    fun mochiDependencyUpgradeIsNotBlockedByHardcodedFilename() {
        val compat = projectFileOf("app/mochi-compat.gradle")
        assertFalse(
            "compat transform 不应再硬编码 AAR 文件名，否则升级 mochi 会静默跳过字节码改写",
            compat.contains("mochi-compose-0.2.0.aar"),
        )
        assertTrue(
            "compat transform 应按 mochi-compose-* 前缀匹配",
            compat.contains("startsWith('mochi-compose-')"),
        )
        val settings = projectFileOf("settings.gradle")
        assertTrue("fork 依赖必须使用项目专用 Maven 仓库", settings.contains("build/ui-forks/maven"))
        assertTrue(
            "Mochi 必须独占路由到固定 fork，避免回落上游或命中开发机旧包",
            settings.contains("exclusiveContent") &&
                settings.contains("includeModule('io.github.feice11', 'mochi-compose')"),
        )
        assertFalse("固定 fork 不应再读取开发机 Maven Local", settings.contains("mavenLocal {"))
    }

    private fun sourceOf(path: String) = projectFileOf("app/src/main/java/$path")

    private fun projectFileOf(relative: String): String {
        var directory: File? = File("").absoluteFile
        while (directory != null) {
            val candidate = File(directory, relative)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile
        }
        error("从 ${File("").absolutePath} 向上找不到 $relative")
    }

    private companion object {
        val BRANCHES = listOf(
            "io/legado/app/ui/widget/components/menu/AppChoiceDropdown.kt" to
                "MochiDropdownMenu(",
            "io/legado/app/ui/widget/components/menu/AppActionMenu.kt" to
                "MochiDropdownMenu(",
            "io/legado/app/ui/widget/components/menu/AppAnchoredPanel.kt" to
                "AppMochiPopover(",
        )

        val LIBRARY_BUTTONS = listOf(
            "MochiDropdown(",
            "MochiIconDropdown(",
            "MochiPopoverButton(",
        )
    }
}
