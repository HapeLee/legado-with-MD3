package io.legado.app.feature.reader.core.semantic

/** Body coordinates are UTF-16 code units; ranges are [start, endExclusive). */
internal data class SemanticRange(val start: Int, val endExclusive: Int) {
    init {
        require(start >= 0 && endExclusive >= start)
    }
}

internal object ReaderSemanticLocation {
    fun isValidOffset(text: String, offset: Int): Boolean =
        offset in 0..text.length && !(offset > 0 && offset < text.length &&
            text[offset - 1].isHighSurrogate() && text[offset].isLowSurrogate())

    fun validateRange(text: String, range: SemanticRange): Boolean =
        isValidOffset(text, range.start) && isValidOffset(text, range.endExclusive)

    fun matchesAt(text: String, position: Int, expected: String, before: String = "", after: String = ""): Boolean =
        expected.isNotEmpty() && isValidOffset(text, position) &&
            expected.length <= text.length - position &&
            isValidOffset(text, position + expected.length) &&
            text.regionMatches(position, expected, 0, expected.length) &&
            (before.isBlank() || (position >= before.length &&
                text.regionMatches(position - before.length, before, 0, before.length))) &&
            (after.isBlank() || text.regionMatches(position + expected.length, after, 0, after.length))

    /** A page snippet may surround the stored position; only a unique occurrence is evidence. */
    fun matchesContainingText(text: String, position: Int, expected: String): Boolean {
        if (expected.isBlank() || !isValidOffset(text, position)) return false
        val start = text.indexOf(expected)
        return start >= 0 && matchesAt(text, start, expected) &&
            position in start until start + expected.length &&
            text.indexOf(expected, start + 1) < 0
    }

    /** Range correction only; this does not establish the identity of the target text. */
    fun clampOffset(text: String, position: Int): Int {
        val clamped = position.coerceIn(0, text.length)
        return if (isValidOffset(text, clamped)) clamped else clamped - 1
    }
}
