package io.legado.app.help.book

/**
 * 替换净化期间保护不属于正文文字的特殊 token。
 *
 * `<img>` 标签与 `[newpage]` 分页标记以前直接暴露给替换规则：一条 `\d+` 或人名规则就能
 * 改坏图片地址、吞掉分页标记。这里在规则执行前换成正文不可能出现的私用区占位符，
 * 规则跑完再原样还原（与 Arc 分支的 SpecialContentProtector 同序）。
 */
internal object SpecialContentProtector {

    // 引号感知，避免 title="a>b" 这类属性值被截断。
    private val imgRegex = Regex(
        """<img\b(?:"[^"]*"|'[^']*'|[^'">])*>""",
        RegexOption.IGNORE_CASE,
    )

    // 只吃行内空白，不跨行；[newpage] 在正文里是独立整行。
    private val newPageRegex = Regex("""(?m)^[ \t\u3000]*\[newpage][ \t\u3000]*$""")

    fun protect(content: String): Protected {
        val placeholders = LinkedHashMap<String, String>()
        fun reserve(value: String): String {
            val key = "\uE000LEGADO_SPECIAL_${placeholders.size}\uE001"
            placeholders[key] = value
            return key
        }
        var protected = imgRegex.replace(content) { reserve(it.value) }
        protected = newPageRegex.replace(protected) { reserve(it.value) }
        return Protected(protected, placeholders)
    }

    class Protected(val content: String, private val placeholders: Map<String, String>) {

        fun restore(value: String): String {
            if (placeholders.isEmpty()) return value
            var restored = value
            placeholders.forEach { (placeholder, original) ->
                restored = restored.replace(placeholder, original)
            }
            return restored
        }
    }
}
