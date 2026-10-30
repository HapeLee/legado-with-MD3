package io.legado.app.domain.model.manga

/**
 * Explicit view of the stages a page actually goes through: recognition, translation, composition.
 *
 * [MangaTranslationSettings] stores a flat list of engine choices, so every consumer previously had
 * to re-derive which branch runs (`MangaLocator` is really a recognition source, `MangaComposition`
 * mixes the composition carrier with whether text translation happens at all). Deriving once keeps
 * the sheet, diagnostics and error text describing the same pipeline.
 */
data class MangaTranslationPipeline(
    val recognition: Recognition,
    val translation: Translation,
    val composition: Composition,
) {

    sealed interface Recognition {
        /** No recognition request: the whole page is redrawn by an image model. */
        data object Unused : Recognition

        /** Empty [modelId] means recognition follows the translation model in one fused request. */
        data class AiVision(val modelId: String) : Recognition

        data class LocalOcr(val engine: MangaLocalOcrEngine) : Recognition

        data class CloudOcr(val provider: MangaCloudOcrProvider) : Recognition
    }

    sealed interface Translation {
        /** Translation happens inside the whole-page image edit. */
        data object Unused : Translation

        /** Recognition, translation and layout share a single request. */
        data object FusedWithRecognition : Translation

        /** Pure-text translation; empty [modelId] follows the chapter translation preset. */
        data class AiText(val modelId: String) : Translation

        /** Pure-text translation by the free Google gtx endpoint; no glossary or context. */
        data object GoogleText : Translation

        /** Resolved at run time from the global chapter translation settings. */
        data object FollowSettings : Translation
    }

    sealed interface Composition {
        /** Local erase (optionally by an inpaint model) plus local typesetting. */
        data class LocalLayout(val inpaint: MangaInpaintEngine) : Composition

        /** Local typesetting over the original lettering, without erasing it. */
        data object LocalOverlay : Composition

        /** The image model owns erase/redraw for [mode]. */
        data class AiImage(val mode: MangaComposition, val modelId: String) : Composition
    }

    companion object {
        fun of(settings: MangaTranslationSettings): MangaTranslationPipeline {
            val pageEdit = settings.composition == MangaComposition.AI_PAGE
            val recognition = when {
                pageEdit -> Recognition.Unused
                settings.locator == MangaLocator.AI -> Recognition.AiVision(settings.ocrModelId)
                settings.locator == MangaLocator.LOCAL -> Recognition.LocalOcr(settings.localOcrEngine)
                else -> Recognition.CloudOcr(settings.cloudOcrProvider)
            }
            // Identical vision/translation model configs are still coalesced into one request by the
            // repository at call time; this view reports the configured intent, not that optimization.
            val translation = when {
                pageEdit -> Translation.Unused
                settings.textEngine == MangaTranslateEngine.GOOGLE -> Translation.GoogleText
                settings.textEngine == MangaTranslateEngine.FOLLOW_SETTINGS -> Translation.FollowSettings
                settings.usesAiTypesetting && settings.ocrModelId.isBlank() -> Translation.FusedWithRecognition
                else -> Translation.AiText(settings.modelId)
            }
            val composition = when (settings.composition) {
                MangaComposition.SMART -> Composition.LocalLayout(settings.inpaintEngine)
                // PATCH covers the original lettering; the selected inpaint model does not run.
                MangaComposition.PATCH -> Composition.LocalOverlay
                else -> Composition.AiImage(settings.composition, settings.imageModelId)
            }
            return MangaTranslationPipeline(recognition, translation, composition)
        }
    }
}
