package io.legado.app.feature.settings.thememanage

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
import androidx.compose.ui.unit.dp
import io.legado.app.feature.settings.res.Res
import io.legado.app.feature.settings.res.bottom_bar_opacity
import io.legado.app.feature.settings.res.container_opacity
import io.legado.app.feature.settings.res.day
import io.legado.app.feature.settings.res.default_home_page
import io.legado.app.feature.settings.res.floating_bottom_bar
import io.legado.app.feature.settings.res.is_blur_enable
import io.legado.app.feature.settings.res.material_version
import io.legado.app.feature.settings.res.night
import io.legado.app.feature.settings.res.other
import io.legado.app.feature.settings.res.palette_style
import io.legado.app.feature.settings.res.preferred_contrast
import io.legado.app.feature.settings.res.pure_black
import io.legado.app.feature.settings.res.save
import io.legado.app.feature.settings.res.show_bottom_nav
import io.legado.app.feature.settings.res.show_home
import io.legado.app.feature.settings.res.tabletInterface
import io.legado.app.feature.settings.res.theme_manage_background_color
import io.legado.app.feature.settings.res.theme_manage_bottom_bar_blur_opacity
import io.legado.app.feature.settings.res.theme_manage_bottom_bar_blur_radius
import io.legado.app.feature.settings.res.theme_manage_day_seed_color
import io.legado.app.feature.settings.res.theme_manage_edit_theme
import io.legado.app.feature.settings.res.theme_manage_label_container_color
import io.legado.app.feature.settings.res.theme_manage_label_visibility
import io.legado.app.feature.settings.res.theme_manage_night_seed_color
import io.legado.app.feature.settings.res.theme_manage_page_turn_animation
import io.legado.app.feature.settings.res.theme_manage_primary_color
import io.legado.app.feature.settings.res.theme_manage_primary_text_color
import io.legado.app.feature.settings.res.theme_manage_secondary_color
import io.legado.app.feature.settings.res.theme_manage_secondary_text_color
import io.legado.app.feature.settings.res.theme_manage_section_basic
import io.legado.app.feature.settings.res.theme_manage_section_blur
import io.legado.app.feature.settings.res.theme_manage_section_colors
import io.legado.app.feature.settings.res.theme_manage_section_container
import io.legado.app.feature.settings.res.theme_manage_section_layout
import io.legado.app.feature.settings.res.theme_manage_section_opacity
import io.legado.app.feature.settings.res.theme_manage_show_discovery
import io.legado.app.feature.settings.res.theme_manage_status_bar
import io.legado.app.feature.settings.res.theme_manage_top_bar_blur_opacity
import io.legado.app.feature.settings.res.theme_manage_top_bar_blur_radius
import io.legado.app.feature.settings.res.theme_manage_use_palette_colors
import io.legado.app.feature.settings.res.theme_mode
import io.legado.app.feature.settings.res.top_bar_opacity
import io.legado.app.feature.settings.res.use_flexible_top_bar
import io.legado.app.feature.settings.res.customContrast
import io.legado.app.feature.settings.res.customContrast_value
import io.legado.app.feature.settings.res.default_home_page_value
import io.legado.app.feature.settings.res.label_vis_mode
import io.legado.app.feature.settings.res.label_vis_mode_value
import io.legado.app.feature.settings.res.materialVersion
import io.legado.app.feature.settings.res.materialVersion_value
import io.legado.app.feature.settings.res.paletteStyle
import io.legado.app.feature.settings.res.paletteStyle_value
import io.legado.app.feature.settings.res.tabletInterface_value
import io.legado.app.feature.settings.res.theme_mode_v
import io.legado.app.domain.model.settings.ThemeExportData
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.dialog.ColorPickerSheet
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.CompactClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.CompactDropdownSettingItem
import io.legado.app.ui.widget.components.settingItem.CompactSliderSettingItem
import io.legado.app.ui.widget.components.settingItem.CompactSwitchSettingItem
import io.legado.app.ui.widget.components.text.AppText
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

/**
 * M5-17a：从 `:app` 的 `ui/config/themeManage/EditThemeSheet.kt` 迁入。
 *
 * **正文逐字保留**（本次是脚本改写而非重打），只有四类改写：
 *
 * 1. 包名 → `io.legado.app.feature.settings.thememanage`。
 * 2. `androidx.compose.ui.res.stringResource` / `stringArrayResource` →
 *    `org.jetbrains.compose.resources.*`（前者读 Android res，`commonMain` 只能用 CMP 那套）。
 * 3. `R.string.*`（43 条）/ `R.array.*`（14 个）→ `Res.string.*` / `Res.array.*`，
 *    配套把用到的 key 逐行 import（本模块既有写法）。
 * 4. `Integer.toHexString(colorValue).uppercase()` → `colorValue.toString(16).uppercase()`
 *    —— `java.lang.Integer` 进不了 `commonMain`；Kotlin 的 `Int.toString(16)` 与之等价
 *    （同 M5-13c 对 `Integer.toHexString` 的处理）。它只在**颜色值非 0** 时才显示，
 *    是给用户看的十六进制色号。
 *
 * ⚠️ 资源侧的注意点（都已核过）：
 * - `default_home_page` 与 `tabletInterface` 是**同名不同类**的 key（既有 `string` 又有
 *   `string-array`）⇒ 搬运脚本的正则必须用否定前瞻区分 `<string` 与 `<string-array`，
 *   否则会把数组体当成字符串值插进去（本次踩过，已回退重做）。
 * - CMP 的 composeResources **不参与 Android 资源合并** ⇒ `:app` 里靠 `values` 回落的条目，
 *   在这里必须**每个语言目录各有一份**（值取回落结果，逐字一致）。
 * - 7 个值数组（`*_value` / `_v`）只落默认目录：实测它们在 `:app` 里只定义在
 *   `values/array_values.xml`，与语言无关。
 */

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
        title = stringResource(Res.string.theme_manage_edit_theme),
        endAction = {
            MediumTonalButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(name, data)
                    }
                },
                icon = Icons.Default.Done,
                contentDescription = stringResource(Res.string.save)
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
            SectionTitle(stringResource(Res.string.theme_manage_section_basic))
            CompactDropdownSettingItem(
                title = stringResource(Res.string.theme_mode),
                selectedValue = data.themeMode,
                displayEntries = stringArrayResource(Res.array.theme_mode).toTypedArray(),
                entryValues = stringArrayResource(Res.array.theme_mode_v).toTypedArray(),
                onValueChange = { data = data.copy(themeMode = it) }
            )
            CompactDropdownSettingItem(
                title = stringResource(Res.string.palette_style),
                selectedValue = data.paletteStyle,
                displayEntries = stringArrayResource(Res.array.paletteStyle).toTypedArray(),
                entryValues = stringArrayResource(Res.array.paletteStyle_value).toTypedArray(),
                onValueChange = { data = data.copy(paletteStyle = it) }
            )
            CompactDropdownSettingItem(
                title = stringResource(Res.string.material_version),
                selectedValue = data.materialVersion,
                displayEntries = stringArrayResource(Res.array.materialVersion).toTypedArray(),
                entryValues = stringArrayResource(Res.array.materialVersion_value).toTypedArray(),
                onValueChange = { data = data.copy(materialVersion = it) }
            )
            CompactDropdownSettingItem(
                title = stringResource(Res.string.preferred_contrast),
                selectedValue = data.customContrast,
                displayEntries = stringArrayResource(Res.array.customContrast).toTypedArray(),
                entryValues = stringArrayResource(Res.array.customContrast_value).toTypedArray(),
                onValueChange = { data = data.copy(customContrast = it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Colors
            SectionTitle(stringResource(Res.string.theme_manage_section_colors))
            CompactSwitchSettingItem(
                title = stringResource(Res.string.theme_manage_use_palette_colors),
                checked = !data.enableDeepPersonalization,
                onCheckedChange = { data = data.copy(enableDeepPersonalization = !it) }
            )
            if (data.enableDeepPersonalization) {
                SectionTitle(stringResource(Res.string.day))
                ColorItem(stringResource(Res.string.theme_manage_primary_color), data.themeColor) {
                    currentColorKey = "themeColor"; showColorPicker = true
                }
                ColorItem(stringResource(Res.string.theme_manage_secondary_color), data.secondaryThemeColor) {
                    currentColorKey = "secondaryThemeColor"; showColorPicker = true
                }
                ColorItem(stringResource(Res.string.theme_manage_primary_text_color), data.primaryTextColor) {
                    currentColorKey = "primaryTextColor"; showColorPicker = true
                }
                ColorItem(stringResource(Res.string.theme_manage_secondary_text_color), data.secondaryTextColor) {
                    currentColorKey = "secondaryTextColor"; showColorPicker = true
                }
                ColorItem(stringResource(Res.string.theme_manage_background_color), data.themeBackgroundColor) {
                    currentColorKey = "themeBackgroundColor"; showColorPicker = true
                }
                ColorItem(stringResource(Res.string.theme_manage_label_container_color), data.labelContainerColor) {
                    currentColorKey = "labelContainerColor"; showColorPicker = true
                }
                SectionTitle(stringResource(Res.string.night))
                ColorItem(stringResource(Res.string.theme_manage_primary_color), data.themeColorNight) {
                    currentColorKey = "themeColorNight"; showColorPicker = true
                }
                ColorItem(stringResource(Res.string.theme_manage_secondary_color), data.secondaryThemeColorNight) {
                    currentColorKey = "secondaryThemeColorNight"; showColorPicker = true
                }
                ColorItem(stringResource(Res.string.theme_manage_primary_text_color), data.primaryTextColorNight) {
                    currentColorKey = "primaryTextColorNight"; showColorPicker = true
                }
                ColorItem(stringResource(Res.string.theme_manage_secondary_text_color), data.secondaryTextColorNight) {
                    currentColorKey = "secondaryTextColorNight"; showColorPicker = true
                }
                ColorItem(stringResource(Res.string.theme_manage_background_color), data.themeBackgroundColorNight) {
                    currentColorKey = "themeBackgroundColorNight"; showColorPicker = true
                }
                ColorItem(stringResource(Res.string.theme_manage_label_container_color), data.labelContainerColorNight) {
                    currentColorKey = "labelContainerColorNight"; showColorPicker = true
                }
            } else {
                ColorItem(stringResource(Res.string.theme_manage_day_seed_color), data.cPrimary) {
                    currentColorKey = "cPrimary"; showColorPicker = true
                }
                ColorItem(stringResource(Res.string.theme_manage_night_seed_color), data.cNPrimary) {
                    currentColorKey = "cNPrimary"; showColorPicker = true
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Interface layout
            SectionTitle(stringResource(Res.string.theme_manage_section_layout))
            CompactSwitchSettingItem(
                title = stringResource(Res.string.show_home),
                checked = data.showHome,
                onCheckedChange = { data = data.copy(showHome = it) }
            )
            CompactSwitchSettingItem(
                title = stringResource(Res.string.theme_manage_show_discovery),
                checked = data.showDiscovery,
                onCheckedChange = { data = data.copy(showDiscovery = it) }
            )
            CompactSwitchSettingItem(
                title = "RSS",
                checked = data.showRss,
                onCheckedChange = { data = data.copy(showRss = it) }
            )
            CompactSwitchSettingItem(
                title = stringResource(Res.string.show_bottom_nav),
                checked = data.showBottomView,
                onCheckedChange = { data = data.copy(showBottomView = it) }
            )
            CompactSwitchSettingItem(
                title = stringResource(Res.string.floating_bottom_bar),
                checked = data.useFloatingBottomBar,
                onCheckedChange = { data = data.copy(useFloatingBottomBar = it) }
            )
            CompactSwitchSettingItem(
                title = stringResource(Res.string.theme_manage_status_bar),
                checked = data.showStatusBar,
                onCheckedChange = { data = data.copy(showStatusBar = it) }
            )
            CompactSwitchSettingItem(
                title = stringResource(Res.string.theme_manage_page_turn_animation),
                checked = data.swipeAnimation,
                onCheckedChange = { data = data.copy(swipeAnimation = it) }
            )
            CompactDropdownSettingItem(
                title = stringResource(Res.string.tabletInterface),
                selectedValue = data.tabletInterface,
                displayEntries = stringArrayResource(Res.array.tabletInterface).toTypedArray(),
                entryValues = stringArrayResource(Res.array.tabletInterface_value).toTypedArray(),
                onValueChange = { data = data.copy(tabletInterface = it) }
            )
            CompactDropdownSettingItem(
                title = stringResource(Res.string.theme_manage_label_visibility),
                selectedValue = data.labelVisibilityMode,
                displayEntries = stringArrayResource(Res.array.label_vis_mode).toTypedArray(),
                entryValues = stringArrayResource(Res.array.label_vis_mode_value).toTypedArray(),
                onValueChange = { data = data.copy(labelVisibilityMode = it) }
            )
            CompactDropdownSettingItem(
                title = stringResource(Res.string.default_home_page),
                selectedValue = data.defaultHomePage,
                displayEntries = stringArrayResource(Res.array.default_home_page).toTypedArray(),
                entryValues = stringArrayResource(Res.array.default_home_page_value).toTypedArray(),
                onValueChange = { data = data.copy(defaultHomePage = it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Blur
            SectionTitle(stringResource(Res.string.theme_manage_section_blur))
            CompactSwitchSettingItem(
                title = stringResource(Res.string.is_blur_enable),
                checked = data.enableBlur,
                onCheckedChange = { data = data.copy(enableBlur = it) }
            )
            if (data.enableBlur) {
                CompactSliderSettingItem(
                    title = stringResource(Res.string.theme_manage_top_bar_blur_radius),
                    value = data.topBarBlurRadius.toFloat(),
                    valueRange = 1f..60f,
                    onValueChange = { data = data.copy(topBarBlurRadius = it.toInt()) }
                )
                CompactSliderSettingItem(
                    title = stringResource(Res.string.theme_manage_bottom_bar_blur_radius),
                    value = data.bottomBarBlurRadius.toFloat(),
                    valueRange = 1f..60f,
                    onValueChange = { data = data.copy(bottomBarBlurRadius = it.toInt()) }
                )
                CompactSliderSettingItem(
                    title = stringResource(Res.string.theme_manage_top_bar_blur_opacity),
                    value = data.topBarBlurAlpha.toFloat(),
                    valueRange = 0f..255f,
                    onValueChange = { data = data.copy(topBarBlurAlpha = it.toInt()) }
                )
                CompactSliderSettingItem(
                    title = stringResource(Res.string.theme_manage_bottom_bar_blur_opacity),
                    value = data.bottomBarBlurAlpha.toFloat(),
                    valueRange = 0f..255f,
                    onValueChange = { data = data.copy(bottomBarBlurAlpha = it.toInt()) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Opacity
            SectionTitle(stringResource(Res.string.theme_manage_section_opacity))
            CompactSliderSettingItem(
                title = stringResource(Res.string.top_bar_opacity),
                value = data.topBarOpacity.toFloat(),
                valueRange = 0f..100f,
                onValueChange = { data = data.copy(topBarOpacity = it.toInt()) }
            )
            CompactSliderSettingItem(
                title = stringResource(Res.string.bottom_bar_opacity),
                value = data.bottomBarOpacity.toFloat(),
                valueRange = 0f..100f,
                onValueChange = { data = data.copy(bottomBarOpacity = it.toInt()) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Container
            SectionTitle(stringResource(Res.string.theme_manage_section_container))
            CompactSliderSettingItem(
                title = stringResource(Res.string.container_opacity),
                value = data.containerOpacity.toFloat(),
                valueRange = 0f..100f,
                onValueChange = { data = data.copy(containerOpacity = it.toInt()) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Other
            SectionTitle(stringResource(Res.string.other))
            CompactSwitchSettingItem(
                title = stringResource(Res.string.pure_black),
                checked = data.isPureBlack,
                onCheckedChange = { data = data.copy(isPureBlack = it) }
            )
            CompactSwitchSettingItem(
                title = stringResource(Res.string.use_flexible_top_bar),
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
            "themeColorNight" -> data.themeColorNight
            "secondaryThemeColorNight" -> data.secondaryThemeColorNight
            "primaryTextColorNight" -> data.primaryTextColorNight
            "secondaryTextColorNight" -> data.secondaryTextColorNight
            "themeBackgroundColorNight" -> data.themeBackgroundColorNight
            "labelContainerColorNight" -> data.labelContainerColorNight
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
                "themeColorNight" -> data.copy(themeColorNight = color)
                "secondaryThemeColorNight" -> data.copy(secondaryThemeColorNight = color)
                "primaryTextColorNight" -> data.copy(primaryTextColorNight = color)
                "secondaryTextColorNight" -> data.copy(secondaryTextColorNight = color)
                "themeBackgroundColorNight" -> data.copy(themeBackgroundColorNight = color)
                "labelContainerColorNight" -> data.copy(labelContainerColorNight = color)
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
            "#${colorValue.toString(16).uppercase()}"
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
