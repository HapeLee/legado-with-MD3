package io.legado.app.ui.config.coverConfig

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.button.series.MediumPlainButton
import io.legado.app.ui.widget.components.checkBox.CheckboxItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet

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
        title = stringResource(R.string.cover_rule),
        startAction = {
            MediumPlainButton(
            icon = Icons.Default.SettingsBackupRestore,
            contentDescription = stringResource(R.string.restore_default),
                onClick = {
                    onIntent(CoverConfigIntent.RestoreDefaultRule)
                }
            )
        },
        endAction = {
            MediumPlainButton(
            icon = Icons.Default.Save,
            contentDescription = stringResource(R.string.save),
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
                title = stringResource(R.string.enable),
                checked = state.enabled,
                onCheckedChange = { onIntent(CoverConfigIntent.SetRuleEnabled(it)) }
            )

            AppTextField(
                value = state.searchUrl,
                onValueChange = { onIntent(CoverConfigIntent.SetRuleSearchUrl(it)) },
                label = stringResource(R.string.search_via_url),
                modifier = Modifier.fillMaxWidth()
            )

            AppTextField(
                value = state.coverRule,
                onValueChange = { onIntent(CoverConfigIntent.SetRuleExpression(it)) },
                label = stringResource(R.string.cover_rule_edit),
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
        }
    }
}
