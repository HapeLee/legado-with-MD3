package io.legado.app.feature.manga.translation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.legado.app.R
import io.legado.app.domain.model.manga.*
import io.legado.app.ui.widget.components.button.series.SmallOutlinedButton
import io.legado.app.ui.widget.components.button.series.SmallTonalButton

@Composable
internal fun mangaInpaintEngineName(engine: MangaInpaintEngine): String = when (engine) {
    MangaInpaintEngine.LOCAL -> stringResource(R.string.manga_inpaint_local)
    MangaInpaintEngine.MI_GAN -> "MI-GAN"
    MangaInpaintEngine.LAMA -> "LaMa"
}

@Composable
internal fun MangaInpaintModelControls(
    engine: MangaInpaintEngine,
    state: MangaTranslationUiState,
    onIntent: (MangaTranslationIntent) -> Unit
) {
    val model = state.inpaintModels[engine] ?: MangaInpaintModelState()
    val downloading = model.phase == MangaInpaintModelPhase.DOWNLOADING
    MangaModelSection(
        title = mangaInpaintEngineName(engine),
        hint = stringResource(if (engine == MangaInpaintEngine.MI_GAN) R.string.manga_inpaint_migan_hint else R.string.manga_inpaint_lama_hint),
        status = stringResource(
            when (model.phase) {
                MangaInpaintModelPhase.CHECKING -> R.string.manga_inpaint_checking
                MangaInpaintModelPhase.MISSING -> R.string.manga_inpaint_missing
                MangaInpaintModelPhase.DOWNLOADING -> R.string.manga_inpaint_downloading
                MangaInpaintModelPhase.READY -> R.string.manga_inpaint_ready
                MangaInpaintModelPhase.FAILED -> R.string.manga_inpaint_failed
            }
        ),
        statusColor = mangaModelStatusColor(
            ready = model.phase == MangaInpaintModelPhase.READY,
            failed = model.phase == MangaInpaintModelPhase.FAILED, running = downloading
        ),
        progress = if (downloading) (model.downloadedBytes.toFloat() / model.totalBytes.coerceAtLeast(
            1
        )).coerceIn(0f, 1f) else null,
        sizeText = when {
            downloading -> stringResource(
                R.string.manga_inpaint_download_size,
                model.downloadedBytes / 1048576f, model.totalBytes / 1048576f
            )

            model.phase == MangaInpaintModelPhase.READY && model.totalBytes > 0 ->
                stringResource(R.string.manga_model_size, model.totalBytes / 1048576f)

            else -> null
        },
        error = model.error,
    ) {
        if (downloading) SmallOutlinedButton(
            onClick = { onIntent(MangaTranslationIntent.CancelInpaintDownload(engine)) },
            text = stringResource(R.string.cancel)
        )
        else {
            if (model.phase != MangaInpaintModelPhase.READY) SmallOutlinedButton(
                enabled = model.phase != MangaInpaintModelPhase.CHECKING && state.inpaintOperation == null,
                icon = Icons.Default.Download,
                onClick = { onIntent(MangaTranslationIntent.DownloadInpaint(engine)) },
                text = stringResource(R.string.manga_inpaint_download)
            )
            if (model.phase != MangaInpaintModelPhase.CHECKING) SmallOutlinedButton(
                enabled = state.inpaintOperation == null,
                icon = Icons.Default.Delete,
                onClick = { onIntent(MangaTranslationIntent.DeleteInpaint(engine)) },
                text = stringResource(R.string.manga_inpaint_delete)
            )
        }
    }
}
