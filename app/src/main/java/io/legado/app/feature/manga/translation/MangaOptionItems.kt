package io.legado.app.feature.manga.translation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.settingItem.DropdownListSettingItem
import io.legado.app.ui.widget.components.settingItem.SliderSettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.ui.widget.components.settingItem.TinyDropdownSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySettingItem
import io.legado.app.ui.widget.components.settingItem.TinySliderSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySwitchSettingItem

/**
 * 漫画翻译选项的两种密度。
 *
 * 单书面板挂在底部 sheet 里，行高与圆角跟随 Tiny 系列；全局设置页是独立页面，仍用完整设置项。
 * 密度由外层 [MangaOptionsStyle] 决定一次，各条目与子面板不必逐层传参。
 */
internal val LocalMangaOptionsTiny = staticCompositionLocalOf { false }

@Composable
internal fun MangaOptionsStyle(tiny: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMangaOptionsTiny provides tiny, content = content)
}

/** 分组容器：完整密度是带标题的卡片组，Tiny 密度直接平铺（每行自带卡片）。 */
@Composable
internal fun MangaOptionGroup(
    title: String,
    tiny: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    MangaOptionsStyle(tiny) {
        if (tiny) Column(Modifier.fillMaxWidth(), content = content)
        else SplicedColumnGroup(title = title, items = content)
    }
}

@Composable
internal fun MangaOptionDropdown(
    title: String,
    selectedValue: String,
    displayEntries: Array<String>,
    entryValues: Array<String>,
    description: String? = null,
    enabled: Boolean = true,
    unavailableValues: Set<String> = emptySet(),
    onUnavailableSelected: ((String) -> Unit)? = null,
    onValueChange: (String) -> Unit,
) {
    if (LocalMangaOptionsTiny.current) TinyDropdownSettingItem(
        title = title,
        selectedValue = selectedValue,
        color = LegadoTheme.colorScheme.onSheetContent,
        displayEntries = displayEntries,
        entryValues = entryValues,
        description = description,
        enabled = enabled,
        unavailableValues = unavailableValues,
        onUnavailableSelected = onUnavailableSelected,
        onValueChange = onValueChange,
    ) else DropdownListSettingItem(
        title = title,
        selectedValue = selectedValue,
        displayEntries = displayEntries,
        entryValues = entryValues,
        description = description,
        enabled = enabled,
        unavailableValues = unavailableValues,
        onUnavailableSelected = onUnavailableSelected,
        onValueChange = onValueChange,
    )
}

@Composable
internal fun MangaOptionSwitch(
    title: String,
    checked: Boolean,
    description: String? = null,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    if (LocalMangaOptionsTiny.current) TinySwitchSettingItem(
        title = title,
        checked = checked,
        color = LegadoTheme.colorScheme.onSheetContent,
        description = description,
        enabled = enabled,
        onCheckedChange = onCheckedChange,
    ) else SwitchSettingItem(
        title = title,
        checked = checked,
        description = description,
        enabled = enabled,
        onCheckedChange = onCheckedChange,
    )
}

/** Tiny 密度下没有"恢复默认值"入口，[defaultValue] 只在完整设置项里用来做重置。 */
@Composable
internal fun MangaOptionSlider(
    title: String,
    value: Float,
    defaultValue: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    description: String? = null,
    valueLabel: ((Float) -> String)? = null,
    onValueChange: (Float) -> Unit,
) {
    if (LocalMangaOptionsTiny.current) TinySliderSettingItem(
        title = title,
        value = value,
        color = LegadoTheme.colorScheme.onSheetContent,
        valueRange = valueRange,
        description = description,
        valueFormat = valueLabel,
        onValueChange = onValueChange,
    ) else SliderSettingItem(
        title = title,
        value = value,
        defaultValue = defaultValue,
        valueRange = valueRange,
        description = description,
        valueLabel = valueLabel,
        onValueChange = onValueChange,
    )
}

@Composable
internal fun MangaOptionExpansion(
    title: String,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    expandContent: @Composable ColumnScope.() -> Unit,
) {
    if (LocalMangaOptionsTiny.current) TinySettingItem(
        title = title,
        color = LegadoTheme.colorScheme.onSheetContent,
        expanded = expanded,
        onExpandChange = onExpandChange,
        expandContent = expandContent,
    ) else MangaSettingsExpansion(
        title = title,
        expanded = expanded,
        onExpandChange = onExpandChange,
        // 完整密度的 expandContent 没有 ColumnScope 接收者，这里补一层 Column。
        expandContent = { Column { expandContent() } },
    )
}
