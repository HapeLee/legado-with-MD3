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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.DefaultData
import io.legado.app.model.BookCover
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.button.MediumIconButton
import io.legado.app.ui.widget.components.checkBox.CheckboxItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import splitties.init.appCtx

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoverRuleConfigSheet(
    show: Boolean,
    onDismissRequest: () -> Unit
) {
    var ruleState by remember { mutableStateOf<BookCover.CoverRule?>(null) }
    var enable by remember { mutableStateOf(false) }
    var searchUrl by remember { mutableStateOf("") }
    var coverRule by remember { mutableStateOf("") }

    LaunchedEffect(show) {
        if (show) {
            val rule = withContext(Dispatchers.IO) {
                BookCover.getCoverRule()
            }
            ruleState = rule
            enable = rule.enable
            searchUrl = rule.searchUrl
            coverRule = rule.coverRule
        }
    }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.cover_rule),
        startAction = {
            MediumIconButton(
                imageVector = Icons.Default.SettingsBackupRestore,
                onClick = {
                    DefaultData.coverRule.let {
                        enable = it.enable
                        searchUrl = it.searchUrl
                        coverRule = it.coverRule
                    }
                    appCtx.toastOnUi(R.string.restore_default)
                }
            )
        },
        endAction = {
            MediumIconButton(
                imageVector = Icons.Default.Save,
                onClick = {
                    if (searchUrl.isBlank() || coverRule.isBlank()) {
                        appCtx.toastOnUi(R.string.cover_rule_fields_required)
                    } else {
                        val newConfig = ruleState?.copy(
                            enable = enable,
                            searchUrl = searchUrl,
                            coverRule = coverRule
                        ) ?: BookCover.CoverRule(enable, searchUrl, coverRule)

                        if (newConfig == DefaultData.coverRule) {
                            BookCover.delCoverRule()
                        } else {
                            BookCover.saveCoverRule(newConfig)
                        }
                        onDismissRequest()
                    }
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
                checked = enable,
                onCheckedChange = { enable = it }
            )

            AppTextField(
                value = searchUrl,
                onValueChange = { searchUrl = it },
                label = stringResource(R.string.search_via_url),
                modifier = Modifier.fillMaxWidth()
            )

            AppTextField(
                value = coverRule,
                onValueChange = { coverRule = it },
                label = stringResource(R.string.cover_rule_edit),
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
        }
    }
}
