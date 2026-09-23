package io.legado.app.ui.config.coverConfig

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.feature.settings.coverconfig.CoverConfigEffect
import io.legado.app.feature.settings.coverconfig.CoverConfigScreen
import io.legado.app.feature.settings.coverconfig.CoverConfigViewModel
import io.legado.app.feature.settings.coverconfig.localizedText
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

// M5-13c：本文件是 `CoverConfigScreen.kt` 拆出来的**宿主壳那一半** —— 原文件里同时放着宿主壳
// 与页面本体（371 行），本片把后者搬进 `:feature:settings/coverconfig/`，壳留在这里
// （顺手把文件名改成与函数同名，与其余宿主壳一致：`BackupConfigRouteScreen` /
// `OtherConfigRouteScreen` / `ReadConfigRouteScreen` …）。
//
// 壳里保留的是**平台动作**：`LocalContext` 取 Context，把 Effect 里的提示
// `context.toastOnUi(...)` 弹出来。文案由共享层的枚举 + `localizedText()`（suspend，查 CMP 资源）
// 提供 —— 迁移前这里是 `context.toastOnUi(effect.stringRes)`（`@StringRes Int`，进不了共享层）。

@Composable
fun CoverConfigRouteScreen(
    onBackClick: () -> Unit,
    onNavigateToCoverAlbums: () -> Unit,
    viewModel: CoverConfigViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is CoverConfigEffect.ShowToast -> context.toastOnUi(effect.toast.localizedText())
            }
        }
    }
    CoverConfigScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onBackClick = onBackClick,
        onNavigateToCoverAlbums = onNavigateToCoverAlbums,
    )
}
