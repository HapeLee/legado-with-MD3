package io.legado.app.ui.config.otherConfig

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.lib.dialogs.selector
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.ConfirmDismissButtonsRow
import io.legado.app.ui.widget.components.button.series.MediumPlainButton
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
    state: OtherConfigUiState,
    onIntent: (OtherConfigIntent) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    AppModalBottomSheet(
        show = show,
        title = stringResource(R.string.direct_link_upload_config),
        startAction = {
            MediumPlainButton(
                onClick = {
                    onIntent(OtherConfigIntent.TestDirectLinkRule)
                },
                icon = Icons.Default.Checklist,
                contentDescription = stringResource(R.string.test)
            )
        },
        endAction = {
            Box {
                MediumPlainButton(
                    onClick = { showMenu = true },
                icon = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.more_menu)
                )
                RoundDropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    RoundDropdownMenuItem(
                        text = stringResource(R.string.import_default_rule),
                        leadingIcon = { Icon(Icons.Default.Download, null) },
                        onClick = {
                            showMenu = false
                            context.selector(state.directRulePresets) { _, rule, _ ->
                                onIntent(
                                    OtherConfigIntent.DirectRuleChanged(
                                        uploadUrl = rule.uploadUrl,
                                        downloadUrlRule = rule.downloadUrlRule,
                                        summary = rule.summary,
                                        compress = rule.compress,
                                    )
                                )
                            }
                        }
                    )
                    RoundDropdownMenuItem(
                        text = stringResource(R.string.copy_rule),
                        leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                        onClick = {
                            showMenu = false
                            val rule = DirectLinkRuleUi(
                                uploadUrl = state.directUploadUrl,
                                downloadUrlRule = state.directDownloadUrlRule,
                                summary = state.directSummary,
                                compress = state.directCompress,
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
                                        GSON.fromJsonObject<DirectLinkRuleUi>(it)
                                            .getOrThrow()
                                    onIntent(
                                        OtherConfigIntent.DirectRuleChanged(
                                            uploadUrl = rule.uploadUrl,
                                            downloadUrlRule = rule.downloadUrlRule,
                                            summary = rule.summary,
                                            compress = rule.compress,
                                        )
                                    )
                                }
                            }.onFailure {
                                context.toastOnUi(R.string.clipboard_empty_or_invalid)
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
                value = state.directUploadUrl,
                onValueChange = { onIntent(OtherConfigIntent.DirectUploadUrlChanged(it)) },
                backgroundColor = LegadoTheme.colorScheme.onSheetContent,
                label = stringResource(R.string.upload_url),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            AppTextField(
                value = state.directDownloadUrlRule,
                onValueChange = { onIntent(OtherConfigIntent.DirectDownloadUrlRuleChanged(it)) },
                backgroundColor = LegadoTheme.colorScheme.onSheetContent,
                label = stringResource(R.string.download_url_rule),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            AppTextField(
                value = state.directSummary,
                onValueChange = { onIntent(OtherConfigIntent.DirectSummaryChanged(it)) },
                backgroundColor = LegadoTheme.colorScheme.onSheetContent,
                label = stringResource(R.string.summary),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            CheckboxItem(
                title = stringResource(R.string.is_compress),
                color = LegadoTheme.colorScheme.onSheetContent,
                checked = state.directCompress,
                onCheckedChange = { onIntent(OtherConfigIntent.DirectCompressChanged(it)) }
            )

            Spacer(Modifier.height(24.dp))

            ConfirmDismissButtonsRow(
                modifier = Modifier.fillMaxWidth(),
                onDismiss = onDismiss,
                onConfirm = { onIntent(OtherConfigIntent.ConfirmDirectLinkRule) },
                dismissText = stringResource(R.string.cancel),
                confirmText = stringResource(R.string.ok)
            )
        }
    }

    AppAlertDialog(
        data = state.directTestResult,
        onDismissRequest = { onIntent(OtherConfigIntent.DismissDirectTestResult) },
        title = "Result",
        content = { result ->
            SelectionContainer {
                AppText(text = result)
            }
        },
        confirmText = stringResource(R.string.ok),
        onConfirm = {
            onIntent(OtherConfigIntent.DismissDirectTestResult)
        },
        dismissText = stringResource(R.string.copy_text),
        onDismiss = {
            state.directTestResult?.let { context.sendToClip(it) }
        }
    )
}
