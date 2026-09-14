package io.legado.app.ui.util

import androidx.compose.ui.platform.ClipEntry

/**
 * [plainTextClipEntry] 的平台实现契约：把一段纯文本包成平台剪贴板条目。
 *
 * ## 为什么需要这个 seam
 *
 * Compose 的 `LocalClipboard`（新 API）把「读/写剪贴板」抽成了跨平台接口，
 * `Clipboard.setClipEntry` 在 `commonMain` 里就能调；**但 `ClipEntry` 本身是 CMP 的
 * `expect class`，共享层没有构造器**——Android 侧是 `ClipEntry(clipData: ClipData)`，
 * desktop 侧是 `ClipEntry(nativeClipEntry: Any)`（AWT `Transferable`）。所以「把文本
 * 包成 `ClipEntry`」这一步只能由宿主注入。
 *
 * 旧 API（`ClipboardManager.setText(AnnotatedString)`，无需平台类型）在上游已标注
 * 「Use Clipboard instead, which supports suspend functions.」弃用，故不采用。
 *
 * ## 为什么契约住在 `:core:designsystem` 而不是 `:core:platform`
 *
 * 本仓的平台契约默认放 `:core:platform`（`Clipboard` / `Toaster` / `MimeTypeResolver` …），
 * 那是**纯 KMP 模块、零 Compose**；本契约的方法返回 `ClipEntry`，是 Compose 类型，放不进去。
 * 这是第一个「契约住在 Compose 模块」的案例——位置由类型决定，不是随手放。
 *
 * ## 与 `io.legado.app.core.platform.Clipboard` 的区别（刻意不合并）
 *
 * 那个是**文本级**能力，给规则 VM 这类非 UI 代码用，且它的 `setText` 会顺带弹一次
 * 「复制完成」提示。本契约服务于 **Compose 侧**已经在用 `LocalClipboard.setClipEntry(...)`
 * 的调用点，语义是**只写不提示**——那些调用点全是 Snackbar 的「复制链接」动作，动作本身
 * 就是反馈。两者若合并，等于给这 14 个调用点凭空多弹一次 toast：那是行为变化，不是重构。
 */
fun interface PlainTextClipEntryFactory {
    /** [label] 是系统剪贴板管理器看到的元数据标签，对用户无感知。 */
    fun plainText(label: String, text: String): ClipEntry
}

/**
 * [PlainTextClipEntryFactory] 的注入点。模式同 `DynamicColorSchemes` / `MimeTypeResolverProvider`。
 *
 * ⚠️ **未注入时抛异常**，不做静默兜底：判据与 `ClipboardProvider` 相同——「复制」是用户
 * 主动触发的动作，宿主没接上就该炸，而不是让用户点了「复制链接」却什么都没有发生。
 * （对比 `MimeTypeResolverProvider`：「没有 MIME 表」在 desktop / iOS 上是**正常状态**，
 * 所以那里退回「一律 null」。判据是「缺失是否合理」，不是一刀切。）
 *
 * 实现见 `io.legado.app.platform.AndroidPlatformCapabilities.plainTextClipEntryFactory()`，
 * 由 `io.legado.app.help.PlatformServices.install()` 在 `Application.onCreate` 阶段注入。
 */
object PlainTextClipEntryProvider {

    @Volatile
    private var factory: PlainTextClipEntryFactory? = null

    fun install(factory: PlainTextClipEntryFactory) {
        this.factory = factory
    }

    fun uninstall() {
        factory = null
    }

    val isInstalled: Boolean get() = factory != null

    val current: PlainTextClipEntryFactory
        get() = factory ?: error(
            "PlainTextClipEntryProvider 未安装：请在应用启动时调用 " +
                "PlainTextClipEntryProvider.install(...) 注入平台实现（Android 侧见 " +
                "io.legado.app.platform.AndroidPlatformCapabilities）。"
        )
}

/**
 * 构造一个包含纯文本的 [ClipEntry]，供 `clipboard.setClipEntry(...)` 使用。
 *
 * 调用点（app 与各 feature 的 Screen）形如
 * `clipboard.setClipEntry(plainTextClipEntry(label, text))`，因此不必再 import
 * 任何 `android.content.*`。
 *
 * [label] 是系统剪贴板管理器看到的元数据标签（Android 12+ 部分应用可能据此提示来源），
 * 对用户无感知；Android 侧语义与迁移前的 `ClipData.newPlainText(label, text)` **逐字等价**
 * （纯写入、不弹提示）。
 *
 * ⚠️ **成功路径无法在 `commonTest` 里断言**：要断言就得先造一个 `ClipEntry`，而「共享层
 * 造不出 `ClipEntry`」正是这个 seam 存在的原因。单测只锁失败路径与参数透传
 * （见 `PlainTextClipEntryTest`）。
 */
fun plainTextClipEntry(label: String, text: String): ClipEntry =
    PlainTextClipEntryProvider.current.plainText(label, text)
