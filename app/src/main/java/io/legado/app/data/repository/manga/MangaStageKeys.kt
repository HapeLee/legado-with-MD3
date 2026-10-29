package io.legado.app.data.repository.manga

import android.graphics.Rect
import io.legado.app.domain.gateway.MangaTranslationRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Dependencies are explicit here: layout edits must not invalidate OCR, translation or repair. */
internal class MangaStageKeys(val source: String, private val ctd: String, private val bubble: String,
    private val aot: String, private val request: MangaTranslationRequest) {
    fun rendered(modelIdentity: String) = MangaStageCache.key(source, modelIdentity, "ocr3-min32-mask5-paragraph1", MangaTextLayout.VERSION,
        request.sourceLanguage, request.targetLanguage, request.config.cacheIdentity(),
        // Keep canonical ordering, but select the supported Map serializer rather than SortedMap.
        request.layoutDirection.name, request.fontScale.toString(), Json.encodeToString<Map<String, String>>(request.translationOverrides.toSortedMap()))

    val ocr = MangaStageCache.key(source, ctd, request.sourceLanguage, "mlkit-ja-ko-bundled16.0.1-strip2-ctd-db2-min32")
    val bubbles = MangaStageCache.key(source, bubble, "yolo1024-mask1-cap256")
    private fun geometry(region: MangaOcrRegion) = region.saved().copy(lines = region.saved().lines
        .map { it.copy(text = "") }.sortedWith(compareBy({ it.bounds.top }, { it.bounds.left }, { it.bounds.right }, { it.bounds.bottom })))
    fun region(region: MangaOcrRegion) = MangaStageCache.key(source, request.sourceLanguage, Json.encodeToString(geometry(region)))
    fun translation(texts: List<String>) = MangaStageCache.key("mlkit-translate17.0.3-ai-context1", request.sourceLanguage,
        request.targetLanguage, request.config.cacheIdentity(), Json.encodeToString(texts))
    fun mask(bounds: Rect) = MangaStageCache.key(source, ctd, "ctd-mask1", Json.encodeToString(bounds.saved()))
    fun repair(region: MangaOcrRegion, bounds: Rect) = MangaStageCache.key(source, ctd, aot, "mask-refine6-repair3",
        Json.encodeToString(geometry(region)), Json.encodeToString(bounds.saved()))
    fun layout(text: String, geometry: String, sourceSize: Float, pageWidth: Int, fontIdentity: String) =
        MangaStageCache.key(MangaTextLayout.VERSION, text, geometry, sourceSize.toString(), pageWidth.toString(),
            request.targetLanguage, request.layoutDirection.name, request.fontScale.toString(), fontIdentity)
}
