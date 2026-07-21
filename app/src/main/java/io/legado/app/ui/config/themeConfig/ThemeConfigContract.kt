package io.legado.app.ui.config.themeConfig

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.domain.model.settings.ThemeSettings
import io.legado.app.utils.FileDoc

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
    data class Background(val dark: Boolean) : ThemeConfigSheet
    data object MainNavigation : ThemeConfigSheet
    data object LauncherIcon : ThemeConfigSheet
    data object DividerColor : ThemeConfigSheet
    data class BaseCardBorderColor(val dark: Boolean) : ThemeConfigSheet
    data object Font : ThemeConfigSheet
}

sealed interface ThemeConfigDialog {
    data object ResetDefaults : ThemeConfigDialog
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
    data class SelectAppFont(val file: FileDoc) : ThemeConfigIntent
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
    data class OpenTimePicker(
        val field: ThemeTimeField,
        val currentValue: String,
    ) : ThemeConfigEffect
    data class ShowToast(val stringRes: Int) : ThemeConfigEffect
}
