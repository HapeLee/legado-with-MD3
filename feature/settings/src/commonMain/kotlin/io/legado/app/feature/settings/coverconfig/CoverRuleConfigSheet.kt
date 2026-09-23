package io.legado.app.feature.settings.coverconfig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.legado.app.feature.settings.res.Res
import io.legado.app.feature.settings.res.cover_rule
import io.legado.app.feature.settings.res.cover_rule_edit
import io.legado.app.feature.settings.res.enable
import io.legado.app.feature.settings.res.restore_default
import io.legado.app.feature.settings.res.save
import io.legado.app.feature.settings.res.search_via_url
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.checkBox.CheckboxItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import org.jetbrains.compose.resources.stringResource

/**
 * 封面自定义规则编辑弹层。
 *
 * M5-13a：从 `:app` 的 `ui/config/coverConfig` 迁来。**唯一改动**是资源访问
 * （`androidx.compose.ui.res.stringResource` → CMP 的、`R.string.*` → `Res.string.*`，6 条），
 * 结构逐字保留（含原文那两处不齐的缩进）。
 *
 * 它已是**共享契约**（`CoverConfigIntent` / `CoverRuleUiState`）的纯消费者 ⇒ 自己没有任何
 * 平台依赖，是封面设置页本体迁移的前置件之一（另一个是 `CoverAlbumSelectSheet`，
 * 那个要用 coil 渲染缩略图，涉及给本模块加依赖 ⇒ 单独一片处理）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoverRuleConfigSheet(
    show: Boolean,
    state: CoverRuleUiState,
    onIntent: (CoverConfigIntent) -> Unit,
    onDismissRequest: () -> Unit,
) {
    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(Res.string.cover_rule),
        startAction = {
            MediumTonalButton(
            icon = Icons.Default.SettingsBackupRestore,
            contentDescription = stringResource(Res.string.restore_default),
                onClick = {
                    onIntent(CoverConfigIntent.RestoreDefaultRule)
                }
            )
        },
        endAction = {
            MediumTonalButton(
            icon = Icons.Default.Save,
            contentDescription = stringResource(Res.string.save),
                onClick = {
                    onIntent(CoverConfigIntent.SaveRule)
                }
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CheckboxItem(
                title = stringResource(Res.string.enable),
                checked = state.enabled,
                onCheckedChange = { onIntent(CoverConfigIntent.SetRuleEnabled(it)) }
            )

            AppTextField(
                value = state.searchUrl,
                onValueChange = { onIntent(CoverConfigIntent.SetRuleSearchUrl(it)) },
                label = stringResource(Res.string.search_via_url),
                modifier = Modifier.fillMaxWidth()
            )

            AppTextField(
                value = state.coverRule,
                onValueChange = { onIntent(CoverConfigIntent.SetRuleExpression(it)) },
                label = stringResource(Res.string.cover_rule_edit),
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
        }
    }
}
