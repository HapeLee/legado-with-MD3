package io.legado.app.feature.tagrules.highlight

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.core.platform.ImportJsonEditor
import io.legado.app.ui.platform.rememberDocumentPicker
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * `HighlightTagRuleScreen` 的 **Android Route**（M1-3w 从 `HighlightTagRuleScreen.kt` 拆出）。
 *
 * 为什么它必须留在 `androidMain`：两个依赖都是 Android-only 的——
 *   1. `koinViewModel()`（`org.koin.androidx.compose`），Koin 的 Android Compose 入口；
 *   2. `rememberDocumentPicker()`（`:core:ui`），SAF 的 `ActivityResult` launcher **必须在
 *      Composition 里注册**，无法由共享层提供。
 *
 * Route / Screen 的职责切分是既有设计，不是为迁移新造的：Route 负责「装配平台能力」，
 * 把文件选择与导出目标的回调交给纯 Screen（后者零 Android API，见同名 `commonMain` 文件）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HighlightTagRuleRouteScreen(
    viewModel: HighlightTagRuleViewModel = koinViewModel(),
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()

    // 文件选择（SAF）只能在 Composition 里注册 launcher，所以它是**本 Route 的职责**，
    // 而 Screen 只收回调（M1-3c）。选中的是「不透明引用」（Android 即 `Uri.toString()`），
    // 读文本交给 VM 侧的 `RuleTransferPlatform.readImportSource`——那里本来就要处理
    // URL / URI / 纯文本三种形态，与迁移前 Screen 直接 `openInputStream` 读等价。
    val documentPicker = rememberDocumentPicker()

    // M2-1：导入对话框「编辑」页的字段拆解能力。原先 designsystem 直接读全局
    // `ImportJsonEditorProvider`，现在由 Route 从 Koin 取——本文件的职责就是装配平台能力。
    val importJsonEditor: ImportJsonEditor = koinInject()

    HighlightTagRuleScreen(
        state = uiState,
        importState = importState,
        events = viewModel.events,
        effects = viewModel.effects,
        onIntent = viewModel::onIntent,
        onPasteRule = viewModel::pasteRule,
        onPickImportSource = { mimeTypes ->
            documentPicker.openDocument(mimeTypes) { ref ->
                if (ref != null) {
                    viewModel.onIntent(HighlightTagRuleIntent.ImportSource(ref))
                }
            }
        },
        onPickExportTarget = { fileName ->
            documentPicker.createDocument(fileName) { ref ->
                if (ref != null) {
                    viewModel.onIntent(HighlightTagRuleIntent.ExportSelection(ref))
                }
            }
        },
        onBackClick = onBackClick,
        importJsonEditor = importJsonEditor,
    )
}
