package io.legado.app.ui.config.customTheme

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
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
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
                title = stringResource(R.string.custom_theme_colors),
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
                        title = stringResource(R.string.theme_manage_use_palette_colors),
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
                        title = stringResource(R.string.day),
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
                        title = stringResource(R.string.night),
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
                    SplicedColumnGroup(title = stringResource(R.string.custom_theme_colors)) {
                        ClickableSettingItem(
                            title = stringResource(R.string.seed_color),
                            description = stringResource(R.string.day),
                            option = formatColorOption(state.primarySeedColor)
                                ?: stringResource(R.string.click_to_select),
                            onClick = { onIntent(CustomThemeIntent.OpenPicker(CustomThemePicker.DaySeed)) },
                            trailingContent = { ColorSwatch(colorValue = state.primarySeedColor) }
                        )
                        ClickableSettingItem(
                            title = stringResource(R.string.seed_color),
                            description = stringResource(R.string.night),
                            option = formatColorOption(state.nightPrimarySeedColor)
                                ?: stringResource(R.string.click_to_select),
                            onClick = { onIntent(CustomThemeIntent.OpenPicker(CustomThemePicker.NightSeed)) },
                            trailingContent = { ColorSwatch(colorValue = state.nightPrimarySeedColor) }
                        )
                        DropdownListSettingItem(
                            title = stringResource(R.string.palette_style),
                            selectedValue = state.paletteStyle,
                            displayEntries = stringArrayResource(R.array.paletteStyle),
                            entryValues = stringArrayResource(R.array.paletteStyle_value),
                            onValueChange = { onIntent(CustomThemeIntent.PaletteStyleChanged(it)) }
                        )
                        DropdownListSettingItem(
                            title = stringResource(R.string.preferred_contrast),
                            selectedValue = state.customContrast,
                            displayEntries = stringArrayResource(R.array.customContrast),
                            entryValues = stringArrayResource(R.array.customContrast_value),
                            onValueChange = { onIntent(CustomThemeIntent.CustomContrastChanged(it)) }
                        )
                        DropdownListSettingItem(
                            title = stringResource(R.string.material_version),
                            selectedValue = state.materialVersion,
                            displayEntries = stringArrayResource(R.array.materialVersion),
                            entryValues = stringArrayResource(R.array.materialVersion_value),
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
            title = stringResource(R.string.theme_manage_primary_color),
            colorValue = primary,
            onClick = { onSelect(if (keySuffix.isEmpty()) CustomThemeColorSlot.Primary else CustomThemeColorSlot.PrimaryNight) },
        )
        CustomColorSettingItem(
            title = stringResource(R.string.theme_manage_secondary_color),
            colorValue = secondary,
            onClick = {
                onSelect(
                    if (keySuffix.isEmpty()) CustomThemeColorSlot.Secondary
                    else CustomThemeColorSlot.SecondaryNight
                )
            },
        )
        CustomColorSettingItem(
            title = stringResource(R.string.theme_manage_primary_text_color),
            colorValue = primaryText,
            onClick = { onSelect(if (keySuffix.isEmpty()) CustomThemeColorSlot.PrimaryText else CustomThemeColorSlot.PrimaryTextNight) },
        )
        CustomColorSettingItem(
            title = stringResource(R.string.theme_manage_secondary_text_color),
            colorValue = secondaryText,
            onClick = { onSelect(if (keySuffix.isEmpty()) CustomThemeColorSlot.SecondaryText else CustomThemeColorSlot.SecondaryTextNight) },
        )
        CustomColorSettingItem(
            title = stringResource(R.string.theme_manage_background_color),
            colorValue = background,
            onClick = { onSelect(if (keySuffix.isEmpty()) CustomThemeColorSlot.Background else CustomThemeColorSlot.BackgroundNight) },
        )
        CustomColorSettingItem(
            title = stringResource(R.string.theme_manage_label_container_color),
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
        option = formatColorOption(colorValue) ?: stringResource(R.string.click_to_select),
        onClick = onClick,
        trailingContent = { ColorSwatch(colorValue) },
    )
}

private fun formatColorOption(colorValue: Int): String? {
    if (colorValue == 0) return null
    return "#${Integer.toHexString(colorValue).uppercase()}"
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
