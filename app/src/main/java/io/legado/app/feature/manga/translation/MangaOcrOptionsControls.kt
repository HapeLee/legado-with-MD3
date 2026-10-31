package io.legado.app.feature.manga.translation

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
import io.legado.app.domain.model.manga.*
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.series.SmallPlainButton
import io.legado.app.ui.widget.components.settingItem.DropdownListSettingItem
import io.legado.app.ui.widget.components.settingItem.SliderSettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.ui.widget.components.text.AppText
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun MangaOcrOptionsControls(settings: MangaTranslationSettings, onIntent: (MangaTranslationIntent) -> Unit) {
    val options = settings.ocrOptions
    val paddle = settings.locator == MangaLocator.LOCAL && settings.localOcrEngine.downloadable
    var advanced by rememberSaveable { mutableStateOf(false) }
    fun change(transform: (MangaOcrOptions) -> MangaOcrOptions) {
        onIntent(MangaTranslationIntent.ChangeSettings { it.copy(ocrOptions = transform(it.ocrOptions)) })
    }
    if (paddle) DropdownListSettingItem(stringResource(R.string.manga_ocr_detection_profile), options.detectionProfile.name,
        displayEntries = arrayOf(stringResource(R.string.manga_ocr_profile_fast), stringResource(R.string.manga_ocr_profile_accurate)),
        entryValues = MangaOcrDetectionProfile.entries.map { it.name }.toTypedArray(), description = stringResource(R.string.manga_ocr_profile_hint)) { value ->
        change { it.copy(detectionProfile = MangaOcrDetectionProfile.valueOf(value)) }
    }
    SwitchSettingItem(stringResource(R.string.manga_ocr_advanced), checked = advanced) { advanced = it }
    if (!advanced) return
    if (paddle) {
        val effective = options.detectionThresholds(settings.localOcrEngine)
        val defaults = options.copy(probabilityThreshold = null, boxScoreThreshold = null, unclipRatio = null).detectionThresholds(settings.localOcrEngine)
        AppText(stringResource(R.string.manga_ocr_effective_thresholds, effective.probability, effective.score, effective.unclip),
            style = LegadoTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        SwitchSettingItem(stringResource(R.string.manga_ocr_custom_detection), checked = options.customDetection) { enabled ->
            onIntent(MangaTranslationIntent.ChangeSettings { current ->
                val base = current.ocrOptions.detectionThresholds(current.localOcrEngine)
                current.copy(ocrOptions = current.ocrOptions.copy(
                    probabilityThreshold = if (enabled) base.probability else null,
                    boxScoreThreshold = if (enabled) base.score else null,
                    unclipRatio = if (enabled) base.unclip else null))
            })
        }
        if (options.customDetection) {
            SliderSettingItem(stringResource(R.string.manga_ocr_probability_threshold), value = effective.probability * 100, defaultValue = defaults.probability * 100,
                valueRange = 5f..95f,
                description = stringResource(R.string.manga_ocr_probability_hint), valueLabel = { String.format(Locale.ROOT, "%.2f", it / 100) }, onValueChange = { value ->
                change { it.copy(probabilityThreshold = value.roundToInt() / 100f) }
            })
            SliderSettingItem(stringResource(R.string.manga_ocr_score_threshold), value = effective.score * 100, defaultValue = defaults.score * 100,
                valueRange = 5f..100f,
                description = stringResource(R.string.manga_ocr_score_hint), valueLabel = { String.format(Locale.ROOT, "%.2f", it / 100) }, onValueChange = { value ->
                change { it.copy(boxScoreThreshold = value.roundToInt() / 100f) }
            })
            SliderSettingItem(stringResource(R.string.manga_ocr_unclip_ratio), value = effective.unclip * 20, defaultValue = defaults.unclip * 20,
                valueRange = 10f..60f, valueLabel = { String.format(Locale.ROOT, "%.2f", it / 20) }, description = stringResource(R.string.manga_ocr_unclip_hint), onValueChange = { value ->
                change { it.copy(unclipRatio = value.roundToInt() / 20f) }
            })
        }
    }
    AppText(stringResource(R.string.manga_ocr_preprocess), style = LegadoTheme.typography.titleSmall,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    SwitchSettingItem(stringResource(R.string.manga_ocr_upscale), description = stringResource(R.string.manga_ocr_upscale_hint), checked = options.upscale2x) { value ->
        change { it.copy(upscale2x = value) }
    }
    SwitchSettingItem(stringResource(R.string.manga_ocr_invert), checked = options.invert) { value -> change { it.copy(invert = value) } }
    SwitchSettingItem(stringResource(R.string.manga_ocr_otsu), description = stringResource(R.string.manga_ocr_otsu_hint), checked = options.binarize) { value ->
        change { it.copy(binarize = value) }
    }
    AppText(stringResource(R.string.manga_ocr_options_hint), style = LegadoTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
    SmallPlainButton(onClick = { change { MangaOcrOptions() } }, modifier = Modifier.fillMaxWidth(), text = stringResource(R.string.manga_ocr_reset_options))
}
