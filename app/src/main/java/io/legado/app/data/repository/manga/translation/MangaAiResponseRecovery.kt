package io.legado.app.data.repository.manga.translation

import io.legado.app.domain.model.manga.MangaTranslationPlan
import io.legado.app.domain.model.manga.mangaAiLinesPreserveText
import kotlinx.serialization.json.*

/** A repair may complete a broken response, but cannot discard or rewrite usable text already read.
 * Invalid IDs may be rebound by the model only when their source text has one unambiguous match.
 * This never guesses geometry or maps an unknown ID to a local candidate by array position.
 */
internal fun validateMangaAiResponseCorrection(before: String, after: MangaTranslationPlan, knownIds: Set<String>? = null) {
    val clean = before.trim().let { if (it.startsWith("```")) it.substringAfter('\n').substringBeforeLast("```").trim() else it }
    // A syntactically truncated response has no usable text contract to lock. The repaired
    // response must still pass the complete normal parser and all local identity checks.
    val root = try { MangaTranslationPlan.json.parseToJsonElement(clean) as? JsonObject }
        catch (_: IllegalArgumentException) { null } ?: return
    val blocks = ((root["regions"] ?: root["translations"]) as? JsonArray)?.mapNotNull { it as? JsonObject } ?: return
    fun JsonObject.string(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
    if (blocks.isEmpty() && (root["noText"] as? JsonPrimitive)?.booleanOrNull == true)
        require(after.noText) { "AI correction changed no-text result" }
    val idCounts = blocks.mapNotNull { it.string("id") }.groupingBy { it }.eachCount()
    var previousIndex = -1
    blocks.forEach { block ->
        val original = block.string("original")
        val rawLines = (block["typesetting"] as? JsonObject)?.get("lines")
        val lines = when (rawLines) {
            is JsonPrimitive -> rawLines.takeIf { it.isString }?.content
            is JsonArray -> rawLines.takeIf { it.all { line -> line is JsonPrimitive && line.isString } }
                ?.joinToString("\n") { it.jsonPrimitive.content }
            else -> null
        }?.replace("\r\n", "\n")?.replace('\r', '\n')?.replace('\t', ' ')
        val translation = block.string("translation") ?: lines?.takeIf { it.isNotBlank() }
        if (original == null && translation == null) return@forEach
        val id = block.string("id")
        val fixedId = id != null && idCounts[id] == 1 && (knownIds == null || id in knownIds || id.startsWith("free-"))
        val matches = after.regions.withIndex().filter { (_, region) ->
            if (fixedId) region.id == id else original != null && mangaAiLinesPreserveText(listOf(region.original), original)
        }
        require(matches.size == 1) { "AI correction changed text region IDs or omitted completed source" }
        val (index, region) = matches.single()
        require(index >= previousIndex) { "AI correction changed text reading order" }
        previousIndex = index
        require(original == null || mangaAiLinesPreserveText(listOf(region.original), original)) { "AI correction changed source text" }
        require(translation == null || mangaAiLinesPreserveText(region.typesetting?.lines ?: region.translation.lines(), translation)) {
            "AI correction changed text or omitted translation"
        }
    }
}
