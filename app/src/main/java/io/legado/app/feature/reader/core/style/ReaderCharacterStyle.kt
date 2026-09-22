package io.legado.app.feature.reader.core.style

import io.legado.app.feature.reader.core.model.ReaderTextBackgroundImage
import io.legado.app.feature.reader.core.model.ReaderUnderline

enum class ReaderStyleTarget { ALL, TITLE, BODY }

data class ReaderCharacterStyle(
    val colorArgb: Int? = null,
    val backgroundArgb: Int? = null,
    val underline: ReaderUnderline? = null,
    val fontPath: String? = null,
    val fontWeight: Int? = null,
    val italic: Boolean? = null,
    val fontSizeOffsetPx: Float = 0f,
    val markingId: String? = null,
    val backgroundImage: ReaderTextBackgroundImage? = null,
)

data class ReaderStyleRange(
    val start: Int,
    val endExclusive: Int,
    val target: ReaderStyleTarget,
    val style: ReaderCharacterStyle,
    val priority: Int = 0,
) {
    fun contains(position: Int, isTitle: Boolean): Boolean =
        position in start until endExclusive && when (target) {
            ReaderStyleTarget.ALL -> true
            ReaderStyleTarget.TITLE -> isTitle
            ReaderStyleTarget.BODY -> !isTitle
        }
}

object ReaderCharacterStyleResolver {
    fun resolve(
        ranges: List<ReaderStyleRange>,
        position: Int,
        isTitle: Boolean
    ): ReaderCharacterStyle? {
        var winner: ReaderStyleRange? = null
        for (range in ranges) {
            if (range.contains(position, isTitle) &&
                (winner == null || range.priority >= winner.priority)
            ) {
                // Equal priority keeps the later range, matching the original index tie-break.
                winner = range
            }
        }
        return winner?.style
    }
}
