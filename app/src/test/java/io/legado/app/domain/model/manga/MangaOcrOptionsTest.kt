package io.legado.app.domain.model.manga

import org.junit.Assert.*
import org.junit.Test

class MangaOcrOptionsTest {
    @Test fun `legacy settings preserve old detector input and model-specific defaults`() {
        val settings = MangaTranslationPlan.json.decodeFromString<MangaTranslationSettings>("{}")
        assertEquals(MangaOcrOptions(), settings.ocrOptions)
        assertEquals(960, settings.ocrOptions.detectionProfile.maxSide)
        assertEquals(MangaOcrDetectionThresholds(.3f, .6f, 1.5f), settings.ocrOptions.detectionThresholds(MangaLocalOcrEngine.PADDLE_V5))
        assertEquals(MangaOcrDetectionThresholds(.2f, .4f, 1.4f), settings.ocrOptions.detectionThresholds(MangaLocalOcrEngine.PADDLE_V6_TINY))
        assertEquals(MangaOcrDetectionThresholds(.2f, .45f, 1.4f), settings.ocrOptions.detectionThresholds(MangaLocalOcrEngine.PADDLE_V6_SMALL))
    }

    @Test fun `partial overrides retain remaining model defaults and mode is independent`() {
        val options = MangaOcrOptions(detectionProfile = MangaOcrDetectionProfile.ACCURATE, probabilityThreshold = .4f)
        assertEquals(1920, options.detectionProfile.maxSide)
        assertEquals(MangaOcrDetectionThresholds(.4f, .6f, 1.5f), options.detectionThresholds(MangaLocalOcrEngine.PADDLE_V5))
        assertEquals(MangaOcrDetectionThresholds(.4f, .45f, 1.4f), options.detectionThresholds(MangaLocalOcrEngine.PADDLE_V6_MEDIUM))
        val settings = MangaTranslationSettings(ocrOptions = options.copy(upscale2x = true, binarize = true, invert = true))
        assertEquals(settings, MangaTranslationPlan.json.decodeFromString<MangaTranslationSettings>(
            MangaTranslationPlan.json.encodeToString(MangaTranslationSettings.serializer(), settings)))
    }

    @Test fun `invalid thresholds cannot enter inference or saved settings`() {
        for (bad in listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 2f)) {
            assertTrue(runCatching { MangaOcrOptions(probabilityThreshold = bad).validate() }.isFailure)
            assertTrue(runCatching { MangaOcrOptions(boxScoreThreshold = bad).validate() }.isFailure)
        }
        assertTrue(runCatching { MangaOcrOptions(unclipRatio = 0f).validate() }.isFailure)
        assertTrue(runCatching { MangaOcrOptions(unclipRatio = 4f).validate() }.isFailure)
        assertTrue(runCatching { MangaOcrOptions().detectionThresholds(MangaLocalOcrEngine.ML_KIT) }.isFailure)
    }

    @Test fun `cache identity only includes controls that affect the selected OCR route`() {
        assertEquals("", MangaOcrOptions().analysisIdentity(true))
        val detection = MangaOcrOptions(detectionProfile = MangaOcrDetectionProfile.ACCURATE, unclipRatio = 2f)
        assertEquals("", detection.analysisIdentity(false))
        assertTrue(detection.analysisIdentity(true).contains("ACCURATE"))
        assertNotEquals(MangaOcrOptions(upscale2x = true).analysisIdentity(false), MangaOcrOptions(invert = true).analysisIdentity(false))
    }
}
