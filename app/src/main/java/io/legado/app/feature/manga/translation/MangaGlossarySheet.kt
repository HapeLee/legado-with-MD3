package io.legado.app.feature.manga.translation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.button.series.SmallPlainButton
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.TinyClickableSettingItem
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.collections.immutable.toImmutableList

@Composable
internal fun MangaGlossarySheet(
    show: Boolean,
    draft: String,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    val entries = remember(draft) {
        draft.lineSequence().filter { '=' in it }
            .map { it.substringBefore('=') to it.substringAfter('=') }.toList().toImmutableList()
    }
    var editing by rememberSaveable { mutableStateOf(false) }
    var index by rememberSaveable { mutableIntStateOf(-1) }
    var source by rememberSaveable { mutableStateOf("") }
    var target by rememberSaveable { mutableStateOf("") }

    /** 没有保存按钮：每次增删改都立刻落库，界面上的草稿只是同一次写入的回显。 */
    fun publish(next: List<Pair<String, String>>) {
        onDraftChange(next.joinToString("\n") { "${it.first}=${it.second}" })
        onSave()
    }

    AppModalBottomSheet(
        show = show,
        animateContentSize = false,
        title = stringResource(R.string.manga_translation_glossary),
        onDismissRequest = onDismiss,
        endAction = {
            MediumTonalButton(
                onClick = { index = -1; source = ""; target = ""; editing = true },
                enabled = enabled,
                icon = AppIcons.Add,
                contentDescription = stringResource(R.string.manga_glossary_add),
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (entries.isEmpty()) AppText(
                stringResource(R.string.manga_glossary_empty),
                style = LegadoTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            entries.forEachIndexed { i, entry ->
                TinyClickableSettingItem(
                    title = entry.first,
                    description = entry.second,
                    color = LegadoTheme.colorScheme.onSheetContent,
                    onClick = {
                        if (enabled) {
                            index = i; source = entry.first; target = entry.second; editing = true
                        }
                    },
                )
            }
        }
    }
    if (show && editing) AppAlertDialog(
        show = true,
        title = stringResource(R.string.manga_translation_glossary),
        onDismissRequest = { editing = false },
        confirmText = stringResource(R.string.manga_glossary_apply),
        dismissText = stringResource(R.string.cancel),
        confirmEnabled = enabled && source.isNotBlank() && target.isNotBlank() && '=' !in source &&
            '\n' !in source && '\n' !in target && entries.withIndex()
            .none { it.index != index && it.value.first == source.trim() },
        onConfirm = {
            val next = entries.toMutableList()
            val entry = source.trim() to target.trim()
            if (index in next.indices) next[index] = entry else next.add(entry)
            publish(next); editing = false
        },
        onDismiss = { editing = false },
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppTextField(
                    value = source,
                    onValueChange = { source = it },
                    label = stringResource(R.string.manga_glossary_source),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                AppTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = stringResource(R.string.manga_glossary_target),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (index in entries.indices) SmallPlainButton(
                    enabled = enabled,
                    onClick = {
                        publish(entries.filterIndexed { i, _ -> i != index }); editing = false
                    },
                    text = stringResource(R.string.delete)
                )
            }
        })
}
