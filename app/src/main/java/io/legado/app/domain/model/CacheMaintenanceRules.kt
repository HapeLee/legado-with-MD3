package io.legado.app.domain.model

/**
 * 缓存维护的判定规则。
 *
 * 全部是不碰 File / Database 的纯函数，方便 JVM 单测直接覆盖最容易出错的口径
 * （孤儿识别边界、字数解析精度、溢出处理），而不用构造 Robolectric 环境。
 */
object CacheMaintenanceRules {

    /**
     * 从该书缓存目录下的一级条目里找出孤立项。
     *
     * 由调用方保证传入的是书缓存目录下的一级候选条目（目录或 `.nb` 文件）。
     * **不按命名格式过滤**：早期版本/异 Locale 写入的历史命名残留同样查无此章，也是孤儿。
     * 能被当前版本读到的合法缓存，其名字必然等于某章 `getFileName()` 推导出的主体名，
     * 因而必然落在 [legalSubjects] 里受到保护。
     *
     * @param legalSubjects 该书章节列表里现存的、由 `getFileName().substringBeforeLast('.')` 得到主体名
     * @param bookImageFolderName 书级图片目录名（`BookHelp` 里的 `cacheImageFolderName`），它不是章节，永远不算孤儿
     *
     * 注意：当 [legalSubjects] 为空时不产出任何孤儿。章节表读取失败或书刚入库还没拉到目录时，
     * 磁盘上已有的缓存不能因为"查无此章"而被判定要删。
     */
    fun findOrphanSubjects(
        cachedEntryNames: List<String>,
        legalSubjects: Set<String>,
        bookImageFolderName: String,
    ): List<String> {
        if (legalSubjects.isEmpty()) return emptyList()
        return cachedEntryNames.filter { name ->
            if (name == bookImageFolderName) return@filter false
            val subject = name.substringBeforeLast(".nb")
            subject !in legalSubjects
        }
    }

    /**
     * 把库里存的字数字段还原为数值。
     *
     * 只识别我们自己写过的格式（`StringUtils.wordCountFormat` 产出的 `N字` / `N万字`）
     * 以及纯数字；书源可能给任意文案，解析不了就返回 null，让调用方放弃而不是猜一个数。
     *
     * `StringUtils.wordCountFormatter` 是 DecimalFormat("#.#")，万字档只保留 1 位小数，
     * 反解的单章误差最多 500 字；整书求和会累积，但结果本身也只是展示用的近似值。
     */
    fun parseStoredWordCount(raw: String?): Int? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        wanRegex.matchEntire(value)?.let { match ->
            val wan = match.groupValues[1].toDoubleOrNull() ?: return null
            return (wan * 10_000).toLong().coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        }
        plainRegex.matchEntire(value)?.let { match ->
            return match.groupValues[1].toLongOrNull()
                ?.coerceIn(0L, Int.MAX_VALUE.toLong())
                ?.toInt()
        }
        return null
    }

    /**
     * 多章字数求和，溢出时截断到 [Int.MAX_VALUE]。
     *
     * `StringUtils.wordCountFormat` 只吃 Int，这里必须先兜住，否则超大书会求和成负数。
     */
    fun sumWordCounts(values: Iterable<Int>): Int {
        var total = 0L
        for (value in values) {
            total += value.toLong()
            if (total > Int.MAX_VALUE) return Int.MAX_VALUE
        }
        return total.toInt()
    }

    private val wanRegex = Regex("""^(\d+(?:\.\d+)?)万字$""")
    private val plainRegex = Regex("""^(\d+)字?$""")
}
