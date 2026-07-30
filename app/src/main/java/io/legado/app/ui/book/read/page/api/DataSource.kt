package io.legado.app.ui.book.read.page.api

import io.legado.app.ui.book.read.page.entities.TextChapter

interface DataSource {

    val pageIndex: Int

    val currentChapter: TextChapter?

    val nextChapter: TextChapter?

    val prevChapter: TextChapter?

    val isScroll: Boolean

    fun hasNextChapter(): Boolean

    fun hasPrevChapter(): Boolean

    fun upContent(relativePosition: Int = 0, resetPageOffset: Boolean = true)

}