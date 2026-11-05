package io.legado.app.data.repository.manga.translation

/**
 * One detected container region. It stays in the shared layer because geometry regrouping,
 * anchor hints and typesetting all consume it without any runtime dependency.
 */
data class MangaBubbleDetection(val bounds: MangaShapeRect, val confidence: Float, val kind: Int)
