package io.legado.app.ui.main

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import io.legado.app.BuildConfig
import io.legado.app.R
import io.legado.app.base.BaseComposeActivity
import io.legado.app.constant.AppConst.appInfo
import io.legado.app.constant.PreferKey
import io.legado.app.help.AppWebDav
import io.legado.app.help.book.BookHelp
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.storage.Backup
import io.legado.app.help.update.AppUpdateGitHub
import io.legado.app.lib.dialogs.alert
import io.legado.app.service.WebService
import io.legado.app.ui.about.CrashLogsDialog
import io.legado.app.ui.about.UpdateDialog
import io.legado.app.ui.book.import.local.ImportBookScreen
import io.legado.app.ui.book.import.remote.RemoteBookScreen
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.config.ConfigNavScreen
import io.legado.app.ui.config.ConfigTag
import io.legado.app.ui.config.backupConfig.BackupConfigScreen
import io.legado.app.ui.config.coverConfig.CoverConfigScreen
import io.legado.app.ui.config.mainConfig.MainConfig
import io.legado.app.ui.config.otherConfig.OtherConfigScreen
import io.legado.app.ui.config.readConfig.ReadConfigScreen
import io.legado.app.ui.config.themeConfig.ThemeConfigScreen
import io.legado.app.ui.welcome.WelcomeActivity
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.koin.androidx.viewmodel.ext.android.viewModel
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * 主界面
 */
open class MainActivity : BaseComposeActivity() {

    companion object {
        const val EXTRA_START_ROUTE = "startRoute"
        private const val ROUTE_MAIN = "main"
        private const val ROUTE_SETTINGS = "settings"
        private const val ROUTE_SETTINGS_OTHER = "settings/other"
        private const val ROUTE_SETTINGS_READ = "settings/read"
        private const val ROUTE_SETTINGS_COVER = "settings/cover"
        private const val ROUTE_SETTINGS_THEME = "settings/theme"
        private const val ROUTE_SETTINGS_BACKUP = "settings/backup"
        private const val ROUTE_IMPORT_LOCAL = "import/local"
        private const val ROUTE_IMPORT_REMOTE = "import/remote"

        fun createIntent(context: Context, configTag: String? = null): Intent {
            return Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_START_ROUTE, routeForConfigTag(configTag))
            }
        }

        private fun routeForConfigTag(configTag: String?): String {
            return when (configTag) {
                ConfigTag.OTHER_CONFIG -> ROUTE_SETTINGS_OTHER
                ConfigTag.READ_CONFIG -> ROUTE_SETTINGS_READ
                ConfigTag.COVER_CONFIG -> ROUTE_SETTINGS_COVER
                ConfigTag.THEME_CONFIG -> ROUTE_SETTINGS_THEME
                ConfigTag.BACKUP_CONFIG -> ROUTE_SETTINGS_BACKUP
                else -> ROUTE_SETTINGS
            }
        }
    }

    private val viewModel by viewModel<MainViewModel>()
    private val routeEvents = MutableSharedFlow<MainRoute>(extraBufferCapacity = 1)

    @Serializable
    private sealed interface MainRoute : NavKey

    @Serializable
    private data object MainRouteHome : MainRoute

    @Serializable
    private data object MainRouteSettings : MainRoute

    @Serializable
    private data object MainRouteSettingsOther : MainRoute

    @Serializable
    private data object MainRouteSettingsRead : MainRoute

    @Serializable
    private data object MainRouteSettingsCover : MainRoute

    @Serializable
    private data object MainRouteSettingsTheme : MainRoute

    @Serializable
    private data object MainRouteSettingsBackup : MainRoute

    @Serializable
    private data object MainRouteImportLocal : MainRoute

    @Serializable
    private data object MainRouteImportRemote : MainRoute

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        if (checkStartupRoute()) return

        // 智能自启：如果上次是手动开启状态（web_service_auto 为 true），则自启
        if (AppConfig.webServiceAutoStart) {
            WebService.startForeground(this)
        }

        lifecycleScope.launch {
            //版本更新
            upVersion()
            //设置本地密码
            notifyAppCrash()
            //备份同步
            backupSync()
            //自动更新书籍
            val isAutoRefreshedBook = savedInstanceState?.getBoolean("isAutoRefreshedBook") ?: false
            if (AppConfig.autoRefreshBook && !isAutoRefreshedBook) {
                viewModel.upAllBookToc()
            }
            viewModel.postLoad()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routeEvents.tryEmit(resolveStartRoute(intent))
    }

    @Composable
    override fun Content() {
        val orientation = resources.configuration.orientation
        val smallestWidthDp = resources.configuration.smallestScreenWidthDp
        val tabletInterface = MainConfig.tabletInterface

        val useRail = when (tabletInterface) {
            "always" -> true
            "landscape" -> orientation == Configuration.ORIENTATION_LANDSCAPE
            "off" -> false
            "auto" -> smallestWidthDp >= 600
            else -> false
        }

        val backStack = rememberNavBackStack(resolveStartRoute(intent))

        LaunchedEffect(backStack) {
            routeEvents.collect { route ->
                navigateToRoute(backStack, route)
            }
        }

        NavDisplay(
            backStack = backStack,
            transitionSpec = {
                (slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(
                        durationMillis = 480,
                        easing = FastOutSlowInEasing
                    ),
                    initialOffset = { fullWidth -> fullWidth }
                ) + fadeIn(
                    animationSpec = tween(
                        durationMillis = 360,
                        easing = LinearOutSlowInEasing
                    )
                )) togetherWith (slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(
                        durationMillis = 480,
                        easing = FastOutSlowInEasing
                    ),
                    targetOffset = { fullWidth -> fullWidth / 4 }
                ) + fadeOut(
                    animationSpec = tween(
                        durationMillis = 360,
                        easing = LinearOutSlowInEasing
                    )
                ))
            },
            popTransitionSpec = {
                (slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(
                        durationMillis = 480,
                        easing = FastOutSlowInEasing
                    ),
                    initialOffset = { fullWidth -> -fullWidth / 4 }
                ) + fadeIn(
                    animationSpec = tween(
                        durationMillis = 360,
                        easing = LinearOutSlowInEasing
                    )
                )) togetherWith (scaleOut(
                    targetScale = 0.8f,
                    animationSpec = tween(
                        durationMillis = 480,
                        easing = FastOutSlowInEasing
                    )
                ) + fadeOut(
                    animationSpec = tween(durationMillis = 360)
                ))
            },
            predictivePopTransitionSpec = { _ ->
                (slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(
                        easing = FastOutSlowInEasing
                    ),
                    initialOffset = { fullWidth -> -fullWidth / 4 }
                ) + fadeIn(
                    animationSpec = tween(
                        easing = LinearOutSlowInEasing
                    )
                )) togetherWith (scaleOut(
                    targetScale = 0.8f,
                    animationSpec = tween(
                        easing = FastOutSlowInEasing
                    )
                ) + fadeOut(
                    animationSpec = tween()
                ))
            },
            onBack = {
                if (backStack.size > 1) {
                    backStack.removeLastOrNull()
                } else {
                    finish()
                }
            },
            entryProvider = entryProvider {
                entry<MainRouteHome> {
                    MainScreen(
                        useRail = useRail,
                        onOpenSettings = {
                            navigateToRoute(backStack, MainRouteSettings)
                        },
                        onNavigateToRemoteImport = {
                            navigateToRoute(backStack, MainRouteImportRemote)
                        },
                        onNavigateToLocalImport = {
                            navigateToRoute(backStack, MainRouteImportLocal)
                        }
                    )
                }

                entry<MainRouteSettings> {
                    ConfigNavScreen(
                        onBackClick = { navigateBack(backStack) },
                        onNavigateToOther = { backStack.add(MainRouteSettingsOther) },
                        onNavigateToRead = { backStack.add(MainRouteSettingsRead) },
                        onNavigateToCover = { backStack.add(MainRouteSettingsCover) },
                        onNavigateToTheme = { backStack.add(MainRouteSettingsTheme) },
                        onNavigateToBackup = { backStack.add(MainRouteSettingsBackup) }
                    )
                }

                entry<MainRouteSettingsOther> {
                    OtherConfigScreen(onBackClick = { navigateBack(backStack) })
                }

                entry<MainRouteSettingsRead> {
                    ReadConfigScreen(onBackClick = { navigateBack(backStack) })
                }

                entry<MainRouteSettingsCover> {
                    CoverConfigScreen(onBackClick = { navigateBack(backStack) })
                }

                entry<MainRouteSettingsTheme> {
                    ThemeConfigScreen(onBackClick = { navigateBack(backStack) })
                }

                entry<MainRouteSettingsBackup> {
                    BackupConfigScreen(onBackClick = { navigateBack(backStack) })
                }

                entry<MainRouteImportLocal> {
                    ImportBookScreen(
                        onBackClick = { navigateBack(backStack) }
                    )
                }

                entry<MainRouteImportRemote> {
                    RemoteBookScreen(
                        onBackClick = { navigateBack(backStack) }
                    )
                }
            }
        )
    }

    private fun navigateToRoute(backStack: MutableList<NavKey>, route: MainRoute) {
        val currentRoute = backStack.lastOrNull()
        if (currentRoute == route) return

        when (route) {
            MainRouteHome -> {
                backStack.clear()
                backStack.add(MainRouteHome)
            }

            MainRouteSettings -> {
                if (currentRoute == MainRouteHome) {
                    backStack.add(MainRouteSettings)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(MainRouteSettings)
                }
            }

            MainRouteSettingsOther,
            MainRouteSettingsRead,
            MainRouteSettingsCover,
            MainRouteSettingsTheme,
            MainRouteSettingsBackup -> {
                backStack.clear()
                backStack.add(MainRouteHome)
                backStack.add(MainRouteSettings)
                backStack.add(route)
            }

            MainRouteImportLocal,
            MainRouteImportRemote -> {
                if (currentRoute == MainRouteHome) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(route)
                }
            }
        }
    }

    private fun navigateBack(backStack: MutableList<NavKey>) {
        if (backStack.size > 1) {
            backStack.removeLastOrNull()
        } else {
            finish()
        }
    }

    private fun checkStartupRoute(): Boolean {
        return when {
            LocalConfig.isFirstOpenApp -> {
                startActivity<WelcomeActivity>()
                finish()
                true
            }
            getPrefBoolean(PreferKey.defaultToRead) -> {
                startActivity<ReadBookActivity>()
                false
            }
            else -> false
        }
    }

    /**
     * 版本更新日志
     */
    private suspend fun upVersion() = suspendCoroutine<Unit?> { block ->
        if (LocalConfig.versionCode == appInfo.versionCode) {
            block.resume(null)
            return@suspendCoroutine
        }
        LocalConfig.versionCode = appInfo.versionCode
        if (LocalConfig.isFirstOpenApp) {
            val help = String(assets.open("web/help/md/appHelp.md").readBytes())
            val dialog = TextDialog(getString(R.string.help), help, TextDialog.Mode.MD)
            dialog.setOnDismissListener { block.resume(null) }
            showDialogFragment(dialog)
            return@suspendCoroutine
        }
        if (!BuildConfig.DEBUG) {
            lifecycleScope.launch {
                try {
                    val info = AppUpdateGitHub.getReleaseByTag(BuildConfig.VERSION_NAME)
                    if (info != null) {
                        val dialog = UpdateDialog(info, UpdateDialog.Mode.VIEW_LOG)
                        dialog.setOnDismissListener { block.resume(null) }
                        showDialogFragment(dialog)
                    } else {
                        val fallback = String(assets.open("updateLog.md").readBytes())
                        val dialog = TextDialog(getString(R.string.update_log), fallback, TextDialog.Mode.MD)
                        dialog.setOnDismissListener { block.resume(null) }
                        showDialogFragment(dialog)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    val fallback = String(assets.open("updateLog.md").readBytes())
                    val dialog = TextDialog(getString(R.string.update_log), fallback, TextDialog.Mode.MD)
                    dialog.setOnDismissListener { block.resume(null) }
                    showDialogFragment(dialog)
                }
            }
        } else {
            block.resume(null)
        }
    }

    private fun notifyAppCrash() {
        if (!LocalConfig.appCrash || BuildConfig.DEBUG) {
            return
        }
        LocalConfig.appCrash = false
        alert(getString(R.string.draw), "检测到阅读发生了崩溃，是否打开崩溃日志以便报告问题？") {
            yesButton {
                showDialogFragment<CrashLogsDialog>()
            }
            noButton()
        }
    }

    /**
     * 备份同步
     */
    private fun backupSync() {
        if (!AppConfig.autoCheckNewBackup) {
            return
        }
        lifecycleScope.launch {
            val lastBackupFile =
                withContext(IO) { AppWebDav.lastBackUp().getOrNull() } ?: return@launch
            if (lastBackupFile.lastModify - LocalConfig.lastBackup > DateUtils.MINUTE_IN_MILLIS) {
                LocalConfig.lastBackup = lastBackupFile.lastModify
                alert(R.string.restore, R.string.webdav_after_local_restore_confirm) {
                    cancelButton()
                    okButton {
                        viewModel.restoreWebDav(lastBackupFile.displayName)
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (AppConfig.autoRefreshBook) {
            outState.putBoolean("isAutoRefreshedBook", true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Coroutine.async {
            BookHelp.clearInvalidCache()
        }
        if (!BuildConfig.DEBUG) {
            Backup.autoBack(this)
        }
    }

    private fun resolveStartRoute(intent: Intent?): MainRoute {
        return resolveStartRoute(intent?.getStringExtra(EXTRA_START_ROUTE))
    }

    private fun resolveStartRoute(route: String?): MainRoute {
        return when (route) {
            "main" -> MainRouteHome
            "settings" -> MainRouteSettings
            "settings/other" -> MainRouteSettingsOther
            "settings/read" -> MainRouteSettingsRead
            "settings/cover" -> MainRouteSettingsCover
            "settings/theme" -> MainRouteSettingsTheme
            "settings/backup" -> MainRouteSettingsBackup
            "import/local" -> MainRouteImportLocal
            "import/remote" -> MainRouteImportRemote
            else -> MainRouteHome
        }
    }

}

class LauncherW : MainActivity()
class Launcher1 : MainActivity()
class Launcher2 : MainActivity()
class Launcher3 : MainActivity()
class Launcher4 : MainActivity()
class Launcher5 : MainActivity()
class Launcher6 : MainActivity()
class Launcher0 : MainActivity()
