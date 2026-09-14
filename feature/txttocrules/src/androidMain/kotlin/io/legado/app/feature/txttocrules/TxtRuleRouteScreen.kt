package io.legado.app.feature.txttocrules

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.core.platform.ImportJsonEditor
import io.legado.app.core.platform.Toaster
import io.legado.app.ui.platform.rememberDocumentPicker
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * `TxtRuleScreen` 的 **Android Route**（M1-3y 从 `TxtRuleScreen.kt` 拆出，与
 * `tagrules` / `replacerules` 同形）。
 *
 * 为什么它必须留在 `androidMain`：三个依赖都是 Android-only 的——
 *   1. `koinViewModel()`（`org.koin.androidx.compose`），Koin 的 Android Compose 入口；
 *   2. `rememberDocumentPicker()`（`:core:ui`），SAF 的 `ActivityResult` launcher **必须在
 *      Composition 里注册**，无法由共享层提供；
 *   3. `koinInject<Toaster>()`，轻提示实现是 Android `Toast`（`onShowToast` 回调）。
 *
 * 文件选择的结果是**不透明引用字符串**（Android 即 `Uri.toString()`）：读文本交给 VM 侧的
 * `RuleTransferPlatform.readImportSource`（它本来就要处理 URL / URI / 纯文本三种形态），
 * 写文件交给 `writeExport`——与迁移前 Screen 自己 `contentResolver.openInputStream` 读、
 * `ActivityResultContracts.CreateDocument` 写等价（`:core:platform` 的 `DocumentPicker`
 * 契约注释里有逐条对应说明）。
 *
 * 导出文件名保持迁移前的字面量 `exportDictRule.json`（历史遗留的复制痕迹，但**不改**：
 * 改它等于改用户看到的默认文件名）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TxtRuleRouteScreen(
    viewModel: TxtTocRuleViewModel = koinViewModel(),
    initialRule: String? = null,
    onPickRule: ((String) -> Unit)? = null,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()

    val documentPicker = rememberDocumentPicker()
    val toaster = koinInject<Toaster>()

    // M2-1：导入对话框「编辑」页的字段拆解能力。原先 designsystem 直接读全局
    // `ImportJsonEditorProvider`，现在由 Route 从 Koin 取——本文件的职责就是装配平台能力。
    val importJsonEditor: ImportJsonEditor = koinInject()

    TxtRuleScreen(
        state = uiState,
        importState = importState,
        events = viewModel.events,
        effects = viewModel.effects,
        onIntent = viewModel::onIntent,
        onPasteRule = viewModel::pasteRule,
        onPickImportSource = { mimeTypes ->
            documentPicker.openDocument(mimeTypes) { ref ->
                if (ref != null) {
                    viewModel.onIntent(TxtTocRuleIntent.ImportSource(ref))
                }
            }
        },
        onPickExportTarget = { fileName ->
            documentPicker.createDocument(fileName) { ref ->
                if (ref != null) {
                    viewModel.onIntent(TxtTocRuleIntent.ExportSelection(ref))
                }
            }
        },
        onShowToast = { message -> toaster.toast(message) },
        initialRule = initialRule,
        onPickRule = onPickRule,
        onBackClick = onBackClick,
        importJsonEditor = importJsonEditor,
    )
}
