package io.legado.app.data.bigdata

/**
 * 大变量存储契约（P3 深水区第 1 刀）。
 *
 * 书源规则里超过 10000 字符的变量（见 [io.legado.app.model.analyzeRule.RuleDataInterface.putVariable]
 * 的大值分支）不进数据库，而是落到以「实体标识 + key」为路径的文件里。
 *
 * 原实现是 `:app` 的 `help.RuleBigDataHelp`，它同时依赖 `Context.externalFiles`、`MD5Utils`、
 * `FileUtils` 和 `appDb`，整体无法下沉。因此这里只抽出「读写大变量」这一件事，
 * 实现仍留在平台侧。
 *
 * **为什么是 interface + composition root 注入，而不是 expect/actual**：
 * 存储根目录来自 Android `Context`，且实现复用 `:app` 的 `MD5Utils`/`FileUtils`
 * （`core:*` 不能反向依赖 `:app`）。按 AGENTS.md「可用普通接口 + DI 表达的能力，
 * 不使用 expect/actual」，这里走接口注入。
 *
 * **未注入时显式失败**：[BigDataStoreProvider.current] 在未安装时抛异常，
 * 不做静默空实现——平台能力不可用必须显式可见（AGENTS.md）。
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
 * [BigDataStore] 的注入点。
 *
 * 注意：调用方包括 Room entity 的实例方法（`BaseRssArticle.putBigVariable` 等），
 * 而 entity 由 Room 构造、不经过 DI，因此这里用全局 holder 而非构造函数注入——
 * 与 `:app` 的 `appDb` 同模式。平台侧在应用 composition root 注入一次即可。
 */
object BigDataStoreProvider {

    @Volatile
    private var delegate: BigDataStore? = null

    /** 注入平台实现；重复调用以最后一次为准。 */
    fun install(store: BigDataStore) {
        delegate = store
    }

    /** 移除已注入的实现，回到「未安装」状态；用于测试隔离。 */
    fun uninstall() {
        delegate = null
    }

    /** 是否已注入平台实现。 */
    val isInstalled: Boolean get() = delegate != null

    /**
     * 当前实现。
     *
     * @throws IllegalStateException 未注入时抛出，提示调用方先完成 composition root 组装。
     */
    val current: BigDataStore
        get() = delegate ?: error(
            "BigDataStore 未安装：请在应用 composition root 调用 " +
                "BigDataStoreProvider.install(...) 注入平台实现（Android 为 RuleBigDataHelp）。"
        )
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
