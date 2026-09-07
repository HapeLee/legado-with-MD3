package io.legado.app.core.platform

import com.script.ScriptBindings
import com.script.rhino.RhinoScriptEngine
import org.mozilla.javascript.Scriptable

/**
 * [JsEngine] 的 android actual：委托 `com.script.rhino.RhinoScriptEngine`。
 *
 * 走 `com.script` 封装层而非裸 Rhino，是为了与 app 当前行为**逐项一致**：
 * RhinoContext 的协程取消（observeInstructionCount）、ClassShutter 安全名单、
 * WrapFactory 等都在该封装层里，绕开会让书源脚本的执行语义分叉。
 */
actual object JsEngine {

    actual fun eval(js: String, bindings: JsBindings): Any? {
        return RhinoScriptEngine.eval(js) {
            // lambda 已在 Context.enter() 内（见 RhinoScriptEngine.eval 实现），
            // ScriptBindings.set 内部会做 Context.javaToJS 转换
            bindings.forEach { (key, value) -> put(key, value) }
        }
    }

    actual fun eval(js: String, scope: JsScope): Any? {
        return RhinoScriptEngine.eval(js, scope.native as Scriptable)
    }

    actual fun getRuntimeScope(bindings: JsBindings, parent: JsScope?): JsScope {
        val sb = toScriptBindings(bindings)
        // 与 app 侧 BaseSource.evalJS 原逻辑一致：
        // - 无 jsLib(parent == null)：getRuntimeScope 会把 prototype 设为标准对象
        // - 有 jsLib：prototype 指向共享 scope，让书源脚本命中 jsLib 里的自由函数
        //   （注意不能走 getRuntimeScope，它会把 prototype 重设为标准对象而覆盖 parent）
        return if (parent == null) {
            NativeJsScope(RhinoScriptEngine.getRuntimeScope(sb))
        } else {
            sb.prototype = parent.native as Scriptable
            NativeJsScope(sb)
        }
    }

    private fun toScriptBindings(bindings: JsBindings): ScriptBindings {
        val sb = ScriptBindings()
        bindings.forEach { (key, value) -> sb[key] = value }
        return sb
    }
}
