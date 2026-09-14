package io.legado.app.core.platform

import java.awt.EventQueue
import java.awt.GraphicsEnvironment

/**
 * [isOnMainThread] 的 desktop 实现：AWT 事件分发线程（Compose Desktop 的 UI 线程就是它，
 * `Dispatchers.Swing` 也映射到这里）。
 *
 * headless 环境下**不存在** UI 线程，且 `EventQueue.isDispatchThread()` 会抛
 * `HeadlessException`。这时返回 `false` 是在陈述平台事实（没有 UI 线程，自然没有线程在 UI
 * 线程上），不是把「平台没做」伪装成内容属性——有图形环境时走真实判定。
 */
actual fun isOnMainThread(): Boolean {
    if (GraphicsEnvironment.isHeadless()) return false
    return EventQueue.isDispatchThread()
}
