package io.legado.app.core.platform

/**
 * Cookie 存储契约（P4-e BaseSource 下沉第 2 刀）。
 *
 * 取代 app 侧 `help.http.CookieStore` 的核心用法，让 commonMain 的 BaseSource
 * 能管理登录 Cookie 而不依赖 okhttp 的 `CookieJar`/Android 栈。
 *
 * BaseSource 只用到 [replaceCookie] / [removeCookie] 两个方法，这里按真实调用方
 * 收敛（AGENTS.md「无调用方抽象」）。
 *
 * **为什么是 interface + composition root 注入**：实现依赖 `:app` 的 okhttp
 * `CookieManager`/`appDb.cookieDao`，`core:*` 不能反向依赖 `:app`。
 *
 * **未注入时显式失败**：[CookieStoreProvider.current] 未安装时抛异常。
 */
interface CookieStore {

    /**
     * 以 `url` 为域名键替换（覆盖）一条 Cookie。
     *
     * 对齐 `CookieStore.replaceCookie(url: String, cookie: String)`。
     */
    fun replaceCookie(url: String, cookie: String)

    /**
     * 移除 `url` 域名键下的 Cookie。
     *
     * 对齐 `CookieStore.removeCookie(url: String)`。
     */
    fun removeCookie(url: String)
}

/**
 * [CookieStore] 的注入点。模式同 [KeyValueStoreProvider]。
 */
object CookieStoreProvider {

    @Volatile
    private var delegate: CookieStore? = null

    fun install(store: CookieStore) {
        delegate = store
    }

    fun uninstall() {
        delegate = null
    }

    val isInstalled: Boolean get() = delegate != null

    val current: CookieStore
        get() = delegate ?: error(
            "CookieStore 未安装：请在应用 composition root 调用 " +
                "CookieStoreProvider.install(...) 注入平台实现（Android 为 help.http.CookieStore）。"
        )
}

/**
 * 纯内存实现：用于测试，提供真实「替换后能读回、移除后读不到」的语义。
 */
class InMemoryCookieStore : CookieStore {

    private val cookies = linkedMapOf<String, String>()

    override fun replaceCookie(url: String, cookie: String) {
        cookies[url] = cookie
    }

    override fun removeCookie(url: String) {
        cookies.remove(url)
    }

    /** 测试辅助：读取某域名键下的 Cookie，不存在返回 `null`。 */
    fun getCookie(url: String): String? = cookies[url]
}
