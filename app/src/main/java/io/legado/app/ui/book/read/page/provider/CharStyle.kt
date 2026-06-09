package io.legado.app.ui.book.read.page.provider

/**
 * 每个字符的高亮样式，由高亮规则匹配后填充
 */
data class CharStyle(
    val textColor: Int? = null,
    val bgColor: Int? = null,
    val underlineMode: Int = 0,
    val underlineColor: Int? = null,
    val underlineWidth: Float = 1f,
    val underlineOffset: Float = 2f,
    val underlineSvgPath: String = "",
    val bgImage: String = "",
    val bgImageFit: Int = 0,
    val bgImageScale: Float = 1f,
    val fontPath: String = "",
) {
    val hasStyle: Boolean
        get() = textColor != null || bgColor != null || underlineMode != 0 || bgImage.isNotEmpty() || fontPath.isNotEmpty()
}
