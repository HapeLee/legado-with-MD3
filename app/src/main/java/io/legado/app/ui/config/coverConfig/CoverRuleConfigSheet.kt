package io.legado.app.ui.config.coverConfig

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.model.BookCover
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.InputSettingItem
import io.legado.app.ui.widget.components.text.AppText
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
    var enable by remember { mutableStateOf(false) }
    var searchUrl by remember { mutableStateOf("") }
    var coverRule by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val rule = withContext(Dispatchers.IO) {
            BookCover.getCoverRule()
        }
        enable = rule.enable
        searchUrl = rule.searchUrl
        coverRule = rule.coverRule
    }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            AppText(
                text = stringResource(R.string.cover_rule),
                style = LegadoTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = enable,
                    onCheckedChange = { enable = it }
                )
                AppText(text = stringResource(R.string.enable))
            }

            InputSettingItem(
                title = stringResource(R.string.search_via_url),
                value = searchUrl,
                onConfirm = { searchUrl = it }
            )

            InputSettingItem(
                title = stringResource(R.string.cover_rule_edit),
                value = coverRule,
                onConfirm = { coverRule = it }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = {
                    BookCover.delCoverRule()
                    onDismissRequest()
                }) {
                    AppText(
                        stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                OutlinedButton(onClick = onDismissRequest) {
                    AppText(stringResource(R.string.cancel))
                }

                Spacer(modifier = Modifier.padding(horizontal = 4.dp))

                Button(onClick = {
                    if (searchUrl.isBlank() || coverRule.isBlank()) {
                        appCtx.toastOnUi("搜索url和cover规则不能为空")
                    } else {
                        BookCover.CoverRule(enable, searchUrl, coverRule).let { config ->
                            BookCover.saveCoverRule(config)
                        }
                        onDismissRequest()
                    }
                }) {
                    AppText(stringResource(R.string.ok))
                }
            }
        }
    }
}
