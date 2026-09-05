package io.legado.app.feature.reader.core.selection

data class ReaderWordRange(
    val start: Int,
    val endExclusive: Int,
)

sealed interface ReaderWordBoundaryResult {
    data class Resolved(val range: ReaderWordRange) : ReaderWordBoundaryResult

    data object Unsupported : ReaderWordBoundaryResult
}

/** Platform text-segmentation capability used only for long-press word expansion. */
interface ReaderWordBoundaryResolver {
    fun rangeAt(text: String, offset: Int): ReaderWordBoundaryResult
}
