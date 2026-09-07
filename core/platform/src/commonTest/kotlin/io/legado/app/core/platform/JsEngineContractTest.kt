package io.legado.app.core.platform

import kotlinx.coroutines.Job
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.coroutines.CoroutineContext

/**
 * [JsEngine] 契约测试：android（委托 com.script 封装层）与 desktop（裸 Rhino）
 * 两个 actual 必须满足同一组行为，尤其「jsLib 共享 scope 的 prototype 继承」——
 * 那是书源脚本能命中 jsLib 自由函数的机制，两端语义必须一致。
 *
 * 断言一律用字符串结果，避开 Rhino 数值类型（Double / Integer）的两端差异。
 */
class JsEngineContractTest {

    @Test
    fun `eval reads bindings`() {
        val bindings = JsBindings()
        bindings["name"] = "world"

        assertEquals("world", JsEngine.eval("name", bindings))
    }

    @Test
    fun `eval returns last expression value`() {
        assertEquals("ab", JsEngine.eval("'a' + 'b'", JsBindings()))
        assertEquals("3", JsEngine.eval("'' + (1 + 2)", JsBindings()))
    }

    @Test
    fun `eval supports function declaration and call`() {
        val js = "function f(x) { return 'got-' + x; } f('v')"

        assertEquals("got-v", JsEngine.eval(js, JsBindings()))
    }

    @Test
    fun `scope keeps state between evals`() {
        val scope = JsEngine.getRuntimeScope(JsBindings(), null)

        JsEngine.eval("var kept = 'persisted'", scope)

        assertEquals("persisted", JsEngine.eval("kept", scope))
    }

    @Test
    fun `runtime scope exposes bindings`() {
        val bindings = JsBindings()
        bindings["k"] = "v"
        val scope = JsEngine.getRuntimeScope(bindings, null)

        assertEquals("v", JsEngine.eval("k", scope))
    }

    @Test
    fun `parent scope is inherited via prototype`() {
        // 模拟 jsLib：在父 scope 里定义自由函数
        val parent = JsEngine.getRuntimeScope(JsBindings(), null)
        JsEngine.eval("function fromLib() { return 'lib'; }", parent)

        // 子 scope 应能通过 prototype 链命中父 scope 的函数
        // （对应 BaseSource.evalJS 里 bindings.prototype = sharedScope）
        val child = JsEngine.getRuntimeScope(JsBindings(), parent)

        assertEquals("lib", JsEngine.eval("fromLib()", child))
    }

    @Test
    fun `parent scope can be reused by multiple children`() {
        val parent = JsEngine.getRuntimeScope(JsBindings(), null)
        JsEngine.eval("var counter = 0; function inc() { counter = counter + 1; return '' + counter; }", parent)

        val c1 = JsEngine.getRuntimeScope(JsBindings(), parent)
        val c2 = JsEngine.getRuntimeScope(JsBindings(), parent)

        assertEquals("1", JsEngine.eval("inc()", c1))
        assertEquals("2", JsEngine.eval("inc()", c2))
    }

    @Test
    fun `native scope is exposed for platform code`() {
        val scope = JsEngine.getRuntimeScope(JsBindings(), null)

        assertNotNull(scope.native, "平台原生 scope 不应为 null（jsLib 缓存等平台侧代码依赖它）")
    }

    @Test
    fun `host object passed through bindings is callable`() {
        val bindings = JsBindings()
        bindings["host"] = HostStub()

        // 注意：Rhino 把 Java 方法返回值包装为 NativeJavaObject 是引擎固有行为
        // （com.script 的 RhinoWrapFactory 也只管对象/类包装，不碰原生类型）。
        // 故在 JS 层面强制转字符串再断言——这也是书源 JS 的真实用法。
        assertEquals("hi-legado", JsEngine.eval("'' + host.greet('legado')", bindings))
    }

    /** 模拟注入给 JS 的宿主对象（书源脚本会调 source.xxx()）。 */
    class HostStub {
        fun greet(name: String): String = "hi-$name"
    }

    @Test
    fun `eval with coroutine context runs when active`() {
        val scope = JsEngine.getRuntimeScope(JsBindings(), null)

        // 未取消的 Job：正常执行
        assertEquals("3", JsEngine.eval("'' + (1 + 2)", scope, Job()))
    }

    @Test
    fun `preventExtensions blocks new globals`() {
        val scope = JsEngine.getRuntimeScope(JsBindings(), null)
        JsEngine.preventExtensions(scope)

        // 冻结后漏写 var 的隐式全局赋值不再生效：Rhino 非 strict 模式下是
        // **静默失败**（不抛异常，但也不在全局对象上建属性），这正是原
        // SharedJsScope 注释说的「隐性创建失败，提示变量未定义」的效果。
        // 断言「变量未创建」，而非「抛异常」——两端行为一致。
        JsEngine.eval("leaked = 'x'", scope)
        assertEquals("undefined", JsEngine.eval("typeof leaked", scope))
    }

    @Test
    fun `compiled script evaluates across scopes`() {
        // 脚本末尾是「定义 + 调用」，eval 返回调用结果而非函数对象
        val compiled = JsEngine.compile("function g(x) { return 'got-' + x; } g('a')")

        // 编译一次，跨不同 scope 复用（书源里同一段 js 会被反复执行）
        val s1 = JsEngine.getRuntimeScope(JsBindings(), null)
        val s2 = JsEngine.getRuntimeScope(JsBindings(), null)

        assertEquals("got-a", compiled.eval(s1, null))
        assertEquals("got-a", compiled.eval(s2, null))
    }

    @Test
    fun `compiled script sees bindings`() {
        val compiled = JsEngine.compile("who")
        val bindings = JsBindings()
        bindings["who"] = "legado"
        val scope = JsEngine.getRuntimeScope(bindings, null)

        assertEquals("legado", compiled.eval(scope, null))
    }
}
