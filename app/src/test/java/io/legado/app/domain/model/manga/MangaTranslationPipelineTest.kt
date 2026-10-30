package io.legado.app.domain.model.manga

import io.legado.app.domain.model.TranslationConstants
import org.junit.Assert.assertEquals
import org.junit.Test

class MangaTranslationPipelineTest {

    private fun pipeline(settings: MangaTranslationSettings) = MangaTranslationPipeline.of(settings)

    @Test fun `ai vision without a separate model fuses recognition and translation`() {
        val pipeline = pipeline(MangaTranslationSettings(
            locator = MangaLocator.AI, composition = MangaComposition.SMART, modelId = "text", ocrModelId = ""))
        assertEquals(MangaTranslationPipeline.Recognition.AiVision(""), pipeline.recognition)
        assertEquals(MangaTranslationPipeline.Translation.FusedWithRecognition, pipeline.translation)
        assertEquals(MangaTranslationPipeline.Composition.LocalLayout(MangaInpaintEngine.LOCAL), pipeline.composition)
    }

    @Test fun `separate vision model exposes a pure text translation stage`() {
        val pipeline = pipeline(MangaTranslationSettings(
            locator = MangaLocator.AI, composition = MangaComposition.SMART, modelId = "text", ocrModelId = "vision"))
        assertEquals(MangaTranslationPipeline.Recognition.AiVision("vision"), pipeline.recognition)
        assertEquals(MangaTranslationPipeline.Translation.AiText("text"), pipeline.translation)
    }

    @Test fun `on-device ocr keeps the text stage even for the default model`() {
        val pipeline = pipeline(MangaTranslationSettings(
            locator = MangaLocator.LOCAL, localOcrEngine = MangaLocalOcrEngine.PADDLE_V6_MANGA))
        assertEquals(MangaTranslationPipeline.Recognition.LocalOcr(MangaLocalOcrEngine.PADDLE_V6_MANGA), pipeline.recognition)
        assertEquals(MangaTranslationPipeline.Translation.AiText(""), pipeline.translation)
    }

    @Test fun `cloud ocr reports the provider and patch never runs the inpaint model`() {
        val pipeline = pipeline(MangaTranslationSettings(
            locator = MangaLocator.CLOUD, cloudOcrProvider = MangaCloudOcrProvider.BAIDU,
            composition = MangaComposition.PATCH, inpaintEngine = MangaInpaintEngine.MI_GAN))
        assertEquals(MangaTranslationPipeline.Recognition.CloudOcr(MangaCloudOcrProvider.BAIDU), pipeline.recognition)
        assertEquals(MangaTranslationPipeline.Translation.AiText(""), pipeline.translation)
        assertEquals(MangaTranslationPipeline.Composition.LocalOverlay, pipeline.composition)
    }

    @Test fun `whole page edit has no recognition or text stage`() {
        val pipeline = pipeline(MangaTranslationSettings(
            locator = MangaLocator.LOCAL, composition = MangaComposition.AI_PAGE, imageModelId = "image"))
        assertEquals(MangaTranslationPipeline.Recognition.Unused, pipeline.recognition)
        assertEquals(MangaTranslationPipeline.Translation.Unused, pipeline.translation)
        assertEquals(MangaTranslationPipeline.Composition.AiImage(MangaComposition.AI_PAGE, "image"), pipeline.composition)
    }

    @Test fun `ai region keeps text translation before the image model composes`() {
        val pipeline = pipeline(MangaTranslationSettings(
            locator = MangaLocator.LOCAL, composition = MangaComposition.AI_REGION, modelId = "text", imageModelId = "image"))
        assertEquals(MangaTranslationPipeline.Recognition.LocalOcr(MangaLocalOcrEngine.ML_KIT), pipeline.recognition)
        assertEquals(MangaTranslationPipeline.Translation.AiText("text"), pipeline.translation)
        assertEquals(MangaTranslationPipeline.Composition.AiImage(MangaComposition.AI_REGION, "image"), pipeline.composition)
    }

    @Test fun `smart composition carries the selected inpaint engine`() {
        val pipeline = pipeline(MangaTranslationSettings(composition = MangaComposition.SMART, inpaintEngine = MangaInpaintEngine.LAMA))
        assertEquals(MangaTranslationPipeline.Composition.LocalLayout(MangaInpaintEngine.LAMA), pipeline.composition)
    }

    @Test fun `google engine replaces only the text stage`() {
        val pipeline = pipeline(MangaTranslationSettings(
            locator = MangaLocator.LOCAL, textEngine = MangaTranslateEngine.GOOGLE, modelId = "text"))
        assertEquals(MangaTranslationPipeline.Recognition.LocalOcr(MangaLocalOcrEngine.ML_KIT), pipeline.recognition)
        assertEquals(MangaTranslationPipeline.Translation.GoogleText, pipeline.translation)
        assertEquals(MangaTranslationPipeline.Composition.LocalLayout(MangaInpaintEngine.LOCAL), pipeline.composition)
    }

    @Test fun `settings written before the engine choice decode as ai`() {
        val settings = MangaTranslationPlan.json.decodeFromString<MangaTranslationSettings>("{}")
        assertEquals(MangaTranslateEngine.AI, settings.textEngine)
    }

    @Test fun `follow settings stays dynamic in the pipeline view`() {
        val pipeline = pipeline(MangaTranslationSettings(locator = MangaLocator.LOCAL, textEngine = MangaTranslateEngine.FOLLOW_SETTINGS))
        assertEquals(MangaTranslationPipeline.Translation.FollowSettings, pipeline.translation)
    }

    @Test fun `follow settings resolves against the global translation provider`() {
        val settings = MangaTranslationSettings(textEngine = MangaTranslateEngine.FOLLOW_SETTINGS)
        assertEquals(MangaTranslateEngine.GOOGLE, settings.resolvedEngine(TranslationConstants.PROVIDER_GOOGLE))
        assertEquals(MangaTranslateEngine.AI, settings.resolvedEngine(TranslationConstants.PROVIDER_APP_AI))
        assertEquals(MangaTranslateEngine.AI, MangaTranslationSettings().resolvedEngine(TranslationConstants.PROVIDER_GOOGLE))
    }
}
