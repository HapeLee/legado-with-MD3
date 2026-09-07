package io.legado.app.help

import android.webkit.JavascriptInterface
import io.legado.app.data.entities.BaseSource

/**
 * WebView 注入的 BaseSource 包装器（P4-d）。
 *
 * `BaseSource by source` 接口委托保证 `var` 属性（header / concurrentRate / loginUrl 等）
 * 的 getter/setter 以及所有方法转发回原实体——页面 JS 里 `source.setHeader(...)` /
 * `source.getVariable()` 改的仍是数据库实体本身，不是包装器副本。
 *
 * 唯一需要「重新声明」的是 9 个 WebView 方法：`@JavascriptInterface` 注解只对
 * `WebView.addJavascriptInterface` 注入的对象生效（Rhino 桥是反射、不看注解），
 * 且注解不会随接口委托复制。实体下沉后这 9 个注解就从 `BaseSource` 上移除、
 * 由本包装器承接，WebView 路径语义不变。
 */
class BookSourceJsExt(private val source: BaseSource) : BaseSource by source {

    override fun getSource(): BaseSource? = source

    @JavascriptInterface
    override fun login() = source.login()

    @JavascriptInterface
    override fun getLoginHeader(): String? = source.getLoginHeader()

    @JavascriptInterface
    override fun getLoginInfo(): String? = source.getLoginInfo()

    @JavascriptInterface
    override fun putLoginInfo(info: String): Boolean = source.putLoginInfo(info)

    @JavascriptInterface
    override fun removeLoginInfo() = source.removeLoginInfo()

    @JavascriptInterface
    override fun putVariable(variable: String?) = source.putVariable(variable)

    @JavascriptInterface
    override fun getVariable(): String = source.getVariable()

    @JavascriptInterface
    override fun put(key: String, value: String): String = source.put(key, value)

    @JavascriptInterface
    override fun get(key: String): String = source.get(key)
}
