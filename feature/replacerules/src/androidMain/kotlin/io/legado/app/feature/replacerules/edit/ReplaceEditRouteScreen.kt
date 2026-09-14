package io.legado.app.feature.replacerules.edit

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.core.platform.Toaster
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.koinInject
import org.koin.androidx.compose.koinViewModel

/**
 * `ReplaceEditScreen` 的 **Android Route**（M1-3x 从 `ReplaceEditScreen.kt` 拆出）。
 *
 * 为什么它必须留在 `androidMain`：
 *   1. `koinViewModel()`（`org.koin.androidx.compose`）是 Android-only 的 Koin 入口；
 *   2. effect 里的轻提示走**注入的** `Toaster`（`:core:platform` 的窄契约，实现由 `:app` 装配）
 *      ——它是 Android 侧装配细节，不该出现在共享 Screen 里。
 *
 * Route / Screen 的职责切分是既有设计：Route 负责「取 VM + 消费一次性 effect」，
 * Screen 只收 `state` 与 `onIntent`、回调（零 Android API，见同名 `commonMain` 文件）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReplaceEditRouteScreen(
    viewModel: ReplaceEditViewModel = koinViewModel(),
    onBack: () -> Unit,
    onSaveSuccess: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // `Toaster` 走注入而不是 `ToasterProvider.current`：后者是 G4 `coreProvider` 棘轮盯的静态
    // 委托，而本文件的源集（`androidMain`）在门禁看来是**新区域**，必须为零。Koin 里
    // `single<Toaster>` 与 Provider 路径共用同一组工厂（见 `appModule`），语义不分叉。
    val toaster = koinInject<Toaster>()

    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                ReplaceEditEffect.NavigateBack -> onSaveSuccess()
                is ReplaceEditEffect.ShowMessage -> toaster.toast(effect.message)
            }
        }
    }

    ReplaceEditScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onBack = onBack,
    )
}
