package io.legado.app.core.platform

/**
 * 键值存储契约（P4-e BaseSource 下沉第 1 刀）。
 *
 * 取代 app 侧 `help.CacheManager` 的核心 KV 用法，让 commonMain 的值对象
 * （BaseSource 等 entity）能读写持久化缓存而不依赖 Android `Context`/`appDb`。
 *
 * BaseSource 只用到 [get]（`String?` 单参数）、[put]（字符串 + saveTime）、[delete]
 * 三个方法，这里按真实调用方收敛，不复制 CacheManager 的全部重载
 * （AGENTS.md「无调用方抽象」）。
 *
 * **为什么是 interface + composition root 注入，而不是 expect/actual**：
 * 磁盘缓存根目录来自 Android `Context`，实现复用 `:app` 的 `appDb.cacheDao` +
 * ACache（`core:*` 不能反向依赖 `:app`）。按 AGENTS.md「可用普通接口 + DI 表达的
 * 能力，不使用 expect/actual」，这里走接口注入（与 [io.legado.app.data.bigdata.BigDataStore]
 * 同模式）。
 *
 * **未注入时显式失败**：[KeyValueStoreProvider.current] 在未安装时抛异常，
 * 不做静默空实现——平台能力不可用必须显式可见（AGENTS.md）。
 */
interface KeyValueStore {

    /**
     * 读取字符串缓存，不存在或已过期返回 `null`。
     *
     * 对齐 `CacheManager.get(key): String?` 语义（内存 LruCache 优先，未命中回落磁盘）。
     */
    fun get(key: String): String?

    /**
     * 写入字符串缓存；`saveTime` 单位为秒，`0` 表示永不过期。
     *
     * 对齐 `CacheManager.put(key, value: String, saveTime: Int)`。
     */
    fun put(key: String, value: String, saveTime: Int = 0)

    /**
     * 删除缓存（内存 + 磁盘）。
     *
     * 对齐 `CacheManager.delete(key)`。
     */
    fun delete(key: String)
}

/**
 * [KeyValueStore] 的注入点。
 *
 * 调用方包括 Room entity 的实例方法（`BaseSource.getVariable` 等），entity 由 Room
 * 构造、不经过 DI，因此用全局 holder 而非构造函数注入——与 `:app` 的 `appDb` 同模式。
 * 平台侧在应用 composition root 注入一次即可。
 */
object KeyValueStoreProvider {

    @Volatile
    private var delegate: KeyValueStore? = null

    /** 注入平台实现；重复调用以最后一次为准。 */
    fun install(store: KeyValueStore) {
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
    val current: KeyValueStore
        get() = delegate ?: error(
            "KeyValueStore 未安装：请在应用 composition root 调用 " +
                "KeyValueStoreProvider.install(...) 注入平台实现（Android 为 CacheManager）。"
        )
}

/**
 * 纯内存实现：用于测试与非持久化场景，**不是**生产平台实现的替代品。
 *
 * 提供真实读写语义（写入能读回、删除读不到、saveTime 到期失效），只是不落盘，
 * 因此不属于 AGENTS.md 禁止的「静默空实现伪造跨平台支持」。
 */
class InMemoryKeyValueStore : KeyValueStore {

    private class Entry(val value: String, val deadline: Long)

    private val values = linkedMapOf<String, Entry>()

    override fun get(key: String): String? {
        val entry = values[key] ?: return null
        if (entry.deadline != 0L && entry.deadline <= currentTimeMillis()) {
            values.remove(key)
            return null
        }
        return entry.value
    }

    override fun put(key: String, value: String, saveTime: Int) {
        val deadline = if (saveTime == 0) 0L else currentTimeMillis() + saveTime * 1000L
        values[key] = Entry(value, deadline)
    }

    override fun delete(key: String) {
        values.remove(key)
    }

    private fun currentTimeMillis(): Long = io.legado.app.core.platform.systemTimeMillis()
}
