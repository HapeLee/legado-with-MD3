package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import io.legado.app.R
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.ui.book.read.ConfigUpdate
import io.legado.app.ui.book.read.ReadBookColorPickerIds
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.dialog.ColorPickerSheet
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.TinyClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.TinyColorSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySliderSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySwitchSettingItem

@Composable
fun BgTextConfigSheet(
    onDismissRequest: () -> Unit,
    onIntent: (ReadBookIntent) -> Unit,
    onSelectImage: () -> Unit,
    onImportConfig: () -> Unit,
    onExportConfig: () -> Unit,
) {
    var styleName by remember { mutableStateOf(ReadBookConfig.durConfig.name.ifBlank { "文字" }) }
    var darkStatusIcon by remember { mutableStateOf(ReadBookConfig.durConfig.curStatusIconDark()) }
    var bgAlpha by remember { mutableFloatStateOf(ReadBookConfig.bgAlpha.toFloat()) }
    var bgColor by remember {
        mutableIntStateOf(
            ReadBookConfig.durConfig.run {
                if (curBgType() == 0) curBgStr().toColorInt() else "#015A86".toColorInt()
            }
        )
    }
    var showColorPicker by remember { mutableStateOf(false) }

    AppModalBottomSheet(
        show = true,
        onDismissRequest = {
            onIntent(ReadBookIntent.SaveReadStyleConfig)
            onDismissRequest()
        },
        title = stringResource(R.string.style_name),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // Style name section
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.style_name),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = styleName,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
                Row {
                    IconButton(onClick = {
                        // TODO: Show edit name dialog
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_edit),
                            contentDescription = stringResource(R.string.edit),
                        )
                    }
                    IconButton(onClick = {
                        onIntent(ReadBookIntent.DeleteCurrentReadStyleConfig)
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_clear_all),
                            contentDescription = stringResource(R.string.delete),
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Action buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ActionCard(
                    title = stringResource(R.string.restore),
                    imageVector = Icons.Default.Refresh,
                    modifier = Modifier.weight(1f),
                    onClick = { /* TODO: Show restore presets dialog */ },
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
                    darkStatusIcon = it
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.StatusIconDark(it)))
                },
            )

            TinyColorSettingItem(
                title = stringResource(R.string.bg_color),
                colorValue = bgColor,
                onClick = { showColorPicker = true },
            )

            TinySliderSettingItem(
                title = stringResource(R.string.bg_alpha),
                value = bgAlpha,
                valueRange = 0f..100f,
                steps = 99,
                onValueChange = {
                    bgAlpha = it
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.BgAlpha(it.toInt())))
                },
            )

            TinyClickableSettingItem(
                title = stringResource(R.string.bg_image),
                description = stringResource(R.string.select_image),
                onClick = onSelectImage,
            )

            // TODO: Add background image grid from assets
        }
    }

    if (showColorPicker) {
        ColorPickerSheet(
            show = true,
            initialColor = bgColor,
            onDismissRequest = { showColorPicker = false },
            onColorSelected = { color ->
                bgColor = color
                onIntent(ReadBookIntent.ColorSelected(ReadBookColorPickerIds.BG_COLOR, color))
                showColorPicker = false
            },
        )
    }
}

@Composable
private fun ActionCard(
    title: String,
    imageVector: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    NormalCard(
        onClick = onClick,
        modifier = modifier,
        containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
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
                tint = LegadoTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = title,
                style = LegadoTheme.typography.labelSmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
