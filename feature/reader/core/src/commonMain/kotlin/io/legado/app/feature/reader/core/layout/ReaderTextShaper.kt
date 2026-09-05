package io.legado.app.feature.reader.core.layout

/** Actual font bounds at the shaped size, without leaking Android Paint into pagination. */
data class ReaderFontBounds(val topPx: Float, val bottomPx: Float, val descentPx: Float) {
    val heightPx: Float get() = bottomPx - topPx
    val baselineOffsetPx: Float get() = heightPx - descentPx
}

/** Line box used by Android Layout: ascent/descent/leading rather than glyph-extreme bounds. */
data class ReaderFontLineMetrics(
    val heightPx: Float,
    val baselineOffsetPx: Float,
    /** Positive distances from the baseline, excluding leading. */
    val ascentPx: Float = baselineOffsetPx,
    val descentPx: Float = heightPx - baselineOffsetPx,
)

fun interface ReaderTextShaper {
    fun shape(text: String): GlyphClusters
    val fontBounds: ReaderFontBounds? get() = null
    val fontLineMetrics: ReaderFontLineMetrics? get() = null
}

class ReaderParagraphFactory(private val shaper: ReaderTextShaper) {
    fun create(
        text: String,
        style: io.legado.app.feature.reader.core.model.ReaderTextStyle,
        chapterPosition: Int,
        indentCharacters: Int = 0,
        alignment: ReaderTextAlignment = ReaderTextAlignment.START,
        isTitle: Boolean = false,
        link: String? = null,
        lineHeightPx: Float? = null,
        baselineOffsetPx: Float? = null,
    ): ReaderMeasuredParagraph {
        val shaped = shaper.shape(text)
        return ReaderMeasuredParagraph(
            text = text,
            clusters = shaped.text,
            clusterWidthsPx = shaped.widthsPx,
            style = style,
            chapterPosition = chapterPosition,
            indentCharacters = indentCharacters,
            alignment = alignment,
            isTitle = isTitle,
            link = link,
            lineHeightPx = lineHeightPx,
            baselineOffsetPx = baselineOffsetPx,
        )
    }
}
