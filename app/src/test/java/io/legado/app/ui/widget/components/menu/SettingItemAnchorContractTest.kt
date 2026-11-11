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
        assertTrue(
            "菜单未交给取值 Chip 承载",
            source.contains("row({ showMenu = true }, menu)"),
        )
        assertFalse(
            "仍在整行 Box 末尾直接渲染菜单",
            source.contains("if (!useValueAnchor) menu()"),
        )
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
        const val TINY = "io/legado/app/ui/widget/components/settingItem/TinySettingItems.kt"
        const val SETTING_ITEM = "io/legado/app/ui/widget/components/settingItem/SettingItem.kt"
    }
}
