package io.legado.app.ui.config.otherConfig

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.DirectLinkUpload
import io.legado.app.lib.dialogs.selector
import io.legado.app.ui.widget.components.checkBox.CheckboxItem
import io.legado.app.ui.widget.components.modalBottomSheet.GlassModalBottomSheet
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getClipText
import io.legado.app.utils.sendToClip
import io.legado.app.utils.toastOnUi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectLinkUploadBottomSheet(
    viewModel: OtherConfigViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showMenu by remember { mutableStateOf(false) }
    var showTestResult by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.initDirectLinkRule()
    }

    GlassModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.direct_link_upload_config),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("导入默认") },
                            leadingIcon = { Icon(Icons.Default.Download, null) },
                            onClick = {
                                showMenu = false
                                context.selector(DirectLinkUpload.defaultRules) { _, rule, _ ->
                                    viewModel.upView(rule)
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.copy_rule)) },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                            onClick = {
                                showMenu = false
                                val rule = DirectLinkUpload.Rule(
                                    viewModel.uploadUrl,
                                    viewModel.downloadUrlRule,
                                    viewModel.summary,
                                    viewModel.compress
                                )
                                context.sendToClip(GSON.toJson(rule))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.paste_rule)) },
                            leadingIcon = { Icon(Icons.Default.ContentPaste, null) },
                            onClick = {
                                showMenu = false
                                runCatching {
                                    context.getClipText()?.let {
                                        val rule =
                                            GSON.fromJsonObject<DirectLinkUpload.Rule>(it)
                                                .getOrThrow()
                                        viewModel.upView(rule)
                                    }
                                }.onFailure {
                                    context.toastOnUi("剪贴板为空或格式不对")
                                }
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = viewModel.uploadUrl,
                onValueChange = { viewModel.uploadUrl = it },
                label = { Text(stringResource(R.string.upload_url)) },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = viewModel.downloadUrlRule,
                onValueChange = { viewModel.downloadUrlRule = it },
                label = { Text(stringResource(R.string.download_url_rule)) },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = viewModel.summary,
                onValueChange = { viewModel.summary = it },
                label = { Text(stringResource(R.string.summary)) },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            CheckboxItem(
                title = stringResource(R.string.is_compress),
                color = MaterialTheme.colorScheme.surface,
                checked = viewModel.compress,
                onCheckedChange = { viewModel.compress = it }
            )

            Spacer(Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = {
                    viewModel.testRule { result -> showTestResult = result }
                }) {
                    Text("测试")
                }
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
                Button(onClick = {
                    if (viewModel.saveDirectLinkRule()) {
                        onDismiss()
                    } else {
                        context.toastOnUi("请填写完整信息")
                    }
                }) {
                    Text(stringResource(R.string.ok))
                }
            }
        }
    }

    showTestResult?.let { result ->
        AlertDialog(
            onDismissRequest = { showTestResult = null },
            title = { Text("Result") },
            text = { Text(result) },
            confirmButton = {
                TextButton(onClick = {
                    showTestResult = null
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { context.sendToClip(result) }) { Text(stringResource(R.string.copy_text)) }
            }
        )
    }
}
