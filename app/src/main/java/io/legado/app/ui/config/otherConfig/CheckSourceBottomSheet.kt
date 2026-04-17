package io.legado.app.ui.config.otherConfig

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.ConfirmDismissButtonsRow
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.checkBox.CheckboxGroupContainer
import io.legado.app.ui.widget.components.checkBox.CheckboxItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.SliderSettingItem
import io.legado.app.ui.widget.components.text.AppText
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckSourceBottomSheet(
    show: Boolean,
    viewModel: OtherConfigViewModel = koinViewModel(),
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

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
                    value = viewModel.checkSourceTimeout.toFloat(),
                    defaultValue = 180f,
                    onValueChange = { viewModel.checkSourceTimeout = it.toLong() },
                    valueRange = 0f..300f,
                )
            }


            Spacer(modifier = Modifier.padding(8.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CheckboxItem(
                    title = stringResource(R.string.search),
                    checked = viewModel.checkSearch,
                    onCheckedChange = {
                        viewModel.checkSearch = it
                        if (!it && !viewModel.checkDiscovery) {
                            viewModel.checkDiscovery = true
                        }
                    }
                )

                CheckboxItem(
                    title = stringResource(R.string.discovery),
                    checked = viewModel.checkDiscovery,
                    onCheckedChange = {
                        viewModel.checkDiscovery = it
                        if (!it && !viewModel.checkSearch) {
                            viewModel.checkSearch = true
                        }
                    }
                )



                CheckboxItem(
                    title = stringResource(R.string.source_tab_info),
                    checked = viewModel.checkInfo,
                    onCheckedChange = {
                        viewModel.checkInfo = it
                        if (!it) {
                            viewModel.checkCategory = false
                            viewModel.checkContent = false
                        }
                    }
                )


                CheckboxItem(
                    title = stringResource(R.string.chapter_list),
                    checked = viewModel.checkCategory,
                    enabled = viewModel.checkInfo,
                    onCheckedChange = {
                        viewModel.checkCategory = it
                        if (!it) viewModel.checkContent = false
                    }
                )


                CheckboxItem(
                    title = stringResource(R.string.source_tab_content),
                    checked = viewModel.checkContent,
                    enabled = viewModel.checkCategory,
                    onCheckedChange = { viewModel.checkContent = it }
                )
            }

            ConfirmDismissButtonsRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                onDismiss = onDismiss,
                onConfirm = {
                    if (viewModel.saveCheckSourceConfig()) {
                        onDismiss()
                    } else {
                        Toast.makeText(context, R.string.error, Toast.LENGTH_SHORT).show()
                    }
                },
                dismissText = stringResource(R.string.cancel),
                confirmText = stringResource(R.string.ok)
            )
        }
    }
}