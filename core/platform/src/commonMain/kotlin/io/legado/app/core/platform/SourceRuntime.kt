package io.legado.app.core.platform

/**
 * 源运行时契约（P4-e BaseSource 下沉第 5 刀）。
 *
 * 承载 BaseSource 里「源生命周期/运行时杂项」能力，这些能力原本散落在 app 侧的
 * `HandlerUtils.isMainThread`、`BaseSourceExtensions.getShareScope`、
 * `BookSourceExtensions.clearExploreKindsCache`、`SharedJsScope.remove`、
 * `ConcurrentRateLimiter.updateConcurrentRate`、`AppConst.androidId` 里。
 *
 * 它们共同点是：都是「源实体运行时需要、但依赖平台/Android 栈」的能力，
 * 且只在 `refreshExplore`/`refreshJSLib`/`putConcurrent`/`evalJS` 这几个 JS 桥方法里
 * 被使用。收进一个契约，避免为每个杂项各立一个 provider
 * （AGENTS.md「接口 + DI」对「无调用方抽象」的平衡：这些是同一职责簇）。
 *
 * 方法签名刻意用基础类型 / `Any` 而非 `BaseSource`，避免 core:platform 反向依赖
 * core:data 的实体类型（`clearExploreKindsCache(source: Any)` 由实现侧判定 `is BookSource`）。
 */
interface SourceRuntime {

    /** 当前是否主线程（对齐 `HandlerUtils.isMainThread`）。 */
    fun isMainThread(): Boolean

    /** 获取共享 JS 作用域（对齐 `SharedJsScope.getScope(jsLib, null)`）。 */
    fun getShareScope(jsLib: String?): JsScope?

    /** 移除指定 jsLib 的共享作用域缓存（对齐 `SharedJsScope.remove(jsLib)`）。 */
    fun removeJsLib(jsLib: String?)

    /** 清理探索分类缓存；`source` 为实体（实现侧判定 `is BookSource`）。 */
    fun clearExploreKindsCache(source: Any)

    /** 更新并发率限制（对齐 `ConcurrentRateLimiter.updateConcurrentRate`）。 */
    fun updateConcurrentRate(key: String, value: String)

    /** 设备唯一标识（对齐 `AppConst.androidId`，用作 AES 密钥来源）。 */
    fun androidId(): String
}

/**
 * [SourceRuntime] 的注入点。模式同 [KeyValueStoreProvider]。
 */
object SourceRuntimeProvider {

    @Volatile
    private var delegate: SourceRuntime? = null

    fun install(runtime: SourceRuntime) {
        delegate = runtime
    }

    fun uninstall() {
        delegate = null
    }

    val isInstalled: Boolean get() = delegate != null

    val current: SourceRuntime
        get() = delegate ?: error(
            "SourceRuntime 未安装：请在应用 composition root 调用 " +
                "SourceRuntimeProvider.install(...) 注入平台实现。"
        )
}
