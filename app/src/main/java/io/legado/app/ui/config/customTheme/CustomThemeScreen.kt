package io.legado.app.ui.config.customTheme

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import io.legado.app.R
import io.legado.app.help.TagColorGenerator
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.config.mainConfig.MainConfig
import io.legado.app.ui.config.themeConfig.TagColorPair
import io.legado.app.ui.config.themeConfig.ThemeConfig
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.ui.widget.components.settingItem.SliderSettingItem
import io.legado.app.ui.widget.components.settingItem.DropdownListSettingItem
import io.legado.app.ui.widget.components.dialog.ColorPickerSheet
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.MediumOutlinedIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomThemeScreen(
    onBackClick: () -> Unit,
    onNavigateToThemePack: () -> Unit
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    var showColorPicker by remember { mutableStateOf(false) }
    var currentColorKey by remember { mutableStateOf("themeColor") }
    var listVersion by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    val enableDeepPersonalization = ThemeConfig.enableDeepPersonalization

    val themeColor = ThemeConfig.themeColor
    val secondaryThemeColor = ThemeConfig.secondaryThemeColor
    val primaryTextColor = ThemeConfig.primaryTextColor
    val secondaryTextColor = ThemeConfig.secondaryTextColor
    val themeBackgroundColor = ThemeConfig.themeBackgroundColor
    val labelContainerColor = ThemeConfig.labelContainerColor

    // 标签颜色设置
    val enableCustomTagColors = ThemeConfig.enableCustomTagColors
    val tagColors = remember(listVersion) { ThemeConfig.getCustomTagColors().toMutableStateList() }
    var showTagColorPicker by remember { mutableStateOf(false) }
    var editingTagColorIndex by remember { mutableIntStateOf(-1) }
    var showTagColorManagement by remember { mutableStateOf(false) }
    var editingTagTextColor by remember { mutableIntStateOf(0) }
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
                title = stringResource(R.string.custom_theme),
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
                        title = "使用色板生成颜色",
                        checked = !enableDeepPersonalization,
                        onCheckedChange = { ThemeConfig.enableDeepPersonalization = !it }
                    )
                }
            }

            // Color settings vs Seed color toggle based on enableDeepPersonalization
            if (enableDeepPersonalization) {
                item {
                    SplicedColumnGroup(title = stringResource(R.string.color_setting)) {
                    // Primary colors
                    ClickableSettingItem(
                        title = "主题色",
                        option = if (themeColor != 0) "#${Integer.toHexString(themeColor).uppercase()}" else stringResource(R.string.click_to_select),
                        onClick = {
                            currentColorKey = "themeColor"
                            showColorPicker = true
                        },
                        trailingContent = {
                            if (themeColor != 0) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(themeColor))
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant,
                                            CircleShape
                                        )
                                )
                            }
                        }
                    )

                    ClickableSettingItem(
                        title = "次要主题色",
                        option = if (secondaryThemeColor != 0) "#${Integer.toHexString(secondaryThemeColor).uppercase()}" else stringResource(R.string.click_to_select),
                        onClick = {
                            currentColorKey = "secondaryThemeColor"
                            showColorPicker = true
                        },
                        trailingContent = {
                            if (secondaryThemeColor != 0) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(secondaryThemeColor))
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant,
                                            CircleShape
                                        )
                                )
                            }
                        }
                    )

                    ClickableSettingItem(
                        title = "主要字体色",
                        option = if (primaryTextColor != 0) "#${Integer.toHexString(primaryTextColor).uppercase()}" else stringResource(R.string.click_to_select),
                        onClick = {
                            currentColorKey = "primaryTextColor"
                            showColorPicker = true
                        },
                        trailingContent = {
                            if (primaryTextColor != 0) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(primaryTextColor))
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant,
                                            CircleShape
                                        )
                                )
                            }
                        }
                    )

                    ClickableSettingItem(
                        title = "次要字体色",
                        option = if (secondaryTextColor != 0) "#${Integer.toHexString(secondaryTextColor).uppercase()}" else stringResource(R.string.click_to_select),
                        onClick = {
                            currentColorKey = "secondaryTextColor"
                            showColorPicker = true
                        },
                        trailingContent = {
                            if (secondaryTextColor != 0) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(secondaryTextColor))
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant,
                                            CircleShape
                                        )
                                )
                            }
                        }
                    )

                    ClickableSettingItem(
                        title = "背景色",
                        option = if (themeBackgroundColor != 0) "#${Integer.toHexString(themeBackgroundColor).uppercase()}" else stringResource(R.string.click_to_select),
                        onClick = {
                            currentColorKey = "themeBackgroundColor"
                            showColorPicker = true
                        },
                        trailingContent = {
                            if (themeBackgroundColor != 0) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(themeBackgroundColor))
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant,
                                            CircleShape
                                        )
                                )
                            }
                        }
                    )

                    ClickableSettingItem(
                        title = "标签容器色",
                        option = if (labelContainerColor != 0) "#${Integer.toHexString(labelContainerColor).uppercase()}" else stringResource(R.string.click_to_select),
                        onClick = {
                            currentColorKey = "labelContainerColor"
                            showColorPicker = true
                        },
                        trailingContent = {
                            if (labelContainerColor != 0) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(labelContainerColor))
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
            }
            } else {
                item {
                    SplicedColumnGroup(title = stringResource(R.string.custom_theme)) {
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

            // Theme Pack entry
            item {
                SplicedColumnGroup {
                    ClickableSettingItem(
                        title = stringResource(R.string.theme_pack),
                        description = stringResource(R.string.theme_pack_s),
                        onClick = onNavigateToThemePack
                    )
                }
            }

            // Custom tag color settings
            item {
                SplicedColumnGroup(title = "自定义标签颜色设置") {
                    SwitchSettingItem(
                        title = "启用自定义标签颜色",
                        checked = enableCustomTagColors,
                        onCheckedChange = { ThemeConfig.enableCustomTagColors = it }
                    )

                    if (enableCustomTagColors) {
                        ClickableSettingItem(
                            title = "自动生成标签颜色",
                            description = "根据主题色自动生成一组标签颜色",
                            onClick = {
                                val baseColor = if (themeColor != 0) Color(themeColor) else primaryColor
                                val generatedColors = TagColorGenerator.generateTagColors(baseColor)
                                tagColors.clear()
                                tagColors.addAll(generatedColors)
                                ThemeConfig.saveCustomTagColors(tagColors.toList())
                                listVersion++
                            },
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "自动生成",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        )

                        ClickableSettingItem(
                            title = "管理标签颜色",
                            description = if (tagColors.isEmpty()) "暂无自定义颜色" else "已设置 ${tagColors.size} 个颜色",
                            onClick = { showTagColorManagement = true }
                        )

                        if (tagColors.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                tagColors.forEach { colorPair ->
                                    Box(
                                        modifier = Modifier
                                            .width(48.dp)
                                            .padding(vertical = 4.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(colorPair.bgColor))
                                            .border(
                                                1.dp,
                                                Color(colorPair.textColor),
                                                RoundedCornerShape(8.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "A",
                                            color = Color(colorPair.textColor),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
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
                    nightPrimaryColorValue.value = color
                    ThemeConfig.cNPrimary = color
                } else {
                    primaryColorValue.value = color
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

        // Tag color management dialog
        AppAlertDialog(
            data = if (showTagColorManagement) "tagColorManagement" else null,
            onDismissRequest = { showTagColorManagement = false },
            title = "管理标签颜色",
            content = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(tagColors.size) { index ->
                        val colorPair = tagColors[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(colorPair.bgColor))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "标签 ${index + 1}",
                                color = Color(colorPair.textColor),
                                modifier = Modifier.weight(1f)
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                MediumOutlinedIconButton(
                                    onClick = {
                                        editingTagColorIndex = index
                                        editingTagTextColor = colorPair.textColor
                                        showTagColorPicker = true
                                    },
                                    imageVector = Icons.Default.Edit
                                )
                                MediumOutlinedIconButton(
                                    onClick = {
                                        tagColors.removeAt(index)
                                        ThemeConfig.saveCustomTagColors(tagColors.toList())
                                        listVersion++
                                    },
                                    imageVector = Icons.Default.Delete
                                )
                            }
                        }
                    }
                    item {
                        MediumOutlinedIconButton(
                            onClick = {
                                tagColors.add(TagColorPair(0, 0))
                                editingTagColorIndex = tagColors.size - 1
                                editingTagTextColor = 0
                                showTagColorPicker = true
                            },
                            imageVector = Icons.Default.Add,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmText = stringResource(android.R.string.ok),
            onConfirm = {
                ThemeConfig.saveCustomTagColors(tagColors.toList())
                listVersion++
                showTagColorManagement = false
            },
            dismissText = stringResource(android.R.string.cancel),
            onDismiss = { showTagColorManagement = false }
        )

        // Tag color picker
        ColorPickerSheet(
            show = showTagColorPicker && editingTagColorIndex >= 0,
            initialColor = editingTagTextColor,
            onDismissRequest = { showTagColorPicker = false },
            onColorSelected = { selectedColor ->
                if (editingTagColorIndex >= 0 && editingTagColorIndex < tagColors.size) {
                    val hsl = FloatArray(3)
                    ColorUtils.colorToHSL(selectedColor, hsl)
                    hsl[1] = (hsl[1] * 0.4f).coerceAtMost(0.35f)
                    hsl[2] = 0.90f
                    val bgColor = Color.hsl(hsl[0], hsl[1], hsl[2]).toArgb()
                    tagColors[editingTagColorIndex] = TagColorPair(
                        textColor = selectedColor,
                        bgColor = bgColor
                    )
                    showTagColorPicker = false
                }
            }
        )
    }
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
