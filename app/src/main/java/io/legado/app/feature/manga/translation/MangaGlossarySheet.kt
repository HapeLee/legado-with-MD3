package io.legado.app.feature.manga.translation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.series.SmallPlainButton
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.collections.immutable.toImmutableList

@Composable
internal fun MangaGlossarySheet(show: Boolean, draft: String, enabled: Boolean,
    onDismiss: () -> Unit, onDraftChange: (String) -> Unit, onSave: () -> Unit) {
    val entries = remember(draft) { draft.lineSequence().filter { '=' in it }
        .map { it.substringBefore('=') to it.substringAfter('=') }.toList().toImmutableList() }
    var editing by rememberSaveable { mutableStateOf(false) }
    var index by rememberSaveable { mutableIntStateOf(-1) }
    var source by rememberSaveable { mutableStateOf("") }
    var target by rememberSaveable { mutableStateOf("") }
    fun publish(next: List<Pair<String, String>>) = onDraftChange(next.joinToString("\n") { "${it.first}=${it.second}" })
    AppModalBottomSheet(show = show, animateContentSize = false, title = stringResource(R.string.manga_translation_glossary), onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (entries.isEmpty()) AppText(stringResource(R.string.manga_glossary_empty), style = LegadoTheme.typography.bodyMedium)
            if (entries.isNotEmpty()) SplicedColumnGroup {
                entries.forEachIndexed { i, entry ->
                    ClickableSettingItem(title = entry.first, description = entry.second, onClick = {
                        if (enabled) { index = i; source = entry.first; target = entry.second; editing = true }
                    })
                }
            }
            SmallPlainButton(enabled = enabled, onClick = { index = -1; source = ""; target = ""; editing = true }, text = stringResource(R.string.manga_glossary_add))
            SmallPlainButton(enabled = enabled, onClick = onSave, text = stringResource(R.string.manga_translation_save_glossary))
        }
    }
    if (show && editing) AppAlertDialog(show = true, title = stringResource(R.string.manga_translation_glossary),
        onDismissRequest = { editing = false },
        confirmText = stringResource(R.string.manga_glossary_apply), dismissText = stringResource(R.string.cancel),
        confirmEnabled = enabled && source.isNotBlank() && target.isNotBlank() && '=' !in source &&
            '\n' !in source && '\n' !in target && entries.withIndex().none { it.index != index && it.value.first == source.trim() },
        onConfirm = {
            val next = entries.toMutableList()
            val entry = source.trim() to target.trim()
            if (index in next.indices) next[index] = entry else next.add(entry)
            publish(next); editing = false
        }, onDismiss = { editing = false }, content = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = source, onValueChange = { source = it }, singleLine = true,
                    textStyle = LegadoTheme.typography.bodyLarge, label = { AppText(stringResource(R.string.manga_glossary_source)) })
                OutlinedTextField(value = target, onValueChange = { target = it }, singleLine = true,
                    textStyle = LegadoTheme.typography.bodyLarge, label = { AppText(stringResource(R.string.manga_glossary_target)) })
                if (index in entries.indices) SmallPlainButton(enabled = enabled, onClick = {
                    publish(entries.filterIndexed { i, _ -> i != index }); editing = false
                }, text = stringResource(R.string.delete))
            }
        })
}
