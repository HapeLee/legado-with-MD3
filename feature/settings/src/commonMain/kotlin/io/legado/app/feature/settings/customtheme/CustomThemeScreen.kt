package io.legado.app.feature.settings.customtheme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import io.legado.app.feature.settings.res.Res
import io.legado.app.feature.settings.res.click_to_select
import io.legado.app.feature.settings.res.customContrast
import io.legado.app.feature.settings.res.customContrast_value
import io.legado.app.feature.settings.res.custom_theme_colors
import io.legado.app.feature.settings.res.day
import io.legado.app.feature.settings.res.materialVersion
import io.legado.app.feature.settings.res.materialVersion_value
import io.legado.app.feature.settings.res.material_version
import io.legado.app.feature.settings.res.night
import io.legado.app.feature.settings.res.paletteStyle
import io.legado.app.feature.settings.res.paletteStyle_value
import io.legado.app.feature.settings.res.palette_style
import io.legado.app.feature.settings.res.preferred_contrast
import io.legado.app.feature.settings.res.seed_color
import io.legado.app.feature.settings.res.theme_manage_background_color
import io.legado.app.feature.settings.res.theme_manage_label_container_color
import io.legado.app.feature.settings.res.theme_manage_primary_color
import io.legado.app.feature.settings.res.theme_manage_primary_text_color
import io.legado.app.feature.settings.res.theme_manage_secondary_color
import io.legado.app.feature.settings.res.theme_manage_secondary_text_color
import io.legado.app.feature.settings.res.theme_manage_use_palette_colors
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.dialog.ColorPickerSheet
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.DropdownListSettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

// M5-3b：从 `:app` 的 `io.legado.app.ui.config.customTheme.CustomThemeScreen` 迁来。
//
// 三类差异，其中**前两类是真正需要小心的**（第三类只是搬资源）：
//   1. **`stringArrayResource` 的返回类型变了**：Android 的返回 `Array<String>`，
//      CMP 的返回 `List<String>` ⇒ 调用点加 `.toTypedArray()`（`DropdownListSettingItem`
//      的签名要 `Array<String>`）。这是本仓第一次把 `string-array` 搬进 composeResources。
//   2. **`Integer.toHexString` 在 `commonMain` 里不存在**（`java.lang.Integer`）。
//      它和 `Int.toString(16)` 有**一处实质差异**：对负数，`Integer.toHexString`
//      给无符号十六进制（`0xFF000000` ⇒ `"ff000000"`），而 `toString(16)` 会带 `-` 号。
//      颜色值确实可能为负（`0xFF000000.toInt()`）⇒ 用 `.toUInt().toString(16)`
//      才与迁移前逐字等价。这是个不在编译期暴露的坑。
//   3. `R.string.*` → `Res.string.*`（15 条）+ 6 个 `string-array`（4 语言，
//      `*_value` 三个只在默认语言、靠 fallback，与 `:app` 的覆盖情况一致）。
//
// `ColorPickerSheet` 原本在 Android-only 的 `:core:ui`，已由 M5-3a-pre 上提到
// designsystem；其余组件（`ClickableSettingItem` / `DropdownListSettingItem` /
// `SwitchSettingItem`）由 M5-2a-pre / M5-2b 上提。

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomThemeScreen(
    state: CustomThemeUiState,
    onIntent: (CustomThemeIntent) -> Unit,
    onBackClick: () -> Unit,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(Res.string.custom_theme_colors),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    TopBarNavigationButton(onClick = onBackClick)
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = adaptiveContentPadding(
                top = paddingValues.calculateTopPadding(),
                bottom = 120.dp
            )
        ) {
            // Master switch: ON = deep color overrides, OFF = seed color mode
            item {
                SplicedColumnGroup {
                    SwitchSettingItem(
                        title = stringResource(Res.string.theme_manage_use_palette_colors),
                        checked = !state.enableDeepPersonalization,
                        onCheckedChange = {
                            onIntent(CustomThemeIntent.DeepPersonalizationChanged(!it))
                        }
                    )
                }
            }

            // Color settings vs Seed color toggle based on state.enableDeepPersonalization
            if (state.enableDeepPersonalization) {
                item {
                    CustomColorSettings(
                        title = stringResource(Res.string.day),
                        primary = state.themeColor,
                        secondary = state.secondaryThemeColor,
                        primaryText = state.primaryTextColor,
                        secondaryText = state.secondaryTextColor,
                        background = state.themeBackgroundColor,
                        labelContainer = state.labelContainerColor,
                        keySuffix = "",
                        onSelect = { onIntent(CustomThemeIntent.OpenPicker(CustomThemePicker.DeepColor(it))) },
                    )
                }
                item {
                    CustomColorSettings(
                        title = stringResource(Res.string.night),
                        primary = state.themeColorNight,
                        secondary = state.secondaryThemeColorNight,
                        primaryText = state.primaryTextColorNight,
                        secondaryText = state.secondaryTextColorNight,
                        background = state.themeBackgroundColorNight,
                        labelContainer = state.labelContainerColorNight,
                        keySuffix = "Night",
                        onSelect = { onIntent(CustomThemeIntent.OpenPicker(CustomThemePicker.DeepColor(it))) },
                    )
                }
            } else {
                item {
                    SplicedColumnGroup(title = stringResource(Res.string.custom_theme_colors)) {
                        ClickableSettingItem(
                            title = stringResource(Res.string.seed_color),
                            description = stringResource(Res.string.day),
                            option = formatColorOption(state.primarySeedColor)
                                ?: stringResource(Res.string.click_to_select),
                            onClick = { onIntent(CustomThemeIntent.OpenPicker(CustomThemePicker.DaySeed)) },
                            trailingContent = { ColorSwatch(colorValue = state.primarySeedColor) }
                        )
                        ClickableSettingItem(
                            title = stringResource(Res.string.seed_color),
                            description = stringResource(Res.string.night),
                            option = formatColorOption(state.nightPrimarySeedColor)
                                ?: stringResource(Res.string.click_to_select),
                            onClick = { onIntent(CustomThemeIntent.OpenPicker(CustomThemePicker.NightSeed)) },
                            trailingContent = { ColorSwatch(colorValue = state.nightPrimarySeedColor) }
                        )
                        DropdownListSettingItem(
                            title = stringResource(Res.string.palette_style),
                            selectedValue = state.paletteStyle,
                            // CMP 的 `stringArrayResource` 返回 `List<String>`，这里要 `Array<String>`
                            displayEntries = stringArrayResource(Res.array.paletteStyle).toTypedArray(),
                            entryValues = stringArrayResource(Res.array.paletteStyle_value).toTypedArray(),
                            onValueChange = { onIntent(CustomThemeIntent.PaletteStyleChanged(it)) }
                        )
                        DropdownListSettingItem(
                            title = stringResource(Res.string.preferred_contrast),
                            selectedValue = state.customContrast,
                            displayEntries = stringArrayResource(Res.array.customContrast).toTypedArray(),
                            entryValues = stringArrayResource(Res.array.customContrast_value).toTypedArray(),
                            onValueChange = { onIntent(CustomThemeIntent.CustomContrastChanged(it)) }
                        )
                        DropdownListSettingItem(
                            title = stringResource(Res.string.material_version),
                            selectedValue = state.materialVersion,
                            displayEntries = stringArrayResource(Res.array.materialVersion).toTypedArray(),
                            entryValues = stringArrayResource(Res.array.materialVersion_value).toTypedArray(),
                            onValueChange = { onIntent(CustomThemeIntent.MaterialVersionChanged(it)) }
                        )
                    }
                }
            }

        }

        ColorPickerSheet(
            show = state.activePicker is CustomThemePicker.DeepColor,
            initialColor = state.colorForPicker(),
            onDismissRequest = { onIntent(CustomThemeIntent.DismissPicker) },
            onColorSelected = { onIntent(CustomThemeIntent.ColorSelected(it)) },
        )

        ColorPickerSheet(
            show = state.activePicker == CustomThemePicker.DaySeed ||
                state.activePicker == CustomThemePicker.NightSeed,
            initialColor = state.colorForPicker(),
            onDismissRequest = { onIntent(CustomThemeIntent.DismissPicker) },
            onColorSelected = { onIntent(CustomThemeIntent.ColorSelected(it)) },
        )

    }
}

@Composable
private fun CustomColorSettings(
    title: String,
    primary: Int,
    secondary: Int,
    primaryText: Int,
    secondaryText: Int,
    background: Int,
    labelContainer: Int,
    keySuffix: String,
    onSelect: (CustomThemeColorSlot) -> Unit,
) {
    SplicedColumnGroup(title = title) {
        CustomColorSettingItem(
            title = stringResource(Res.string.theme_manage_primary_color),
            colorValue = primary,
            onClick = { onSelect(if (keySuffix.isEmpty()) CustomThemeColorSlot.Primary else CustomThemeColorSlot.PrimaryNight) },
        )
        CustomColorSettingItem(
            title = stringResource(Res.string.theme_manage_secondary_color),
            colorValue = secondary,
            onClick = {
                onSelect(
                    if (keySuffix.isEmpty()) CustomThemeColorSlot.Secondary
                    else CustomThemeColorSlot.SecondaryNight
                )
            },
        )
        CustomColorSettingItem(
            title = stringResource(Res.string.theme_manage_primary_text_color),
            colorValue = primaryText,
            onClick = { onSelect(if (keySuffix.isEmpty()) CustomThemeColorSlot.PrimaryText else CustomThemeColorSlot.PrimaryTextNight) },
        )
        CustomColorSettingItem(
            title = stringResource(Res.string.theme_manage_secondary_text_color),
            colorValue = secondaryText,
            onClick = { onSelect(if (keySuffix.isEmpty()) CustomThemeColorSlot.SecondaryText else CustomThemeColorSlot.SecondaryTextNight) },
        )
        CustomColorSettingItem(
            title = stringResource(Res.string.theme_manage_background_color),
            colorValue = background,
            onClick = { onSelect(if (keySuffix.isEmpty()) CustomThemeColorSlot.Background else CustomThemeColorSlot.BackgroundNight) },
        )
        CustomColorSettingItem(
            title = stringResource(Res.string.theme_manage_label_container_color),
            colorValue = labelContainer,
            onClick = { onSelect(if (keySuffix.isEmpty()) CustomThemeColorSlot.LabelContainer else CustomThemeColorSlot.LabelContainerNight) },
        )
    }
}

@Composable
private fun CustomColorSettingItem(
    title: String,
    colorValue: Int,
    onClick: () -> Unit,
) {
    ClickableSettingItem(
        title = title,
        option = formatColorOption(colorValue) ?: stringResource(Res.string.click_to_select),
        onClick = onClick,
        trailingContent = { ColorSwatch(colorValue) },
    )
}

/**
 * ⚠️ `.toUInt()` 是不能省的：迁移前用的是 `Integer.toHexString(colorValue)`，它对负数给
 * **无符号**十六进制（`0xFF000000.toInt()` ⇒ `"ff000000"`）；而 `Int.toString(16)` 会给
 * `"-1000000"`。颜色值确实可能为负 ⇒ 只有 `.toUInt().toString(16)` 才与迁移前逐字等价。
 * 这个函数在 `commonMain` 里，不能再用 `java.lang.Integer`。
 */
private fun formatColorOption(colorValue: Int): String? {
    if (colorValue == 0) return null
    return "#${colorValue.toUInt().toString(16).uppercase()}"
}

@Composable
private fun ColorSwatch(colorValue: Int) {
    if (colorValue == 0) return
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color(colorValue))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
                CircleShape
            )
    )
}

private fun CustomThemeUiState.colorForPicker(): Int = when (val picker = activePicker) {
    is CustomThemePicker.DeepColor -> when (picker.slot) {
        CustomThemeColorSlot.Primary -> themeColor
        CustomThemeColorSlot.Secondary -> secondaryThemeColor
        CustomThemeColorSlot.PrimaryText -> primaryTextColor
        CustomThemeColorSlot.SecondaryText -> secondaryTextColor
        CustomThemeColorSlot.Background -> themeBackgroundColor
        CustomThemeColorSlot.LabelContainer -> labelContainerColor
        CustomThemeColorSlot.PrimaryNight -> themeColorNight
        CustomThemeColorSlot.SecondaryNight -> secondaryThemeColorNight
        CustomThemeColorSlot.PrimaryTextNight -> primaryTextColorNight
        CustomThemeColorSlot.SecondaryTextNight -> secondaryTextColorNight
        CustomThemeColorSlot.BackgroundNight -> themeBackgroundColorNight
        CustomThemeColorSlot.LabelContainerNight -> labelContainerColorNight
    }
    CustomThemePicker.DaySeed -> primarySeedColor
    CustomThemePicker.NightSeed -> nightPrimarySeedColor
    null -> 0
}
