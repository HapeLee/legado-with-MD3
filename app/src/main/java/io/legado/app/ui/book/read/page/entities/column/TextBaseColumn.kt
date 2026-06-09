package io.legado.app.ui.book.read.page.entities.column

/**
 * 文字基列
 */
interface TextBaseColumn : BaseColumn {
    override var start: Float
    override var end: Float
    val charData: String
    val textColor: Int?
    val bgColor: Int?
    val underlineMode: Int
    val underlineColor: Int?
    val underlineWidth: Float
    val underlineOffset: Float
    val underlineSvgPath: String
    val bgImage: String
    val bgImageFit: Int
    val bgImageScale: Float
    val fontPath: String
    var selected: Boolean
    var isSearchResult: Boolean
}