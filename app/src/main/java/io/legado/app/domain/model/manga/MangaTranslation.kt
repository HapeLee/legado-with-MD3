package io.legado.app.domain.model.manga

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.domain.model.TranslationConstants
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class MangaLocator { AI, LOCAL, CLOUD }

@Serializable
enum class MangaComposition { SMART, PATCH, AI_REPAIR, AI_REGION, AI_PAGE }

/** Text-only translation engine. Only meaningful when recognition and translation are separate. */
@Serializable
enum class MangaTranslateEngine {
    AI,
    GOOGLE,

    /** Uses whichever engine the global chapter translation settings select. */
    FOLLOW_SETTINGS,
}

/** Resolves [MangaTranslateEngine.FOLLOW_SETTINGS] against the global translation provider. */
fun MangaTranslationSettings.resolvedEngine(globalProvider: String): MangaTranslateEngine = when (textEngine) {
    MangaTranslateEngine.FOLLOW_SETTINGS ->
        if (globalProvider == TranslationConstants.PROVIDER_GOOGLE) MangaTranslateEngine.GOOGLE else MangaTranslateEngine.AI
    else -> textEngine
}

enum class MangaTranslationStage { PREPARING, ANALYZING, PLANNING, EDITING, COMPOSING }

@Stable
data class MangaTranslationProgress(
    val stage: MangaTranslationStage = MangaTranslationStage.PREPARING,
    val completed: Int = 0,
    val total: Int = 0,
    val ai: MangaTranslationAiProgress? = null,
    val diagnosticPath: String? = null,
)

/** Only provider-returned reasoning is displayed. Transient; never stores image payloads or keys. */
@Stable
data class MangaTranslationAiProgress(
    val requestId: String,
    val modelName: String,
    val reasoningCapabilityDeclared: Boolean = false,
    /** Public control fields only, built by the same policy as the actual request body. */
    val reasoningParameters: String? = null,
    val reasoningDisabledRequested: Boolean = false,
    val reasoning: String = "",
    val reasoningTruncated: Boolean = false,
    val outputCharacters: Int = 0,
    val elapsedMillis: Long = 0,
    val thinkingMillis: Long = 0,
    val running: Boolean = true,
    val thinking: Boolean = false,
)

@Stable
@Serializable
data class MangaTranslationGlobalSettings(
    val automatic: Boolean = false,
    val defaults: MangaTranslationSettings = MangaTranslationSettings(),
)

@Stable
@Serializable
data class MangaTranslationSettings(
    val automatic: Boolean = false,
    val retryCount: Int = 3,
    val sourceLanguage: String = "ja",
    val targetLanguage: String = "zh-CN",
    val locator: MangaLocator = MangaLocator.AI,
    val composition: MangaComposition = MangaComposition.SMART,
    val textEngine: MangaTranslateEngine = MangaTranslateEngine.AI,
    val modelId: String = "",
    val imageModelId: String = "",
    val imageEditPath: String = "/images/edits",
    val fontScale: Float = 1f,
    val reasoningLevel: String = AiReasoningLevel.OFF.storageValue,
    val localOcrEngine: MangaLocalOcrEngine = MangaLocalOcrEngine.ML_KIT,
    /** Empty follows the translation model and preserves the legacy combined AI request. */
    val ocrModelId: String = "",
    val cloudOcrProvider: MangaCloudOcrProvider = MangaCloudOcrProvider.BAIDU,
    val ocrOptions: MangaOcrOptions = MangaOcrOptions(),
    val geometryDiagnostics: Boolean = false,
    val inpaintEngine: MangaInpaintEngine = MangaInpaintEngine.LOCAL,
) {
    val usesAiTypesetting: Boolean get() = locator == MangaLocator.AI && composition != MangaComposition.AI_PAGE
}

/** Original transport identity is retained when the host displays a derived image. */
data class MangaTranslationPage(
    val key: String,
    val bookUrl: String,
    val imageUrl: String,
    val chapterSubject: String,
    val sourceOrigin: String?,
    val sourceRevision: Int = 0,
)

/** Coordinates normalized to 0..1000 in an EXIF-corrected analysis tile. */
@Serializable
data class MangaTextBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun validate() {
        require(listOf(left, top, right, bottom).all { it.isFinite() && it in 0f..1000f }) {
            "Invalid region coordinates (left=$left, top=$top, right=$right, bottom=$bottom; expected 0..1000)"
        }
        require(right > left && bottom > top) { "Empty text region (left=$left, top=$top, right=$right, bottom=$bottom)" }
    }
}

@Serializable
data class MangaBubbleShape(
    val id: String,
    val bounds: MangaTextBox,
    val width: Int,
    val height: Int,
    /** Alternating start/count runs in the analysis-resolution mask, never an erased image. */
    val runs: List<Int>,
) {
    fun validate() {
        bounds.validate()
        require(id.isNotBlank() && id.length <= 80 && width in 1..3072 && height in 1..3072 && width.toLong() * height <= 1024 * 1024)
        require(runs.isNotEmpty() && runs.size <= 65536 && runs.size % 2 == 0)
        var end = 0
        for (i in runs.indices step 2) {
            require(runs[i] >= end && runs[i + 1] > 0 && runs[i].toLong() + runs[i + 1] <= width.toLong() * height)
            end = runs[i] + runs[i + 1]
        }
    }
}

@Serializable
data class MangaTextPoint(val x: Float, val y: Float)

/** Clockwise TL/TR/BR/BL source corners; recognition padding is never stored here. */
@Serializable
data class MangaTextQuad(val points: List<MangaTextPoint>) {
    fun validate() {
        require(points.size == 4 && points.all { it.x.isFinite() && it.y.isFinite() && it.x in 0f..1000f && it.y in 0f..1000f }) { "Invalid source quad" }
        for (i in points.indices) {
            val a = points[i]; val b = points[(i + 1) % 4]; val c = points[(i + 2) % 4]
            require((b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x) > .0001f) { "Degenerate or unordered source quad" }
        }
    }
}

@Serializable
data class MangaSourceLine(val text: String, val bounds: MangaTextBox, val quad: MangaTextQuad? = null)

@Serializable
data class MangaTranslatedRegion(
    val id: String,
    val original: String,
    val translation: String,
    val textBox: MangaTextBox,
    val layoutBox: MangaTextBox = textBox,
    val eraseBoxes: List<MangaTextBox> = listOf(textBox),
    val vertical: Boolean = false,
    /** Source line/glyph thickness ceiling for the overlay policy, normalized by analysis height. */
    val sourceFontSize: Float? = null,
    /** Original recognition anchor, retained when local balloon recovery adjusts textBox. */
    val recognitionBox: MangaTextBox? = null,
    /** Trusted local container mask. Model responses cannot supply this field. */
    val bubbleShape: MangaBubbleShape? = null,
    val sourceLines: List<MangaSourceLine> = emptyList(),
    val aiGeometry: MangaAiGeometry? = null,
    val fontMeasurement: MangaFontMeasurement? = null,
    val typesetting: MangaAiTypesetting? = null,
    val sourceColumnCount: Int? = null,
)

@Serializable
data class MangaTranslationPlan(
    val regions: List<MangaTranslatedRegion>,
    val terms: Map<String, String> = emptyMap(),
    val noText: Boolean = false,
    /** Version of client preparation/validation; authored AI geometry is retained, never claimed as locally measured. */
    val localGeometryVersion: Int = 0,
    val flows: List<MangaDialogueFlow> = emptyList(),
) {
    fun validate(expectedIds: Set<String>? = null): MangaTranslationPlan {
        require(regions.size <= 256) { "Too many text regions" }
        require(regions.sumOf { it.bubbleShape?.runs?.size ?: 0 } <= 262144) { "Container masks exceed the page budget" }
        require(regions.isNotEmpty() || noText) { "Missing translation regions" }
        require(!noText || regions.isEmpty()) { "Conflicting no-text result" }
        require(regions.map { it.id }.toSet().size == regions.size) { "Duplicate region IDs" }
        expectedIds?.let { require(regions.map { it.id }.toSet() == it) { "Missing or unexpected region IDs" } }
        require(flows.size <= 256 && flows.map { it.id }.distinct().size == flows.size) { "Invalid dialogue flows" }
        val ids = regions.map { it.id }.toSet()
        val assigned = mutableSetOf<String>()
        regions.mapNotNull { it.aiGeometry }.filter { it.containerId != null }.groupBy { it.containerId }.values.forEach { hints ->
            require(hints.map { it.containerBox }.distinct().size == 1) { "Conflicting AI container bounds" }
        }
        flows.forEach { flow ->
            require(flow.id.isNotBlank() && flow.id.length <= 80 && flow.regionIds.isNotEmpty() && flow.regionIds.size <= 256)
            require(flow.regionIds.all { it in ids && assigned.add(it) }) { "Duplicate or unknown flow region" }
        }
        regions.forEach {
            require(it.id.isNotBlank() && it.id.length <= 80 && it.original.isNotBlank() && it.translation.isNotBlank()) { "Incomplete translation" }
            require(it.original.length <= 8000 && it.translation.length <= 8000) { "Text region is too long" }
            it.textBox.validate()
            it.recognitionBox?.validate()
            it.layoutBox.validate()
            it.bubbleShape?.validate()
            it.validateAiGeometry()
            it.typesetting?.validate(it.translation)
            require(it.sourceColumnCount == null || it.sourceColumnCount in 1..64)
            require(it.sourceLines.size <= 128)
            it.sourceLines.forEach { line ->
                require(line.text.isNotBlank() && line.text.length <= 8000)
                line.bounds.validate(); line.quad?.validate()
                require(it.textBox.contains(line.bounds, 5f)) { "Source line outside text block" }
            }
            require(it.sourceFontSize == null || it.sourceFontSize.isFinite() && it.sourceFontSize > 0 && it.sourceFontSize <= 1000) { "Invalid source font size" }
            require(it.eraseBoxes.isNotEmpty() && it.eraseBoxes.size <= 128) { "Invalid erase regions" }
            it.eraseBoxes.forEach(MangaTextBox::validate)
            require(it.eraseBoxes.all { box ->
                box.left >= it.textBox.left - 5 && box.top >= it.textBox.top - 5 &&
                    box.right <= it.textBox.right + 5 && box.bottom <= it.textBox.bottom + 5
            }) { "Erase region extends outside source lettering" }
        }
        return copy(terms = terms.filter { (source, target) ->
            source.isNotBlank() && target.isNotBlank() && source.length <= 80 && target.length <= 80
        }.entries.take(256).associate { it.toPair() })
    }

    companion object {
        @Serializable
        private data class TextTranslation(val id: String, val translation: String)
        @Serializable
        private data class TextTranslations(val translations: List<TextTranslation>, val terms: Map<String, String> = emptyMap())
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        private fun unwrap(response: String): String = response.trim().let {
                if (it.startsWith("```")) it.substringAfter('\n').substringBeforeLast("```").trim() else it
            }

        fun parse(response: String, expectedIds: Set<String>? = null): MangaTranslationPlan =
            json.decodeFromString<MangaTranslationPlan>(unwrap(response)).let { plan ->
                plan.copy(localGeometryVersion = 0, regions = plan.regions.map {
                    it.copy(recognitionBox = null, bubbleShape = null, sourceFontSize = null, fontMeasurement = null, typesetting = null)
                })
            }.validate(expectedIds)

        /** Text-only translation cannot replace detector-owned source text, order or geometry. */
        fun translateDetected(response: String, detected: List<MangaTranslatedRegion>, flows: List<MangaDialogueFlow> = emptyList()): MangaTranslationPlan {
            require(detected.isNotEmpty() && detected.size <= 256 && detected.map { it.id }.toSet().size == detected.size) { "Invalid detected regions" }
            val output = json.decodeFromString<TextTranslations>(unwrap(response))
            val translations = output.translations.associate { it.id to it.translation }
            require(translations.size == output.translations.size && translations.keys == detected.map { it.id }.toSet()) { "Missing, duplicate or unexpected translation IDs" }
            return MangaTranslationPlan(detected.map { it.copy(translation = translations.getValue(it.id)) }, output.terms, flows = flows).validate(translations.keys)
        }
    }
}

/** Core bands partition the page. Overlap provides context without duplicated output rows. */
@Serializable
data class MangaTranslationBand(val top: Int, val bottom: Int, val coreTop: Int, val coreBottom: Int)

/** Ordinary pages retain their full reading context, regardless of the original pixel count. */
fun mangaTranslationWholePage(width: Int, height: Int): Boolean =
    width > 0 && height > 0 && maxOf(width, height).toLong() <= minOf(width, height).toLong() * 3

data class MangaTranslationPreview(val width: Int, val height: Int, val sampleSize: Int)

fun mangaTranslationPreview(width: Int, height: Int, pixels: Int = 2 * 1024 * 1024, maxSide: Int = 3072): MangaTranslationPreview {
    require(width > 0 && height > 0 && pixels > 0 && maxSide > 0)
    val scale = minOf(1.0, maxSide.toDouble() / maxOf(width, height), kotlin.math.sqrt(pixels.toDouble() / (width.toLong() * height)))
    val targetWidth = (width * scale).toInt().coerceAtLeast(1)
    val targetHeight = (height * scale).toInt().coerceAtLeast(1)
    var sample = 1
    while (width / (sample * 2) >= targetWidth && height / (sample * 2) >= targetHeight) sample *= 2
    return MangaTranslationPreview(targetWidth, targetHeight, sample)
}

/** Prefer an actual gutter; never put a seam inside a detected text block. */
fun mangaTranslationBoundary(nominal: Int, minimum: Int, maximum: Int, text: List<IntRange>, gaps: List<IntRange>): Int {
    fun safe(y: Int) = y in minimum..maximum && text.none { it.first < y && it.last > y }
    val gapChoices = gaps.flatMap { listOf(nominal.coerceIn(it.first, it.last), it.first, it.last) }
        .filter(::safe)
    if (gapChoices.isNotEmpty()) return gapChoices.minBy { kotlin.math.abs(it - nominal) }
    return (listOf(nominal, minimum, maximum) + text.flatMap { listOf(it.first, it.last) })
        .filter(::safe).minByOrNull { kotlin.math.abs(it - nominal) }
        ?: error("Cannot find a translation boundary outside detected text")
}

fun mangaTranslationBands(width: Int, height: Int): List<MangaTranslationBand> {
    require(width in 1..8192 && height in 1..1_000_000) { "Unsupported image dimensions" }
    return buildList {
        var y = 0
        while (y < height) {
            val band = mangaTranslationBandAt(width, height, y)
            add(band)
            y = band.coreBottom
        }
    }
}

fun mangaTranslationBandAt(width: Int, height: Int, top: Int): MangaTranslationBand {
    require(width in 1..8192 && height in 1..1_000_000 && top in 0 until height) { "Unsupported image dimensions" }
    val coreHeight = minOf(1536, (3 * 1024 * 1024 / width) - 256).coerceAtLeast(128)
    val end = minOf(height, top + coreHeight)
    return MangaTranslationBand((top - 128).coerceAtLeast(0), (end + 128).coerceAtMost(height), top, end)
}

data class MangaTranslationResult(val imagePath: String, val revision: String, val canRecompose: Boolean = false)
