package io.legado.app.feature.manga.translation

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.legado.app.R
import io.legado.app.domain.model.manga.MangaComposition
import io.legado.app.domain.model.manga.MangaInpaintEngine
import io.legado.app.domain.model.manga.MangaTranslationPipeline

/**
 * One readable sentence for the configured stages, shared by the settings sheet and diagnostics.
 * Stage names mirror [MangaTranslationPipeline] so copy cannot drift from the actual branch.
 */
@Composable
internal fun mangaPipelineSummary(state: MangaTranslationUiState): String {
    val pipeline = MangaTranslationPipeline.of(state.settings)
    val unknownModel = stringResource(R.string.manga_translation_pipeline_unknown_model)
    val modelName = { id: String -> state.models.firstOrNull { it.id == id }?.name ?: unknownModel }
    val recognition = when (val stage = pipeline.recognition) {
        MangaTranslationPipeline.Recognition.Unused -> null
        is MangaTranslationPipeline.Recognition.AiVision -> stringResource(R.string.manga_translation_pipeline_ai_vision)
            .let { if (stage.modelId.isBlank()) it else "$it · ${modelName(stage.modelId)}" }
        is MangaTranslationPipeline.Recognition.LocalOcr -> mangaOcrEngineName(stage.engine)
        is MangaTranslationPipeline.Recognition.CloudOcr -> mangaCloudOcrProviderName(stage.provider)
    }
    val translation = when (val stage = pipeline.translation) {
        MangaTranslationPipeline.Translation.Unused -> null
        MangaTranslationPipeline.Translation.FusedWithRecognition -> stringResource(R.string.manga_translation_pipeline_fused)
        MangaTranslationPipeline.Translation.GoogleText -> stringResource(R.string.manga_translation_engine_google)
        MangaTranslationPipeline.Translation.FollowSettings -> stringResource(R.string.manga_translation_engine_follow)
        is MangaTranslationPipeline.Translation.AiText ->
            if (stage.modelId.isBlank()) stringResource(R.string.manga_translation_pipeline_ai_preset)
            else stringResource(R.string.manga_translation_pipeline_ai) + " · " + modelName(stage.modelId)
    }
    val composition = when (val stage = pipeline.composition) {
        is MangaTranslationPipeline.Composition.LocalLayout ->
            if (stage.inpaint == MangaInpaintEngine.LOCAL) stringResource(R.string.manga_translation_pipeline_local_fast)
            else stringResource(R.string.manga_translation_pipeline_local_erase) + " · " + mangaInpaintEngineName(stage.inpaint)
        MangaTranslationPipeline.Composition.LocalOverlay -> stringResource(R.string.manga_translation_patch)
        is MangaTranslationPipeline.Composition.AiImage -> {
            val label = stringResource(when (stage.mode) {
                MangaComposition.AI_REPAIR -> R.string.manga_translation_ai_repair
                MangaComposition.AI_REGION -> R.string.manga_translation_ai_region
                else -> R.string.manga_translation_ai_page
            })
            if (stage.modelId.isBlank()) label else "$label · ${modelName(stage.modelId)}"
        }
    }
    return stringResource(R.string.manga_translation_pipeline_summary,
        listOfNotNull(recognition, translation, composition).joinToString(" → "))
}
