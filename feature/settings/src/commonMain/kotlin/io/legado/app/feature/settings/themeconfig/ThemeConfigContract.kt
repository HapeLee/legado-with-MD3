package io.legado.app.feature.settings.themeconfig

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.domain.model.settings.ThemeSettings

/**
 * M5-19b：从 `:app` 的 `ui/config/themeConfig/ThemeConfigContract.kt` 迁入。
 *
 * 它是**多数 themeConfig sheet 的前置**（那些 sheet 无 import 地用同包的
 * `ThemeConfigIntent` / `ThemeConfigSheet` —— 同包引用在 import 列表里看不到，
 * 详见 `tools/audit-slice-deps.py` 的「同包陷阱」一节）。
 *
 * **正文逐字保留**，只有三类改写：
 *
 * 1. 包名 → `io.legado.app.feature.settings.themeconfig`。
 * 2. `ThemeConfigIntent.SelectAppFont(file: FileDoc)` → `SelectAppFont(name, uri)`。
 *    `FileDoc` 是 `io.legado.app.utils` 里的**平台类型**（`Uri` / `DocumentFile` / `appCtx`），
 *    进不了 `commonMain`；形状照本仓既有判据（`AndroidAboutCapabilities` 的 KDoc）：
 *    在边界上退化成「**id + 展示名**」，id 取 `FileDoc.uri.toString()`，宿主侧
 *    `FileDoc.fromUri(Uri.parse(id), false)` 还原。
 * 3. `ThemeConfigEffect.ShowToast(stringRes: Int)` → `ShowToast(text: ThemeConfigToast)`：
 *    共享层拿不到 `R`（同 M5-16a 的 `ThemeManageText`），由宿主映射回自己的文案。
 */

@Stable
data class ThemeConfigUiState(
    val appShell: AppShellSettings = AppShellSettings(),
    val theme: ThemeSettings = ThemeSettings(),
    val fontFolder: String = "",
    val activeSheet: ThemeConfigSheet? = null,
    val activeDialog: ThemeConfigDialog? = null,
    val showEInkTheme: Boolean = false,
)

sealed interface ThemeConfigSheet {
    data class BackgroundImage(val target: BackgroundImageTarget) : ThemeConfigSheet
    data object MainNavigation : ThemeConfigSheet
    data object TopBottomBar : ThemeConfigSheet
    data object LauncherIcon : ThemeConfigSheet
    data object DividerColor : ThemeConfigSheet
    data class BaseCardBorderColor(val dark: Boolean) : ThemeConfigSheet
    data object Font : ThemeConfigSheet
}

/** 背景图片类型：应用的背景图片 / 大容器背景图片 / 项目背景图片 */
enum class BackgroundImageTarget { App, LargeContainer, Item }

enum class ContainerBackgroundTarget { LargeContainer, Item }

sealed interface ThemeConfigDialog {
    data object ResetDefaults : ThemeConfigDialog
    data class TimePicker(
        val field: ThemeTimeField,
        val currentValue: String,
    ) : ThemeConfigDialog
}

enum class ThemeTimeField {
    EyeProtectionStart,
    EyeProtectionEnd,
}

sealed interface ThemeConfigIntent {
    data class UpdateTheme(
        val transform: (ThemeSettings) -> ThemeSettings,
    ) : ThemeConfigIntent
    data class ShowSheet(val sheet: ThemeConfigSheet) : ThemeConfigIntent
    data object DismissSheet : ThemeConfigIntent
    data class ShowDialog(val dialog: ThemeConfigDialog) : ThemeConfigIntent
    data object DismissDialog : ThemeConfigIntent
    data object ResetDefaults : ThemeConfigIntent
    data class SelectTheme(val value: String) : ThemeConfigIntent
    data class SetThemeMode(val value: String) : ThemeConfigIntent
    data class SetComposeEngine(val value: String) : ThemeConfigIntent
    data class SetPredictiveBackEnabled(val enabled: Boolean) : ThemeConfigIntent
    data class SetFontScale(val value: Int) : ThemeConfigIntent
    data class SetShowStatusBar(val visible: Boolean) : ThemeConfigIntent
    data class SetSwipeAnimation(val enabled: Boolean) : ThemeConfigIntent
    data class SetShowBottomView(val visible: Boolean) : ThemeConfigIntent
    data class SetUseFloatingBottomBar(val enabled: Boolean) : ThemeConfigIntent
    data class SetUseFloatingBottomBarLiquidGlass(val enabled: Boolean) : ThemeConfigIntent
    data class SetTabletInterface(val value: String) : ThemeConfigIntent
    data class SetLabelVisibilityMode(val value: String) : ThemeConfigIntent
    data class SetMiuixMonet(val enabled: Boolean) : ThemeConfigIntent
    data class SetDynamicColors(val enabled: Boolean) : ThemeConfigIntent
    data class SetBlurEnabled(val enabled: Boolean) : ThemeConfigIntent
    data class SetMainDestinationVisible(
        val route: String,
        val visible: Boolean,
    ) : ThemeConfigIntent
    data class SetMainNavigationOrder(val routes: String) : ThemeConfigIntent
    data class SetDefaultHomePage(val route: String) : ThemeConfigIntent
    data class SelectLauncherIcon(val value: String) : ThemeConfigIntent
    data class SelectNavigationIcon(val destination: String, val path: String) : ThemeConfigIntent
    data class RequestNavigationIcon(val destination: String) : ThemeConfigIntent
    data class RequestBackgroundImage(val dark: Boolean) : ThemeConfigIntent
    data class SelectBackground(val uri: String, val dark: Boolean) : ThemeConfigIntent
    data class RemoveBackground(val dark: Boolean) : ThemeConfigIntent
    data class RequestContainerBackgroundImage(val target: ContainerBackgroundTarget, val dark: Boolean) : ThemeConfigIntent
    data class SelectContainerBackground(val target: ContainerBackgroundTarget, val dark: Boolean, val uri: String) : ThemeConfigIntent
    data class RemoveContainerBackground(
        val target: ContainerBackgroundTarget,
        val dark: Boolean,
    ) : ThemeConfigIntent
    data class SelectAppFont(val name: String, val uri: String) : ThemeConfigIntent
    data object ClearAppFont : ThemeConfigIntent
    data class SetFontFolder(val path: String) : ThemeConfigIntent
    data object RequestFontFolder : ThemeConfigIntent
    data class RequestTimePicker(
        val field: ThemeTimeField,
        val currentValue: String,
    ) : ThemeConfigIntent
    data class SetTime(val field: ThemeTimeField, val value: String) : ThemeConfigIntent
    data object DismissRefactorTip : ThemeConfigIntent
}

sealed interface ThemeConfigEffect {
    data object ApplyDayNight : ThemeConfigEffect
    data object NotifyMain : ThemeConfigEffect
    data class ChangeLauncherIcon(val value: String) : ThemeConfigEffect
    data object OpenFontFolder : ThemeConfigEffect
    data class OpenNavigationIcon(val destination: String) : ThemeConfigEffect
    data class OpenBackgroundImage(val dark: Boolean) : ThemeConfigEffect
    data class OpenContainerBackgroundImage(val target: ContainerBackgroundTarget, val dark: Boolean) : ThemeConfigEffect
    data class ShowToast(val text: ThemeConfigToast) : ThemeConfigEffect
}

/**
 * `ThemeConfigEffect.ShowToast` 的语义枚举（对应 `:app` 的
 * `R.string.theme_config_reset_success` / `R.string.transparent_theme_alarm`）。
 * 由宿主壳映射回它自己的文案 —— 理由见本文件 KDoc 第 3 条。
 */
enum class ThemeConfigToast {
    ResetSuccess,
    TransparentThemeAlarm,
}
