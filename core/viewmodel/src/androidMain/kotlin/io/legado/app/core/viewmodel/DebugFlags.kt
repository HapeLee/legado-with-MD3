package io.legado.app.core.viewmodel

/**
 * 共享层的调试开关。
 *
 * 背景：`help.coroutine.Coroutine` 原先直接调用 app 侧的 `Throwable.printOnDebug()`
 * （实现读 `io.legado.app.BuildConfig.DEBUG`），导致整个协程辅助层无法离开 `:app`。
 * 这里把「是不是 debug 构建」变成一个可注册的布尔值，宿主在启动最早处写入。
 *
 * 宿主契约：`:app` 的 `App.onCreate` **首行**执行 `DebugFlags.enabled = BuildConfig.DEBUG`。
 * 未注册时保持 `false`（等价 release 行为：不打印栈）。
 */
object DebugFlags {
    @Volatile
    var enabled: Boolean = false
}
