package com.script.rhino

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import org.mozilla.javascript.Context
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext

val rhinoContext: RhinoContext
    get() = Context.getCurrentContext() as RhinoContext

val rhinoContextOrNull: RhinoContext?
    get() = Context.getCurrentContext() as? RhinoContext

/**
 * 进入脚本上下文。所有需要 [RhinoContext] 的入口都必须走这里，不要直接写
 * `Context.enter() as RhinoContext`。
 *
 * 原因：`Context.enter()` 取的是 Rhino 的**全局** [org.mozilla.javascript.ContextFactory]，
 * 而生产 [RhinoContext] 的自定义工厂只在 [RhinoScriptEngine] 的 object init 中通过
 * `ContextFactory.initGlobal` 安装。在安装完成前调用会拿到普通
 * `org.mozilla.javascript.Context`：强转会抛 ClassCastException，且这个 Context 不会
 * 被 `Context.exit()` 释放，会把当前线程永久留在普通 Context 上——此后该线程上的任何
 * 脚本执行都会失败（书源搜索/换源/正文解析等）。
 */
fun enterRhinoContext(): RhinoContext {
    @Suppress("UNUSED_EXPRESSION")
    RhinoScriptEngine
    val cx = Context.enter()
    if (cx is RhinoContext) {
        return cx
    }
    // 无论失败原因是什么都要撤销本次 enter，否则会把非 RhinoContext 留在线程上。
    Context.exit()
    error(
        "无法进入 Rhino 上下文: Context.enter() 返回 ${cx.javaClass.name}，" +
            "当前线程已被非 RhinoContext 占用或 Rhino 全局工厂尚未安装"
    )
}

@Suppress("LEAKED_IN_PLACE_LAMBDA", "WRONG_INVOCATION_KIND")
@OptIn(ExperimentalContracts::class)
inline fun <T> suspendContinuation(crossinline block: suspend CoroutineScope.() -> T): T {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    val cx = Context.enter()
    try {
        val pending = cx.captureContinuation()
        pending.applicationState = suspend {
            supervisorScope {
                block()
            }
        }
        throw pending
    } catch (e: IllegalStateException) {
        return runBlocking { block() }
    } finally {
        Context.exit()
    }
}

inline fun <T> runScriptWithContext(context: CoroutineContext, block: () -> T): T {
    val rhinoContext = enterRhinoContext()
    val previousCoroutineContext = rhinoContext.coroutineContext
    rhinoContext.coroutineContext = context.minusKey(ContinuationInterceptor)
    try {
        return block()
    } finally {
        rhinoContext.coroutineContext = previousCoroutineContext
        Context.exit()
    }
}

suspend inline fun <T> runScriptWithContext(block: () -> T): T {
    val rhinoContext = enterRhinoContext()
    val previousCoroutineContext = rhinoContext.coroutineContext
    rhinoContext.coroutineContext = currentCoroutineContext().minusKey(ContinuationInterceptor)
    try {
        return block()
    } finally {
        rhinoContext.coroutineContext = previousCoroutineContext
        Context.exit()
    }
}
