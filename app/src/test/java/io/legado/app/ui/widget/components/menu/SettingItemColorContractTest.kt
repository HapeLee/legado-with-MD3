package io.legado.app.ui.widget.components.menu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 第四十六轮：设置项组件族配色统一走 `LegadoTheme.colorScheme`。
 *
 * `MaterialTheme.colorScheme` 是 M3 默认取色，不随项目的自定义主题 / 四套引擎解析结果变化；
 * `LegadoTheme.colorScheme`（`LegadoColorScheme`）才是项目主题色，
 * 且已提供一一对应的 `primary` / `onSurface` / `onSurfaceVariant` / `surfaceContainer*`
 * 系列色，替换不需要引入新色值。
 *
 * 覆盖范围：`SettingItem` / `CompactSettingItems` / `ClickableSettingItem` 三个设置项组件，
 * 共 7 处混用。静态断言不证明四引擎下的实际观感，需真机复核设置页与编辑页。
 */
class SettingItemColorContractTest {

    /** 组件族内不再混用 M3 默认取色。 */
    @Test
    fun settingItemComponentsDoNotMixMaterialThemeColors() {
        TARGETS.forEach { path ->
            val source = sourceOf(path)
            assertFalse(
                "$path 仍混用 MaterialTheme.colorScheme（不跟随项目主题 / 引擎）",
                source.contains("MaterialTheme.colorScheme."),
            )
            assertTrue(
                "$path 未使用 LegadoTheme.colorScheme",
                source.contains("LegadoTheme.colorScheme."),
            )
        }
    }

    /** 替换必须落到同名系列色，不能顺手改成别的色值。 */
    @Test
    fun eachReplacementUsesTheMatchingSeriesColor() {
        val setting = sourceOf(SETTING_ITEM)
        assertTrue("leading 图标色未替换", setting.contains("LegadoTheme.colorScheme.onSurfaceVariant"))
        assertTrue(
            "说明文字色未替换",
            setting.contains("LegadoTheme.colorScheme.onSurface.copy(alpha = 0.7f)"),
        )
        assertTrue("取值文字色未替换", setting.contains("LegadoTheme.colorScheme.primary"))
        assertTrue("卡片底色未替换", setting.contains("LegadoTheme.colorScheme.surfaceContainerLow"))

        val compact = sourceOf(COMPACT)
        assertTrue("取值 Chip 底色未替换", compact.contains("LegadoTheme.colorScheme.surfaceContainer"))
        assertTrue("取值 Chip 文字色未替换", compact.contains("LegadoTheme.colorScheme.onSurface"))
        assertTrue("紧凑滑块图标色未替换", compact.contains("LegadoTheme.colorScheme.onSurfaceVariant"))

        val clickable = sourceOf(CLICKABLE)
        assertTrue("可点击项图标色未替换", clickable.contains("LegadoTheme.colorScheme.onSurfaceVariant"))
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
        const val SETTING_ITEM = "io/legado/app/ui/widget/components/settingItem/SettingItem.kt"
        const val COMPACT = "io/legado/app/ui/widget/components/settingItem/CompactSettingItems.kt"
        const val CLICKABLE = "io/legado/app/ui/widget/components/settingItem/ClickableSettingItem.kt"
        val TARGETS = listOf(SETTING_ITEM, COMPACT, CLICKABLE)
    }
}
