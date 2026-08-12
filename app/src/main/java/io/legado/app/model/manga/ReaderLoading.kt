package io.legado.app.model.manga

data class ReaderLoading(
    override val chapterIndex: Int = 0,
    override val index: Int = 0,
    val message: String? = null,
    val isVolume: Boolean = false,
) : BaseMangaPage
