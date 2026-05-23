package io.legado.app.ui.main.homepage.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.gson.JsonParser
import io.legado.app.R
import io.legado.app.domain.model.HomepageModuleType
import io.legado.app.domain.model.ModuleDef
import io.legado.app.ui.main.homepage.HomepageViewModel
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.JsonConfigEditor
import io.legado.app.ui.widget.components.JsonRawEditor
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.divider.PillHeaderDivider
import io.legado.app.ui.widget.components.settingItem.CompactClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.DropdownListSettingItem
import io.legado.app.ui.widget.components.text.AppText

data class AddDialogPrefill(
    val title: String = "",
    val url: String = "",
    val type: String = "card"
)

@Composable
fun <T> AddCustomModuleDialog(
    data: T?,
    sourceUrl: String = "",
    targetSetId: String = "",
    prefillTitle: String = "",
    prefillUrl: String = "",
    prefillType: String = "card",
    prefillArgs: String = "",
    prefillLayoutConfig: String = "",
    canSelectInfinite: Boolean = true,
    onDismissRequest: () -> Unit,
    onConfirm: (ModuleDef) -> Unit,
) {
    var title by remember(data) { mutableStateOf(prefillTitle) }
    var url by remember(data) { mutableStateOf(prefillUrl) }
    var type by remember(data) { mutableStateOf(prefillType) }
    var args by remember(data) { mutableStateOf(prefillArgs) }
    var layoutConfig by remember(data) { mutableStateOf(prefillLayoutConfig) }
    var showRawLayoutConfig by remember(data) { mutableStateOf(false) }

    val hasVisualizableKeys = remember(layoutConfig) {
        runCatching {
            val jsonObject = JsonParser.parseString(layoutConfig).asJsonObject
            jsonObject.keySet().any { key ->
                key == "columns" || key == "rows"
            }
        }.getOrElse { false }
    }

    AppAlertDialog(
        data = data,
        onDismissRequest = onDismissRequest,
        title = if (prefillTitle.isEmpty()) stringResource(R.string.homepage_add_module) else stringResource(
            R.string.homepage_edit_module
        ),
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AppTextField(
                    value = title,
                    onValueChange = { title = it },
                    backgroundColor = LegadoTheme.colorScheme.onSheetContent,
                    label = stringResource(R.string.homepage_title_label),
                    modifier = Modifier.fillMaxWidth()
                )
                AppTextField(
                    value = url,
                    onValueChange = { url = it },
                    backgroundColor = LegadoTheme.colorScheme.onSheetContent,
                    label = "URL",
                    modifier = Modifier.fillMaxWidth()
                )
                val typeList = remember(canSelectInfinite) {
                    HomepageModuleType.entries.filter {
                        it != HomepageModuleType.Unknown && (canSelectInfinite || !HomepageViewModel.isInfinite(
                            it.key,
                            null
                        ))
                    }
                }

                GlassCard(
                    containerColor = LegadoTheme.colorScheme.onSheetContent
                ) {
                    DropdownListSettingItem(
                        title = stringResource(R.string.homepage_type_label),
                        selectedValue = type,
                        displayEntries = typeList.map { it.title }.toTypedArray(),
                        entryValues = typeList.map { it.key }.toTypedArray(),
                        onValueChange = { type = it }
                    )
                }

                if (HomepageViewModel.isInfinite(type, null) && !canSelectInfinite) {
                    AppText(
                        text = stringResource(R.string.homepage_module_duplicate_infinite),
                        color = LegadoTheme.colorScheme.error,
                        style = LegadoTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                AppTextField(
                    value = args,
                    onValueChange = { args = it },
                    backgroundColor = LegadoTheme.colorScheme.onSheetContent,
                    label = "Args (JSON)",
                    modifier = Modifier.fillMaxWidth()
                )

                PillHeaderDivider(
                    title = stringResource(R.string.homepage_layout_config_label)
                )

                if (hasVisualizableKeys) {
                    JsonConfigEditor(
                        jsonString = layoutConfig,
                        onJsonStringChange = { layoutConfig = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                    CompactClickableSettingItem(
                        title = stringResource(R.string.homepage_edit_raw_json),
                        onClick = { showRawLayoutConfig = !showRawLayoutConfig }
                    )
                    if (showRawLayoutConfig) {
                        JsonRawEditor(
                            value = layoutConfig,
                            onValueChange = { layoutConfig = it },
                            label = "LayoutConfig (JSON) RAW",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    JsonRawEditor(
                        value = layoutConfig,
                        onValueChange = { layoutConfig = it },
                        label = "LayoutConfig (JSON)",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        onConfirm = {
            onConfirm(
                ModuleDef(
                    title = title,
                    url = url,
                    type = type,
                    args = args,
                    layoutConfig = layoutConfig
                )
            )
        },
        confirmText = stringResource(R.string.dialog_confirm),
        dismissText = stringResource(R.string.dialog_cancel),
        onDismiss = onDismissRequest
    )
}
