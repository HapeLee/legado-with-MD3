package io.legado.app.data.repository.manga.translation

import io.legado.app.domain.model.manga.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

@Serializable
internal data class MangaAiFrame(val id: String, val width: Int, val height: Int)

internal fun validateMangaAiFrame(response: String, expected: MangaAiFrame) {
    val clean = response.trim().let { if (it.startsWith("```")) it.substringAfter('\n').substringBeforeLast("```").trim() else it }
    val frame = MangaTranslationPlan.json.parseToJsonElement(clean).jsonObject["frame"]
    // Older short responses remain readable; a supplied frame must never silently be rebased.
    if (frame != null && frame != JsonNull) require(MangaTranslationPlan.json.decodeFromJsonElement<MangaAiFrame>(frame) == expected) {
        "AI coordinates refer to a different image frame"
    }
}
