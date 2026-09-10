package io.legado.app.ui.platform

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.legado.app.core.platform.DocumentPicker

/**
 * [DocumentPicker] 的 Android 实现（M1-3c）——SAF 的 `OpenDocument` / `CreateDocument`。
 *
 * 为什么做成 `remember` 而不是注入的单例：Activity Result 的 launcher **必须在 Composition 里
 * 注册**（`rememberLauncherForActivityResult` 依赖 `ActivityResultRegistryOwner`），
 * 无法由 app 侧的全局对象提供；所以契约的入口是这个函数，由各 Feature 的 Route 调用。
 *
 * 结果为什么走「pending 回调」：SAF 的 launcher 只能一次性注册 `onResult`，而调用方是
 * 「点一下按钮 → 弹选择器」这种分散的调用点。这里把最近一次调用的回调存起来，
 * launcher 回来后回调它并**立即清空**，保证 `DocumentPicker` 契约承诺的「最多回调一次」。
 *
 * 导出的 MIME 固定为 `application/json`：规则导入导出目前只有 JSON 一种格式
 * （`tagrules` / `dict` / `replacerules` / `txttocrules` 四个 Screen 迁移前都是
 * `ActivityResultContracts.CreateDocument("application/json")`），不为尚未出现的格式做参数化。
 *
 * 注意：本文件在 `:core:ui`（Android 专用模块）。模块 CMP 化后，它随 Route 一起落到
 * `androidMain`，`DocumentPicker` 契约本身在 `:core:platform` 的 `commonMain` 不动。
 */
@Composable
fun rememberDocumentPicker(): DocumentPicker {
    var pendingOpen by remember { mutableStateOf<((String?) -> Unit)?>(null) }
    var pendingCreate by remember { mutableStateOf<((String?) -> Unit)?>(null) }

    val openLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val callback = pendingOpen
        pendingOpen = null
        callback?.invoke(uri?.toString())
    }

    val createLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val callback = pendingCreate
        pendingCreate = null
        callback?.invoke(uri?.toString())
    }

    return remember(openLauncher, createLauncher) {
        object : DocumentPicker {

            override fun openDocument(mimeTypes: Array<String>, onResult: (String?) -> Unit) {
                pendingOpen = onResult
                // SAF 的 OpenDocument 不接受空数组（会抛 IllegalArgumentException），
                // 契约里「空数组 = 不过滤」在这一层翻译成全放行的通配类型。
                openLauncher.launch(mimeTypes.ifEmpty { arrayOf("*/*") })
            }

            override fun createDocument(fileName: String, onResult: (String?) -> Unit) {
                pendingCreate = onResult
                createLauncher.launch(fileName)
            }
        }
    }
}
