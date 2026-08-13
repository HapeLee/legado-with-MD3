package io.legado.app.model.manga

data class MangaPage(
    override val chapterIndex: Int = 0,
    val chapterSize: Int,
    val imageUrl: String = "",
    override val index: Int = 0,
    var imageCount: Int = 0,
    val chapterName: String = "",
) : BaseMangaPage
