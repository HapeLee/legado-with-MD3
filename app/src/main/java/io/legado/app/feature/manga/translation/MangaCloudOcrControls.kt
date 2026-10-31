package io.legado.app.feature.manga.translation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.domain.model.manga.MangaCloudOcrConfig
import io.legado.app.domain.model.manga.MangaCloudOcrProvider
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.series.SmallPlainButton
import io.legado.app.ui.widget.components.settingItem.DropdownListSettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.ui.widget.components.text.AppText

/** Shared with the pipeline summary so a provider is never named two different ways. */
@Composable
internal fun mangaCloudOcrProviderName(provider: MangaCloudOcrProvider): String = when (provider) {
    MangaCloudOcrProvider.BAIDU -> stringResource(R.string.manga_cloud_ocr_baidu)
    MangaCloudOcrProvider.TENCENT -> stringResource(R.string.manga_cloud_ocr_tencent)
    MangaCloudOcrProvider.PADDLE -> stringResource(R.string.manga_cloud_ocr_paddle)
    MangaCloudOcrProvider.PADDLE_SERVER -> stringResource(R.string.manga_cloud_ocr_server)
}

@Composable
internal fun MangaCloudOcrControls(state: MangaTranslationUiState, onIntent: (MangaTranslationIntent) -> Unit) {
    val provider = state.settings.cloudOcrProvider
    DropdownListSettingItem(stringResource(R.string.manga_cloud_ocr_provider), provider.name,
        displayEntries = MangaCloudOcrProvider.entries.map { mangaCloudOcrProviderName(it) }.toTypedArray(),
        entryValues = MangaCloudOcrProvider.entries.map { it.name }.toTypedArray()) { value ->
        onIntent(MangaTranslationIntent.ChangeSettings { it.copy(cloudOcrProvider = MangaCloudOcrProvider.valueOf(value)) })
    }
    if (state.cloudProvider != provider || state.cloudLoading) {
        AppText(stringResource(R.string.manga_cloud_ocr_loading), modifier = Modifier.padding(16.dp), style = LegadoTheme.typography.bodyMedium)
        return
    }
    var expanded by rememberSaveable(provider) { mutableStateOf(false) }
    val config = state.cloudDraft
    fun edit(transform: (MangaCloudOcrConfig) -> MangaCloudOcrConfig) = onIntent(MangaTranslationIntent.EditCloudOcr(transform))
    MangaSettingsExpansion(title = stringResource(R.string.manga_translation_connection), expanded = expanded,
        onExpandChange = { expanded = it }, expandContent = {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        AppText(stringResource(R.string.manga_cloud_ocr_hint), style = LegadoTheme.typography.bodySmall)
        OutlinedTextField(textStyle = LegadoTheme.typography.bodyLarge, value = config.apiKey, onValueChange = { value -> edit { it.copy(apiKey = value) } },
            label = { AppText(when (provider) {
                MangaCloudOcrProvider.BAIDU -> "API Key"
                MangaCloudOcrProvider.TENCENT -> "SecretId"
                else -> stringResource(R.string.manga_cloud_ocr_token)
            }, style = LegadoTheme.typography.bodySmall, color = LocalContentColor.current) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), enabled = !state.cloudSaving,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        if (provider in setOf(MangaCloudOcrProvider.BAIDU, MangaCloudOcrProvider.TENCENT)) OutlinedTextField(textStyle = LegadoTheme.typography.bodyLarge,
            value = config.secretKey, onValueChange = { value -> edit { it.copy(secretKey = value) } },
            label = { AppText("Secret Key", style = LegadoTheme.typography.bodySmall, color = LocalContentColor.current) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), enabled = !state.cloudSaving,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
    }
    when (provider) {
        MangaCloudOcrProvider.BAIDU, MangaCloudOcrProvider.TENCENT -> {
            SwitchSettingItem(stringResource(R.string.manga_cloud_ocr_accurate), checked = config.accurate, enabled = !state.cloudSaving) { value -> edit { it.copy(accurate = value) } }
            if (provider == MangaCloudOcrProvider.TENCENT) OutlinedTextField(textStyle = LegadoTheme.typography.bodyLarge, value = config.region,
                onValueChange = { value -> edit { it.copy(region = value.trim()) } },
                label = { AppText(stringResource(R.string.manga_cloud_ocr_region), style = LegadoTheme.typography.bodySmall, color = LocalContentColor.current) }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(12.dp))
        }
        MangaCloudOcrProvider.PADDLE -> DropdownListSettingItem(stringResource(R.string.manga_cloud_ocr_model), config.model,
            displayEntries = arrayOf("PP-OCRv6", "PP-OCRv5", "PP-OCRv5-latin"), entryValues = arrayOf("PP-OCRv6", "PP-OCRv5", "PP-OCRv5-latin")) { value -> edit { it.copy(model = value) } }
        MangaCloudOcrProvider.PADDLE_SERVER -> {
            OutlinedTextField(textStyle = LegadoTheme.typography.bodyLarge, value = config.endpoint,
                onValueChange = { value -> edit { it.copy(endpoint = value.trim()) } },
                label = { AppText(stringResource(R.string.manga_cloud_ocr_endpoint), style = LegadoTheme.typography.bodySmall, color = LocalContentColor.current) }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(12.dp))
            DropdownListSettingItem(stringResource(R.string.manga_cloud_ocr_auth_scheme), config.authScheme,
                displayEntries = arrayOf("Bearer", "token"), entryValues = arrayOf("Bearer", "token")) { value -> edit { it.copy(authScheme = value) } }
            AppText(stringResource(R.string.manga_cloud_ocr_server_hint), style = LegadoTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
    SmallPlainButton(onClick = { onIntent(MangaTranslationIntent.SaveCloudOcr) }, enabled = !state.cloudSaving,
        modifier = Modifier.fillMaxWidth(), text = stringResource(if (state.cloudSaving) R.string.manga_cloud_ocr_saving else R.string.manga_cloud_ocr_save))
    })
    if (state.cloudSaved) AppText(stringResource(R.string.manga_cloud_ocr_saved), modifier = Modifier.padding(horizontal = 16.dp), style = LegadoTheme.typography.bodyMedium)
}
