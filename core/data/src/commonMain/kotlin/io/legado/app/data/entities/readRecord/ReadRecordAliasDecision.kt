package io.legado.app.data.entities.readRecord

/** 未知作者记录的归属处理方式。 */
enum class ReadRecordAliasAction { MERGE, KEEP }

/** 负责作者归属决定的规范化、编码、解析和撤销。 */
object ReadRecordAliasDecision {
    private const val SEPARATOR = "\u0001"

    /** 生成用于持久化用户选择的规范化书名/作者键。 */
    fun key(bookName: String, author: String): String =
        ReadRecordIdentity.key(bookName, author)

    /** 将一个归属决定编码为单行文本，格式为 key、分隔符和 merge/keep。 */
    fun encode(key: String, action: ReadRecordAliasAction): String =
        "$key$SEPARATOR${action.name.lowercase()}"

    /** 解析指定键的决定；键不匹配或内容非法时返回 null。 */
    fun decode(line: String, key: String): ReadRecordAliasAction? {
        if (!line.startsWith("$key$SEPARATOR")) return null
        return when (line.substringAfter(SEPARATOR)) {
            "merge" -> ReadRecordAliasAction.MERGE
            "keep" -> ReadRecordAliasAction.KEEP
            else -> null
        }
    }

    /** 从多行持久化内容中删除指定键的旧决定。 */
    fun removeForKey(lines: String, key: String): String = lines
        .lineSequence()
        .filter { it.isNotBlank() && !it.startsWith("$key$SEPARATOR") }
        .joinToString("\n")
}
