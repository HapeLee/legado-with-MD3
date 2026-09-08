package io.legado.app.core.platform

/**
 * 剪贴板契约（P4 规则 VM 去 app 直连第 1 刀）。
 *
 * 取代规则类 ViewModel 里的 `context.getClipText()` / `context.sendToClip(...)`——
 * 它们是 `Context` 扩展（`utils/ContextExtensions.kt`），Feature 提升为独立模块后不可用。
 *
 * **`setText` 会顺带弹一次「复制完成」提示**：这是迁移前 `sendToClip` 的既有行为
 * （内部调 `longToastOnUi(R.string.copy_complete)`），本契约按「行为等价优先」原样保留，
 * 不在这里偷偷去掉。需要「只复制不提示」的场景应另立能力，不要改这里。
 */
interface Clipboard {

    /** 读取剪贴板首个条目的文本（已 `trim`），为空返回 null。对齐 `Context.getClipText()`。 */
    fun getText(): String?

    /** 写入剪贴板。对齐 `Context.sendToClip(text)`（含「复制完成」提示）。 */
    fun setText(text: String)
}

/**
 * [Clipboard] 的注入点。模式同 [LoggerProvider]。
 */
object ClipboardProvider {

    @Volatile
    private var delegate: Clipboard? = null

    fun install(clipboard: Clipboard) {
        delegate = clipboard
    }

    fun uninstall() {
        delegate = null
    }

    val isInstalled: Boolean get() = delegate != null

    val current: Clipboard
        get() = delegate ?: error(
            "Clipboard 未安装：请在应用 composition root 调用 " +
                "ClipboardProvider.install(...) 注入平台实现。"
        )
}
