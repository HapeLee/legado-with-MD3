package io.legado.app.help.book

/**
 * 书籍自定义标签辅助工具。
 * 标签以分隔符存储于 [Book.customTag]，支持解析、合并、查询。
 */
object BookTagHelper {

    private const val SEPARATOR = ";"

    /** 解析 customTag 字符串为标签列表，去空白、去重（忽略大小写）、保持顺序。 */
    fun parse(customTag: String?): List<String> {
        if (customTag.isNullOrBlank()) return emptyList()
        val seen = LinkedHashSet<String>()
        customTag.split(SEPARATOR, ",", "，", "\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { seen.add(it) }
        return seen.toList()
    }

    /** 将标签列表合并为 customTag 字符串。 */
    fun join(tags: List<String>): String {
        return tags
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(SEPARATOR)
    }

    /** 判断 customTag 中是否包含指定标签（忽略大小写）。 */
    fun has(customTag: String?, tag: String): Boolean {
        if (tag.isBlank()) return false
        return parse(customTag).any { it.equals(tag, ignoreCase = true) }
    }
}
