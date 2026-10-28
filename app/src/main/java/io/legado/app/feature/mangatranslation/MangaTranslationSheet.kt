package io.legado.app.feature.mangatranslation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.domain.gateway.MangaTranslationStage
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet

@Stable
data class MangaTranslationUiState(
    val pageKey: String = "",
    val stage: MangaTranslationStage? = null,
    val sourceLanguage: String = "ja",
    val targetLanguage: String = "zh",
    val messageRes: Int? = null,
)

@Composable
fun MangaTranslationSheet(
    state: MangaTranslationUiState,
    hasTranslation: Boolean,
    showingOriginal: Boolean,
    onLanguages: (String, String) -> Unit,
    onTranslate: () -> Unit,
    onToggleOriginal: () -> Unit,
    onCancel: () -> Unit,
    onClearCache: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppModalBottomSheet(show = true, onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.manga_translation_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.manga_translation_notice), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.manga_translation_source))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ja" to R.string.manga_translation_japanese, "en" to R.string.manga_translation_english, "ko" to R.string.manga_translation_korean).forEach { (code, label) ->
                    FilterChip(selected = state.sourceLanguage == code, enabled = state.stage == null,
                        onClick = { onLanguages(code, if (state.targetLanguage == code) "zh" else state.targetLanguage) },
                        label = { Text(stringResource(label)) })
                }
            }
            Text(stringResource(R.string.manga_translation_target))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("zh" to R.string.manga_translation_chinese, "en" to R.string.manga_translation_english, "ja" to R.string.manga_translation_japanese, "ko" to R.string.manga_translation_korean).forEach { (code, label) ->
                    if (code != state.sourceLanguage) {
                        FilterChip(selected = state.targetLanguage == code, enabled = state.stage == null,
                            onClick = { onLanguages(state.sourceLanguage, code) }, label = { Text(stringResource(label)) })
                    }
                }
            }
            state.stage?.let { stage ->
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(when (stage) {
                    MangaTranslationStage.PREPARING -> R.string.manga_translation_preparing
                    MangaTranslationStage.RECOGNIZING -> R.string.manga_translation_recognizing
                    MangaTranslationStage.DOWNLOADING -> R.string.manga_translation_downloading
                    MangaTranslationStage.TRANSLATING -> R.string.manga_translation_translating
                    MangaTranslationStage.RENDERING -> R.string.manga_translation_rendering
                }))
                TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            }
            state.messageRes?.let { Text(stringResource(it)) }
            if (state.stage == null) {
                Button(onClick = onTranslate) { Text(stringResource(R.string.manga_translation_start)) }
            }
            if (hasTranslation) {
                TextButton(onClick = onToggleOriginal) {
                    Text(stringResource(if (showingOriginal) R.string.manga_translation_show_result else R.string.manga_translation_show_original))
                }
            }
            TextButton(onClick = onClearCache, enabled = state.stage == null) {
                Text(stringResource(R.string.manga_translation_clear_cache))
            }
        }
    }
}
