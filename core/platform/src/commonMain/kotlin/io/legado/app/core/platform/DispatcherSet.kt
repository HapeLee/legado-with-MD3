package io.legado.app.core.platform

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * 调度器集合：可注入的 [CoroutineDispatcher] 来源。
 *
 * kotlinx.coroutines 的 [Dispatchers] 在 commonMain 可用，故无需 expect/actual；
 * 抽象为接口以便测试替身与未来平台差异注入（例如非 JVM target 的 Main）。
 */
interface DispatcherSet {
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
    val main: CoroutineDispatcher
}

/**
 * 默认实现：委托 kotlinx.coroutines 的 [Dispatchers]。
 * 用 `get()` 延迟求值，避免 [DefaultDispatcherSet] 初始化时即触碰
 * 可能未安装的 Main dispatcher（Desktop 纯 JVM 无 Main 时访问会抛）。
 */
object DefaultDispatcherSet : DispatcherSet {
    override val io: CoroutineDispatcher get() = Dispatchers.IO
    override val default: CoroutineDispatcher get() = Dispatchers.Default
    override val main: CoroutineDispatcher get() = Dispatchers.Main
}
