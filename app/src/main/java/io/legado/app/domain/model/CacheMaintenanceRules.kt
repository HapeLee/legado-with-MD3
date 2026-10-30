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
     * 由调用方保证传入的 [cachedEntryNames] 只含两类：章节目录（`%05d-<titleMD5>`）
     * 和章节正文文件（`%05d-<titleMD5>.nb`）。其它条目不要传进来。
     *
     * @param legalSubjects 该书章节列表里现存的、由 `getFileName().substringBeforeLast('.')` 得到主体名
     * @param bookImageFolderName 书级图片目录名（`BookHelp` 里的 `cacheImageFolderName`），它不是章节，永远不算孤儿
     *
     * 注意：当 [legalSubjects] 为空时不产出任何孤儿。书刚入库还没拉到目录时，
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
     * `N万字` 只保留两位小数，反解得出的值最多偏差不到万字档的一半，用作展示级近似是安全的。
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
