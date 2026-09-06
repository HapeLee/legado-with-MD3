package io.legado.app.utils

/** Natural sort order for filenames and remote directory entries. */
object AlphanumComparator : Comparator<String> {

    override fun compare(s1: String, s2: String): Int {
        var thisMarker = 0
        var thatMarker = 0
        val s1Length = s1.length
        val s2Length = s2.length

        while (thisMarker < s1Length && thatMarker < s2Length) {
            val thisChunk = getChunk(s1, s1Length, thisMarker)
            thisMarker += thisChunk.length

            val thatChunk = getChunk(s2, s2Length, thatMarker)
            thatMarker += thatChunk.length

            val result = if (isDigit(thisChunk[0]) && isDigit(thatChunk[0])) {
                compareNumericChunks(thisChunk, thatChunk)
            } else {
                thisChunk.compareTo(thatChunk)
            }

            if (result != 0) return result
        }

        return s1Length - s2Length
    }

    private fun compareNumericChunks(first: String, second: String): Int {
        if (first.length != second.length) return first.length - second.length

        for (index in first.indices) {
            val result = first[index] - second[index]
            if (result != 0) return result
        }
        return 0
    }

    private fun getChunk(string: String, length: Int, marker: Int): String {
        var current = marker
        val chunk = StringBuilder()
        var char = string[current]
        chunk.append(char)
        current++
        if (isDigit(char)) {
            while (current < length) {
                char = string[current]
                if (!isDigit(char)) break
                chunk.append(char)
                current++
            }
        } else {
            while (current < length) {
                char = string[current]
                if (isDigit(char)) break
                chunk.append(char)
                current++
            }
        }
        return chunk.toString()
    }

    private fun isDigit(char: Char): Boolean = char in '0'..'9'
}
