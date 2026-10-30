package io.legado.app.feature.manga.translation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.domain.model.manga.*
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.series.SmallPlainButton
import io.legado.app.ui.widget.components.text.AppText

@Composable
internal fun mangaInpaintEngineName(engine: MangaInpaintEngine): String = when (engine) {
    MangaInpaintEngine.LOCAL -> stringResource(R.string.manga_inpaint_local)
    MangaInpaintEngine.MI_GAN -> "MI-GAN"
    MangaInpaintEngine.LAMA -> "LaMa"
}

@Composable
internal fun MangaInpaintModelControls(engine: MangaInpaintEngine, state: MangaTranslationUiState, onIntent: (MangaTranslationIntent) -> Unit) {
    val model = state.inpaintModels[engine] ?: MangaInpaintModelState()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        AppText(mangaInpaintEngineName(engine), style = LegadoTheme.typography.titleSmall)
        AppText(stringResource(if (engine == MangaInpaintEngine.MI_GAN) R.string.manga_inpaint_migan_hint else R.string.manga_inpaint_lama_hint),
            style = LegadoTheme.typography.bodySmall)
        AppText(stringResource(when (model.phase) {
            MangaInpaintModelPhase.CHECKING -> R.string.manga_inpaint_checking
            MangaInpaintModelPhase.MISSING -> R.string.manga_inpaint_missing
            MangaInpaintModelPhase.DOWNLOADING -> R.string.manga_inpaint_downloading
            MangaInpaintModelPhase.READY -> R.string.manga_inpaint_ready
            MangaInpaintModelPhase.FAILED -> R.string.manga_inpaint_failed
        }), style = LegadoTheme.typography.bodyMedium)
        if (model.totalBytes > 0) AppText(stringResource(R.string.manga_inpaint_download_size,
            model.downloadedBytes / 1048576f, model.totalBytes / 1048576f), style = LegadoTheme.typography.bodySmall)
        if (model.phase == MangaInpaintModelPhase.DOWNLOADING) {
            LinearProgressIndicator(progress = { (model.downloadedBytes.toFloat() / model.totalBytes.coerceAtLeast(1)).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            SmallPlainButton(onClick = { onIntent(MangaTranslationIntent.CancelInpaintDownload(engine)) }, text = stringResource(R.string.cancel))
        } else {
            model.error?.let { AppText(it, color = LegadoTheme.colorScheme.error, style = LegadoTheme.typography.bodyMedium) }
            if (model.phase != MangaInpaintModelPhase.READY) SmallPlainButton(
                enabled = model.phase != MangaInpaintModelPhase.CHECKING && state.inpaintOperation == null,
                onClick = { onIntent(MangaTranslationIntent.DownloadInpaint(engine)) }, text = stringResource(R.string.manga_inpaint_download))
            if (model.phase != MangaInpaintModelPhase.CHECKING) SmallPlainButton(enabled = state.inpaintOperation == null,
                onClick = { onIntent(MangaTranslationIntent.DeleteInpaint(engine)) }, text = stringResource(R.string.manga_inpaint_delete))
        }
    }
}
