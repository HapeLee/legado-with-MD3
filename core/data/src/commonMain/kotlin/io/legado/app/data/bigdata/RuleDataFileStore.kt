package io.legado.app.data.bigdata

import io.legado.app.core.platform.RuleDataStorage

/** 大变量目录下的一个实体条目：目录名（实体标识的 MD5）+ 从标记文件读回的原始标识。 */
data class RuleDataOwner(val name: String, val id: String?, val isDirectory: Boolean)

/**
 * [BigDataStore] 的共享实现：大变量落到 [RuleDataStorage.rootDir] 下，
 * 路径由「实体标识 + key」的 MD5 拼出。
 *
 * 移植自 `:app` 的 `help.RuleBigDataHelp`，**路径规则逐字节保留**（MD5 小写十六进制、
 * 标记文件名、目录分区），既有数据可直接读。不同之处只有一处：不再依赖
 * `Context`/`FileUtils`/`MD5Utils`/`appDb` —— 前三项收在 [RuleDataStorage] 里，
 * `appDb` 只出现在清理任务（属于 host，见 `:app` 的 `RuleDataCleaner`）。
 *
 * 为什么实现住在共享层而不是平台侧：消费方是 Room entity 的实例方法
 * （`BaseBook`/`BaseRssArticle`/`BookChapter` 的 `putBigVariable`），而 entity 由 Room 构造、
 * 不经过 DI，又必须保留书源 JS 的调用签名（`book.putVariable`）。实现下沉后，
 * entity 直接引用本 object，既没有全局 service locator，也没有「漏装配 = 运行时错误」的缺口。
 *
 * **前置条件**：使用前 host 必须已设置 [RuleDataStorage.rootDir]，否则读写显式抛异常。
 */
object RuleDataFileStore : BigDataStore {

    private const val DIR_BOOK = "book"
    private const val DIR_RSS = "rss"
    private const val MARKER_BOOK_URL = "bookUrl.txt"
    private const val MARKER_ORIGIN = "origin.txt"

    override fun putBookVariable(bookUrl: String, key: String, value: String?) {
        val dir = bookDir(bookUrl)
        if (value == null) {
            RuleDataStorage.delete(valuePath(dir, key), true)
        } else {
            RuleDataStorage.writeText(valuePath(dir, key), value)
            writeMarkerIfAbsent("$dir/$MARKER_BOOK_URL", bookUrl)
        }
    }

    override fun getBookVariable(bookUrl: String, key: String?): String? {
        // 与迁移前一致：key 为 null 时按空串取 MD5（通常不存在 ⇒ null），不特判。
        return RuleDataStorage.readText(valuePath(bookDir(bookUrl), key.orEmpty()))
    }

    override fun hasBookVariable(bookUrl: String, key: String): Boolean {
        return RuleDataStorage.exists(valuePath(bookDir(bookUrl), key))
    }

    override fun putChapterVariable(
        bookUrl: String,
        chapterUrl: String,
        key: String,
        value: String?,
    ) {
        val dir = chapterDir(bookUrl, chapterUrl)
        if (value == null) {
            RuleDataStorage.delete(valuePath(dir, key), true)
        } else {
            RuleDataStorage.writeText(valuePath(dir, key), value)
            writeMarkerIfAbsent("${bookDir(bookUrl)}/$MARKER_BOOK_URL", bookUrl)
        }
    }

    override fun getChapterVariable(
        bookUrl: String,
        chapterUrl: String,
        key: String,
    ): String? {
        return RuleDataStorage.readText(valuePath(chapterDir(bookUrl, chapterUrl), key))
    }

    override fun putRssVariable(origin: String, link: String, key: String, value: String?) {
        val dir = rssLinkDir(origin, link)
        if (value == null) {
            RuleDataStorage.delete(valuePath(dir, key), true)
        } else {
            RuleDataStorage.writeText(valuePath(dir, key), value)
            writeMarkerIfAbsent("${rssOriginDir(origin)}/$MARKER_ORIGIN", origin)
            writeMarkerIfAbsent("$dir/$MARKER_ORIGIN", link)
        }
    }

    override fun getRssVariable(origin: String, link: String, key: String): String? {
        return RuleDataStorage.readText(valuePath(rssLinkDir(origin, link), key))
    }

    /**
     * 列出书籍分区下的全部条目（含目录名、从 `bookUrl.txt` 读回的原始标识、是否目录）。
     *
     * 供 host 的清理任务判断「这条大变量所属的书还在不在」。
     */
    fun listBookOwners(): List<RuleDataOwner> = listOwners(DIR_BOOK, MARKER_BOOK_URL)

    /** 列出 RSS 分区下的全部条目；原始标识来自 `origin.txt`。 */
    fun listRssOwners(): List<RuleDataOwner> = listOwners(DIR_RSS, MARKER_ORIGIN)

    /** 删除书籍分区下某个条目（目录名取自 [listBookOwners]）。 */
    fun deleteBookEntry(name: String) {
        RuleDataStorage.delete("$DIR_BOOK/$name", true)
    }

    /** 删除 RSS 分区下某个条目（目录名取自 [listRssOwners]）。 */
    fun deleteRssEntry(name: String) {
        RuleDataStorage.delete("$DIR_RSS/$name", true)
    }

    private fun listOwners(dir: String, markerName: String): List<RuleDataOwner> {
        return RuleDataStorage.list(dir).map { entry ->
            val id = if (entry.isDirectory) {
                RuleDataStorage.readText("$dir/${entry.name}/$markerName")
            } else {
                null
            }
            RuleDataOwner(entry.name, id, entry.isDirectory)
        }
    }

    private fun bookDir(bookUrl: String): String = "$DIR_BOOK/${RuleDataStorage.md5(bookUrl)}"

    private fun chapterDir(bookUrl: String, chapterUrl: String): String =
        "${bookDir(bookUrl)}/${RuleDataStorage.md5(chapterUrl)}"

    private fun rssOriginDir(origin: String): String = "$DIR_RSS/${RuleDataStorage.md5(origin)}"

    private fun rssLinkDir(origin: String, link: String): String =
        "${rssOriginDir(origin)}/${RuleDataStorage.md5(link)}"

    private fun valuePath(dir: String, key: String): String = "$dir/${RuleDataStorage.md5(key)}.txt"

    private fun writeMarkerIfAbsent(relativePath: String, content: String) {
        if (!RuleDataStorage.exists(relativePath)) {
            RuleDataStorage.writeText(relativePath, content)
        }
    }
}
