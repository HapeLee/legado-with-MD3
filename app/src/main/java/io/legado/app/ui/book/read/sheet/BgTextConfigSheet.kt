package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import io.legado.app.R
import io.legado.app.help.DefaultData
import io.legado.app.ui.book.read.ConfigUpdate
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.book.read.ReadBookStyleConfig
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.dialog.ColorPickerSheet
import io.legado.app.ui.widget.components.dialog.TextListInputDialog
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.TinyBgImageModeSettingItem
import io.legado.app.ui.widget.components.settingItem.TinyColorModeSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySliderSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySwitchSettingItem
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.hexString

@Composable
fun BgTextConfigSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onIntent: (ReadBookIntent) -> Unit,
    onSelectImage: () -> Unit,
    onSelectImageForMode: (isNight: Boolean) -> Unit,
    onImportConfig: () -> Unit,
    onExportConfig: () -> Unit,
    styleConfig: ReadBookStyleConfig = ReadBookStyleConfig(),
) {
    // Derive values directly from styleConfig (reactive state)
    val styleName = styleConfig.styleName
    val darkStatusIcon = styleConfig.darkStatusIcon
    val bgAlpha = styleConfig.bgAlpha
    val dayBgColor = if (styleConfig.bgType == 0) {
        runCatching { styleConfig.bgStr.toColorInt() }.getOrDefault(0xFFEEEEEE.toInt())
    } else 0
    val nightBgColor = if (styleConfig.bgTypeNight == 0) {
        runCatching { styleConfig.bgStrNight.toColorInt() }.getOrDefault(0xFF000000.toInt())
    } else 0
    val dayBgImage = if (styleConfig.bgType != 0) styleConfig.bgStr else null
    val nightBgImage = if (styleConfig.bgTypeNight != 0) styleConfig.bgStrNight else null

    var showColorPicker by remember { mutableStateOf(false) }
    var colorPickerIsNight by remember { mutableStateOf(false) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var showPresetDialog by remember { mutableStateOf(false) }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = {
            onIntent(ReadBookIntent.SaveReadStyleConfig)
            onDismissRequest()
        },
        title = styleName,
        contentWindowInsets = { WindowInsets.navigationBars },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // Action buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ActionCard(
                    title = stringResource(R.string.edit),
                    imageVector = Icons.Default.Edit,
                    modifier = Modifier.weight(1f),
                    onClick = { showEditNameDialog = true },
                )
                ActionCard(
                    title = stringResource(R.string.delete),
                    imageVector = Icons.Default.Delete,
                    modifier = Modifier.weight(1f),
                    enabled = styleConfig.styleSelect >= 5,
                    onClick = { onIntent(ReadBookIntent.DeleteCurrentReadStyleConfig) },
                )
                ActionCard(
                    title = stringResource(R.string.restore),
                    imageVector = Icons.Default.Refresh,
                    modifier = Modifier.weight(1f),
                    onClick = { showPresetDialog = true },
                )
                ActionCard(
                    title = stringResource(R.string.import_str),
                    imageVector = Icons.Default.Download,
                    modifier = Modifier.weight(1f),
                    onClick = onImportConfig,
                )
                ActionCard(
                    title = stringResource(R.string.export_str),
                    imageVector = Icons.Default.Upload,
                    modifier = Modifier.weight(1f),
                    onClick = onExportConfig,
                )
            }

            Spacer(Modifier.height(12.dp))

            TinySwitchSettingItem(
                title = stringResource(R.string.dark_status_icon),
                checked = darkStatusIcon,
                onCheckedChange = {
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.StatusIconDark(it)))
                },
            )

            TinyColorModeSettingItem(
                title = stringResource(R.string.bg_color),
                dayColor = dayBgColor,
                nightColor = nightBgColor,
                onClickColor = { isNight ->
                    colorPickerIsNight = isNight
                    showColorPicker = true
                },
            )

            TinyBgImageModeSettingItem(
                title = stringResource(R.string.bg_image),
                dayBgImage = dayBgImage,
                nightBgImage = nightBgImage,
                onClickImage = { isNight ->
                    onSelectImageForMode(isNight)
                },
                onClearImage = { isNight ->
                    if (isNight) {
                        onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.BgTypeNight(0)))
                        onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.BgStrNight("#000000")))
                    } else {
                        onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.BgType(0)))
                        onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.BgStr("#EEEEEE")))
                    }
                },
            )

            TinySliderSettingItem(
                title = stringResource(R.string.bg_alpha),
                value = bgAlpha,
                valueRange = 0f..100f,
                steps = 99,
                onValueChange = {
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.BgAlpha(it.toInt())))
                },
            )

            // TODO: Add background image grid from assets
        }
    }

    run {
        val initialColor = if (colorPickerIsNight) nightBgColor else dayBgColor
        ColorPickerSheet(
            show = showColorPicker,
            initialColor = if (initialColor != 0) initialColor else if (colorPickerIsNight) 0xFF000000.toInt() else 0xFFEEEEEE.toInt(),
            onDismissRequest = { showColorPicker = false },
            onColorSelected = { color ->
                if (colorPickerIsNight) {
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.BgStrNight("#${color.hexString}")))
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.BgTypeNight(0)))
                } else {
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.BgStr("#${color.hexString}")))
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.BgType(0)))
                }
                showColorPicker = false
            },
        )
    }


    TextListInputDialog(
        show = showEditNameDialog,
        title = stringResource(R.string.style_name),
        hint = stringResource(R.string.style_name),
        initialValue = styleName,
        onDismissRequest = { showEditNameDialog = false },
        onConfirm = { newName ->
            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.StyleName(newName)))
            onIntent(ReadBookIntent.SaveReadStyleConfig)
            showEditNameDialog = false
        },
    )

    val presets = DefaultData.readConfigs
    AppAlertDialog(
        show = showPresetDialog,
        onDismissRequest = { showPresetDialog = false },
        title = stringResource(R.string.restore),
        content = {
            presets.forEachIndexed { index, preset ->
                val bgColor = runCatching { preset.bgStr.toColorInt() }
                    .getOrDefault(0xFFEEEEEE.toInt())
                val textColor = runCatching { preset.curTextColor() }
                    .getOrDefault(0xFF000000.toInt())
                NormalCard(
                    onClick = {
                        onIntent(ReadBookIntent.ApplyPresetTheme(index))
                        showPresetDialog = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(bgColor))
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(textColor))
                        )
                        Spacer(Modifier.width(12.dp))
                        AppText(
                            text = preset.name.ifBlank { "预设${index}" },
                            style = LegadoTheme.typography.labelMediumEmphasized
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun ActionCard(
    title: String,
    imageVector: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val contentAlpha = if (enabled) 1f else 0.38f
    NormalCard(
        onClick = if (enabled) onClick else null,
        modifier = modifier,
        containerColor = if (enabled) LegadoTheme.colorScheme.surfaceContainerLow else LegadoTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = null,
                tint = LegadoTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = title,
                style = LegadoTheme.typography.labelSmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
            )
        }
    }
}
