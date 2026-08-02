package io.legado.app.ui.book.source.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
    timeoutSeconds: Long,
    checkSearch: Boolean,
    checkDiscovery: Boolean,
    checkInfo: Boolean,
    checkCategory: Boolean,
    checkContent: Boolean,
    onTimeoutChange: (Long) -> Unit,
    onCheckSearchChange: (Boolean) -> Unit,
    onCheckDiscoveryChange: (Boolean) -> Unit,
    onCheckInfoChange: (Boolean) -> Unit,
    onCheckCategoryChange: (Boolean) -> Unit,
    onCheckContentChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
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
                    value = timeoutSeconds.toFloat(),
                    defaultValue = 180f,
                    onValueChange = { onTimeoutChange(it.toLong()) },
                    valueRange = 0f..300f,
                )
            }


            Spacer(modifier = Modifier.padding(8.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CheckboxItem(
                    title = stringResource(R.string.search),
                    checked = checkSearch,
                    onCheckedChange = onCheckSearchChange
                )

                CheckboxItem(
                    title = stringResource(R.string.discovery),
                    checked = checkDiscovery,
                    onCheckedChange = onCheckDiscoveryChange
                )



                CheckboxItem(
                    title = stringResource(R.string.source_tab_info),
                    checked = checkInfo,
                    onCheckedChange = onCheckInfoChange
                )


                CheckboxItem(
                    title = stringResource(R.string.chapter_list),
                    checked = checkCategory,
                    enabled = checkInfo,
                    onCheckedChange = onCheckCategoryChange
                )


                CheckboxItem(
                    title = stringResource(R.string.source_tab_content),
                    checked = checkContent,
                    enabled = checkCategory,
                    onCheckedChange = onCheckContentChange
                )
            }

            ConfirmDismissButtonsRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                onDismiss = onDismiss,
                onConfirm = onConfirm,
                dismissText = stringResource(R.string.cancel),
                confirmText = stringResource(R.string.ok)
            )
        }
    }
}
