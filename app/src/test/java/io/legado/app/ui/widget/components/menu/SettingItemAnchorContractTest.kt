package io.legado.app.ui.widget.components.menu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 设置行下拉的锚点契约（第二十二轮）。
 *
 * 设置行的菜单弹层必须出现在**右侧取值区**：`SettingItem` 把 `menu()` 放进 `trailing` 的
 * `Box` 里，`TinySettingItems` 把菜单交给 `row(..., anchoredMenu)` 塞进取值 Chip 的 `Box`。
 * M3 的 `DropdownMenu` 位置跟随调用点在组合树中的位置，所以「挂在哪里」就等于「弹在哪里」；
 * 挂在整行 `Box` 末尾会贴到行左端。
 *
 * `TinySettingItems` 原先只在 Mochi 引擎下这么做（`useValueAnchor`），四套引擎表现不一致，
 * 现已统一。`SettingItem` 的 `useOptionAnchor` 是另一回事：Mochi 且 `option != null` 时
 * 取值文本渲染在标题下方，锚点只能跟着它走；这是取值位置决定的，不是遗漏。
 *
 * 静态契约只保证结构位置，**不证明弹层在场上的实际像素位置与避让行为**。
 */
class SettingItemAnchorContractTest {

    @Test
    fun tinySelectMenuAlwaysAnchorsOnValueChip() {
        val source = sourceOf(TINY)
        assertFalse("仍在按引擎切换锚点", source.contains("useValueAnchor"))
        assertFalse(
            "仍在整行 Box 末尾直接渲染菜单",
            source.contains("if (!useValueAnchor) menu()"),
        )
        // 契约：`menu()` 在取值 Chip 的 `trailingContent` 槽里展开，行点击只置位开合状态。
        val chipSlot = source.substringAfter("trailingContent = {", "")
        assertTrue("找不到取值 Chip 槽", chipSlot.isNotEmpty())
        val beforeRowClick = chipSlot.substringBefore("onClick = { showMenu = true }", "")
        assertTrue(
            "菜单未交给取值 Chip 承载",
            beforeRowClick.isNotEmpty() && beforeRowClick.contains("menu()"),
        )
        // 弹层要撑满 Chip：0 尺寸容器量不到锚点（Miuix 不弹、COUI/Mochi 只能落到窗口原点附近）。
        assertTrue("弹层未撑满取值 Chip", source.contains("Modifier.matchParentSize()"))
    }

    @Test
    fun settingItemKeepsMenuInsideTrailingSlot() {
        val source = sourceOf(SETTING_ITEM)
        assertTrue(
            "SettingItem 的菜单应挂在 trailing 槽",
            source.contains("if (!useOptionAnchor) menu()"),
        )
        // Mochi + option 时取值在标题下方，锚点跟随取值文本
        assertTrue(
            "Mochi 取值锚点特例丢失",
            source.contains("if (useOptionAnchor) menu()"),
        )
    }

    /**
     * 两处菜单已换成 `AppChoiceDropdown` 的受控弹层模式：开合由本行持有、弹层放在
     * 取值区，因此 `RoundDropdownMenu` 从设置行组件里消失。
     */
    @Test
    fun settingRowsUseControlledEmbeddedPopup() {
        listOf(TINY, SETTING_ITEM).forEach { path ->
            val source = sourceOf(path)
            assertFalse("$path 仍手画 RoundDropdownMenu", source.contains("RoundDropdownMenu("))
            assertTrue("$path 未使用受控弹层模式", source.contains("embeddedExpanded = showMenu"))
        }
    }

    /**
     * 受控模式**不能**把 `couiMenuAnchor` 挂到调用方容器上。
     *
     * 该修饰符内含 `pointerInput`：作为覆盖在触发内容之上的兄弟节点，它自己会进入命中路径，
     * 同层兄弟（取值区、触发按钮）就再也收不到触摸。定位也不靠它——COUI 的触点锚点只在
     * `open()` 记下触点后才产生坐标，受控模式没有触点，`Menu` 按 `Popup` 的父容器矩形定位
     * （`ControlledMenuAnchorContractTest` 锁定这条 Compose 语义），容器就是调用方给的位置。
     */
    @Test
    fun couiEmbeddedModeDoesNotOverlayAnchorOnContainer() {
        val source = sourceOf(APP_CHOICE)
        assertFalse(
            "受控模式把 couiMenuAnchor 挂到了容器上，会挡掉同层触发内容的点击",
            source.contains("modifier.couiMenuAnchor(couiAnchor)"),
        )
        assertTrue(
            "受控弹层仍应把 null 触点锚点传给 Menu",
            source.contains("anchor = if (embeddedExpanded == null) couiAnchor else null"),
        )
    }

    /**
     * COUI 通过 `AppMenuTriggerScope.modifier` 把 `couiMenuAnchor` 注入 trigger；纯展示
     * trigger 不透传 `this.modifier` 就没有注册锚点的节点，弹层同样退回窗口原点。
     * `MediumTonalButton` 的 KDoc 明确要求透传，`CloudTtsScreen` 的 5 处是正确样本。
     * 静态契约只保证 modifier 到位，不证明弹层在场上的实际像素位置。
     */
    @Test
    fun pureDisplayTriggersForwardScopeModifier() {
        assertTrue(
            "COUI 分支须继续把锚点注入 scope.modifier",
            sourceOf(APP_CHOICE).contains("modifier = Modifier.couiMenuAnchor(couiAnchor)"),
        )
        listOf(
            "io/legado/app/ui/main/homepage/HomepageModuleManageSheet.kt",
            "io/legado/app/ui/book/readaloud/cloudtts/CloudTtsScreen.kt",
        ).forEach { path ->
            val blocks = Regex("MediumTonalButton\\((.*?)\\)", RegexOption.DOT_MATCHES_ALL)
                .findAll(sourceOf(path))
                .map { it.groupValues[1] }
                .filter { it.contains("onClick = null") }
                .toList()
            assertTrue("$path 应有纯展示 MediumTonalButton trigger", blocks.isNotEmpty())
            blocks.forEach { block ->
                assertTrue(
                    "$path 的纯展示 trigger 必须透传 this.modifier，否则 COUI 锚点节点未注册",
                    block.contains("modifier = this.modifier"),
                )
            }
        }
    }

    /** 三个设置行包装器不再走 `CouiSettingMenu`：它的 `Menu` 拿整行 bounds，弹层从行左端展开。 */
    @Test
    fun settingWrappersNoLongerAnchorOnWholeRow() {
        listOf(LIST, COMPACT, TINY).forEach { path ->
            val source = sourceOf(path)
            assertFalse("$path 仍在用整行锚点的 CouiSettingMenu", source.contains("CouiSettingMenu("))
        }
    }

    private fun sourceOf(path: String): String {
        var directory = File("").absoluteFile
        while (directory != null) {
            val candidate = File(directory, "app/src/main/java/$path")
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile
        }
        error("从 ${File("").absolutePath} 向上找不到 $path")
    }

    private companion object {
        const val TINY = "io/legado/app/ui/widget/components/settingItem/TinySettingItems.kt"
        const val SETTING_ITEM = "io/legado/app/ui/widget/components/settingItem/SettingItem.kt"
        const val LIST = "io/legado/app/ui/widget/components/settingItem/ListSettingItem.kt"
        const val COMPACT = "io/legado/app/ui/widget/components/settingItem/CompactSettingItems.kt"
        const val APP_CHOICE = "io/legado/app/ui/widget/components/menu/AppChoiceDropdown.kt"
    }
}
