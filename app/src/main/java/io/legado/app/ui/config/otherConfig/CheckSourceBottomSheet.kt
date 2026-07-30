package io.legado.app.ui.config.otherConfig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.widget.components.button.ConfirmDismissButtonsRow
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.checkBox.CheckboxItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.SliderSettingItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckSourceBottomSheet(
    show: Boolean,
    state: OtherConfigUiState,
    onIntent: (OtherConfigIntent) -> Unit,
    onDismiss: () -> Unit,
) {

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.check_source_config)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {

            GlassCard {
                SliderSettingItem(
                    title = stringResource(R.string.check_source_timeout),
                    value = state.checkSourceTimeoutSeconds.toFloat(),
                    defaultValue = 180f,
                    onValueChange = { onIntent(OtherConfigIntent.CheckSourceTimeoutChanged(it.toLong())) },
                    valueRange = 0f..300f,
                )
            }


            Spacer(modifier = Modifier.padding(8.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CheckboxItem(
                    title = stringResource(R.string.search),
                    checked = state.checkSearch,
                    onCheckedChange = { onIntent(OtherConfigIntent.CheckSearchChanged(it)) }
                )

                CheckboxItem(
                    title = stringResource(R.string.discovery),
                    checked = state.checkDiscovery,
                    onCheckedChange = { onIntent(OtherConfigIntent.CheckDiscoveryChanged(it)) }
                )



                CheckboxItem(
                    title = stringResource(R.string.source_tab_info),
                    checked = state.checkInfo,
                    onCheckedChange = { onIntent(OtherConfigIntent.CheckInfoChanged(it)) }
                )


                CheckboxItem(
                    title = stringResource(R.string.chapter_list),
                    checked = state.checkCategory,
                    enabled = state.checkInfo,
                    onCheckedChange = { onIntent(OtherConfigIntent.CheckCategoryChanged(it)) }
                )


                CheckboxItem(
                    title = stringResource(R.string.source_tab_content),
                    checked = state.checkContent,
                    enabled = state.checkCategory,
                    onCheckedChange = { onIntent(OtherConfigIntent.CheckContentChanged(it)) }
                )
            }

            ConfirmDismissButtonsRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                onDismiss = onDismiss,
                onConfirm = { onIntent(OtherConfigIntent.ConfirmCheckSource) },
                dismissText = stringResource(R.string.cancel),
                confirmText = stringResource(R.string.ok)
            )
        }
    }
}
