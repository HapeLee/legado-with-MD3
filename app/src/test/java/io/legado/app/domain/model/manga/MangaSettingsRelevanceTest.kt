package io.legado.app.domain.model.manga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 设置项生效与否只能有一个答案：界面隐藏与说明都取自这里。 */
class MangaSettingsRelevanceTest {
    private val full = MangaProductCapabilities(localEngine = true)
    private val core = MangaProductCapabilities(localEngine = false)

    private fun smart(transform: (MangaTranslationSettings) -> MangaTranslationSettings = { it }) =
        MangaSettingsRelevance.of(transform(MangaTranslationSettings(ocrModelId = "vision")), full)

    @Test fun `local layout keeps every composition knob active`() {
        val relevance = smart()
        assertTrue(relevance.isActive(MangaSettingField.INPAINT_ENGINE))
        assertTrue(relevance.isActive(MangaSettingField.BUBBLE_SOURCE))
        assertTrue(relevance.isActive(MangaSettingField.FONT_SCALE))
        assertTrue(relevance.isActive(MangaSettingField.GEOMETRY_DIAGNOSTICS))
        // 本地排版不经过图片模型。
        assertEquals(MangaSettingReason.LOCAL_COMPOSITION, relevance.ignored[MangaSettingField.IMAGE_MODEL])
        assertTrue(relevance.degraded.isEmpty())
    }

    @Test fun `patch keeps the source lettering so the inpaint choice is ignored`() {
        val relevance = smart { it.copy(composition = MangaComposition.PATCH) }
        assertEquals(MangaSettingReason.SOURCE_TEXT_KEPT, relevance.ignored[MangaSettingField.INPAINT_ENGINE])
        assertTrue(relevance.isActive(MangaSettingField.BUBBLE_SOURCE))
        assertTrue(relevance.isActive(MangaSettingField.FONT_SCALE))
    }

    @Test fun `ai repair still typesets locally but never erases locally`() {
        val relevance = smart { it.copy(composition = MangaComposition.AI_REPAIR) }
        assertEquals(MangaSettingReason.AI_IMAGE_OWNS_COMPOSITION, relevance.ignored[MangaSettingField.INPAINT_ENGINE])
        assertTrue(relevance.isActive(MangaSettingField.FONT_SCALE))
    }

    @Test fun `ai region hands both erase and fit to the model`() {
        val relevance = smart { it.copy(composition = MangaComposition.AI_REGION) }
        assertEquals(MangaSettingReason.AI_IMAGE_OWNS_COMPOSITION, relevance.ignored[MangaSettingField.FONT_SCALE])
        assertTrue(relevance.isActive(MangaSettingField.IMAGE_MODEL))
    }

    @Test fun `whole page edit silences every local stage but keeps the image model`() {
        val relevance = smart { it.copy(composition = MangaComposition.AI_PAGE) }
        listOf(
            MangaSettingField.LOCATOR, MangaSettingField.TEXT_ENGINE, MangaSettingField.TEXT_MODEL,
            MangaSettingField.REASONING_LEVEL, MangaSettingField.INPAINT_ENGINE,
            MangaSettingField.BUBBLE_SOURCE, MangaSettingField.FONT_SCALE,
            MangaSettingField.GEOMETRY_DIAGNOSTICS,
        ).forEach { assertEquals(it.name, MangaSettingReason.PAGE_EDIT_OWNS_PAGE, relevance.ignored[it]) }
        assertTrue(relevance.isActive(MangaSettingField.IMAGE_MODEL))
        assertTrue(relevance.isActive(MangaSettingField.IMAGE_EDIT_PATH))
        assertTrue(relevance.isActive(MangaSettingField.TARGET_LANGUAGE))
    }

    @Test fun `fused recognition keeps the text engine switchable`() {
        val relevance = MangaSettingsRelevance.of(MangaTranslationSettings(ocrModelId = ""), full)
        // 合并只说明当前没有独立文字阶段；换引擎后识别沿用翻译模型，选项不能禁掉。
        assertNull(relevance.ignored[MangaSettingField.TEXT_ENGINE])
        assertTrue(relevance.isActive(MangaSettingField.TEXT_ENGINE))
        assertTrue(relevance.isActive(MangaSettingField.TEXT_MODEL))
    }

    @Test fun `google text engine drops the model id but keeps the reasoning level recognition uses`() {
        val relevance = smart { it.copy(textEngine = MangaTranslateEngine.GOOGLE) }
        assertEquals(MangaSettingReason.TEXT_ENGINE_NOT_AI, relevance.ignored[MangaSettingField.TEXT_MODEL])
        // 识别仍是 AI 请求：思考强度照常作用于识别与排版。
        assertNull(relevance.ignored[MangaSettingField.REASONING_LEVEL])
        assertTrue(relevance.isActive(MangaSettingField.TEXT_ENGINE))
    }

    @Test fun `fused recognition keeps the translation model visible for a non-ai engine`() {
        val relevance = MangaSettingsRelevance.of(
            MangaTranslationSettings(ocrModelId = "", textEngine = MangaTranslateEngine.GOOGLE), full
        )
        // 合并态下翻译模型就是识别模型，隐藏它会让用户既看不见也改不了实际在跑的模型。
        assertNull(relevance.ignored[MangaSettingField.TEXT_MODEL])
        assertNull(relevance.ignored[MangaSettingField.REASONING_LEVEL])
    }

    @Test fun `follow settings moves the model choice to the global preset`() {
        val relevance = smart { it.copy(textEngine = MangaTranslateEngine.FOLLOW_SETTINGS) }
        assertEquals(MangaSettingReason.TEXT_FOLLOWS_GLOBAL, relevance.ignored[MangaSettingField.TEXT_MODEL])
    }

    @Test fun `ai vision alone ignores ocr preprocessing but keeps local engine list`() {
        val relevance = smart()
        assertEquals(MangaSettingReason.RECOGNITION_IS_AI, relevance.ignored[MangaSettingField.OCR_OPTIONS])
        assertEquals(MangaSettingReason.RECOGNITION_NOT_LOCAL, relevance.ignored[MangaSettingField.LOCAL_OCR_ENGINE])
        assertTrue(relevance.isActive(MangaSettingField.OCR_MODEL))
    }

    @Test fun `model inpaint is reported as degraded without a local runtime`() {
        val relevance = MangaSettingsRelevance.of(
            MangaTranslationSettings(inpaintEngine = MangaInpaintEngine.MI_GAN), core
        )
        assertEquals(MangaSettingReason.PRODUCT_WITHOUT_LOCAL_ENGINE, relevance.degraded[MangaSettingField.INPAINT_ENGINE])
        assertNull(relevance.ignored[MangaSettingField.INPAINT_ENGINE])
    }

    @Test fun `notes group inactive fields by reason`() {
        val relevance = smart { it.copy(composition = MangaComposition.PATCH) }
        val grouped = relevance.inactiveOf(
            listOf(MangaSettingField.INPAINT_ENGINE, MangaSettingField.IMAGE_MODEL, MangaSettingField.BUBBLE_SOURCE)
        )
        assertEquals(listOf(MangaSettingField.INPAINT_ENGINE), grouped[MangaSettingReason.SOURCE_TEXT_KEPT])
        assertEquals(listOf(MangaSettingField.IMAGE_MODEL), grouped[MangaSettingReason.LOCAL_COMPOSITION])
        assertFalse(grouped.values.flatten().contains(MangaSettingField.BUBBLE_SOURCE))
    }

    @Test fun `stored on-device choices are reported as degraded without a local runtime`() {
        val stored = MangaTranslationSettings(locator = MangaLocator.LOCAL, inpaintEngine = MangaInpaintEngine.MI_GAN,
            bubbleSource = MangaBubbleSource.DEVICE)
        val relevance = MangaSettingsRelevance.of(
            MangaTranslationSettings(locator = MangaLocator.AI, inpaintEngine = MangaInpaintEngine.BUILTIN,
                bubbleSource = MangaBubbleSource.OFF),
            core,
            stored,
        )
        assertEquals(MangaSettingReason.PRODUCT_WITHOUT_LOCAL_ENGINE, relevance.degraded[MangaSettingField.LOCATOR])
        assertEquals(MangaSettingReason.PRODUCT_WITHOUT_LOCAL_ENGINE, relevance.degraded[MangaSettingField.INPAINT_ENGINE])
        assertEquals(MangaSettingReason.PRODUCT_WITHOUT_LOCAL_ENGINE, relevance.degraded[MangaSettingField.BUBBLE_SOURCE])
    }

    @Test fun `the comic build never reports the same stored choices as degraded`() {
        val stored = MangaTranslationSettings(locator = MangaLocator.LOCAL, inpaintEngine = MangaInpaintEngine.MI_GAN,
            bubbleSource = MangaBubbleSource.DEVICE)
        val relevance = MangaSettingsRelevance.of(stored, full, stored)
        assertTrue(relevance.degraded.isEmpty())
    }
}
