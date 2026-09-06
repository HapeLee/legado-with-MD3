package io.legado.app.model.analyzeRule

// 通用的规则切分处理。
class RuleAnalyzer(data: String, code: Boolean = false) {

    private var queue: String = data
    private var pos = 0
    private var start = 0
    private var startX = 0

    private var rule = ArrayList<String>()
    private var step: Int = 0
    var elementsType = ""

    fun trim() {
        if (queue[pos] == '@' || queue[pos] < '!') {
            pos++
            while (queue[pos] == '@' || queue[pos] < '!') pos++
            start = pos
            startX = pos
        }
    }

    fun reSetPos() {
        pos = 0
        startX = 0
    }

    private fun consumeTo(seq: String): Boolean {
        start = pos
        val offset = queue.indexOf(seq, pos)
        return if (offset != -1) {
            pos = offset
            true
        } else false
    }

    private fun consumeToAny(vararg seq: String): Boolean {
        var currentPos = pos
        while (currentPos != queue.length) {
            for (item in seq) {
                if (queue.regionMatches(currentPos, item, 0, item.length)) {
                    step = item.length
                    pos = currentPos
                    return true
                }
            }
            currentPos++
        }
        return false
    }

    private fun findToAny(vararg seq: Char): Int {
        var currentPos = pos
        while (currentPos != queue.length) {
            for (item in seq) if (queue[currentPos] == item) return currentPos
            currentPos++
        }
        return -1
    }

    private fun chompCodeBalanced(open: Char, close: Char): Boolean {
        var currentPos = pos
        var depth = 0
        var otherDepth = 0
        var inSingleQuote = false
        var inDoubleQuote = false
        do {
            if (currentPos == queue.length) break
            val char = queue[currentPos++]
            if (char != ESC) {
                if (char == '\'' && !inDoubleQuote) inSingleQuote = !inSingleQuote
                else if (char == '"' && !inSingleQuote) inDoubleQuote = !inDoubleQuote
                if (inSingleQuote || inDoubleQuote) continue
                if (char == '[') depth++
                else if (char == ']') depth--
                else if (depth == 0) {
                    if (char == open) otherDepth++
                    else if (char == close) otherDepth--
                }
            } else currentPos++
        } while (depth > 0 || otherDepth > 0)
        return if (depth > 0 || otherDepth > 0) false else {
            pos = currentPos
            true
        }
    }

    private fun chompRuleBalanced(open: Char, close: Char): Boolean {
        var currentPos = pos
        var depth = 0
        var inSingleQuote = false
        var inDoubleQuote = false
        do {
            if (currentPos == queue.length) break
            val char = queue[currentPos++]
            if (char == '\'' && !inDoubleQuote) inSingleQuote = !inSingleQuote
            else if (char == '"' && !inSingleQuote) inDoubleQuote = !inDoubleQuote
            if (inSingleQuote || inDoubleQuote) continue
            else if (char == '\\') {
                currentPos++
                continue
            }
            if (char == open) depth++
            else if (char == close) depth--
        } while (depth > 0)
        return if (depth > 0) false else {
            pos = currentPos
            true
        }
    }

    tailrec fun splitRule(vararg split: String): ArrayList<String> {
        if (split.size == 1) {
            elementsType = split[0]
            return if (!consumeTo(elementsType)) {
                rule += queue.substring(startX)
                rule
            } else {
                step = elementsType.length
                splitRule()
            }
        } else if (!consumeToAny(*split)) {
            rule += queue.substring(startX)
            return rule
        }

        val end = pos
        pos = start
        do {
            val selectorStart = findToAny('[', '(')
            if (selectorStart == -1) {
                rule = arrayListOf(queue.substring(startX, end))
                elementsType = queue.substring(end, end + step)
                pos = end + step
                while (consumeTo(elementsType)) {
                    rule += queue.substring(start, pos)
                    pos += step
                }
                rule += queue.substring(pos)
                return rule
            }
            if (selectorStart > end) {
                rule = arrayListOf(queue.substring(startX, end))
                elementsType = queue.substring(end, end + step)
                pos = end + step
                while (consumeTo(elementsType) && pos < selectorStart) {
                    rule += queue.substring(start, pos)
                    pos += step
                }
                return if (pos > selectorStart) {
                    startX = start
                    splitRule()
                } else {
                    rule += queue.substring(pos)
                    rule
                }
            }
            pos = selectorStart
            val next = if (queue[pos] == '[') ']' else ')'
            if (!chompBalanced(queue[pos], next)) throw Error(queue.substring(0, start) + "后未平衡")
        } while (end > pos)
        start = pos
        return splitRule(*split)
    }

    @JvmName("splitRuleNext")
    private tailrec fun splitRule(): ArrayList<String> {
        val end = pos
        pos = start
        do {
            val selectorStart = findToAny('[', '(')
            if (selectorStart == -1) {
                rule += arrayOf(queue.substring(startX, end))
                pos = end + step
                while (consumeTo(elementsType)) {
                    rule += queue.substring(start, pos)
                    pos += step
                }
                rule += queue.substring(pos)
                return rule
            }
            if (selectorStart > end) {
                rule += arrayListOf(queue.substring(startX, end))
                pos = end + step
                while (consumeTo(elementsType) && pos < selectorStart) {
                    rule += queue.substring(start, pos)
                    pos += step
                }
                return if (pos > selectorStart) {
                    startX = start
                    splitRule()
                } else {
                    rule += queue.substring(pos)
                    rule
                }
            }
            pos = selectorStart
            val next = if (queue[pos] == '[') ']' else ')'
            if (!chompBalanced(queue[pos], next)) throw Error(queue.substring(0, start) + "后未平衡")
        } while (end > pos)
        start = pos
        return if (!consumeTo(elementsType)) {
            rule += queue.substring(startX)
            rule
        } else splitRule()
    }

    fun innerRule(inner: String, startStep: Int = 1, endStep: Int = 1, fr: (String) -> String?): String {
        val result = StringBuilder()
        while (consumeTo(inner)) {
            val before = pos
            if (chompCodeBalanced('{', '}')) {
                val value = fr(queue.substring(before + startStep, pos - endStep))
                if (!value.isNullOrEmpty()) {
                    result.append(queue.substring(startX, before) + value)
                    startX = pos
                    continue
                }
            }
            pos += inner.length
        }
        return if (startX == 0) "" else result.apply { append(queue.substring(startX)) }.toString()
    }

    fun innerRule(startStr: String, endStr: String, fr: (String) -> String?): String {
        val result = StringBuilder()
        while (consumeTo(startStr)) {
            pos += startStr.length
            val before = pos
            if (consumeTo(endStr)) {
                val value = fr(queue.substring(before, pos))
                result.append(queue.substring(startX, before - startStr.length) + value)
                pos += endStr.length
                startX = pos
            }
        }
        return if (startX == 0) queue else result.apply { append(queue.substring(startX)) }.toString()
    }

    val chompBalanced = if (code) ::chompCodeBalanced else ::chompRuleBalanced

    private companion object {
        const val ESC = '\\'
    }
}
