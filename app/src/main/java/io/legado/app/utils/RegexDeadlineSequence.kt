package io.legado.app.utils

import io.legado.app.exception.RegexTimeoutException

/**
 * 带时间预算的只读字符视图，给无法被协程取消的 Java 正则加截止时间。
 *
 * 病态表达式（如 `(a+)+$`）的指数回溯只在 `CharSequence.charAt` 上暴露，[RegexTimeoutException]
 * 会从中抛出并打断 matcher；调用方负责把它转成"没有结果"或错误提示。
 */
class RegexDeadlineSequence(
    private val source: CharSequence,
    timeoutMillis: Long,
) : CharSequence {

    private val deadline = System.currentTimeMillis() + timeoutMillis

    override val length: Int
        get() = source.length

    override fun get(index: Int): Char {
        if (System.currentTimeMillis() > deadline) {
            throw RegexTimeoutException("正则匹配超时：表达式回溯过多，请简化表达式")
        }
        return source[index]
    }

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
        source.subSequence(startIndex, endIndex)

    override fun toString(): String = source.toString()
}
