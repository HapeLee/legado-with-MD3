package io.legado.app.feature.reader.core.gesture

enum class ReaderTapAction(val legacyValue: Int) {
    NONE(-1),
    MENU(0),
    NEXT_PAGE(1),
    PREVIOUS_PAGE(2),
    NEXT_CHAPTER(3),
    PREVIOUS_CHAPTER(4),
    READ_ALOUD_PREVIOUS_PARAGRAPH(5),
    READ_ALOUD_NEXT_PARAGRAPH(6),
    ADD_BOOKMARK(7),
    OPEN_CONTENT_EDIT(8),
    TOGGLE_REPLACE(9),
    OPEN_CHAPTER_LIST(10),
    OPEN_SEARCH(11),
    SYNC_PROGRESS(12),
    TOGGLE_READ_ALOUD_PAUSE(13);

    companion object {
        fun fromLegacyValue(value: Int): ReaderTapAction =
            entries.firstOrNull { it.legacyValue == value } ?: NONE
    }
}

data class ReaderTapActionGrid(
    val topLeft: ReaderTapAction,
    val topCenter: ReaderTapAction,
    val topRight: ReaderTapAction,
    val middleLeft: ReaderTapAction,
    val middleCenter: ReaderTapAction,
    val middleRight: ReaderTapAction,
    val bottomLeft: ReaderTapAction,
    val bottomCenter: ReaderTapAction,
    val bottomRight: ReaderTapAction,
) {
    fun actionAt(x: Float, y: Float, width: Float, height: Float): ReaderTapAction {
        if (width <= 0f || height <= 0f || x < 0f || y < 0f || x > width || y > height) return ReaderTapAction.NONE
        val column = when {
            x < width * .33f -> 0
            x < width * .66f -> 1
            else -> 2
        }
        val row = when {
            y < height * .33f -> 0
            y < height * .66f -> 1
            else -> 2
        }
        return arrayOf(
            topLeft, topCenter, topRight,
            middleLeft, middleCenter, middleRight,
            bottomLeft, bottomCenter, bottomRight,
        )[row * 3 + column]
    }

    companion object {
        fun fromLegacyValues(
            topLeft: Int,
            topCenter: Int,
            topRight: Int,
            middleLeft: Int,
            middleCenter: Int,
            middleRight: Int,
            bottomLeft: Int,
            bottomCenter: Int,
            bottomRight: Int,
        ) = ReaderTapActionGrid(
            ReaderTapAction.fromLegacyValue(topLeft),
            ReaderTapAction.fromLegacyValue(topCenter),
            ReaderTapAction.fromLegacyValue(topRight),
            ReaderTapAction.fromLegacyValue(middleLeft),
            ReaderTapAction.fromLegacyValue(middleCenter),
            ReaderTapAction.fromLegacyValue(middleRight),
            ReaderTapAction.fromLegacyValue(bottomLeft),
            ReaderTapAction.fromLegacyValue(bottomCenter),
            ReaderTapAction.fromLegacyValue(bottomRight),
        )
    }
}
