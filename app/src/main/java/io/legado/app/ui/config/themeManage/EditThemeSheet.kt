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
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.config.ThemeExportData
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.SearchBar
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
        title = "编辑主题",
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
            SectionTitle("基础设置")
            CompactDropdownSettingItem(
                title = "主题模式",
                selectedValue = data.themeMode,
                displayEntries = stringArrayResource(R.array.theme_mode),
                entryValues = stringArrayResource(R.array.theme_mode_v),
                onValueChange = { data = data.copy(themeMode = it) }
            )
            CompactDropdownSettingItem(
                title = "调色板风格",
                selectedValue = data.paletteStyle,
                displayEntries = stringArrayResource(R.array.paletteStyle),
                entryValues = stringArrayResource(R.array.paletteStyle_value),
                onValueChange = { data = data.copy(paletteStyle = it) }
            )
            CompactDropdownSettingItem(
                title = "Material 版本",
                selectedValue = data.materialVersion,
                displayEntries = stringArrayResource(R.array.materialVersion),
                entryValues = stringArrayResource(R.array.materialVersion_value),
                onValueChange = { data = data.copy(materialVersion = it) }
            )
            CompactDropdownSettingItem(
                title = "对比度偏好",
                selectedValue = data.customContrast,
                displayEntries = stringArrayResource(R.array.customContrast),
                entryValues = stringArrayResource(R.array.customContrast_value),
                onValueChange = { data = data.copy(customContrast = it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Colors
            SectionTitle("颜色设置")
            CompactSwitchSettingItem(
                title = "使用色板生成颜色",
                checked = !data.enableDeepPersonalization,
                onCheckedChange = { data = data.copy(enableDeepPersonalization = !it) }
            )
            if (data.enableDeepPersonalization) {
                ColorItem("主题色", data.themeColor) {
                    currentColorKey = "themeColor"; showColorPicker = true
                }
                ColorItem("次要主题色", data.secondaryThemeColor) {
                    currentColorKey = "secondaryThemeColor"; showColorPicker = true
                }
                ColorItem("主要字体色", data.primaryTextColor) {
                    currentColorKey = "primaryTextColor"; showColorPicker = true
                }
                ColorItem("次要字体色", data.secondaryTextColor) {
                    currentColorKey = "secondaryTextColor"; showColorPicker = true
                }
                ColorItem("背景色", data.themeBackgroundColor) {
                    currentColorKey = "themeBackgroundColor"; showColorPicker = true
                }
                ColorItem("标签容器色", data.labelContainerColor) {
                    currentColorKey = "labelContainerColor"; showColorPicker = true
                }
            } else {
                ColorItem("日间种子色", data.cPrimary) {
                    currentColorKey = "cPrimary"; showColorPicker = true
                }
                ColorItem("夜间种子色", data.cNPrimary) {
                    currentColorKey = "cNPrimary"; showColorPicker = true
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Interface layout
            SectionTitle("界面布局")
            CompactSwitchSettingItem(
                title = "发现",
                checked = data.showDiscovery,
                onCheckedChange = { data = data.copy(showDiscovery = it) }
            )
            CompactSwitchSettingItem(
                title = "RSS",
                checked = data.showRss,
                onCheckedChange = { data = data.copy(showRss = it) }
            )
            CompactSwitchSettingItem(
                title = "显示底栏",
                checked = data.showBottomView,
                onCheckedChange = { data = data.copy(showBottomView = it) }
            )
            CompactSwitchSettingItem(
                title = "浮动底栏",
                checked = data.useFloatingBottomBar,
                onCheckedChange = { data = data.copy(useFloatingBottomBar = it) }
            )
            CompactSwitchSettingItem(
                title = "状态栏",
                checked = data.showStatusBar,
                onCheckedChange = { data = data.copy(showStatusBar = it) }
            )
            CompactSwitchSettingItem(
                title = "翻页动画",
                checked = data.swipeAnimation,
                onCheckedChange = { data = data.copy(swipeAnimation = it) }
            )
            CompactDropdownSettingItem(
                title = "平板模式",
                selectedValue = data.tabletInterface,
                displayEntries = stringArrayResource(R.array.tabletInterface),
                entryValues = stringArrayResource(R.array.tabletInterface_value),
                onValueChange = { data = data.copy(tabletInterface = it) }
            )
            CompactDropdownSettingItem(
                title = "标签显示",
                selectedValue = data.labelVisibilityMode,
                displayEntries = stringArrayResource(R.array.label_vis_mode),
                entryValues = stringArrayResource(R.array.label_vis_mode_value),
                onValueChange = { data = data.copy(labelVisibilityMode = it) }
            )
            CompactDropdownSettingItem(
                title = "默认主页",
                selectedValue = data.defaultHomePage,
                displayEntries = stringArrayResource(R.array.default_home_page),
                entryValues = stringArrayResource(R.array.default_home_page_value),
                onValueChange = { data = data.copy(defaultHomePage = it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Blur
            SectionTitle("模糊效果")
            CompactSwitchSettingItem(
                title = "启用模糊",
                checked = data.enableBlur,
                onCheckedChange = { data = data.copy(enableBlur = it) }
            )
            if (data.enableBlur) {
                CompactSliderSettingItem(
                    title = "顶栏模糊半径",
                    value = data.topBarBlurRadius.toFloat(),
                    valueRange = 1f..60f,
                    onValueChange = { data = data.copy(topBarBlurRadius = it.toInt()) }
                )
                CompactSliderSettingItem(
                    title = "底栏模糊半径",
                    value = data.bottomBarBlurRadius.toFloat(),
                    valueRange = 1f..60f,
                    onValueChange = { data = data.copy(bottomBarBlurRadius = it.toInt()) }
                )
                CompactSliderSettingItem(
                    title = "顶栏透明度",
                    value = data.topBarBlurAlpha.toFloat(),
                    valueRange = 0f..255f,
                    onValueChange = { data = data.copy(topBarBlurAlpha = it.toInt()) }
                )
                CompactSliderSettingItem(
                    title = "底栏透明度",
                    value = data.bottomBarBlurAlpha.toFloat(),
                    valueRange = 0f..255f,
                    onValueChange = { data = data.copy(bottomBarBlurAlpha = it.toInt()) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Opacity
            SectionTitle("透明度")
            CompactSliderSettingItem(
                title = "顶栏透明度",
                value = data.topBarOpacity.toFloat(),
                valueRange = 0f..100f,
                onValueChange = { data = data.copy(topBarOpacity = it.toInt()) }
            )
            CompactSliderSettingItem(
                title = "底栏透明度",
                value = data.bottomBarOpacity.toFloat(),
                valueRange = 0f..100f,
                onValueChange = { data = data.copy(bottomBarOpacity = it.toInt()) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Container
            SectionTitle("容器设置")
            CompactSliderSettingItem(
                title = "容器不透明度",
                value = data.containerOpacity.toFloat(),
                valueRange = 0f..100f,
                onValueChange = { data = data.copy(containerOpacity = it.toInt()) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Other
            SectionTitle("其他")
            CompactSwitchSettingItem(
                title = "纯黑模式",
                checked = data.isPureBlack,
                onCheckedChange = { data = data.copy(isPureBlack = it) }
            )
            CompactSwitchSettingItem(
                title = "弹性顶栏",
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
