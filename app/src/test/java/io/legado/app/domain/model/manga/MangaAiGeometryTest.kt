package io.legado.app.domain.model.manga

import org.junit.Assert.*
import org.junit.Test

class MangaAiGeometryTest {
    private val box = MangaTextBox(100f, 100f, 300f, 400f)
    private val region = MangaTranslatedRegion("r0", "原文", "译文", box,
        aiGeometry = MangaAiGeometry("b0", MangaTextBox(50f, 50f, 400f, 500f), MangaTextBox(80f, 80f, 350f, 450f),
            listOf(MangaSourceGlyph("原", MangaTextBox(110f, 110f, 150f, 150f)))))

    @Test fun `recognition geometry and ordered flows survive storage and text translation`() {
        val plan = MangaTranslationPlan(listOf(region, region.copy(id = "r1")), flows = listOf(MangaDialogueFlow("f", listOf("r1", "r0"))))
        val json = MangaTranslationPlan.json
        val decoded = json.decodeFromString<MangaTranslationPlan>(json.encodeToString(MangaTranslationPlan.serializer(), plan)).validate()
        assertEquals(plan, decoded)
        val translated = MangaTranslationPlan.translateDetected("""{"translations":[{"id":"r0","translation":"first"},{"id":"r1","translation":"second"}]}""", decoded.regions, decoded.flows)
        assertEquals(plan.flows, translated.flows)
        assertEquals(region.aiGeometry, translated.regions[0].aiGeometry)
    }

    @Test fun `unknown duplicate or conflicting geometry cannot be published`() {
        val second = region.copy(id = "r1")
        for (flows in listOf(listOf(MangaDialogueFlow("f", listOf("missing"))), listOf(MangaDialogueFlow("f", listOf("r0", "r0"))),
            listOf(MangaDialogueFlow("f", listOf("r0")), MangaDialogueFlow("g", listOf("r0"))))) {
            assertThrows(IllegalArgumentException::class.java) { MangaTranslationPlan(listOf(region, second), flows = flows).validate() }
        }
        for (bad in listOf(region.aiGeometry!!.copy(safeBox = box.copy(right = 800f)),
            region.aiGeometry.copy(glyphSamples = listOf(MangaSourceGlyph("原", box.copy(left = 0f)))))) {
            assertThrows(IllegalArgumentException::class.java) { MangaTranslationPlan(listOf(region.copy(aiGeometry = bad))).validate() }
        }
        assertThrows(IllegalArgumentException::class.java) {
            MangaTranslationPlan(listOf(region, second.copy(aiGeometry = region.aiGeometry.copy(containerBox = box)))).validate()
        }
    }

    @Test fun `vision cannot inject a measured font mask or local provenance`() {
        val remote = MangaTranslationPlan(listOf(region.copy(sourceFontSize = 900f,
            fontMeasurement = MangaFontMeasurement(900f, 2, MangaFontEvidence.ORIGINAL_GLYPHS))), localGeometryVersion = 99)
        val parsed = MangaTranslationPlan.parse(MangaTranslationPlan.json.encodeToString(MangaTranslationPlan.serializer(), remote))
        assertNull(parsed.regions.single().sourceFontSize)
        assertNull(parsed.regions.single().fontMeasurement)
        assertEquals(region.aiGeometry, parsed.regions.single().aiGeometry)
        assertEquals(0, parsed.localGeometryVersion)
    }
}
