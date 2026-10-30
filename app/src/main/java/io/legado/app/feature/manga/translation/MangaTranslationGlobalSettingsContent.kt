package io.legado.app.feature.manga.translation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.text.AppText

/** Inline global options; the app host binds this content to the manga settings group. */
@Composable
fun MangaTranslationGlobalSettingsContent(state: MangaTranslationUiState, onIntent: (MangaTranslationIntent) -> Unit) {
    AppText(stringResource(R.string.manga_translation_global_hint), style = LegadoTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    state.error?.let { AppText(it, color = LegadoTheme.colorScheme.error, modifier = Modifier.padding(16.dp), style = LegadoTheme.typography.bodyMedium) }
    if (state.loaded) MangaTranslationOptions(state, onIntent)
}

@Composable
internal fun MangaSettingsExpansion(title: String, expanded: Boolean, onExpandChange: (Boolean) -> Unit,
    expandContent: @Composable () -> Unit) {
    ClickableSettingItem(title, onClick = { onExpandChange(!expanded) })
    if (expanded) Column(Modifier.fillMaxWidth()) { expandContent() }
}
