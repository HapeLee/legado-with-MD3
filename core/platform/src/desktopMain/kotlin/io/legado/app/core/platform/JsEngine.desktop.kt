package io.legado.app.core.platform

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import org.mozilla.javascript.Context
import org.mozilla.javascript.Script
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import kotlin.coroutines.CoroutineContext

/**
 * [JsEngine] 的 desktop actual：委托裸 `org.mozilla.javascript`。
 *
 * `com.script` 封装层（`modules/rhino`）是 android library，desktop 无法依赖，
 * 而 Rhino 本身是纯 JVM 库，故这里直接驱动 Rhino 原生 API。
 *
 * **已知缺口**（desktop 非生产目标，仅用于「管线可编译 + 契约行为一致」验证）：
 * - 协程取消（com.script 的 RhinoContext.observeInstructionCount）不生效
 * - ClassShutter 安全名单 / WrapFactory 等封装层增强不生效
 * 这些只影响 desktop 端书源脚本的安全与取消语义，android actual 保持完整行为。
 */
actual object JsEngine {

    actual fun eval(js: String, bindings: JsBindings): Any? {
        val cx = Context.enter()
        return try {
            val scope = cx.initStandardObjects()
            putAll(cx, scope, bindings)
            cx.evaluateString(scope, js, "js", 1, null)
        } finally {
            Context.exit()
        }
    }

    actual fun eval(js: String, scope: JsScope): Any? {
        val cx = Context.enter()
        return try {
            cx.evaluateString(scope.native as Scriptable, js, "js", 1, null)
        } finally {
            Context.exit()
        }
    }

    actual fun eval(
        js: String,
        scope: JsScope,
        coroutineContext: CoroutineContext?
    ): Any? {
        // 裸 Rhino 未接 com.script 的 RhinoContext.observeInstructionCount，
        // 无法打断执行中的脚本。这里只在入口做一次取消检查，尽力而为——
        // 契约已声明调用方不可依赖取消一定生效。
        coroutineContext?.let { ctx ->
            if (ctx[Job]?.isActive == false) {
                throw CancellationException("js eval cancelled before execution")
            }
        }
        return eval(js, scope)
    }

    actual fun preventExtensions(scope: JsScope) {
        (scope.native as? ScriptableObject)?.preventExtensions()
    }

    actual fun compile(js: String): JsCompiledScript {
        val cx = Context.enter()
        return try {
            NativeJsCompiledScript(cx.compileString(js, "js", 1, null))
        } finally {
            Context.exit()
        }
    }

    actual fun evalCompiled(
        script: JsCompiledScript,
        scope: JsScope,
        coroutineContext: CoroutineContext?
    ): Any? {
        // 与 eval 相同的「尽力而为」取消语义：入口检查一次
        coroutineContext?.let { ctx ->
            if (ctx[Job]?.isActive == false) {
                throw CancellationException("js eval cancelled before execution")
            }
        }
        val cx = Context.enter()
        return try {
            val compiled = script.native as Script
            compiled.exec(cx, scope.native as Scriptable)
        } finally {
            Context.exit()
        }
    }

    actual fun getRuntimeScope(bindings: JsBindings, parent: JsScope?): JsScope {
        val cx = Context.enter()
        return try {
            val parentScriptable = parent?.native as? Scriptable
            val scope = if (parentScriptable == null) {
                cx.initStandardObjects()
            } else {
                // 新对象的 prototype 指向共享 scope，等价 android 侧
                // `bindings.prototype = sharedScope`
                val obj = cx.newObject(parentScriptable)
                obj.prototype = parentScriptable
                obj
            }
            putAll(cx, scope, bindings)
            NativeJsScope(scope)
        } finally {
            Context.exit()
        }
    }

    private fun putAll(cx: Context, scope: Scriptable, bindings: JsBindings) {
        bindings.forEach { (key, value) ->
            ScriptableObject.putProperty(scope, key, Context.javaToJS(value, scope))
        }
    }
}
