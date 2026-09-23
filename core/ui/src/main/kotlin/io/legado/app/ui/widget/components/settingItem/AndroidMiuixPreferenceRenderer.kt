package io.legado.app.ui.widget.components.settingItem

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference

/**
 * [MiuixPreferenceRenderer] 的 Android 实现。
 *
 * 留在 `:core:ui` 而不是 `:app`：这条分支要用的 `top.yukonga.miuix.kmp.preference`
 * 制品只有 `-android` 变体，而 `:core:ui` 正是本仓承载「Android 专用 Compose UI」的地方
 * （同 M1-3r 把 `LiquidGlassEffects` 的 Android 实现留在这里）。对外只暴露本文件的
 * [androidMiuixPreferenceRenderer] 工厂，实现类私有。
 *
 * 参数与迁移前 `SwitchSettingItem` 里那段 `SwitchPreference(...)` 一致：`modifier` 传
 * `Modifier`（原调用方没有额外修饰）。
 */
fun androidMiuixPreferenceRenderer(): MiuixPreferenceRenderer = AndroidMiuixPreferenceRenderer

private object AndroidMiuixPreferenceRenderer : MiuixPreferenceRenderer {

    @Composable
    override fun switchPreference(
        title: String,
        summary: String?,
        checked: Boolean,
        enabled: Boolean,
        onCheckedChange: (Boolean) -> Unit,
    ) {
        SwitchPreference(
            title = title,
            summary = summary,
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier,
            enabled = enabled,
        )
    }

    // M5-2a-pre：与迁移前 `ClickableSettingItem` 里那段 `ArrowPreference(...)` 逐字等价
    // （`insideMargin` 一直是 `BasicComponentDefaults.InsideMargin`）。
    @Composable
    override fun arrowPreference(
        title: String,
        summary: String?,
        onClick: () -> Unit,
    ) {
        ArrowPreference(
            title = title,
            summary = summary,
            insideMargin = BasicComponentDefaults.InsideMargin,
            onClick = onClick,
        )
    }

    // M5-2b：与迁移前 `DropdownListSettingItem` 里那段 `OverlaySpinnerPreference(...)` 逐字等价。
    // 契约把 miuix 的 `DropdownItem` 换成了 `List<String>`、`startAction` 换成了
    // `ImageVector?`，所以这里要做回那层包装（`DropdownItem(title = …)` 与 `Icon(...)`）。
    @Composable
    override fun overlaySpinnerPreference(
        title: String,
        summary: String?,
        items: List<String>,
        selectedIndex: Int,
        imageVector: ImageVector?,
        onSelectedIndexChange: (Int) -> Unit,
    ) {
        OverlaySpinnerPreference(
            title = title,
            summary = summary,
            items = items.map { display -> DropdownItem(title = display) },
            selectedIndex = selectedIndex,
            startAction = imageVector?.let { icon ->
                {
                    Icon(
                        imageVector = icon,
                        contentDescription = null
                    )
                }
            },
            onSelectedIndexChange = onSelectedIndexChange,
        )
    }

    // M5-15c：与迁移前 `CompactSettingItems` 里那段 `WindowDropdownPreference(...)` 逐字等价。
    // 与 `overlaySpinnerPreference` 的区别只在两处：`items` **不做** `DropdownItem` 包装
    // （该组件的 `items` 本来就是字符串列表），其余（`startAction` 的那个 Icon、`insideMargin`
    // 之类常量）与那段原调用一致。
    @Composable
    override fun windowDropdownPreference(
        title: String,
        summary: String?,
        items: List<String>,
        selectedIndex: Int,
        imageVector: ImageVector?,
        onSelectedIndexChange: (Int) -> Unit,
    ) {
        WindowDropdownPreference(
            title = title,
            summary = summary,
            items = items,
            selectedIndex = selectedIndex,
            startAction = imageVector?.let { icon ->
                {
                    Icon(
                        imageVector = icon,
                        contentDescription = null
                    )
                }
            },
            onSelectedIndexChange = onSelectedIndexChange,
        )
    }
}
