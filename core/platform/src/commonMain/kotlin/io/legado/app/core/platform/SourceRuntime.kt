package io.legado.app.core.platform

/**
 * 源运行时契约（P4-e BaseSource 下沉第 5 刀，M2-4b 收窄）。
 *
 * 承载 BaseSource 里「源生命周期/运行时杂项」能力，这些能力原本散落在 app 侧的
 * `HandlerUtils.isMainThread`、`BaseSourceExtensions.getShareScope`、
 * `BookSourceExtensions.clearExploreKindsCache`、`SharedJsScope.remove`、
 * `ConcurrentRateLimiter.updateConcurrentRate`、`AppConst.androidId` 里。
 *
 * **M2-4b 已清退其中 3 项**（`Provider` 存在的唯一理由就是实现在 `:app`，能搬走就删方法）：
 *  - `isMainThread` → 平台原语 [isOnMainThread]；
 *  - `androidId` → 宿主配置项 [DeviceId]；
 *  - `updateConcurrentRate` → 并发率登记表下沉 `:core:data`（`ConcurrentRateRegistry`）。
 *
 * 剩下的 3 项都用的是 `SharedJsScope`/`BookSource` 的 app 侧状态或 okhttp/gson 栈，
 * 仍进不了共享层，故契约保留。方法签名刻意用基础类型 / `Any` 而非 `BaseSource`，
 * 避免 core:platform 反向依赖 core:data 的实体类型
 * （`clearExploreKindsCache(source: Any)` 由实现侧判定 `is BookSource`）。
 */
interface SourceRuntime {

    /** 获取共享 JS 作用域（对齐 `SharedJsScope.getScope(jsLib, null)`）。 */
    fun getShareScope(jsLib: String?): JsScope?

    /** 移除指定 jsLib 的共享作用域缓存（对齐 `SharedJsScope.remove(jsLib)`）。 */
    fun removeJsLib(jsLib: String?)

    /** 清理探索分类缓存；`source` 为实体（实现侧判定 `is BookSource`）。 */
    fun clearExploreKindsCache(source: Any)
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
