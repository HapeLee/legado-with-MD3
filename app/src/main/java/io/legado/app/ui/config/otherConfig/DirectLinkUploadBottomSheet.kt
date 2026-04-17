package io.legado.app.ui.config.otherConfig

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.DirectLinkUpload
import io.legado.app.lib.dialogs.selector
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.ConfirmDismissButtonsRow
import io.legado.app.ui.widget.components.button.MediumIconButton
import io.legado.app.ui.widget.components.checkBox.CheckboxItem
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getClipText
import io.legado.app.utils.sendToClip
import io.legado.app.utils.toastOnUi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectLinkUploadBottomSheet(
    show: Boolean,
    viewModel: OtherConfigViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showTestResult by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.initDirectLinkRule()
    }

    AppModalBottomSheet(
        show = show,
        title = stringResource(R.string.direct_link_upload_config),
        startAction = {
            MediumIconButton(
                onClick = {
                    viewModel.testRule { result -> showTestResult = result }
                },
                imageVector = Icons.Default.Checklist
            )
        },
        endAction = {
            Box {
                MediumIconButton(
                    onClick = { showMenu = true },
                    imageVector = Icons.Default.MoreVert
                )
                RoundDropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    RoundDropdownMenuItem(
                        text = "导入默认",
                        leadingIcon = { Icon(Icons.Default.Download, null) },
                        onClick = {
                            showMenu = false
                            context.selector(DirectLinkUpload.defaultRules) { _, rule, _ ->
                                viewModel.upView(rule)
                            }
                        }
                    )
                    RoundDropdownMenuItem(
                        text = stringResource(R.string.copy_rule),
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
                    RoundDropdownMenuItem(
                        text = stringResource(R.string.paste_rule),
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
            }

        },
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            AppTextField(
                value = viewModel.uploadUrl,
                onValueChange = { viewModel.uploadUrl = it },
                backgroundColor = LegadoTheme.colorScheme.onSheetContent,
                label = stringResource(R.string.upload_url),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            AppTextField(
                value = viewModel.downloadUrlRule,
                onValueChange = { viewModel.downloadUrlRule = it },
                backgroundColor = LegadoTheme.colorScheme.onSheetContent,
                label = stringResource(R.string.download_url_rule),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            AppTextField(
                value = viewModel.summary,
                onValueChange = { viewModel.summary = it },
                backgroundColor = LegadoTheme.colorScheme.onSheetContent,
                label = stringResource(R.string.summary),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            CheckboxItem(
                title = stringResource(R.string.is_compress),
                color = LegadoTheme.colorScheme.onSheetContent,
                checked = viewModel.compress,
                onCheckedChange = { viewModel.compress = it }
            )

            Spacer(Modifier.height(24.dp))

            ConfirmDismissButtonsRow(
                modifier = Modifier.fillMaxWidth(),
                onDismiss = onDismiss,
                onConfirm = {
                    if (viewModel.saveDirectLinkRule()) {
                        onDismiss()
                    } else {
                        context.toastOnUi("请填写完整信息")
                    }
                },
                dismissText = stringResource(R.string.cancel),
                confirmText = stringResource(R.string.ok)
            )
        }
    }

    AppAlertDialog(
        data = showTestResult,
        onDismissRequest = { showTestResult = null },
        title = "Result",
        content = { result ->
            SelectionContainer {
                AppText(text = result)
            }
        },
        confirmText = stringResource(R.string.ok),
        onConfirm = {
            showTestResult = null
        },
        dismissText = stringResource(R.string.copy_text),
        onDismiss = {
            showTestResult?.let { context.sendToClip(it) }
        }
    )
}
