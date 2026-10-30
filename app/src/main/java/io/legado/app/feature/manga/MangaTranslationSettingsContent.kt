package io.legado.app.feature.manga

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.domain.gateway.MangaModelItem
import io.legado.app.domain.gateway.MangaModelStatus
import io.legado.app.ui.book.manga.MangaReaderIntent
import io.legado.app.ui.book.manga.MangaReaderSettingKey
import io.legado.app.ui.book.manga.MangaReaderUiState
import io.legado.app.ui.widget.components.settingItem.TinySettingItem
import io.legado.app.ui.widget.components.settingItem.TinySliderSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySwitchSettingItem
import java.util.Locale
import kotlin.math.roundToInt
import io.legado.app.domain.model.settings.MangaTranslationBackend
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.AppRadioButton

enum class MangaTranslationSettingsPage { SETTINGS, MODELS }

@Stable
data class MangaModelUi(val model: MangaModelItem)

@Composable
fun MangaTranslationSettingsContent(state: MangaReaderUiState, onIntent: (MangaReaderIntent) -> Unit) {
    var chooseBackend by rememberSaveable { mutableStateOf(false) }
    val backendLabel = stringResource(if (state.settings.translationBackend == MangaTranslationBackend.AI)
        R.string.feature_manga_translation_backend_ai else R.string.feature_manga_translation_backend_local)
    AppAlertDialog(show = chooseBackend, onDismissRequest = { chooseBackend = false },
        title = stringResource(R.string.feature_manga_translation_ai), content = {
            MangaTranslationBackend.entries.forEach { backend ->
                Row(Modifier.fillMaxWidth().selectable(
                    selected = backend == state.settings.translationBackend, role = Role.RadioButton,
                    onClick = {
                        chooseBackend = false
                        onIntent(MangaReaderIntent.SelectTranslationBackend(backend))
                    }).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppRadioButton(selected = backend == state.settings.translationBackend, onClick = null)
                    Text(stringResource(if (backend == MangaTranslationBackend.AI)
                        R.string.feature_manga_translation_backend_ai else R.string.feature_manga_translation_backend_local))
                }
            }
        }, onConfirm = { chooseBackend = false })
    val modelsPage = state.translationPage == MangaTranslationSettingsPage.MODELS
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onIntent(MangaReaderIntent.CloseSettings) }) { Text(stringResource(R.string.back)) }
            Text(stringResource(if (modelsPage) R.string.feature_manga_translation_models else R.string.feature_manga_translation_title),
                style = MaterialTheme.typography.titleMedium)
        }
        Column(Modifier.fillMaxWidth().heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
            if (!modelsPage) {
                TinySwitchSettingItem(title = stringResource(R.string.feature_manga_translation_enabled),
                    checked = state.settings.translationEnabled,
                    onCheckedChange = { onIntent(MangaReaderIntent.UpdateSetting(MangaReaderSettingKey.TRANSLATION_ENABLED, if (it) 1 else 0)) })
                TinySwitchSettingItem(title = stringResource(R.string.feature_manga_translation_show_bubble),
                    checked = state.settings.translationBubbleVisible,
                    onCheckedChange = { onIntent(MangaReaderIntent.UpdateSetting(MangaReaderSettingKey.TRANSLATION_BUBBLE_VISIBLE, if (it) 1 else 0)) })
                TinySwitchSettingItem(title = stringResource(R.string.feature_manga_translation_auto),
                    checked = state.translationControls.automatic,
                    enabled = state.settings.translationEnabled && !state.translationModelBusy && !state.translationBackendChanging,
                    onCheckedChange = { onIntent(MangaReaderIntent.ToggleAutomaticTranslation) })
                TinySliderSettingItem(
                    title = stringResource(R.string.feature_manga_translation_membrane_transparency),
                    value = state.settings.translationMembraneTransparency.toFloat(),
                    valueRange = 0f..100f,
                    valueFormat = { "${it.roundToInt()}%" },
                    onValueChange = { onIntent(MangaReaderIntent.UpdateSetting(
                        MangaReaderSettingKey.TRANSLATION_MEMBRANE_TRANSPARENCY, it.roundToInt())) },
                )
                TinySwitchSettingItem(title = stringResource(R.string.feature_manga_translation_show_result),
                    checked = state.translationControls.showTranslation,
                    enabled = state.settings.translationEnabled,
                    onCheckedChange = { onIntent(MangaReaderIntent.ToggleTranslationDisplay) })
                if (state.translationControls.total > 0) Text(
                    stringResource(R.string.feature_manga_translation_progress, state.translationControls.completed, state.translationControls.total),
                    modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                TinySettingItem(title = stringResource(R.string.feature_manga_translation_models),
                    trailingContent = { Text("›") }, onClick = { onIntent(MangaReaderIntent.OpenTranslationModels) })
                TinySettingItem(title = stringResource(R.string.feature_manga_translation_ai),
                    description = if (state.translationBackendChanging) stringResource(R.string.feature_manga_translation_switching) else backendLabel,
                    trailingContent = { Text("›") }, onClick = { if (!state.translationBackendChanging) chooseBackend = true })
                TinySettingItem(title = stringResource(R.string.feature_manga_translation_clear_current),
                    onClick = { onIntent(MangaReaderIntent.RequestClearTranslation) })
                TinySettingItem(title = stringResource(R.string.feature_manga_translation_clear_all),
                    onClick = { onIntent(MangaReaderIntent.RequestClearAllTranslations) })
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(enabled = !state.translationModelBusy,
                        onClick = { onIntent(MangaReaderIntent.DownloadTranslationModel()) }) { Text(stringResource(R.string.feature_manga_translation_download_missing)) }
                    TextButton(enabled = !state.translationModelBusy,
                        onClick = { onIntent(MangaReaderIntent.ImportTranslationModels) }) { Text(stringResource(R.string.feature_manga_translation_import)) }
                    if (state.translationModelBusy) TextButton(onClick = { onIntent(MangaReaderIntent.CancelTranslationModelOperation) }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
                state.translationImportErrors.forEach { Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp)) }
                listOf("detector", "ocr", "inpainter", "translator").forEach { role ->
                    Text(stringResource(when (role) {
                        "detector" -> R.string.feature_manga_translation_detector
                        "ocr" -> R.string.feature_manga_translation_ocr
                        "translator" -> R.string.feature_manga_translation_local_models
                        else -> R.string.feature_manga_translation_inpainter
                    }), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 12.dp))
                    val items = state.translationModels.filter { it.model.role == role }
                    val displayed = if (role != "translator" || items.isEmpty()) items else {
                        val models = items.map { it.model }
                        val active = models.firstOrNull { it.status in setOf(MangaModelStatus.DOWNLOADING,
                            MangaModelStatus.IMPORTING, MangaModelStatus.VERIFYING, MangaModelStatus.DELETING) }
                        listOf(MangaModelUi(MangaModelItem("SMaLL-100", role, models.sumOf { it.size },
                            installed = models.all { it.installed },
                            progress = if (active == null) null else (models.sumOf {
                                it.size * (if (it.installed) 100 else it.progress ?: 0)
                            } / models.sumOf { it.size }).toInt(),
                            status = active?.status ?: if (models.all { it.installed }) MangaModelStatus.READY
                                else if (models.any { it.error != null }) MangaModelStatus.FAILED else MangaModelStatus.MISSING,
                            error = models.mapNotNull { it.error }.distinct().joinToString("\n").takeIf { it.isNotBlank() })))
                    }
                    displayed.forEach { item ->
                        val model = item.model
                        val status = stringResource(when (model.status) {
                            MangaModelStatus.READY -> R.string.feature_manga_translation_installed
                            MangaModelStatus.DOWNLOADING -> R.string.feature_manga_translation_downloading
                            MangaModelStatus.IMPORTING -> R.string.feature_manga_translation_importing
                            MangaModelStatus.DELETING -> R.string.feature_manga_translation_deleting
                            MangaModelStatus.VERIFYING -> R.string.feature_manga_translation_verifying
                            MangaModelStatus.FAILED -> R.string.feature_manga_translation_failed
                            MangaModelStatus.MISSING -> R.string.feature_manga_translation_not_installed
                        })
                        TinySettingItem(title = model.name,
                            description = "${String.format(Locale.ROOT, "%.2f", model.size / 1048576.0)} MB · $status" +
                                (model.error?.let { "\n$it" } ?: ""),
                            trailingContent = {
                                TextButton(enabled = !state.translationModelBusy, onClick = {
                                    onIntent(if (model.installed) MangaReaderIntent.RequestDeleteTranslationModel(model.name)
                                        else MangaReaderIntent.DownloadTranslationModel(model.name))
                                }) { Text(stringResource(if (model.installed) R.string.feature_manga_translation_delete else R.string.feature_manga_translation_download)) }
                                if (role == "translator" && !model.installed && items.any { it.model.installed }) {
                                    TextButton(enabled = !state.translationModelBusy, onClick = {
                                        onIntent(MangaReaderIntent.RequestDeleteTranslationModel(model.name))
                                    }) { Text(stringResource(R.string.feature_manga_translation_delete)) }
                                }
                            })
                        model.progress?.let { progress -> LinearProgressIndicator(
                            progress = { progress / 100f }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) }
                    }
                }
            }
        }
    }
}
