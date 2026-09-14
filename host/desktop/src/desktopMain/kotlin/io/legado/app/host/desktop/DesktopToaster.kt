package io.legado.app.host.desktop

import io.legado.app.core.platform.Toaster
import java.util.concurrent.CopyOnWriteArrayList

/**
 * [Toaster] 的 desktop 实现：把提示**记录下来**而不是丢掉。
 *
 * desktop 没有 Android `Toast` 的对应物（没有浮动提示层）。按 AGENTS.md
 * 「平台能力不可用时必须显式建模为 capability/unsupported，**不得用静默空实现伪造**跨平台支持」，
 * 这里不做空实现：消息进 [messages]，同时打印到 stdout。
 *
 * 记录（而非只打印）是有意的——M1-4 的 UI 测试要断言「复制后确实弹了提示」这类行为，
 * 只打印没法断言。
 *
 * 两个方法在 desktop 上没有长短之分（[toast] / [longToast] 的实现相同），这符合契约注释
 * 的意图：那对方法只是为了不把 Android `Toast.LENGTH_*` 常量语义带进契约。
 *
 * 线程安全：契约要求实现自行处理线程切换，这里用 `CopyOnWriteArrayList`，任意线程可直接调。
 */
class DesktopToaster : Toaster {

    /** 所有收到的提示，按到达顺序。测试与排障都读它。 */
    val messages: List<String> get() = inner.toList()

    private val inner = CopyOnWriteArrayList<String>()

    /** 清空记录。测试里在每个用例开头调，避免用例间互相污染。 */
    fun clear() = inner.clear()

    override fun toast(message: String) = record(message)

    override fun longToast(message: String) = record(message)

    private fun record(message: String) {
        inner.add(message)
        println("[toast] $message")
    }
}
