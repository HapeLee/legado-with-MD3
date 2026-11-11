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
        // 弹层要撑满 Chip：0 尺寸容器量不到锚点（Miuix 不弹、COUI/Mochi 退回窗口原点）。
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
     * COUI 的 `Menu` 只在 `couiMenuAnchor` 注册过节点时才有锚点矩形，否则退回窗口
     * (0,0)——真机表现就是弹层跑到屏幕左上角。受控模式没有 trigger 节点，必须把锚点
     * 挂到调用方给的容器上。
     */
    @Test
    fun couiEmbeddedModeRegistersAnchorOnContainer() {
        val source = sourceOf(APP_CHOICE)
        assertTrue(
            "受控模式未把 couiMenuAnchor 挂到容器上",
            source.contains("modifier.couiMenuAnchor(couiAnchor)"),
        )
        assertTrue("受控弹层未使用该容器", source.contains("Box(containerModifier)"))
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
