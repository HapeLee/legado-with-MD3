package io.legado.app.feature.reader.core.model

data class ReaderThemeColorChange(
    val oldBodyArgb: Int,
    val newBodyArgb: Int,
    val oldTitleArgb: Int,
    val newTitleArgb: Int,
    val oldShadowArgb: Int = oldBodyArgb,
    val newShadowArgb: Int = newBodyArgb,
    val oldPageUnderlineArgb: Int = oldBodyArgb,
    val newPageUnderlineArgb: Int = newBodyArgb,
)

/** Recolors an already-laid-out page so theme changes do not wait for asynchronous pagination. */
fun ReaderPage.remapThemeColors(change: ReaderThemeColorChange, revisionSalt: Long): ReaderPage {
    if (
        change.oldBodyArgb == change.newBodyArgb &&
        change.oldTitleArgb == change.newTitleArgb &&
        change.oldShadowArgb == change.newShadowArgb &&
        change.oldPageUnderlineArgb == change.newPageUnderlineArgb
    ) return this
    fun Int.mapped(from: Int, to: Int) = if (this == from) to else this
    return copy(
        elements = elements.map { element ->
            when (element) {
                is ReaderElement.Text -> {
                    val oldColor = if (element.emphasized) change.oldTitleArgb else change.oldBodyArgb
                    val newColor = if (element.emphasized) change.newTitleArgb else change.newBodyArgb
                    val bookFallback = element.style.bookColorFallbackArgb
                    element.copy(
                        style = element.style.copy(
                            colorArgb = if (bookFallback == null) {
                                element.style.colorArgb.mapped(oldColor, newColor)
                            } else element.style.colorArgb,
                            bookColorFallbackArgb = bookFallback?.mapped(oldColor, newColor),
                            underline = element.style.underline?.copy(
                                colorArgb = element.style.underline.colorArgb.mapped(oldColor, newColor),
                            ),
                            shadow = element.style.shadow?.copy(
                                colorArgb = element.style.shadow.colorArgb.mapped(
                                    change.oldShadowArgb,
                                    change.newShadowArgb,
                                ),
                            ),
                        ),
                        emphasisUnderline = element.emphasisUnderline?.copy(
                            colorArgb = element.emphasisUnderline.colorArgb.mapped(oldColor, newColor),
                        ),
                    )
                }
                is ReaderElement.Rule -> element.copy(
                    colorArgb = element.colorArgb.mapped(
                        change.oldPageUnderlineArgb,
                        change.newPageUnderlineArgb,
                    ),
                )
                is ReaderElement.ParagraphMarker -> element.copy(
                    colorArgb = element.colorArgb.mapped(change.oldBodyArgb, change.newBodyArgb),
                )
                else -> element
            }
        },
        emphasisUnderlineStyle = emphasisUnderlineStyle?.copy(
            colorArgb = emphasisUnderlineStyle.colorArgb.mapped(change.oldBodyArgb, change.newBodyArgb),
        ),
        // Page-tip glyphs (battery and chapter-arrow variants) are painted with their row's
        // color. The View reader updated them through PageView.upThemeColors(); keep cached
        // Canvas pages in sync without waiting for the next pagination pass.
        decoration = decoration.copy(
            header = decoration.header?.remapThemeColor(change),
            footer = decoration.footer?.remapThemeColor(change),
        ),
        revision = revision xor revisionSalt,
    )
}

private fun ReaderTipRow.remapThemeColor(change: ReaderThemeColorChange): ReaderTipRow = copy(
    colorArgb = if (colorArgb == change.oldBodyArgb) change.newBodyArgb else colorArgb,
    dividerColorArgb = dividerColorArgb?.let { color ->
        if (color == change.oldBodyArgb) change.newBodyArgb else color
    },
)
