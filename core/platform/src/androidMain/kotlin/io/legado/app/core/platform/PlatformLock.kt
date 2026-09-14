package io.legado.app.core.platform

/**
 * [synchronizedOn] 的 JVM 实现：委托对象监视器 `synchronized`。
 *
 * androidMain 与 desktopMain 都是 JVM，故实现相同（同 [RuleDataStorage] 先例）。
 */
internal actual fun <T> synchronizedOn(lock: Any, block: () -> T): T = synchronized(lock, block)
