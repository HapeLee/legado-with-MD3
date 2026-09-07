package io.legado.app.core.platform

import kotlin.coroutines.CoroutineContext

/**
 * JS 变量绑定容器（纯 Map）。
 *
 * 刻意**不继承任何引擎类型**：Rhino 的 `com.script.ScriptBindings` 继承
 * `org.mozilla.javascript.NativeObject`（bindings 与 scope 是同一个对象），
 * 而 commonMain 不能碰引擎类型。由引擎实现在 [JsEngine.getRuntimeScope] /
 * [JsEngine.eval] 入口把 entries 转换为引擎原生 bindings。
 *
 * 与样本仓 `JsBindings` 同思路（`MutableMap by LinkedHashMap`）。
 */
class JsBindings : MutableMap<String, Any?> by LinkedHashMap()

/**
 * JS 执行作用域抽象。
 *
 * [native] 以 `Any?` 透传平台原生 scope（Rhino 侧为 `org.mozilla.javascript.Scriptable`）。
 * 这是与 jsoup 平台岛一致的处理：**不抽象** `Scriptable`——prototype 链、
 * `preventExtensions()`、`sealObject()` 都是 Rhino 独有语义，抽象掉即丢失；
 * 但 commonMain 又不能引用它，故只以 `Any?` 透传给平台侧代码使用
 * （典型消费方：jsLib 共享 scope 的缓存与复用）。
 */
interface JsScope {
    /** 平台原生 scope 对象，仅供平台侧代码按引擎类型取用。 */
    val native: Any?
}

/** [JsScope] 的通用实现：仅持平台原生对象的引用，两个 actual 共用。 */
internal class NativeJsScope(override val native: Any?) : JsScope

/**
 * JS 引擎门面（P4-a 最小面）。
 *
 * 选型为 `expect object` 而非「接口 + provider 注入」：
 * - 本仓库不换引擎（AGENTS.md：换 QuickJS 单独立项），每个 target **静态**只有一个实现，
 *   不需要样本仓那套 `JsEngineType` 运行时切换与双检缓存；
 * - 无 provider 即无「未注册」运行时错误面，与 [cnCompare] / [JsonCodec] 的平台原语定位一致。
 *
 * actual：
 * - androidMain 委托 `com.script.rhino.RhinoScriptEngine`（含协程取消/安全名单，与 app 现行为一致）
 * - desktopMain 委托裸 `org.mozilla.javascript`（纯 JVM 库；已知缺口见 build.gradle.kts 注释）
 *
 * 本面只覆盖「JS 执行」。jsLib 的下载 / 文件缓存 / MD5 key 属于宿主能力，
 * 不进契约（对应 app 侧 SharedJsScope）。
 */
expect object JsEngine {

    /** 一次性 eval：内部创建 scope，执行后立即释放。 */
    fun eval(js: String, bindings: JsBindings): Any?

    /** 在指定 scope 上 eval。 */
    fun eval(js: String, scope: JsScope): Any?

    /**
     * 在指定 scope 上 eval，支持通过 [coroutineContext] 取消。
     *
     * 书源脚本可能写成死循环，取消是**宿主可用性的硬需求**（否则协程取消后线程仍在烧 CPU）。
     * 但这是「平台尽力而为」的能力：
     * - android（`com.script` 的 RhinoContext）通过 `observeInstructionCount` 在
     *   指令计数回调里检查 Job 状态，可**打断执行中**的脚本；
     * - desktop 裸 Rhino 未接指令观察（见 desktop actual 注释），只在入口检查。
     *
     * 因此调用方**不可依赖**取消一定生效，只能把它当作加速退出的手段。
     */
    fun eval(js: String, scope: JsScope, coroutineContext: CoroutineContext?): Any?

    /**
     * 创建运行时 scope，注入 [bindings] 变量。
     *
     * @param parent 非空时作为 prototype 父域——对应 jsLib 共享 scope 的继承：
     *   app 侧原写法是 `bindings.prototype = sharedScope`，让书源脚本命中 jsLib 里的自由函数。
     */
    fun getRuntimeScope(bindings: JsBindings, parent: JsScope?): JsScope

    /**
     * 禁止向 [scope] 新增属性（对应 JS 的 `Object.preventExtensions`）。
     *
     * jsLib 共享 scope 用它**阻止隐式全局变量创建**：脚本里漏写 `var` 的赋值
     * 原本会在全局对象上静默建属性并污染后续所有书源的执行环境，
     * 冻结后该赋值会直接抛「变量未定义」，把错误暴露给书源作者。
     */
    fun preventExtensions(scope: JsScope)
}
