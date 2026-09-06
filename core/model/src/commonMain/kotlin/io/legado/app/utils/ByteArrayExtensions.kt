package io.legado.app.utils

/** Finds the first occurrence of [pattern] with a Knuth-Morris-Pratt scan. */
fun ByteArray.indexOf(pattern: ByteArray, start: Int = 0, stop: Int = size): Int {
    val failure = computeFailure(pattern)
    var patternIndex = 0

    for (dataIndex in start until stop) {
        while (patternIndex > 0 && pattern[patternIndex] != this[dataIndex]) {
            patternIndex = failure[patternIndex - 1]
        }
        if (pattern[patternIndex] == this[dataIndex]) {
            patternIndex++
        }
        if (patternIndex == pattern.size) {
            return dataIndex - pattern.size + 1
        }
    }
    return -1
}

private fun computeFailure(pattern: ByteArray): IntArray {
    val failure = IntArray(pattern.size)
    var patternIndex = 0
    for (index in 1 until pattern.size) {
        while (patternIndex > 0 && pattern[patternIndex] != pattern[index]) {
            patternIndex = failure[patternIndex - 1]
        }
        if (pattern[patternIndex] == pattern[index]) {
            patternIndex++
        }
        failure[index] = patternIndex
    }
    return failure
}
