package io.legado.app.ui.config.themeConfig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import io.legado.app.help.config.TagColorGenerator
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.MediumOutlinedIconButton
import io.legado.app.ui.widget.components.button.SmallIconButton
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.dialog.ColorPickerSheet
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelColorManageSheet(
    show: Boolean,
    onDismissRequest: () -> Unit
) {
    val primaryColor = LegadoTheme.colorScheme.primary
    val themeColor = ThemeConfig.themeColor
    val tagColors = remember(show) {
        ThemeConfig.getCustomTagColors().toMutableStateList()
    }
    var showColorPicker by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableIntStateOf(-1) }
    var editingTextColor by remember { mutableIntStateOf(0) }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = "管理标签颜色",
        startAction = {
            MediumOutlinedIconButton(
                onClick = {
                    val baseColor = if (themeColor != 0) Color(themeColor) else primaryColor
                    val generatedColors = TagColorGenerator.generateTagColors(baseColor)
                    tagColors.clear()
                    tagColors.addAll(generatedColors)
                    ThemeConfig.saveCustomTagColors(tagColors)
                },
                imageVector = Icons.Default.AutoAwesome
            )
        },
        endAction = {
            MediumOutlinedIconButton(
                onClick = {
                    tagColors.add(TagColorPair(0, 0))
                    editingIndex = tagColors.size - 1
                    editingTextColor = 0
                    showColorPicker = true
                },
                imageVector = Icons.Default.Add
            )
        }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tagColors.size) { index ->
                val colorPair = tagColors[index]
                val label = "标签 ${index + 1}"
                NormalCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = LegadoTheme.colorScheme.onSheetContent
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextCard(
                            text = label,
                            backgroundColor = if (colorPair.bgColor != 0) Color(colorPair.bgColor) else LegadoTheme.colorScheme.surfaceContainerHighest,
                            contentColor = if (colorPair.textColor != 0) Color(colorPair.textColor) else LegadoTheme.colorScheme.primary,
                            cornerRadius = 4.dp,
                            horizontalPadding = 8.dp,
                            verticalPadding = 4.dp,
                            textStyle = LegadoTheme.typography.labelSmall
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            SmallIconButton(
                                onClick = {
                                    editingIndex = index
                                    editingTextColor = colorPair.textColor
                                    showColorPicker = true
                                },
                                imageVector = Icons.Default.Edit
                            )
                            SmallIconButton(
                                onClick = {
                                    tagColors.removeAt(index)
                                    ThemeConfig.saveCustomTagColors(tagColors)
                                },
                                imageVector = Icons.Default.Delete
                            )
                        }
                    }
                }
            }
        }
    }

    if (showColorPicker && editingIndex in tagColors.indices) {
        ColorPickerSheet(
            show = true,
            initialColor = editingTextColor,
            onDismissRequest = { showColorPicker = false },
            onColorSelected = { selectedColor ->
                val hsl = FloatArray(3)
                ColorUtils.colorToHSL(selectedColor, hsl)
                hsl[1] = (hsl[1] * 0.4f).coerceAtMost(0.35f)
                hsl[2] = 0.90f
                val bgColor = Color.hsl(hsl[0], hsl[1], hsl[2]).toArgb()
                tagColors[editingIndex] = TagColorPair(
                    textColor = selectedColor,
                    bgColor = bgColor
                )
                ThemeConfig.saveCustomTagColors(tagColors)
                showColorPicker = false
            }
        )
    }
}
