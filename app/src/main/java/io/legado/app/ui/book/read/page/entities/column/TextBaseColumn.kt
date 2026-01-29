package io.legado.app.ui.book.read.page.entities.column

/**
 * 文字基列
 */
interface TextBaseColumn : BaseColumn {
    override var start: Float
    override var end: Float
    val charData: String
    var selected: Boolean
    var isSearchResult: Boolean
}