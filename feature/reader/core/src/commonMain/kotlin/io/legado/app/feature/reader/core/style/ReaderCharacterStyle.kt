package io.legado.app.feature.reader.core.style

import io.legado.app.feature.reader.core.model.ReaderUnderline
import io.legado.app.feature.reader.core.model.ReaderTextBackgroundImage

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
    fun resolve(ranges: List<ReaderStyleRange>, position: Int, isTitle: Boolean): ReaderCharacterStyle? =
        ranges.withIndex().asSequence()
            .filter { it.value.contains(position, isTitle) }
            .maxWithOrNull(compareBy<IndexedValue<ReaderStyleRange>> { it.value.priority }.thenBy { it.index })
            ?.value?.style
}
