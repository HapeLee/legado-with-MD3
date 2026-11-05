package io.legado.app.utils

import io.legado.app.domain.model.TextProjection
import org.apache.commons.text.StringEscapeUtils
import java.io.StringWriter

/** Uses the actual matcher output, including back references; never reruns a rule. */
internal fun String.replaceMapped(regex: Regex, replacement: String, projection: TextProjection?): String {
    if (projection == null) return replace(regex, replacement)
    val matcher = regex.toPattern().matcher(this)
    val result = StringBuffer()
    val pieces = mutableListOf<TextProjection.Piece>()
    var cursor = 0
    while (matcher.find()) {
        matcher.appendReplacement(result, replacement)
        pieces += TextProjection.Piece(cursor, matcher.start())
        var index = 0
        while (index < replacement.length) {
            when (replacement[index]) {
                '\\' -> { pieces += TextProjection.Piece(matcher.start(), matcher.end(), 1); index += 2 }
                '$' -> {
                    index++
                    val groupStart: Int
                    val groupEnd: Int
                    if (replacement[index] == '{') {
                        val end = replacement.indexOf('}', index + 1)
                        val name = replacement.substring(index + 1, end)
                        groupStart = matcher.start(name); groupEnd = matcher.end(name); index = end + 1
                    } else {
                        var number = replacement[index++].digitToInt()
                        while (index < replacement.length && replacement[index].isDigit() &&
                            number * 10 + replacement[index].digitToInt() <= matcher.groupCount()) {
                            number = number * 10 + replacement[index++].digitToInt()
                        }
                        groupStart = matcher.start(number); groupEnd = matcher.end(number)
                    }
                    if (groupStart >= 0) pieces += TextProjection.Piece(groupStart, groupEnd)
                }
                else -> {
                    val begin = index++
                    while (index < replacement.length && replacement[index] != '$' && replacement[index] != '\\') index++
                    pieces += TextProjection.Piece(matcher.start(), matcher.end(), index - begin)
                }
            }
        }
        cursor = matcher.end()
    }
    matcher.appendTail(result)
    pieces += TextProjection.Piece(cursor, length)
    projection.rebuild(pieces)
    return result.toString()
}

internal fun String.replaceMapped(old: String, replacement: String, projection: TextProjection?, ignoreCase: Boolean = false): String {
    if (projection == null) return replace(old, replacement, ignoreCase)
    return replaceMapped(Regex(Regex.escape(old), if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()),
        java.util.regex.Matcher.quoteReplacement(replacement), projection)
}

internal fun String.unescapeHtml4Mapped(projection: TextProjection?): String {
    if (projection == null) return StringEscapeUtils.unescapeHtml4(this)
    val result = StringWriter(length)
    val edits = mutableListOf<TextProjection.Edit>()
    var index = 0
    while (index < length) {
        val before = result.buffer.length
        val consumed = StringEscapeUtils.UNESCAPE_HTML4.translate(this, index, result)
        if (consumed == 0) {
            val end = index + Character.charCount(codePointAt(index))
            result.write(substring(index, end))
            index = end
        } else {
            val end = offsetByCodePoints(index, consumed)
            edits += TextProjection.Edit(index, end, result.buffer.length - before)
            index = end
        }
    }
    projection.replace(edits)
    return result.toString()
}
