package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.data.repository.ReadSettingsRepository
import io.legado.app.model.ReadBook
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun EffectiveReplacesSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onOpenReplaceEditor: (id: Long, pattern: String?) -> Unit,
    onReplaceRuleChanged: () -> Unit,
) {
    val effectiveRules = remember {
        ReadBook.curTextChapter?.effectiveReplaceRules ?: emptyList()
    }
    val scope = rememberCoroutineScope()
    val readSettingsRepository: ReadSettingsRepository = koinInject()
    val preferences by readSettingsRepository.preferences.collectAsStateWithLifecycle(
        initialValue = io.legado.app.data.repository.ReadPreferences()
    )
    val showChineseConvert = preferences.chineseConverterType > 0
    val chineseConvert = remember { ReplaceRule(0, "繁简转换") }
    val items = remember(effectiveRules, showChineseConvert) {
        if (showChineseConvert) effectiveRules + chineseConvert else effectiveRules
    }

    var isEdited by remember { mutableStateOf(false) }
    var showChineseConvertDialog by remember { mutableStateOf(false) }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = {
            if (isEdited) onReplaceRuleChanged()
            onDismissRequest()
        },
        title = stringResource(R.string.effective_replaces),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(items, key = { it.id }) { rule ->
                Text(
                    text = rule.name,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (rule == chineseConvert) {
                                showChineseConvertDialog = true
                            } else {
                                onOpenReplaceEditor(rule.id, rule.pattern)
                            }
                        }
                        .padding(vertical = 12.dp),
                )
                HorizontalDivider()
            }
        }
    }

    if (showChineseConvertDialog) {
        val modes = stringArrayResource(R.array.chinese_mode)
        AlertDialog(
            onDismissRequest = { showChineseConvertDialog = false },
            containerColor = LegadoTheme.colorScheme.surfaceContainer,
            title = { Text(stringResource(R.string.chinese_converter)) },
            text = {
                Column {
                    modes.forEachIndexed { index, mode ->
                        Text(
                            text = mode,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (preferences.chineseConverterType != index) {
                                        scope.launch {
                                            readSettingsRepository.setChineseConverterType(index)
                                        }
                                        isEdited = true
                                    }
                                    showChineseConvertDialog = false
                                }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showChineseConvertDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
