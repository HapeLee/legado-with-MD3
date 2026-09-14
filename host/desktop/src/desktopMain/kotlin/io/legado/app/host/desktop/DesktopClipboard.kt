package io.legado.app.host.desktop

import io.legado.app.core.platform.Clipboard
import io.legado.app.core.platform.Toaster

/**
 * [Clipboard] 的 desktop 实现：**进程内**剪贴板。
 *
 * 行为对齐 Android 侧（`AndroidPlatformCapabilities.clipboard` → `Context.getClipText()` /
 * `Context.sendToClip()`）的两条语义：
 *
 * - `getText()` 返回 **trim 后**的文本，空则返回 `null`；
 * - `setText()` **会顺带弹一次提示**——迁移前 `sendToClip` 内部就调
 *   `longToastOnUi(R.string.copy_complete)`，契约按「行为等价优先」原样保留。
 *
 * ⚠️ 已知缺口：**不是系统剪贴板**，内容不跨进程、不跨应用。desktop 要接系统剪贴板需要
 * AWT `Toolkit.getSystemClipboard()`，而它在 headless 测试环境会抛 `HeadlessException`，
 * 属独立的能力项。按 AGENTS.md「平台能力不可用时必须显式建模」，这里不假装已经接好：
 * 能力边界写在本 KDoc，且 [copyCompleteText] 的文案也未本地化（desktop host 目前只服务
 * 单一 locale）。
 *
 * @param toaster 用于 `setText` 后的「复制完成」提示。
 * @param copyCompleteText 「复制完成」的文案。由 host 注入而非写死，是为了让这个未本地化的
 *   缺口显式可见（调用方一看就知道要传文案，而不是以为它自己有资源）。
 */
class DesktopClipboard(
    private val toaster: Toaster,
    private val copyCompleteText: String = "已复制",
) : Clipboard {

    @Volatile
    private var content: String? = null

    override fun getText(): String? = content?.trim()?.takeIf { it.isNotEmpty() }

    override fun setText(text: String) {
        content = text
        toaster.longToast(copyCompleteText)
    }
}
