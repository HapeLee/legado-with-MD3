package io.legado.app.ui.config.coverConfig

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.feature.settings.coverconfig.CoverAlbumEffect
import io.legado.app.feature.settings.coverconfig.CoverAlbumIntent
import io.legado.app.feature.settings.coverconfig.CoverAlbumManageScreen
import io.legado.app.feature.settings.coverconfig.CoverAlbumManageViewModel
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

// M5-14b：本文件是 `CoverAlbumManageScreen.kt` 拆出来的**宿主壳那一半** —— 原文件里同时放着
// 宿主壳与页面本体（455 行），本片把后者搬进 `:feature:settings/coverconfig/`，壳留在这里
// （顺手把文件名改成与函数同名，与其余宿主壳一致）。
//
// 壳里保留的是**平台动作**：
//   · `rememberLauncherForActivityResult(GetMultipleContents())` —— 系统多选图片；
//   · 记忆「这次选图是给哪个相册、亮色还是暗色」（`imageTargetAlbumId` / `imageTargetIsDark`），
//     因为选择器回调只给 URI，而 `CoverAlbumIntent.ImagesSelected` 需要那个上下文；
//   · `context.toastOnUi(effect.message)` —— `ShowMessage` 的文案是**运行期错误信息**
//     （不是资源），所以这里保持原样传字符串，不涉及资源查表。

@Composable
fun CoverAlbumManageRouteScreen(
    onBackClick: () -> Unit,
    viewModel: CoverAlbumManageViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var imageTargetAlbumId by remember { mutableStateOf<String?>(null) }
    var imageTargetIsDark by remember { mutableStateOf(false) }
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        val albumId = imageTargetAlbumId
        imageTargetAlbumId = null
        if (albumId != null && uris.isNotEmpty()) {
            viewModel.onIntent(
                CoverAlbumIntent.ImagesSelected(
                    albumId = albumId,
                    isDark = imageTargetIsDark,
                    uriStrings = uris.map { it.toString() },
                )
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is CoverAlbumEffect.SelectImages -> {
                    imageTargetAlbumId = effect.albumId
                    imageTargetIsDark = effect.isDark
                    imagePicker.launch("image/*")
                }

                is CoverAlbumEffect.ShowMessage -> context.toastOnUi(effect.message)
            }
        }
    }

    CoverAlbumManageScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onBackClick = onBackClick,
    )
}
