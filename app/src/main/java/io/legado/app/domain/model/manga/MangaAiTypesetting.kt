package io.legado.app.domain.model.manga

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
enum class MangaTextAlign { LEFT, CENTER, RIGHT }

@Serializable
enum class MangaTextVerticalAlign { TOP, CENTER, BOTTOM }

/** An AI-authored paragraph: the renderer keeps its region, direction and explicit rows/columns. */
@Serializable
data class MangaAiTypesetting(
    val lines: List<String> = emptyList(),
    /** Typeface em size, normalized by the attached frame's HEIGHT. */
    val fontSize: Float,
    val vertical: Boolean = false,
    val align: MangaTextAlign = MangaTextAlign.CENTER,
    val verticalAlign: MangaTextVerticalAlign = MangaTextVerticalAlign.CENTER,
    val lineSpacing: Float = 1.2f,
    val bold: Boolean = false,
    val color: String? = null,
) {
    fun validate(translation: String) {
        require(lines.isNotEmpty() && lines.size <= 128 && lines.all { it.isNotBlank() && it.none(Character::isISOControl) } && lines.sumOf { it.length } <= 8000) {
            "Invalid AI text rows (rows=${lines.size}, blank=${lines.count { it.isBlank() }}, controls=${lines.count { it.any(Character::isISOControl) }})"
        }
        require(fontSize.isFinite() && fontSize > 0 && fontSize <= 1000 && lineSpacing.isFinite() && lineSpacing in .8f..2f) { "Invalid AI font size or spacing" }
        require(color == null || color.matches(Regex("#[0-9a-fA-F]{6}"))) { "Invalid AI text color" }
        require(mangaAiLinesPreserveText(lines, translation)) { "AI typesetting changed or omitted translation text" }
    }
}

/** Reflow may consume existing whitespace, but may not rewrite text or break a Latin word. */
fun mangaAiLinesPreserveText(lines: List<String>, text: String): Boolean {
    fun plain(value: String) = value.filterNot { it.isWhitespace() }
    val planned = lines.joinToString("\n")
    val content = plain(text)
    if (plain(planned) != content) return false
    fun wordLetter(point: Int): Boolean = Character.isLetterOrDigit(point) && Character.UnicodeScript.of(point) !in
        setOf(Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA, Character.UnicodeScript.HANGUL)
    fun wordGaps(value: String): Set<Int> {
        val gaps = mutableSetOf<Int>(); var index = 0; var count = 0
        while (index < value.length) {
            if (!value[index].isWhitespace()) { index++; count++; continue }
            val start = index
            while (index < value.length && value[index].isWhitespace()) index++
            if (start > 0 && index < value.length && wordLetter(value.codePointBefore(start)) && wordLetter(value.codePointAt(index))) gaps += count
        }
        return gaps
    }
    if (wordGaps(planned) != wordGaps(text)) return false
    var boundary = 0
    for (line in lines.dropLast(1)) {
        boundary += plain(line).length
        if (boundary <= 0 || boundary >= content.length) continue
        if (content[boundary].isLowSurrogate()) return false
        val before = content.codePointBefore(boundary); val after = content.codePointAt(boundary)
        if (before == 0x200d || after == 0x200d || after in 0xfe00..0xfe0f || after in 0x1f3fb..0x1f3ff ||
            Character.getType(after) in setOf(Character.NON_SPACING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt(), Character.ENCLOSING_MARK.toInt())) return false
        if (after in 0x1f1e6..0x1f1ff) {
            var offset = boundary; var regional = 0
            while (offset > 0 && content.codePointBefore(offset) in 0x1f1e6..0x1f1ff) {
                offset -= Character.charCount(content.codePointBefore(offset)); regional++
            }
            if (regional % 2 != 0) return false
        }
    }
    return true
}

@Serializable
private data class MangaAiPlannedBlock(
    val id: String, val textBox: MangaTextBox, val layoutBox: MangaTextBox, val typesetting: MangaAiTypesetting,
    val original: String? = null, val translation: String? = null, val eraseBoxes: List<MangaTextBox> = listOf(textBox),
    val vertical: Boolean = false, val sourceColumnCount: Int? = null, val aiGeometry: MangaAiGeometry? = null,
)

@Serializable
private data class MangaAiPlannedPage(val regions: List<MangaAiPlannedBlock>, val terms: Map<String, String> = emptyMap(),
    val noText: Boolean = false, val flows: List<MangaDialogueFlow>? = null)

/** Only coordinate failures are eligible for one visual correction; never retry arbitrary failures. */
class MangaAiLayoutCoordinateException(message: String) : IllegalArgumentException(message)

private fun MangaTextBox.validateAiLayout(path: String) {
    val values = "left=$left, top=$top, right=$right, bottom=$bottom"
    if (listOf(left, top, right, bottom).any { !it.isFinite() || it !in 0f..1000f })
        throw MangaAiLayoutCoordinateException("$path: coordinates must be in 0..1000 ($values)")
    if (right <= left || bottom <= top)
        throw MangaAiLayoutCoordinateException("$path: require left < right and top < bottom ($values)")
}

private fun decodeMangaAiTypesetting(response: String): MangaAiPlannedPage {
    val clean = response.trim().let { if (it.startsWith("```")) it.substringAfter('\n').substringBeforeLast("```").trim() else it }
    val json = MangaTranslationPlan.json
    val root = json.parseToJsonElement(clean).jsonObject
    val normalized = JsonObject(root + ("regions" to JsonArray(root.getValue("regions").jsonArray.map { element ->
        val block = element.jsonObject
        val style = block["typesetting"]?.takeUnless { it == JsonNull }?.jsonObject ?: return@map element
        val fields = style.toMutableMap()
        (fields["lines"] as? JsonPrimitive)?.takeIf { it.isString }?.let { fields["lines"] = JsonArray(listOf(it)) }
        for (key in listOf("align", "verticalAlign")) {
            (fields[key] as? JsonPrimitive)?.takeIf { it.isString }?.let { fields[key] = JsonPrimitive(it.content.uppercase()) }
        }
        JsonObject(block + ("typesetting" to JsonObject(fields)))
    })))
    return json.decodeFromJsonElement<MangaAiPlannedPage>(normalized).let { page ->
        page.copy(regions = page.regions.map { block ->
            // Providers sometimes put explicit line breaks inside an array item. Canonicalize
            // those breaks without dropping characters; stored plans still use strict rows.
            val rows = block.typesetting.lines.flatMap { it.replace("\r\n", "\n").replace('\r', '\n').split('\n') }
                .map { it.replace('\t', ' ').trim() }.filter { it.isNotEmpty() }
            block.copy(typesetting = block.typesetting.copy(lines = rows))
        })
    }
}

/** The remote plan cannot provide local masks/measurements, and fixed translations stay exact. */
fun parseMangaAiTypesetting(response: String, translated: MangaTranslationPlan? = null, previousResponse: String? = null): MangaTranslationPlan {
    val output = decodeMangaAiTypesetting(response)
    val expected = translated?.regions?.associateBy { it.id }
    val regions = output.regions.map { block ->
        val previous = expected?.get(block.id)
        require(expected == null || previous != null) { "Unexpected AI layout region" }
        val original = previous?.original ?: requireNotNull(block.original) { "Missing source text in AI layout" }
        require(previous == null || block.original == null || block.original == original) { "AI layout changed source text" }
        val text = previous?.translation ?: block.translation?.takeIf { it.isNotBlank() }
            ?: block.typesetting.lines.joinToString("\n")
        // Empty formatting is recoverable only from an explicit, already available translation.
        // Never substitute the source text or invent a translation for an empty candidate.
        val style = if (block.typesetting.lines.isEmpty() && text.isNotBlank()) block.typesetting.copy(
            lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
                .map { it.replace('\t', ' ').trim() }.filter { it.isNotEmpty() }
        ) else block.typesetting
        try { style.validate(text) }
        catch (failure: IllegalArgumentException) { throw IllegalArgumentException("${block.id}: ${failure.message}", failure) }
        block.textBox.validateAiLayout("${block.id}.textBox")
        block.layoutBox.validateAiLayout("${block.id}.layoutBox")
        block.eraseBoxes.forEachIndexed { index, box -> box.validateAiLayout("${block.id}.eraseBoxes[$index]") }
        block.aiGeometry?.let { geometry ->
            geometry.safeBox?.validateAiLayout("${block.id}.aiGeometry.safeBox")
            geometry.containerBox?.validateAiLayout("${block.id}.aiGeometry.containerBox")
            geometry.glyphSamples.forEachIndexed { index, glyph -> glyph.bounds.validateAiLayout("${block.id}.aiGeometry.glyphSamples[$index].bounds") }
        }
        block.aiGeometry?.safeBox?.let { require(it.contains(block.layoutBox, .1f)) { "AI layout outside its safe area" } }
        block.aiGeometry?.containerBox?.let { require(it.contains(block.layoutBox, .1f)) { "AI layout outside its container" } }
        MangaTranslatedRegion(block.id, original, text, block.textBox, layoutBox = block.layoutBox,
            eraseBoxes = block.eraseBoxes, vertical = previous?.vertical ?: block.vertical, aiGeometry = block.aiGeometry, typesetting = style, sourceColumnCount = previous?.sourceColumnCount ?: block.sourceColumnCount)
    }
    val plan = MangaTranslationPlan(regions, translated?.terms ?: output.terms, output.noText,
        flows = translated?.flows ?: output.flows.orEmpty()).validate(expected?.keys)
    previousResponse?.let { responseBeforeCorrection ->
        val before = decodeMangaAiTypesetting(responseBeforeCorrection)
        require(before.noText == plan.noText && before.regions.map { it.id }.toSet() == plan.regions.map { it.id }.toSet() &&
            before.regions.size == plan.regions.size) { "AI coordinate correction changed text region IDs" }
        before.regions.zip(plan.regions).forEach { (old, current) ->
            require(old.id == current.id && (old.original == null || old.original == current.original) &&
                mangaAiLinesPreserveText(current.typesetting!!.lines, old.typesetting.lines.joinToString("\n"))) {
                "AI coordinate correction changed text or reading order"
            }
        }
    }
    return if (expected == null) plan else plan.copy(regions = expected.keys.map { id -> plan.regions.single { it.id == id } })
}
