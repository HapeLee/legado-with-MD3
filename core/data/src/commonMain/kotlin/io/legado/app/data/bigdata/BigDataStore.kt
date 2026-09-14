package io.legado.app.data.bigdata

/**
 * 大变量存储契约（P3 深水区第 1 刀）。
 *
 * 书源规则里超过 10000 字符的变量（见 [io.legado.app.model.analyzeRule.RuleDataInterface.putVariable]
 * 的大值分支）不进数据库，而是落到以「实体标识 + key」为路径的文件里。
 *
 * **实现已在共享层（M2-2）**：[RuleDataFileStore] 直接实现本契约，只借助
 * `core:platform` 的 [io.legado.app.core.platform.RuleDataStorage] 原语
 * （根目录 + 文件 IO + MD5）——路径规则、目录分区、标记文件都在共享层。
 *
 * **为什么不再是「接口 + 全局注入点」**：调用方是 Room entity 的实例方法
 * （`BaseBook`/`BaseRssArticle`/`BookChapter` 的 `putBigVariable`），它们由 Room 构造、
 * 不经过 DI，而方法名又是书源 JS 的兼容面（`AnalyzeRule` 里 `bindings["book"] = book`，
 * 脚本直接调 `book.putVariable`），签名不能改。这类对象访问平台能力只有「全局
 * service locator」或「平台原语」两条路；实现下沉共享层后，entity 直接引用
 * [RuleDataFileStore]，两条都不要——既没有全局注入点，也没有「漏装配」缺口。
 *
 * 平台差异只剩「根目录」一项，由 host 在 composition root 设置
 * [io.legado.app.core.platform.RuleDataStorage.rootDir]，未设置时读写显式失败。
 */
interface BigDataStore {

    /** 写入书籍级变量；`value == null` 表示删除。 */
    fun putBookVariable(bookUrl: String, key: String, value: String?)

    /** 读取书籍级变量，不存在返回 `null`。 */
    fun getBookVariable(bookUrl: String, key: String?): String?

    /** 书籍级变量是否存在。 */
    fun hasBookVariable(bookUrl: String, key: String): Boolean

    /** 写入章节级变量；`value == null` 表示删除。 */
    fun putChapterVariable(bookUrl: String, chapterUrl: String, key: String, value: String?)

    /** 读取章节级变量，不存在返回 `null`。 */
    fun getChapterVariable(bookUrl: String, chapterUrl: String, key: String): String?

    /** 写入 RSS 文章级变量；`value == null` 表示删除。 */
    fun putRssVariable(origin: String, link: String, key: String, value: String?)

    /** 读取 RSS 文章级变量，不存在返回 `null`。 */
    fun getRssVariable(origin: String, link: String, key: String): String?
}

/**
 * 纯内存实现：用于测试与非持久化场景，**不是**生产平台实现的替代品。
 *
 * 它提供真实的读写语义（写入后能读回、删除后读不到），只是不落盘，
 * 因此不属于 AGENTS.md 禁止的「静默空实现伪造跨平台支持」。
 */
class InMemoryBigDataStore : BigDataStore {

    private val values = linkedMapOf<String, String>()

    override fun putBookVariable(bookUrl: String, key: String, value: String?) {
        put("book|$bookUrl|$key", value)
    }

    override fun getBookVariable(bookUrl: String, key: String?): String? {
        key ?: return null
        return values["book|$bookUrl|$key"]
    }

    override fun hasBookVariable(bookUrl: String, key: String): Boolean {
        return values.containsKey("book|$bookUrl|$key")
    }

    override fun putChapterVariable(
        bookUrl: String,
        chapterUrl: String,
        key: String,
        value: String?
    ) {
        put("chapter|$bookUrl|$chapterUrl|$key", value)
    }

    override fun getChapterVariable(bookUrl: String, chapterUrl: String, key: String): String? {
        return values["chapter|$bookUrl|$chapterUrl|$key"]
    }

    override fun putRssVariable(origin: String, link: String, key: String, value: String?) {
        put("rss|$origin|$link|$key", value)
    }

    override fun getRssVariable(origin: String, link: String, key: String): String? {
        return values["rss|$origin|$link|$key"]
    }

    private fun put(key: String, value: String?) {
        if (value == null) {
            values.remove(key)
        } else {
            values[key] = value
        }
    }
}
