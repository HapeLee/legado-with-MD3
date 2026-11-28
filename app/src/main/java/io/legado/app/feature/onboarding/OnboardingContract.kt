package io.legado.app.feature.onboarding

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.settings.ThemeSettings
import kotlinx.collections.immutable.ImmutableList

/**
 * 欢迎引导流程（隐私 → WebDav → 书籍文件夹 → 主题）的 Compose 契约。
 * 页面文案见 strings.xml 的 onboarding_* 资源。
 */
@Stable
data class OnboardingUiState(
    val page: Int = 0,
    val pageCount: Int = 4,
    val privacyPolicy: String = "",
    val disclaimer: String = "",
    val webDavUrl: String = "",
    val webDavAccount: String = "",
    val webDavPassword: String = "",
    val appAccessPassword: String = "",
    val bookFolderUri: String? = null,
    val theme: ThemeSettings = ThemeSettings(),
    val themeMode: String = "0",
    val eInkGlobalMode: Boolean = false,
    val busyText: String? = null,
    val backupNames: ImmutableList<String>? = null,
    val restoreErrorMessage: String? = null,
)

/** 用户动作；与 Android Intent 无关，命名见 skill 约定。 */
sealed interface OnboardingIntent {
    data object Next : OnboardingIntent
    data object Prev : OnboardingIntent
    data class UpdateWebDavUrl(val value: String) : OnboardingIntent
    data class UpdateWebDavAccount(val value: String) : OnboardingIntent
    data class UpdateWebDavPassword(val value: String) : OnboardingIntent

    /** 应用访问密码，旧实现即时写入 LocalConfig.password */
    data class UpdateAppAccessPassword(val value: String) : OnboardingIntent
    data object SaveAndTestWebDav : OnboardingIntent
    data object FetchBackups : OnboardingIntent
    data class RestoreBackup(val name: String) : OnboardingIntent
    data object DismissBackupSelector : OnboardingIntent
    data object StartLocalRestore : OnboardingIntent
    data class RestoreLocalFile(val uri: String) : OnboardingIntent
    data object SelectFolder : OnboardingIntent
    data class SelectBookFolder(val uri: String) : OnboardingIntent
    data class SelectTheme(val value: String) : OnboardingIntent
    data class SetThemeMode(val value: String) : OnboardingIntent
    data class SetEInkGlobalMode(val enabled: Boolean) : OnboardingIntent
    data object DismissRestoreError : OnboardingIntent
    data object CancelBusy : OnboardingIntent
}

sealed interface OnboardingEffect {
    data object NavigateHome : OnboardingEffect

    /** 首页按返回键：直接结束引导（不进入主界面），保持旧行为 */
    data object Finish : OnboardingEffect
    data object OpenBookFolderPicker : OnboardingEffect
    data object OpenRestoreFilePicker : OnboardingEffect
    data object ApplyDayNight : OnboardingEffect
    data class ShowToast(val resId: Int) : OnboardingEffect
}
