package io.legado.app.domain.model.manga

import kotlinx.serialization.Serializable

@Serializable
enum class MangaGlyphRole { BODY, RUBY }

@Serializable
data class MangaSourceGlyph(val text: String, val bounds: MangaTextBox, val role: MangaGlyphRole = MangaGlyphRole.BODY)

/** Suggestions from vision, distinct from locally verified masks and measurements. */
@Serializable
data class MangaAiGeometry(
    val containerId: String? = null,
    val containerBox: MangaTextBox? = null,
    val safeBox: MangaTextBox? = null,
    val glyphSamples: List<MangaSourceGlyph> = emptyList(),
)

@Serializable
data class MangaDialogueFlow(val id: String, val regionIds: List<String>)

@Serializable
enum class MangaFontEvidence { ORIGINAL_GLYPHS, ORIGINAL_INK }

/** Glyph height is normalized by the original analysis band's height, never a font's pt/sp. */
@Serializable
data class MangaFontMeasurement(val glyphHeight: Float, val samples: Int, val evidence: MangaFontEvidence, val version: Int = 1)

@Serializable
data class MangaAiTextBlock(
    val id: String, val original: String, val textBox: MangaTextBox, val vertical: Boolean = false,
    val sourceLines: List<MangaSourceLine> = emptyList(), val aiGeometry: MangaAiGeometry? = null,
) {
    fun region(): MangaTranslatedRegion = MangaTranslatedRegion(id, original, original, textBox,
        eraseBoxes = sourceLines.map { it.bounds }.ifEmpty { listOf(textBox) }, vertical = vertical,
        sourceLines = sourceLines, aiGeometry = aiGeometry)
}

@Serializable
data class MangaAiRecognition(val regions: List<MangaAiTextBlock>, val noText: Boolean = false,
    val flows: List<MangaDialogueFlow> = emptyList()) {
    fun plan(): MangaTranslationPlan = MangaTranslationPlan(regions.map { it.region() }, noText = noText, flows = flows).validate()
}

fun MangaTextBox.contains(other: MangaTextBox, tolerance: Float = 0f): Boolean =
    other.left >= left - tolerance && other.top >= top - tolerance && other.right <= right + tolerance && other.bottom <= bottom + tolerance

internal fun MangaTranslatedRegion.validateAiGeometry() {
    aiGeometry?.let { geometry ->
        require(geometry.containerId == null || geometry.containerId.isNotBlank() && geometry.containerId.length <= 80)
        require((geometry.containerId == null) == (geometry.containerBox == null)) { "Incomplete AI container" }
        geometry.containerBox?.let { it.validate(); require(it.contains(textBox, 5f)) { "Source outside AI container" } }
        geometry.safeBox?.let { safe ->
            safe.validate()
            geometry.containerBox?.let { require(it.contains(safe)) { "Safe area outside container" } }
        }
        require(geometry.glyphSamples.size <= 8)
        geometry.glyphSamples.forEach { glyph ->
            require(glyph.text.isNotBlank() && glyph.text.length <= 16)
            glyph.bounds.validate()
            require(textBox.contains(glyph.bounds, 5f)) { "Glyph sample outside source" }
        }
    }
    fontMeasurement?.let {
        require(it.glyphHeight.isFinite() && it.glyphHeight > 0 && it.glyphHeight <= 1000 && it.samples in 1..128 && it.version == 1)
    }
}
