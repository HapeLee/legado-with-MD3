package io.legado.app.help

import io.legado.app.data.entities.BaseSource

/**
 * WebView 注入的 JS 扩展工厂。
 *
 * 背景（P4-d）：`BaseSource` 实体最终要下沉 commonMain，而 `@JavascriptInterface`
 * 注解（android.webkit）不能进 commonMain。但注解只对 `WebView.addJavascriptInterface`
 * 生效——Rhino 桥是纯反射、完全不看注解。
 *
 * 于是把「实体」与「带 WebView 注解的 JS 面」分离：
 * - 实体 `BaseSource` 不再带 `@JavascriptInterface`（下沉后只剩纯字段 + Rhino 路径方法）；
 * - WebView 注入点改为注入 [wrap] 返回的**包装器**，包装器 `by source` 委托实体、
 *   自行声明那 9 个 WebView 方法并加注解。
 *
 * [wrap] 返回 [Any] 而非具体包装器类型，是刻意的类型擦除：
 * 等 P4-e 实体下沉后，本 provider 会跟 `BaseSource` 一起迁到 commonMain，
 * 届时它不能反向引用 app 侧的包装器类型，只见 [Any]。
 */
object JsExtProvider {

    private var factory: JsExtFactory? = null

    /**
     * composition root 注入（`App.onCreate`），未注入时 [wrap] 显式抛异常——
     * 不静默回退到裸实体（裸实体已无 WebView 注解，静默回退会让书源 `source.xxx()`
     * 在页面内全部失效）。
     */
    fun install(factory: JsExtFactory) {
        this.factory = factory
    }

    fun wrap(source: BaseSource): Any {
        return factory?.wrap(source)
            ?: error("JsExtFactory 未注册，请在 App.onCreate 调用 JsExtProvider.install")
    }
}

/**
 * 包装器工厂。实现留在 app 侧（[BookSourceJsExt] 引用 JsExtensions 等 app-only 类型）。
 */
fun interface JsExtFactory {
    fun wrap(source: BaseSource): Any
}
