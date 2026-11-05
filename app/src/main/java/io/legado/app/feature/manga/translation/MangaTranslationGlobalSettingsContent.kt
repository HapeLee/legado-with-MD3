package io.legado.app.feature.manga.translation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.text.AppText

/** Inline global options; the app host binds this content to the manga settings group. */
@Composable
fun MangaTranslationGlobalSettingsContent(state: MangaTranslationUiState, onIntent: (MangaTranslationIntent) -> Unit) {
    var clearAllConfirmation by rememberSaveable { mutableStateOf(false) }
    AppAlertDialog(
        show = clearAllConfirmation,
        onDismissRequest = { clearAllConfirmation = false },
        title = stringResource(R.string.manga_translation_clear_all),
        text = stringResource(R.string.manga_translation_clear_all_hint),
        confirmText = stringResource(R.string.delete),
        dismissText = stringResource(R.string.cancel),
        onConfirm = { clearAllConfirmation = false; onIntent(MangaTranslationIntent.ClearAll) },
        onDismiss = { clearAllConfirmation = false })
    AppText(stringResource(R.string.manga_translation_global_hint), style = LegadoTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    state.error?.let { AppText(it, color = LegadoTheme.colorScheme.error, modifier = Modifier.padding(16.dp), style = LegadoTheme.typography.bodyMedium) }
    if (state.loaded) MangaTranslationOptions(state, onIntent)
    if (state.loaded) SplicedColumnGroup {
        ClickableSettingItem(stringResource(R.string.manga_translation_clear_all),
            description = stringResource(R.string.manga_translation_clear_all_hint),
            onClick = { clearAllConfirmation = true })
    }
}

@Composable
internal fun MangaSettingsExpansion(title: String, expanded: Boolean, onExpandChange: (Boolean) -> Unit,
    expandContent: @Composable () -> Unit) {
    ClickableSettingItem(title, onClick = { onExpandChange(!expanded) })
    if (expanded) Column(Modifier.fillMaxWidth()) { expandContent() }
}
