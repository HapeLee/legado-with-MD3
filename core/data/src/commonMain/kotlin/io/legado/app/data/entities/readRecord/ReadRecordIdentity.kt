package io.legado.app.data.entities.readRecord

/** 阅读记录聚合与恢复共同使用的规范化身份工具。 */
object ReadRecordIdentity {
    /** 规范化书名：折叠连续空白并去除首尾空白。 */
    fun bookName(value: String): String = normalize(value)

    /** 空字符串表示确实未知作者，非空字符串表示真实作者。 */
    fun author(value: String): String = normalize(value)

    /** 生成跨恢复、合并流程复用的稳定身份键。 */
    fun key(bookName: String, author: String): String =
        "${bookName(bookName)}\u0000${author(author)}"

    private fun normalize(value: String): String =
        value.replace(WHITESPACE, " ").trim()

    private val WHITESPACE = Regex("[\\p{Z}\\s]+")
}
