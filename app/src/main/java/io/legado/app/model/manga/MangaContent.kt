package io.legado.app.model.manga

data class MangaContent(
    val position: Int,
    val items: List<BaseMangaPage>,
    val currentFinished: Boolean,
    val nextFinished: Boolean,
)
