package io.legado.app.ui.config.themeManage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.config.ThemeExportData
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.button.MediumIconButton
import io.legado.app.ui.widget.components.dialog.ColorPickerSheet
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.CompactClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.CompactDropdownSettingItem
import io.legado.app.ui.widget.components.settingItem.CompactSliderSettingItem
import io.legado.app.ui.widget.components.settingItem.CompactSwitchSettingItem
import io.legado.app.ui.widget.components.text.AppText

@Composable
fun EditThemeSheet(
    show: Boolean,
    themeData: ThemeExportData?,
    themeName: String,
    onDismissRequest: () -> Unit,
    onSave: (newName: String, newData: ThemeExportData) -> Unit
) {
    if (!show || themeData == null) return

    var data by remember(themeData) { mutableStateOf(themeData) }
    var name by remember(themeName) { mutableStateOf(themeName) }
    var showColorPicker by remember { mutableStateOf(false) }
    var currentColorKey by remember { mutableStateOf("") }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.theme_manage_edit_theme),
        endAction = {
            MediumIconButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(name, data)
                    }
                },
                imageVector = Icons.Default.Done,
                contentDescription = "Save"
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Name
            AppTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Basic settings
            SectionTitle(stringResource(R.string.theme_manage_section_basic))
            CompactDropdownSettingItem(
                title = stringResource(R.string.theme_mode),
                selectedValue = data.themeMode,
                displayEntries = stringArrayResource(R.array.theme_mode),
                entryValues = stringArrayResource(R.array.theme_mode_v),
                onValueChange = { data = data.copy(themeMode = it) }
            )
            CompactDropdownSettingItem(
                title = stringResource(R.string.palette_style),
                selectedValue = data.paletteStyle,
                displayEntries = stringArrayResource(R.array.paletteStyle),
                entryValues = stringArrayResource(R.array.paletteStyle_value),
                onValueChange = { data = data.copy(paletteStyle = it) }
            )
            CompactDropdownSettingItem(
                title = stringResource(R.string.material_version),
                selectedValue = data.materialVersion,
                displayEntries = stringArrayResource(R.array.materialVersion),
                entryValues = stringArrayResource(R.array.materialVersion_value),
                onValueChange = { data = data.copy(materialVersion = it) }
            )
            CompactDropdownSettingItem(
                title = stringResource(R.string.preferred_contrast),
                selectedValue = data.customContrast,
                displayEntries = stringArrayResource(R.array.customContrast),
                entryValues = stringArrayResource(R.array.customContrast_value),
                onValueChange = { data = data.copy(customContrast = it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Colors
            SectionTitle(stringResource(R.string.theme_manage_section_colors))
            CompactSwitchSettingItem(
                title = stringResource(R.string.theme_manage_use_palette_colors),
                checked = !data.enableDeepPersonalization,
                onCheckedChange = { data = data.copy(enableDeepPersonalization = !it) }
            )
            if (data.enableDeepPersonalization) {
                ColorItem(stringResource(R.string.theme_manage_primary_color), data.themeColor) {
                    currentColorKey = "themeColor"; showColorPicker = true
                }
                ColorItem(stringResource(R.string.theme_manage_secondary_color), data.secondaryThemeColor) {
                    currentColorKey = "secondaryThemeColor"; showColorPicker = true
                }
                ColorItem(stringResource(R.string.theme_manage_primary_text_color), data.primaryTextColor) {
                    currentColorKey = "primaryTextColor"; showColorPicker = true
                }
                ColorItem(stringResource(R.string.theme_manage_secondary_text_color), data.secondaryTextColor) {
                    currentColorKey = "secondaryTextColor"; showColorPicker = true
                }
                ColorItem(stringResource(R.string.theme_manage_background_color), data.themeBackgroundColor) {
                    currentColorKey = "themeBackgroundColor"; showColorPicker = true
                }
                ColorItem(stringResource(R.string.theme_manage_label_container_color), data.labelContainerColor) {
                    currentColorKey = "labelContainerColor"; showColorPicker = true
                }
            } else {
                ColorItem(stringResource(R.string.theme_manage_day_seed_color), data.cPrimary) {
                    currentColorKey = "cPrimary"; showColorPicker = true
                }
                ColorItem(stringResource(R.string.theme_manage_night_seed_color), data.cNPrimary) {
                    currentColorKey = "cNPrimary"; showColorPicker = true
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Interface layout
            SectionTitle(stringResource(R.string.theme_manage_section_layout))
            CompactSwitchSettingItem(
                title = "首页",
                checked = data.showHome,
                onCheckedChange = { data = data.copy(showHome = it) }
            )
            CompactSwitchSettingItem(
                title = stringResource(R.string.theme_manage_show_discovery),
                checked = data.showDiscovery,
                onCheckedChange = { data = data.copy(showDiscovery = it) }
            )
            CompactSwitchSettingItem(
                title = "RSS",
                checked = data.showRss,
                onCheckedChange = { data = data.copy(showRss = it) }
            )
            CompactSwitchSettingItem(
                title = stringResource(R.string.show_bottom_nav),
                checked = data.showBottomView,
                onCheckedChange = { data = data.copy(showBottomView = it) }
            )
            CompactSwitchSettingItem(
                title = stringResource(R.string.floating_bottom_bar),
                checked = data.useFloatingBottomBar,
                onCheckedChange = { data = data.copy(useFloatingBottomBar = it) }
            )
            CompactSwitchSettingItem(
                title = stringResource(R.string.theme_manage_status_bar),
                checked = data.showStatusBar,
                onCheckedChange = { data = data.copy(showStatusBar = it) }
            )
            CompactSwitchSettingItem(
                title = stringResource(R.string.theme_manage_page_turn_animation),
                checked = data.swipeAnimation,
                onCheckedChange = { data = data.copy(swipeAnimation = it) }
            )
            CompactDropdownSettingItem(
                title = stringResource(R.string.tabletInterface),
                selectedValue = data.tabletInterface,
                displayEntries = stringArrayResource(R.array.tabletInterface),
                entryValues = stringArrayResource(R.array.tabletInterface_value),
                onValueChange = { data = data.copy(tabletInterface = it) }
            )
            CompactDropdownSettingItem(
                title = stringResource(R.string.theme_manage_label_visibility),
                selectedValue = data.labelVisibilityMode,
                displayEntries = stringArrayResource(R.array.label_vis_mode),
                entryValues = stringArrayResource(R.array.label_vis_mode_value),
                onValueChange = { data = data.copy(labelVisibilityMode = it) }
            )
            CompactDropdownSettingItem(
                title = stringResource(R.string.default_home_page),
                selectedValue = data.defaultHomePage,
                displayEntries = stringArrayResource(R.array.default_home_page),
                entryValues = stringArrayResource(R.array.default_home_page_value),
                onValueChange = { data = data.copy(defaultHomePage = it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Blur
            SectionTitle(stringResource(R.string.theme_manage_section_blur))
            CompactSwitchSettingItem(
                title = stringResource(R.string.is_blur_enable),
                checked = data.enableBlur,
                onCheckedChange = { data = data.copy(enableBlur = it) }
            )
            if (data.enableBlur) {
                CompactSliderSettingItem(
                    title = stringResource(R.string.theme_manage_top_bar_blur_radius),
                    value = data.topBarBlurRadius.toFloat(),
                    valueRange = 1f..60f,
                    onValueChange = { data = data.copy(topBarBlurRadius = it.toInt()) }
                )
                CompactSliderSettingItem(
                    title = stringResource(R.string.theme_manage_bottom_bar_blur_radius),
                    value = data.bottomBarBlurRadius.toFloat(),
                    valueRange = 1f..60f,
                    onValueChange = { data = data.copy(bottomBarBlurRadius = it.toInt()) }
                )
                CompactSliderSettingItem(
                    title = stringResource(R.string.theme_manage_top_bar_blur_opacity),
                    value = data.topBarBlurAlpha.toFloat(),
                    valueRange = 0f..255f,
                    onValueChange = { data = data.copy(topBarBlurAlpha = it.toInt()) }
                )
                CompactSliderSettingItem(
                    title = stringResource(R.string.theme_manage_bottom_bar_blur_opacity),
                    value = data.bottomBarBlurAlpha.toFloat(),
                    valueRange = 0f..255f,
                    onValueChange = { data = data.copy(bottomBarBlurAlpha = it.toInt()) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Opacity
            SectionTitle(stringResource(R.string.theme_manage_section_opacity))
            CompactSliderSettingItem(
                title = stringResource(R.string.top_bar_opacity),
                value = data.topBarOpacity.toFloat(),
                valueRange = 0f..100f,
                onValueChange = { data = data.copy(topBarOpacity = it.toInt()) }
            )
            CompactSliderSettingItem(
                title = stringResource(R.string.bottom_bar_opacity),
                value = data.bottomBarOpacity.toFloat(),
                valueRange = 0f..100f,
                onValueChange = { data = data.copy(bottomBarOpacity = it.toInt()) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Container
            SectionTitle(stringResource(R.string.theme_manage_section_container))
            CompactSliderSettingItem(
                title = stringResource(R.string.container_opacity),
                value = data.containerOpacity.toFloat(),
                valueRange = 0f..100f,
                onValueChange = { data = data.copy(containerOpacity = it.toInt()) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Other
            SectionTitle(stringResource(R.string.other))
            CompactSwitchSettingItem(
                title = stringResource(R.string.pure_black),
                checked = data.isPureBlack,
                onCheckedChange = { data = data.copy(isPureBlack = it) }
            )
            CompactSwitchSettingItem(
                title = stringResource(R.string.use_flexible_top_bar),
                checked = data.useFlexibleTopAppBar,
                onCheckedChange = { data = data.copy(useFlexibleTopAppBar = it) }
            )
        }
    }

    ColorPickerSheet(
        show = showColorPicker,
        initialColor = when (currentColorKey) {
            "themeColor" -> data.themeColor
            "secondaryThemeColor" -> data.secondaryThemeColor
            "primaryTextColor" -> data.primaryTextColor
            "secondaryTextColor" -> data.secondaryTextColor
            "themeBackgroundColor" -> data.themeBackgroundColor
            "labelContainerColor" -> data.labelContainerColor
            "cPrimary" -> data.cPrimary
            "cNPrimary" -> data.cNPrimary
            else -> 0
        },
        onDismissRequest = { showColorPicker = false },
        onColorSelected = { color ->
            data = when (currentColorKey) {
                "themeColor" -> data.copy(themeColor = color)
                "secondaryThemeColor" -> data.copy(secondaryThemeColor = color)
                "primaryTextColor" -> data.copy(primaryTextColor = color)
                "secondaryTextColor" -> data.copy(secondaryTextColor = color)
                "themeBackgroundColor" -> data.copy(themeBackgroundColor = color)
                "labelContainerColor" -> data.copy(labelContainerColor = color)
                "cPrimary" -> data.copy(cPrimary = color)
                "cNPrimary" -> data.copy(cNPrimary = color)
                else -> data
            }
            showColorPicker = false
        }
    )
}

@Composable
private fun SectionTitle(text: String) {
    AppText(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun ColorItem(title: String, colorValue: Int, onClick: () -> Unit) {
    CompactClickableSettingItem(
        title = title,
        description = if (colorValue != 0) {
            "#${Integer.toHexString(colorValue).uppercase()}"
        } else {
            null
        },
        onClick = onClick,
        trailingContent = {
            if (colorValue != 0) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(colorValue))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                            CircleShape
                        )
                )
            }
        }
    )
}
