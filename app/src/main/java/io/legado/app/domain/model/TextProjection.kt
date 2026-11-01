package io.legado.app.domain.model

/** Transient UTF-16 provenance. Text remains owned by the existing content pipeline. */
internal class TextProjection private constructor(
    private var starts: IntArray,
    private var ends: IntArray,
    private var inherited: BooleanArray,
    val originLength: Int,
) {
    constructor(length: Int) : this(IntArray(length) { it }, IntArray(length) { it + 1 }, BooleanArray(length) { true }, length)

    val length: Int get() = starts.size
    private var monotonic: Boolean? = null
    private fun ordered(): Boolean {
        monotonic?.let { return it }
        var result = true
        for (i in 1 until length) {
            if (starts[i] < starts[i - 1] || ends[i] < ends[i - 1]) { result = false; break }
        }
        monotonic = result
        return result
    }
    private fun firstEndAfter(position: Int): Int {
        var low = 0
        var high = length
        while (low < high) {
            val middle = (low + high) ushr 1
            if (ends[middle] <= position) low = middle + 1 else high = middle
        }
        return low
    }

    data class Edit(val start: Int, val endExclusive: Int, val length: Int, val inherit: Boolean = true, val inheritPrevious: Boolean = false)
    data class Piece(val start: Int, val endExclusive: Int, val replacementLength: Int? = null, val inherit: Boolean = true)

    fun replace(start: Int, endExclusive: Int, length: Int, inherit: Boolean = true) =
        replace(listOf(Edit(start, endExclusive, length, inherit)))

    fun replace(edits: List<Edit>) {
        if (edits.isEmpty()) return
        var cursor = 0
        val pieces = buildList {
            for (edit in edits) {
                require(edit.start >= cursor && edit.endExclusive in edit.start..length)
                add(Piece(cursor, edit.start))
                add(if (edit.inheritPrevious && edit.start > 0) Piece(edit.start - 1, edit.start, edit.length, edit.inherit)
                    else Piece(edit.start, edit.endExclusive, edit.length, edit.inherit))
                cursor = edit.endExclusive
            }
            add(Piece(cursor, length))
        }
        rebuild(pieces)
    }

    fun rebuild(pieces: List<Piece>) {
        val size = pieces.sumOf { it.replacementLength ?: (it.endExclusive - it.start) }
        val newStarts = IntArray(size)
        val newEnds = IntArray(size)
        val newInherited = BooleanArray(size)
        var offset = 0
        for (piece in pieces) {
            require(piece.start in 0..piece.endExclusive && piece.endExclusive <= length)
            val count = piece.replacementLength
            if (count == null) {
                starts.copyInto(newStarts, offset, piece.start, piece.endExclusive)
                ends.copyInto(newEnds, offset, piece.start, piece.endExclusive)
                inherited.copyInto(newInherited, offset, piece.start, piece.endExclusive)
                offset += piece.endExclusive - piece.start
            } else {
                var from = starts.getOrElse(piece.start) { originLength }
                var until = if (piece.start == piece.endExclusive) from else ends[piece.endExclusive - 1]
                if (piece.start < piece.endExclusive && !ordered()) {
                    for (index in piece.start until piece.endExclusive) {
                        from = minOf(from, starts[index])
                        until = maxOf(until, ends[index])
                    }
                }
                val canInherit = piece.inherit && (piece.start != piece.endExclusive ||
                    piece.start == 0 || piece.start == length || ends[piece.start - 1] == starts[piece.start])
                repeat(count) {
                    newStarts[offset] = from
                    newEnds[offset] = until
                    newInherited[offset++] = canInherit
                }
            }
        }
        monotonic = null
        starts = newStarts
        ends = newEnds
        inherited = newInherited
    }

    fun slice(start: Int, endExclusive: Int): TextProjection = TextProjection(
        starts.copyOfRange(start, endExclusive), ends.copyOfRange(start, endExclusive),
        inherited.copyOfRange(start, endExclusive), originLength)

    fun join(parts: List<TextProjection>, prefixLength: Int = 0, separatorLength: Int = 0) {
        val size = prefixLength + parts.sumOf { it.length } + separatorLength * (parts.size - 1).coerceAtLeast(0)
        val a = IntArray(size)
        val b = IntArray(size)
        val flags = BooleanArray(size)
        var offset = 0
        for ((index, part) in parts.withIndex()) {
            require(part.originLength == originLength)
            val gap = if (index == 0) prefixLength else separatorLength
            val point = part.starts.firstOrNull() ?: originLength
            repeat(gap) { a[offset] = point; b[offset] = point; offset++ }
            part.starts.copyInto(a, offset)
            part.ends.copyInto(b, offset)
            part.inherited.copyInto(flags, offset)
            offset += part.length
        }
        monotonic = null
        starts = a; ends = b; inherited = flags
    }

    /** Only whitespace may differ when binding a cached transport to its original source. */
    fun bindWhitespace(before: String, after: String): Boolean {
        require(before.length == length)
        val pieces = mutableListOf<Piece>()
        var from = 0
        var to = 0
        while (from < before.length || to < after.length) {
            val a = from
            val b = to
            while (from < before.length && before[from].isWhitespace()) from++
            while (to < after.length && after[to].isWhitespace()) to++
            if (a != from || b != to) pieces += Piece(a, from, to - b)
            if (from == before.length || to == after.length) {
                if (from != before.length || to != after.length) return false
                break
            }
            if (before[from] != after[to]) return false
            val begin = from
            do { from++; to++ } while (from < before.length && to < after.length &&
                before[from] == after[to] && !before[from].isWhitespace())
            pieces += Piece(begin, from)
        }
        rebuild(pieces)
        return true
    }

    fun ranges(start: Int, endExclusive: Int, styling: Boolean = false): List<IntRange> {
        if (start >= endExclusive) return emptyList()
        val result = mutableListOf<IntRange>()
        var run = -1
        // Provenance is monotonic except when a real transformation explicitly copies groups.
        // A single pass also handles those copies without guessing repeated text identities.
        val ordered = ordered()
        val from = if (ordered) firstEndAfter(start) else 0
        var stop = from
        for (i in from until length) {
            if (ordered && starts[i] >= endExclusive) break
            stop = i + 1
            val point = starts[i] == ends[i]
            val inside = starts[i] >= start && ends[i] <= endExclusive &&
                (!point || starts[i] > start && starts[i] < endExclusive) &&
                (!styling || inherited[i])
            if (inside && run < 0) run = i
            if (!inside && run >= 0) { result += run until i; run = -1 }
        }
        if (run >= 0) result += run until stop
        return result
    }

    fun boundary(position: Int, keepDeleted: Boolean = false): Int? {
        if (position == originLength) return length
        if (ordered()) {
            val index = firstEndAfter(position)
            return if (index < length && starts[index] <= position) index else if (keepDeleted) index else null
        }
        for (i in starts.indices) {
            if (starts[i] <= position && position < ends[i]) return i
            if (keepDeleted && starts[i] >= position) return i
        }
        return if (keepDeleted) length else null
    }
}
