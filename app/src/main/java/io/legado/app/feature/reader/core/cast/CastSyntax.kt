package io.legado.app.feature.reader.core.cast

/**
 * 多角色标记语法：`<markStart>名字<poolStart>声音池<poolEnd><markEnd>`。
 *
 * 符号可被用户在「多角色规则 → 多角色识别」里改（默认 `<<` `>>` `（` `）`），
 * 所以标记的生成与解析都必须走本对象而不是字面量。除括号形式外，解析侧还接受
 * [ALT_SEPARATORS] 里的单字符分隔（`名字.男青年`、`名字·男青年`），因为外部写出的
 * 标记形式不统一；生成侧始终输出配置的括号形式。
 *
 * 引号对（[CastMarkers.QuotePairs]）不参与配置：锚点序号按引号数出来，
 * 改了会让已存的 chapter_role_assignments.quoteOrdinal 全部错位。
 */
class CastSyntax(
    val markStart: String = DEFAULT_MARK_START,
    val markEnd: String = DEFAULT_MARK_END,
    val poolStart: String = DEFAULT_POOL_START,
    val poolEnd: String = DEFAULT_POOL_END,
) {

    /** 名字/池里禁止出现的字符：包裹符号 + 池符号 + 分隔符 + 引号，防止名字本身破坏标记语法。 */
    private val forbidden: String = buildList {
        addAll(markStart.map { it.toString() })
        addAll(markEnd.map { it.toString() })
        addAll(poolStart.map { it.toString() })
        addAll(poolEnd.map { it.toString() })
        ALT_SEPARATORS.forEach { add(it.toString()) }
        // & 是 HTML 语义文本的转义起点，任何符号配置下都不允许进名字
        addAll(listOf("“", "”", "‘", "’", "「", "」", "『", "』", "\"", "'", "\n", "&"))
    }.map { it.escapeForCharacterClass() }.distinct().joinToString("")

    /** 已分配标记：配置形式 `<<名（池）>>`/`<<名>>`，以及 `.`/`·` 等等价分隔形式。 */
    val markerRegex: Regex = Regex(
        "${Regex.escape(markStart)}([^$forbidden]{1,24})" +
            "(?:${Regex.escape(poolStart)}([^$forbidden]{1,12})${Regex.escape(poolEnd)}" +
            "|[$ALT_SEPARATORS]([^$forbidden]{1,12}))?${Regex.escape(markEnd)}",
    )

    /** 名字合法性（弹层输入与标记语法双重约束）。 */
    private val nameRegex: Regex = Regex("^[^$forbidden]{1,24}$")

    fun isValidName(name: String): Boolean = name.trim().matches(nameRegex)

    /** 标记全文；名字非法返回 null。 */
    fun markerText(characterName: String, voicePoolLabel: String): String? {
        val name = characterName.trim().take(24)
        if (!isValidName(name)) return null
        val pool = voicePoolLabel.trim().take(12)
        return if (pool.isEmpty()) {
            "$markStart$name$markEnd"
        } else {
            "$markStart$name$poolStart$pool$poolEnd$markEnd"
        }
    }

    /** 胶囊显示标签：`名字（池）`，与标记同一份符号配置。 */
    fun labelOf(characterName: String, voicePoolLabel: String): String {
        val name = characterName.trim().take(24)
        val pool = voicePoolLabel.trim().take(12)
        return if (pool.isEmpty()) name else "$name$poolStart$pool$poolEnd"
    }

    /** 去掉标记包裹并拆出名字与池：接受括号与单字符分隔两种内部形式。 */
    fun parse(text: String): Pair<String, String>? {
        val match = markerRegex.find(text.trim()) ?: return null
        val name = match.groupValues[1].trim()
        val pool = (match.groupValues.getOrNull(2).orEmpty()
            .ifBlank { match.groupValues.getOrNull(3).orEmpty() }).trim()
        return name.takeIf { it.isNotEmpty() }?.let { it to pool }
    }

    companion object {        const val DEFAULT_MARK_START = "<<"
        const val DEFAULT_MARK_END = ">>"
        const val DEFAULT_POOL_START = "（"
        const val DEFAULT_POOL_END = "）"

        /** 声音池的单字符等价分隔（解析侧接受，生成侧不用）。 */
        const val ALT_SEPARATORS = ".,·、"

        val DEFAULT = CastSyntax()
    }
}

/**
 * 当前生效的标记语法。运行时由 [io.legado.app.help.readaloud.cast.CastSyntaxStore]
 * 从 prefs 装载；未装载时用默认符号，保证纯 Kotlin 链路（测量、分段）永远可用。
 */
object CastSyntaxHolder {
    @Volatile
    var current: CastSyntax = CastSyntax.DEFAULT
}

/** 用户自定义符号会原样进正则字符类，这几个字符必须先转义，否则 PatternSyntaxException。 */
private fun String.escapeForCharacterClass(): String = buildString {
    for (c in this@escapeForCharacterClass) {
        if (c in "\\]^-[") append('\\')
        append(c)
    }
}
