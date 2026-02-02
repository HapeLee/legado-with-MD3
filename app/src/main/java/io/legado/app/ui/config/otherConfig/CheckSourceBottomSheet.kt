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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.widget.components.checkBox.CheckboxGroupContainer
import io.legado.app.ui.widget.components.checkBox.CheckboxItem
import io.legado.app.ui.widget.components.modalBottomSheet.GlassModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.SliderSettingItem
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckSourceBottomSheet(
    viewModel: OtherConfigViewModel = koinViewModel(),
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    GlassModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.check_source_config),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            SliderSettingItem(
                title = stringResource(R.string.check_source_timeout),
                color = MaterialTheme.colorScheme.surface,
                value = viewModel.checkSourceTimeout.toFloat(),
                defaultValue = 180f,
                onValueChange = { viewModel.checkSourceTimeout = it.toLong() },
                valueRange = 0f..300f,
            )

            Spacer(modifier = Modifier.padding(8.dp))

            CheckboxGroupContainer(columns = 2) {
                item {
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
                }

                item {
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
                }
            }


            CheckboxGroupContainer(columns = 3) {
                item {
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
                }

                item {
                    CheckboxItem(
                        title = stringResource(R.string.chapter_list),
                        checked = viewModel.checkCategory,
                        enabled = viewModel.checkInfo,
                        onCheckedChange = {
                            viewModel.checkCategory = it
                            if (!it) viewModel.checkContent = false
                        }
                    )
                }

                item {
                    CheckboxItem(
                        title = stringResource(R.string.source_tab_content),
                        checked = viewModel.checkContent,
                        enabled = viewModel.checkCategory,
                        onCheckedChange = { viewModel.checkContent = it }
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
                Button(onClick = {
                    if (viewModel.saveCheckSourceConfig()) {
                        onDismiss()
                    } else {
                        Toast.makeText(context, R.string.error, Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text(stringResource(R.string.ok))
                }
            }
        }
    }
}