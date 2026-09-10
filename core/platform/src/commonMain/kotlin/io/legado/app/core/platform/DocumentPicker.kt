package io.legado.app.core.platform

/**
 * 文件选择契约（M1-3c）：把「选一个导入源文件」与「选一个导出目标」两种平台交互收敛成接口，
 * 让规则类 Screen 不再直接依赖 `ActivityResultContracts` / `Context.contentResolver` / `Uri`。
 *
 * 抽出来的原因：`tagrules` 的 `HighlightTagRuleScreen` 原先自己
 * `rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument())`，并在回调里用
 * `context.contentResolver.openInputStream(uri)` 读文本——这两件事把 Android 平台类型
 * 钉死在 Screen 里，模块无法登记为 `cmp`。dict / replacerules / txttocrules 三个 Screen
 * 有**逐字相同**的一段代码，所以收敛到共享契约而不是各改各的。
 *
 * 设计约束：
 * - 契约不暴露 `Context` / `File` / `Uri`（见 AGENTS.md「新的共享领域契约不得暴露
 *   Context/File/Uri」）：选中的文件用**不透明引用字符串**表达，Android 上是
 *   `Uri.toString()`（`content://...`），共享层只负责把它交给
 *   [RuleTransferPlatform][io.legado.app.core.rules.RuleTransferPlatform] 读写，不解释内容。
 * - 结果用回调而不是 `suspend`：Activity Result 必须在 Composition 里注册，调用方（Route）
 *   才持有 launcher；做成 suspend 需要额外的桥接状态，收益不抵复杂度。
 * - 一次调用**最多回调一次**；用户取消或没有结果时回调 `null`。
 *
 * 平台实现：Android 走 SAF（`OpenDocument` / `CreateDocument`），由 `:core:ui` 的
 * `rememberDocumentPicker()` 在 Composition 里注册 launcher 后提供。
 */
interface DocumentPicker {

    /**
     * 弹出「选择导入源文件」。
     *
     * @param mimeTypes 期望的 MIME 类型（如 `application/json`、`text/plain`）；空数组表示
     *   不过滤。桌面端可忽略。
     * @param onResult 选中时回调不透明引用，取消/失败回调 `null`，最多调用一次。
     */
    fun openDocument(mimeTypes: Array<String>, onResult: (documentRef: String?) -> Unit)

    /**
     * 弹出「选择导出目标」。
     *
     * @param fileName 建议的文件名（如 `exportHighlightTagRule.json`），平台可改写。
     * @param onResult 确认时回调不透明引用，取消/失败回调 `null`，最多调用一次。
     *   写入动作由调用方经 `RuleTransferPlatform.writeExport` 完成，本契约只负责选位置。
     */
    fun createDocument(fileName: String, onResult: (documentRef: String?) -> Unit)
}
