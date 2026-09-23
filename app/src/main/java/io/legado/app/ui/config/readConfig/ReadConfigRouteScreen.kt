package io.legado.app.ui.config.readConfig

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import io.legado.app.feature.settings.readconfig.ReadConfigEffect
import io.legado.app.feature.settings.readconfig.ReadConfigViewModel
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.rememberCoroutineScope
import io.legado.app.data.repository.ReadPreferences
import io.legado.app.data.repository.ReadSettingsRepository
import io.legado.app.feature.settings.readconfig.ReadConfigSheet
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import androidx.compose.runtime.getValue
import io.legado.app.feature.settings.readconfig.ReadConfigIntent
import io.legado.app.feature.settings.readconfig.ReadConfigScreen
import io.legado.app.utils.canvasrecorder.CanvasRecorderFactory

@Composable
fun ReadConfigRouteScreen(
    onBackClick: () -> Unit,
    viewModel: ReadConfigViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    // M5-11c：九宫格动作配置要读/写 `ReadSettingsRepository`。共享层的 composable
    // 不自己 inject（`:feature:settings` 刻意没有 koin 依赖）⇒ 由宿主取好再传下去。
    val readSettingsRepository: ReadSettingsRepository = koinInject()
    val preferences by readSettingsRepository.preferences.collectAsStateWithLifecycle(
        initialValue = ReadPreferences()
    )
    val scope = rememberCoroutineScope()

    // M5-11c：sheet 自带的 `BackHandler` 已随共享化移除（跨端的 backhandler 在本项目
    // 不可用，见 M5-11b）⇒ 改由宿主按共享 state 接线，行为不变。
    BackHandler(enabled = state.activeSheet == ReadConfigSheet.ClickActions) {
        viewModel.onIntent(ReadConfigIntent.DismissSheet)
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is ReadConfigEffect.SettingsUpdateFailed -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    ReadConfigScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onBackClick = onBackClick,
        preferences = preferences,
        onSetClickAction = { key, action ->
            scope.launch { readSettingsRepository.setClickAction(key, action) }
        },
        // M5-11d：迁移前是页面里直接读 `CanvasRecorderFactory.isSupport`。它依赖
        // `android.os.Build` 与三个 Android 专用的 `CanvasRecorder*Impl` ⇒ 进不了共享层；
        // 但它只是个**只读的能力开关**（决定是否显示「优化渲染」那一项），不是行为 ⇒
        // 宿主读一次传进来即可，不值得抽一层契约。
        canvasRecorderSupported = CanvasRecorderFactory.isSupport,
    )
}
