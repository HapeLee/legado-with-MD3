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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import io.legado.app.R
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.config.themeConfig.ThemeConfig
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
    onBackClick: () -> Unit
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    var showColorPicker by remember { mutableStateOf(false) }
    var currentColorKey by remember { mutableStateOf("themeColor") }
    val context = LocalContext.current

    val enableDeepPersonalization = ThemeConfig.enableDeepPersonalization

    val themeColor = ThemeConfig.themeColor
    val secondaryThemeColor = ThemeConfig.secondaryThemeColor
    val primaryTextColor = ThemeConfig.primaryTextColor
    val secondaryTextColor = ThemeConfig.secondaryTextColor
    val themeBackgroundColor = ThemeConfig.themeBackgroundColor
    val labelContainerColor = ThemeConfig.labelContainerColor
    val themeColorNight = ThemeConfig.themeColorNight
    val secondaryThemeColorNight = ThemeConfig.secondaryThemeColorNight
    val primaryTextColorNight = ThemeConfig.primaryTextColorNight
    val secondaryTextColorNight = ThemeConfig.secondaryTextColorNight
    val themeBackgroundColorNight = ThemeConfig.themeBackgroundColorNight
    val labelContainerColorNight = ThemeConfig.labelContainerColorNight

    val primaryColor = MaterialTheme.colorScheme.primary

    // 自定义主题 seed color
    var showSeedColorPicker by remember { mutableStateOf(false) }
    var pickNightSeedColor by remember { mutableStateOf(false) }
    val primaryColorValue = remember { mutableIntStateOf(ThemeConfig.cPrimary) }
    val nightPrimaryColorValue = remember { mutableIntStateOf(ThemeConfig.cNPrimary) }

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
                        checked = !enableDeepPersonalization,
                        onCheckedChange = { ThemeConfig.enableDeepPersonalization = !it }
                    )
                }
            }

            // Color settings vs Seed color toggle based on enableDeepPersonalization
            if (enableDeepPersonalization) {
                item {
                    CustomColorSettings(
                        title = stringResource(R.string.day),
                        primary = themeColor,
                        secondary = secondaryThemeColor,
                        primaryText = primaryTextColor,
                        secondaryText = secondaryTextColor,
                        background = themeBackgroundColor,
                        labelContainer = labelContainerColor,
                        keySuffix = "",
                        onSelect = {
                            currentColorKey = it
                            showColorPicker = true
                        },
                    )
                }
                item {
                    CustomColorSettings(
                        title = stringResource(R.string.night),
                        primary = themeColorNight,
                        secondary = secondaryThemeColorNight,
                        primaryText = primaryTextColorNight,
                        secondaryText = secondaryTextColorNight,
                        background = themeBackgroundColorNight,
                        labelContainer = labelContainerColorNight,
                        keySuffix = "Night",
                        onSelect = {
                            currentColorKey = it
                            showColorPicker = true
                        },
                    )
                }
            } else {
                item {
                    SplicedColumnGroup(title = stringResource(R.string.custom_theme_colors)) {
                        ClickableSettingItem(
                            title = stringResource(R.string.seed_color),
                            description = stringResource(R.string.day),
                            option = formatColorOption(primaryColorValue.intValue)
                                ?: stringResource(R.string.click_to_select),
                            onClick = {
                                pickNightSeedColor = false
                                showSeedColorPicker = true
                            },
                            trailingContent = { ColorSwatch(colorValue = primaryColorValue.intValue) }
                        )
                        ClickableSettingItem(
                            title = stringResource(R.string.seed_color),
                            description = stringResource(R.string.night),
                            option = formatColorOption(nightPrimaryColorValue.intValue)
                                ?: stringResource(R.string.click_to_select),
                            onClick = {
                                pickNightSeedColor = true
                                showSeedColorPicker = true
                            },
                            trailingContent = { ColorSwatch(colorValue = nightPrimaryColorValue.intValue) }
                        )
                        DropdownListSettingItem(
                            title = stringResource(R.string.palette_style),
                            selectedValue = ThemeConfig.paletteStyle,
                            displayEntries = stringArrayResource(R.array.paletteStyle),
                            entryValues = stringArrayResource(R.array.paletteStyle_value),
                            onValueChange = { ThemeConfig.paletteStyle = it }
                        )
                        DropdownListSettingItem(
                            title = stringResource(R.string.preferred_contrast),
                            selectedValue = ThemeConfig.customContrast,
                            displayEntries = stringArrayResource(R.array.customContrast),
                            entryValues = stringArrayResource(R.array.customContrast_value),
                            onValueChange = { ThemeConfig.customContrast = it }
                        )
                        DropdownListSettingItem(
                            title = stringResource(R.string.material_version),
                            selectedValue = ThemeConfig.materialVersion,
                            displayEntries = stringArrayResource(R.array.materialVersion),
                            entryValues = stringArrayResource(R.array.materialVersion_value),
                            onValueChange = { ThemeConfig.materialVersion = it }
                        )
                    }
                }
            }

        }

        // Deep personalization color picker
        ColorPickerSheet(
            show = showColorPicker,
            initialColor = when (currentColorKey) {
                "themeColor" -> themeColor
                "secondaryThemeColor" -> secondaryThemeColor
                "primaryTextColor" -> primaryTextColor
                "secondaryTextColor" -> secondaryTextColor
                "themeBackgroundColor" -> themeBackgroundColor
                "labelContainerColor" -> labelContainerColor
                "themeColorNight" -> themeColorNight
                "secondaryThemeColorNight" -> secondaryThemeColorNight
                "primaryTextColorNight" -> primaryTextColorNight
                "secondaryTextColorNight" -> secondaryTextColorNight
                "themeBackgroundColorNight" -> themeBackgroundColorNight
                "labelContainerColorNight" -> labelContainerColorNight
                else -> 0
            },
            onDismissRequest = { showColorPicker = false },
            onColorSelected = {
                when (currentColorKey) {
                    "themeColor" -> ThemeConfig.themeColor = it
                    "secondaryThemeColor" -> ThemeConfig.secondaryThemeColor = it
                    "primaryTextColor" -> ThemeConfig.primaryTextColor = it
                    "secondaryTextColor" -> ThemeConfig.secondaryTextColor = it
                    "themeBackgroundColor" -> ThemeConfig.themeBackgroundColor = it
                    "labelContainerColor" -> ThemeConfig.labelContainerColor = it
                    "themeColorNight" -> ThemeConfig.themeColorNight = it
                    "secondaryThemeColorNight" -> ThemeConfig.secondaryThemeColorNight = it
                    "primaryTextColorNight" -> ThemeConfig.primaryTextColorNight = it
                    "secondaryTextColorNight" -> ThemeConfig.secondaryTextColorNight = it
                    "themeBackgroundColorNight" -> ThemeConfig.themeBackgroundColorNight = it
                    "labelContainerColorNight" -> ThemeConfig.labelContainerColorNight = it
                }
            }
        )

        // Seed color picker (from ThemeConfigScreen)
        ColorPickerSheet(
            show = showSeedColorPicker,
            initialColor = if (pickNightSeedColor) {
                nightPrimaryColorValue.value
            } else {
                primaryColorValue.value
            },
            onDismissRequest = { showSeedColorPicker = false },
            onColorSelected = { color ->
                if (pickNightSeedColor) {
                    nightPrimaryColorValue.intValue = color
                    ThemeConfig.cNPrimary = color
                } else {
                    primaryColorValue.intValue = color
                    ThemeConfig.cPrimary = color
                    ThemeStore.editTheme(context)
                        .primaryColor(color)
                        .apply()
                    DynamicColors.applyToActivitiesIfAvailable(
                        context.applicationContext as android.app.Application,
                        DynamicColorsOptions.Builder()
                            .setContentBasedSource(context.primaryColor)
                            .build()
                    )
                }
            }
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
    onSelect: (String) -> Unit,
) {
    SplicedColumnGroup(title = title) {
        CustomColorSettingItem(
            title = stringResource(R.string.theme_manage_primary_color),
            colorValue = primary,
            onClick = { onSelect("themeColor$keySuffix") },
        )
        CustomColorSettingItem(
            title = stringResource(R.string.theme_manage_secondary_color),
            colorValue = secondary,
            onClick = { onSelect("secondaryThemeColor$keySuffix") },
        )
        CustomColorSettingItem(
            title = stringResource(R.string.theme_manage_primary_text_color),
            colorValue = primaryText,
            onClick = { onSelect("primaryTextColor$keySuffix") },
        )
        CustomColorSettingItem(
            title = stringResource(R.string.theme_manage_secondary_text_color),
            colorValue = secondaryText,
            onClick = { onSelect("secondaryTextColor$keySuffix") },
        )
        CustomColorSettingItem(
            title = stringResource(R.string.theme_manage_background_color),
            colorValue = background,
            onClick = { onSelect("themeBackgroundColor$keySuffix") },
        )
        CustomColorSettingItem(
            title = stringResource(R.string.theme_manage_label_container_color),
            colorValue = labelContainer,
            onClick = { onSelect("labelContainerColor$keySuffix") },
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
