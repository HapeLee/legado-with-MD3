package io.legado.app.domain.model.manga

import org.junit.Assert.*
import org.junit.Test

class MangaAiTypesettingTest {
    private val source = MangaTextBox(100f, 100f, 400f, 400f)
    private val fixed = MangaTranslationPlan(listOf(MangaTranslatedRegion("r0", "原文", "Hello world", source)))
    private fun response(lines: String, extra: String = "") = """{"regions":[{"id":"r0","original":"原文","textBox":{"left":100,"top":100,"right":400,"bottom":400},"layoutBox":{"left":80,"top":80,"right":600,"bottom":600},"typesetting":{"lines":[$lines],"fontSize":16 $extra}}]}"""

    @Test fun `AI location always uses AI typesetting except whole page image editing`() {
        for (composition in MangaComposition.entries) for (locator in MangaLocator.entries) {
            assertEquals(locator == MangaLocator.AI && composition != MangaComposition.AI_PAGE,
                MangaTranslationSettings(locator = locator, composition = composition).usesAiTypesetting)
        }
    }

    @Test fun `settings no longer serialize removed recognition detail batch or typesetting switches`() {
        val encoded = MangaTranslationPlan.json.encodeToString(MangaTranslationSettings.serializer(), MangaTranslationSettings())
        for (field in listOf("aiRefinement", "aiCropBatchSize", "aiTypesetting")) assertFalse(encoded.contains("\"$field\""))
    }

    @Test fun `single response derives translation once from authored rows and persists the plan`() {
        val plan = parseMangaAiTypesetting(response("\"你好\",\"世界\""))
        assertEquals("你好\n世界", plan.regions.single().translation)
        assertEquals(listOf("你好", "世界"), plan.regions.single().typesetting!!.lines)
        assertEquals(16f, plan.regions.single().typesetting!!.fontSize)
        val stored = MangaTranslationPlan.json.encodeToString(MangaTranslationPlan.serializer(), plan)
        assertEquals(plan, MangaTranslationPlan.json.decodeFromString<MangaTranslationPlan>(stored).validate())
        assertNull(MangaTranslationPlan.parse(stored).regions.single().typesetting)
    }

    @Test fun `empty rows recover only explicit or fixed translations without inventing source text`() {
        for (rows in listOf("", "\"\"", "\" \",\"\\n\"")) {
            val raw = response(rows)
            assertEquals(listOf("Hello world"), parseMangaAiTypesetting(raw, fixed).regions.single().typesetting!!.lines)
            val explicit = raw.replace("\"original\":", "\"translation\":\"你好\\n世界\",\"original\":")
            assertEquals(listOf("你好", "世界"), parseMangaAiTypesetting(explicit).regions.single().typesetting!!.lines)
            assertThrows(IllegalArgumentException::class.java) { parseMangaAiTypesetting(raw) }
        }
        val missing = response("").replace("\"lines\":[],", "")
        assertEquals(listOf("Hello world"), parseMangaAiTypesetting(missing, fixed).regions.single().typesetting!!.lines)
        assertThrows(IllegalArgumentException::class.java) { parseMangaAiTypesetting(missing) }
        // Usable rows cannot silently discard a separate explicit translation.
        val mismatch = response("\"你好\"").replace("\"original\":", "\"translation\":\"你好世界\",\"original\":")
        assertThrows(IllegalArgumentException::class.java) { parseMangaAiTypesetting(mismatch) }
    }

    @Test fun `layout only cannot change translated characters punctuation or IDs`() {
        assertEquals(fixed.regions.single().translation, parseMangaAiTypesetting(response("\"Hello\",\"world\""), fixed).regions.single().translation)
        for (lines in listOf("\"Hi world\"", "\"Hello\"", "\"Hello world!\"", "\"Hel\",\"lo world\"")) {
            assertThrows(IllegalArgumentException::class.java) { parseMangaAiTypesetting(response(lines), fixed) }
        }
        assertThrows(IllegalArgumentException::class.java) { parseMangaAiTypesetting(response("\"Hello world\"").replace("r0", "r1"), fixed) }
    }

    @Test fun `reflow preserves word boundaries and graphemes while accepting harmless whitespace changes`() {
        assertTrue(mangaAiLinesPreserveText(listOf("Hello world"), "Hello\nworld"))
        assertTrue(mangaAiLinesPreserveText(listOf("你好世界"), "你好\n世界"))
        assertFalse(mangaAiLinesPreserveText(listOf("therapist"), "the rapist"))
        assertFalse(mangaAiLinesPreserveText(listOf("hel", "lo"), "hello"))
        assertFalse(mangaAiLinesPreserveText(listOf("👨‍", "👩‍👧"), "👨‍👩‍👧"))
        assertTrue(mangaAiLinesPreserveText(listOf("Hello", "👨‍👩‍👧"), "Hello 👨‍👩‍👧"))
    }

    @Test fun `invalid fonts and styles fail without becoming unbounded drawing instructions`() {
        for (bad in listOf(response("\"你好\"", ",\"lineSpacing\":100"), response("\"你好\"", ",\"color\":\"transparent\""),
            response("\"\""), response("\"你好\"").replace("\"fontSize\":16", "\"fontSize\":0"))) {
            assertThrows(IllegalArgumentException::class.java) { parseMangaAiTypesetting(bad) }
        }
    }

    @Test fun `out of frame and inverted boxes identify the exact region field and values`() {
        val valid = response("\"你好\"")
        for ((bad, path) in listOf(
            valid.replace("\"right\":400", "\"right\":1400") to "r0.textBox",
            valid.replace("\"bottom\":600", "\"bottom\":1200") to "r0.layoutBox",
            valid.replace("\"top\":100", "\"top\":-1") to "r0.textBox",
            valid.replace("\"right\":400", "\"right\":50") to "r0.textBox",
            valid.replace("\"typesetting\":", "\"eraseBoxes\":[{\"left\":100,\"top\":100,\"right\":400,\"bottom\":1400}],\"typesetting\":") to "r0.eraseBoxes[0]",
        )) {
            val error = assertThrows(MangaAiLayoutCoordinateException::class.java) { parseMangaAiTypesetting(bad) }
            assertTrue(error.message, error.message!!.contains(path))
            assertTrue(error.message!!.contains("left="))
        }
        // Source ink can legitimately touch all four image boundaries.
        parseMangaAiTypesetting(valid.replace("\"left\":100,\"top\":100,\"right\":400,\"bottom\":400",
            "\"left\":0,\"top\":0,\"right\":1000,\"bottom\":1000"))
    }

    @Test fun `coordinate correction must retain completed source translation and identity`() {
        val before = response("\"你好\",\"世界\"").replace("\"bottom\":600", "\"bottom\":1200")
        val corrected = parseMangaAiTypesetting(response("\"你好世界\""), previousResponse = before)
        assertEquals("你好世界", corrected.regions.single().translation)
        for (bad in listOf(response("\"您好世界\""), response("\"你好世界\"").replace("原文", "改写"),
            response("\"你好世界\"").replace("r0", "r1"), """{"regions":[],"noText":true}""")) {
            assertThrows(IllegalArgumentException::class.java) { parseMangaAiTypesetting(bad, previousResponse = before) }
        }
    }

    @Test fun `remote embedded breaks and blank formatting canonicalize without losing fixed words`() {
        val plan = parseMangaAiTypesetting(response("\"Hello\\r\\nworld\",\"\""), fixed)
        assertEquals(listOf("Hello", "world"), plan.regions.single().typesetting!!.lines)
        assertEquals("Hello world", plan.regions.single().translation)
        assertEquals(listOf("Hello world"), parseMangaAiTypesetting(response("\"Hello\\tworld\""), fixed).regions.single().typesetting!!.lines)
        assertThrows(IllegalArgumentException::class.java) { parseMangaAiTypesetting(response("\"Hello\\u0000world\""), fixed) }
        assertThrows(IllegalArgumentException::class.java) { parseMangaAiTypesetting(response("\"Hel\\nlo world\""), fixed) }
    }

    @Test fun `layout only preserves recognized source direction independently from target flow`() {
        val verticalSource = fixed.copy(regions = fixed.regions.map { it.copy(vertical = true) })
        val result = parseMangaAiTypesetting(response("\"Hello world\""), verticalSource).regions.single()
        assertTrue(result.vertical)
        assertFalse(result.typesetting!!.vertical)
    }

    @Test fun `remote string rows and lowercase alignment retain exact text`() {
        val raw = response("\"Hello world\"").replace("[\"Hello world\"]", "\"Hello\\nworld\"")
            .replace("\"fontSize\":16", "\"fontSize\":16,\"align\":\"center\",\"verticalAlign\":\"top\"")
        val style = parseMangaAiTypesetting(raw, fixed).regions.single().typesetting!!
        assertEquals(listOf("Hello", "world"), style.lines)
        assertEquals(MangaTextAlign.CENTER, style.align)
        assertEquals(MangaTextVerticalAlign.TOP, style.verticalAlign)
    }
}
