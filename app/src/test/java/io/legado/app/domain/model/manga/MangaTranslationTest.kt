package io.legado.app.domain.model.manga

import org.junit.Assert.*
import org.junit.Test

class MangaTranslationTest {
    @Test fun `legacy OCR settings decode to bundled engine and independent choices round trip`() {
        val json = MangaTranslationPlan.json
        val legacy = json.decodeFromString<MangaTranslationSettings>("{}")
        assertEquals(MangaLocalOcrEngine.ML_KIT, legacy.localOcrEngine)
        assertEquals("", legacy.ocrModelId)
        val configured = legacy.copy(localOcrEngine = MangaLocalOcrEngine.PADDLE_V5, ocrModelId = "vision", modelId = "text")
        assertEquals(configured, json.decodeFromString<MangaTranslationSettings>(json.encodeToString(MangaTranslationSettings.serializer(), configured)))
    }
    private val box = MangaTextBox(100f, 100f, 600f, 500f)
    private val region = MangaTranslatedRegion("r0", "原文", "译文", box)

    @Test fun `remote response cannot claim locally verified geometry`() {
        val response = MangaTranslationPlan.json.encodeToString(MangaTranslationPlan.serializer(),
            MangaTranslationPlan(listOf(region.copy(recognitionBox = box.copy(right = 900f))), localGeometryVersion = 99))
        assertEquals(0, MangaTranslationPlan.parse(response).localGeometryVersion)
        assertNull(MangaTranslationPlan.parse(response).regions.single().recognitionBox)
    }

    @Test fun `text translation preserves detector geometry original text and reading order`() {
        val detected = listOf(region.copy(translation = "原文", layoutBox = box.copy(right = 900f), vertical = true, sourceFontSize = 30f),
            region.copy(id = "r1", original = "名前"))
        val output = """{"translations":[{"id":"r1","translation":"名字"},{"id":"r0","translation":"新译文","original":"wrong","textBox":{"left":0}}],"terms":{"名前":"名字"}}"""
        val plan = MangaTranslationPlan.translateDetected("```json\n$output\n```", detected)
        assertEquals(detected.map { it.copy(translation = if (it.id == "r0") "新译文" else "名字") }, plan.regions)
        assertEquals(mapOf("名前" to "名字"), plan.terms)
    }

    @Test fun `text translation rejects missing duplicate unexpected or blank translations`() {
        for (entries in listOf("", """{"id":"r0","translation":""}""",
            """{"id":"other","translation":"Hi"}""",
            """{"id":"r0","translation":"Hi"},{"id":"r0","translation":"Hi"}""")) {
            assertThrows(IllegalArgumentException::class.java) {
                MangaTranslationPlan.translateDetected("""{"translations":[$entries]}""", listOf(region))
            }
        }
    }

    @Test fun `compact vision response defaults layout and erasure to source anchors`() {
        val plan = MangaTranslationPlan.parse("""{"regions":[{"id":"r0","original":"Hello","translation":"你好","textBox":{"left":100,"top":100,"right":600,"bottom":500}}]}""")
        assertEquals(box, plan.regions.single().layoutBox)
        assertEquals(listOf(box), plan.regions.single().eraseBoxes)
    }

    @Test fun `paid responses cannot silently lose or duplicate regions`() {
        assertThrows(IllegalArgumentException::class.java) { MangaTranslationPlan(listOf(region, region)).validate() }
        assertThrows(IllegalArgumentException::class.java) { MangaTranslationPlan(listOf(region)).validate(setOf("r0", "r1")) }
        assertThrows(IllegalArgumentException::class.java) { MangaTranslationPlan(emptyList()).validate() }
        assertThrows(IllegalArgumentException::class.java) { MangaTranslationPlan(listOf(region.copy(translation = ""))).validate() }
        assertTrue(MangaTranslationPlan(emptyList(), noText = true).validate().noText)
    }

    @Test fun `all erase and layout coordinates must be finite and inside the image`() {
        for (bad in listOf(box.copy(left = -1f), box.copy(right = Float.NaN), box.copy(bottom = 1001f), box.copy(right = 50f))) {
            assertThrows(IllegalArgumentException::class.java) { MangaTranslationPlan(listOf(region.copy(eraseBoxes = listOf(bad)))).validate() }
        }
    }

    @Test fun `markdown wrapped JSON retains source text and independent erase boxes`() {
        val plan = MangaTranslationPlan(listOf(region.copy(layoutBox = box.copy(right = 900f))))
        val encoded = MangaTranslationPlan.json.encodeToString(MangaTranslationPlan.serializer(), plan)
        assertEquals(plan, MangaTranslationPlan.parse("```json\n$encoded\n```", setOf("r0")))
    }

    @Test fun `tall-page core bands cover every original row exactly once with bounded working pixels`() {
        val bands = mangaTranslationBands(2048, 120_001)
        assertEquals(0, bands.first().coreTop)
        assertEquals(120_001, bands.last().coreBottom)
        assertEquals(120_001, bands.sumOf { it.coreBottom - it.coreTop })
        bands.zipWithNext().forEach { (a, b) -> assertEquals(a.coreBottom, b.coreTop); assertTrue(a.bottom > b.top) }
        assertTrue(bands.all { (it.bottom - it.top).toLong() * 2048 <= 3 * 1024 * 1024 })
    }

    @Test fun `whole-page analysis depends on page shape instead of render tile height`() {
        assertTrue(mangaTranslationWholePage(2400, 3600))
        assertTrue(mangaTranslationWholePage(6000, 9000))
        assertTrue(mangaTranslationWholePage(3000, 1000))
        assertFalse(mangaTranslationWholePage(800, 12000))
        assertFalse(mangaTranslationWholePage(0, 1000))
    }

    @Test fun `analysis previews preserve aspect ratio with bounded pixels and decoding samples`() {
        for ((width, height) in listOf(6000 to 9000, 8192 to 24000, 800 to 2400, 64 to 96)) {
            val preview = mangaTranslationPreview(width, height)
            assertTrue(preview.width.toLong() * preview.height <= 2 * 1024 * 1024)
            assertTrue(maxOf(preview.width, preview.height) <= 3072)
            assertTrue(kotlin.math.abs(preview.width.toFloat() / preview.height - width.toFloat() / height) < 0.002f)
            assertTrue(preview.sampleSize and (preview.sampleSize - 1) == 0)
            assertTrue((width.toLong() / preview.sampleSize) * (height / preview.sampleSize) < 8 * 1024 * 1024)
        }
        assertEquals(MangaTranslationPreview(64, 96, 1), mangaTranslationPreview(64, 96))
    }

    @Test fun `analysis boundaries prefer gutters but avoid the interior of complete text blocks`() {
        assertEquals(1800, mangaTranslationBoundary(1536, 1000, 2000, listOf(1300..1700), listOf(1500..1550, 1800..1850)))
        val withoutGutter = mangaTranslationBoundary(1536, 1000, 2000, listOf(1300..1700), emptyList())
        assertTrue(withoutGutter == 1300 || withoutGutter == 1700)
        assertThrows(IllegalStateException::class.java) {
            mangaTranslationBoundary(1536, 1000, 2000, listOf(500..2500), emptyList())
        }
    }
}
