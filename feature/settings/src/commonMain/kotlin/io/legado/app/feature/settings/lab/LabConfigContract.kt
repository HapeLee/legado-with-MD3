package io.legado.app.feature.settings.lab

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.settings.LabSettings

// M5-2a：从 `:app` 的 `io.legado.app.ui.config.labConfig` 迁来（**只改包名**，
// 状态/意图/效果三者的结构与迁移前逐字一致）。
//
// `LabConfigEffect` 只有一个分支，且它的消费方（`ACTION_SEND` 分享）留在 `:app`
// 的 `MainNavGraph` entry —— 与本仓 about / tagrules 等 Feature 的形态一致：
// **Effect 在共享层声明，由宿主解释成平台动作**。

@Stable
data class LabConfigUiState(
    val settings: LabSettings = LabSettings(),
    val pageEstimateDiagnosticCount: Int = 0,
)

sealed interface LabConfigIntent {
    data class SetEnabled(val value: Boolean) : LabConfigIntent
    data class SetEInkDisplay(val value: Boolean) : LabConfigIntent
    data object ExportPageEstimateDiagnostics : LabConfigIntent
}

sealed interface LabConfigEffect {
    data class SharePageEstimateDiagnostics(val text: String) : LabConfigEffect
}
