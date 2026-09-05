package io.legado.app.feature.reader.core.selection

data class ReaderSelectionLine(
    val chapterPosition: Int,
    val columnCharacterLengths: List<Int?>,
)

data class ReaderSelectionCoordinate(
    val lineIndex: Int,
    val columnIndex: Int,
)

object ReaderSelectionCoordinateMapper {
    fun find(lines: List<ReaderSelectionLine>, chapterPosition: Int): ReaderSelectionCoordinate? {
        lines.forEachIndexed { lineIndex, line ->
            var position = line.chapterPosition
            line.columnCharacterLengths.forEachIndexed { columnIndex, rawLength ->
                if (rawLength == null) return@forEachIndexed
                val next = position + rawLength.coerceAtLeast(1)
                if (chapterPosition in position until next) {
                    return ReaderSelectionCoordinate(lineIndex, columnIndex)
                }
                position = next
            }
        }
        return null
    }
}
