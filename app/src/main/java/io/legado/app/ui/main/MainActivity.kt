package io.legado.app.ui.main

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.text.format.DateUtils
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import io.legado.app.BuildConfig
import io.legado.app.R
import io.legado.app.base.BaseComposeActivity
import io.legado.app.constant.AppConst.appInfo
import io.legado.app.domain.gateway.BackupSettingsGateway
import io.legado.app.domain.gateway.MangaSettingsGateway
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.help.book.BookHelp
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.storage.Backup
import io.legado.app.help.update.AppUpdateGitHub
import io.legado.app.lib.dialogs.alert
import io.legado.app.service.WebService
import io.legado.app.ui.about.CrashLogsDialog
import io.legado.app.ui.about.UpdateDialog
import io.legado.app.ui.book.read.ReadBookInputHandler
import io.legado.app.ui.book.read.ReadBookRouteHost
import io.legado.app.ui.book.read.page.entities.PageDirection
import io.legado.app.ui.theme.LocalAppUiConfiguration
import io.legado.app.ui.welcome.WelcomeActivity
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.ui.widget.dialog.VariableDialog
import io.legado.app.utils.LogUtils
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.android.ext.android.inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * 主界面
 */
open class MainActivity : BaseComposeActivity(), VariableDialog.Callback {

    companion object {
        private const val KEY_RESTORE_READ_ROUTE = "restoreReadRoute"
        private const val KEY_RESTORE_READ_BOOK_URL = "restoreReadBookUrl"
        private const val KEY_RESTORE_READ_ALOUD = "restoreReadAloud"
        private const val KEY_RESTORE_READ_IN_BOOKSHELF = "restoreReadInBookshelf"
        private const val KEY_RESTORE_READ_CHAPTER_CHANGED = "restoreReadChapterChanged"
        private val startupUpdateCheckGate = ProcessStartupUpdateCheckGate()

        @Volatile
        var hasActiveReadBookRoute: Boolean = false

        fun createLauncherIntent(context: Context): Intent =
            MainIntent.createLauncherIntent(context)

        fun createHomeIntent(context: Context): Intent = MainIntent.createHomeIntent(context)
        fun createIntent(context: Context, configTag: String? = null): Intent =
            MainIntent.createIntent(context, configTag)

        fun createRssSortIntent(
            context: Context,
            sourceUrl: String,
            sortUrl: String? = null,
            key: String? = null
        ): Intent = MainIntent.createRssSortIntent(context, sourceUrl, sortUrl, key)

        fun createRssReadIntent(
            context: Context,
            title: String? = null,
            origin: String,
            link: String? = null,
            openUrl: String? = null
        ): Intent = MainIntent.createRssReadIntent(context, title, origin, link, openUrl)

        fun createBookshelfManageScreenIntent(context: Context, groupId: Long = -1L): Intent =
            MainIntent.createBookshelfManageScreenIntent(context, groupId)

        fun createCacheIntent(context: Context, groupId: Long = -1L): Intent =
            MainIntent.createCacheIntent(context, groupId)

        fun createBookCacheManageIntent(context: Context): Intent =
            MainIntent.createBookCacheManageIntent(context)

        fun createReadBookIntent(
            context: Context,
            bookUrl: String? = null,
            readAloud: Boolean = false,
            inBookshelf: Boolean = true,
            chapterChanged: Boolean = false,
        ): Intent = MainIntent.createReadBookIntent(
            context = context,
            bookUrl = bookUrl,
            readAloud = readAloud,
            inBookshelf = inBookshelf,
            chapterChanged = chapterChanged,
        )

        fun createSearchIntent(
            context: Context,
            key: String? = null,
            scopeRaw: String? = null
        ): Intent = MainIntent.createSearchIntent(context, key, scopeRaw)

        fun createBookInfoIntent(
            context: Context,
            name: String? = null,
            author: String? = null,
            bookUrl: String,
            origin: String? = null,
            coverPath: String? = null
        ): Intent =
            MainIntent.createBookInfoIntent(context, name, author, bookUrl, origin, coverPath)

        fun createBookCharacterDetailIntent(
            context: Context,
            bookUrl: String,
            characterId: String? = null,
        ): Intent = MainIntent.createBookCharacterDetailIntent(context, bookUrl, characterId)

        fun createBookCharacterNetworkIntent(
            context: Context,
            bookUrl: String,
        ): Intent = MainIntent.createBookCharacterNetworkIntent(context, bookUrl)

        fun createBookKnowledgeListIntent(
            context: Context,
            bookUrl: String,
        ): Intent = MainIntent.createBookKnowledgeListIntent(context, bookUrl)

        fun createBookCharacterListIntent(
            context: Context,
            bookUrl: String,
        ): Intent = MainIntent.createBookCharacterListIntent(context, bookUrl)

        fun createBookKnowledgeDetailIntent(
            context: Context,
            bookUrl: String,
            entryId: String? = null,
        ): Intent = MainIntent.createBookKnowledgeDetailIntent(context, bookUrl, entryId)

        fun createBookEventListIntent(
            context: Context,
            bookUrl: String,
        ): Intent = MainIntent.createBookEventListIntent(context, bookUrl)

        fun createBookEventDetailIntent(
            context: Context,
            bookUrl: String,
            eventId: String? = null,
        ): Intent = MainIntent.createBookEventDetailIntent(context, bookUrl, eventId)

        fun createExploreShowIntent(
            context: Context,
            exploreName: String? = null,
            sourceUrl: String,
            exploreUrl: String? = null,
        ): Intent = MainIntent.createExploreShowIntent(context, exploreName, sourceUrl, exploreUrl)
    }

    private val viewModel by viewModel<MainViewModel>()
    private val otherSettingsGateway by inject<OtherSettingsGateway>()
    private val mangaSettingsGateway by inject<MangaSettingsGateway>()
    private val backupSettingsGateway by inject<BackupSettingsGateway>()
    private val routeEvents = MutableSharedFlow<NavKey>(extraBufferCapacity = 1)
    private var bookInfoVariableSetter: ((String, String?) -> Unit)? = null
    private var shouldApplyDefaultToRead = true
    private var restoredReadBookRoute: MainRouteReadBook? = null
    private var latestBackStack: List<NavKey> = emptyList()
    internal var activeReadBookInputHandler: ReadBookInputHandler? = null
    internal var activeReadBookRoute: MainRouteReadBook? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        shouldApplyDefaultToRead = savedInstanceState == null
        restoredReadBookRoute = savedInstanceState?.restoreReadBookRoute()
        super.onCreate(savedInstanceState)

        if (checkStartupRoute()) return
        val shouldAutoCheckUpdate = startupUpdateCheckGate.consume(
            otherSettingsGateway.currentSettings.autoCheckUpdateOnStart
        )

        // 智能自启：如果上次是手动开启状态（web_service_auto 为 true），则自启
        if (otherSettingsGateway.currentSettings.webServiceAutoStart) {
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
            if (otherSettingsGateway.currentSettings.autoRefresh && !isAutoRefreshedBook) {
                viewModel.upAllBookToc()
            }
            if (shouldAutoCheckUpdate) {
                checkUpdateOnStart()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!intent.hasExplicitStartRoute()) return
        routeEvents.tryEmit(MainNavigator.resolveStartRoute(intent))
    }

    @OptIn(ExperimentalSharedTransitionApi::class)
    @Composable
    override fun Content() {
        val orientation = resources.configuration.orientation
        val smallestWidthDp = resources.configuration.smallestScreenWidthDp
        val configuration = LocalAppUiConfiguration.current
        val tabletInterface = configuration.appShell.tabletInterface
        val defaultToReadFlow = remember(otherSettingsGateway) {
            otherSettingsGateway.settings
                .map { it.defaultToRead }
                .distinctUntilChanged()
        }
        val defaultToRead by defaultToReadFlow.collectAsStateWithLifecycle(
            otherSettingsGateway.currentSettings.defaultToRead,
        )
        val mangaSettings by mangaSettingsGateway.settings.collectAsStateWithLifecycle(
            mangaSettingsGateway.currentSettings,
        )

        val useRail = when (tabletInterface) {
            "always" -> true
            "landscape" -> orientation == Configuration.ORIENTATION_LANDSCAPE
            "off" -> false
            "auto" -> smallestWidthDp >= 600
            else -> false
        }

        val startRoutes = remember(defaultToRead) {
            val resolved = MainNavigator.resolveStartRoute(intent)
            val hasExplicitStartRoute = intent?.hasExplicitStartRoute() == true
            when {
                !hasExplicitStartRoute && restoredReadBookRoute != null -> {
                    arrayOf(MainRouteHome, restoredReadBookRoute!!)
                }
                shouldApplyDefaultToRead &&
                        defaultToRead &&
                        resolved == MainRouteHome -> {
                    arrayOf(MainRouteHome, MainRouteReadBook())
                }
                else -> {
                    arrayOf(resolved)
                }
            }
        }
        latestBackStack = startRoutes.toList()
        val backStack = rememberNavBackStack(*startRoutes)

        SideEffect {
            shouldApplyDefaultToRead = false
        }

        LaunchedEffect(backStack) {
            routeEvents.collect { route ->
                MainNavigator.navigateToRoute(backStack, route)
            }
        }

        LaunchedEffect(backStack) {
            snapshotFlow { backStack.toList() }
                .collect {
                    latestBackStack = it
                    MainNavigator.onBackStackChanged()
                }
        }

        SharedTransitionLayout {
            NavDisplay(
                backStack = backStack,
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                sceneStrategies = listOf(SinglePaneSceneStrategy()),
                transitionSpec = {
                    (slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
                        initialOffset = { fullWidth -> fullWidth }
                    ) + fadeIn(
                        animationSpec = tween(
                            durationMillis = 360,
                            easing = LinearOutSlowInEasing
                        )
                    )) togetherWith (slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
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
                        animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
                        initialOffset = { fullWidth -> -fullWidth / 4 }
                    ) + fadeIn(
                        animationSpec = tween(
                            durationMillis = 360,
                            easing = LinearOutSlowInEasing
                        )
                    )) togetherWith (scaleOut(
                        targetScale = 0.8f,
                        animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(durationMillis = 360)))
                },
                predictivePopTransitionSpec = { _ ->
                    (slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(easing = FastOutSlowInEasing),
                        initialOffset = { fullWidth -> -fullWidth / 4 }
                    ) + fadeIn(animationSpec = tween(easing = LinearOutSlowInEasing))) togetherWith (scaleOut(
                        targetScale = 0.8f,
                        animationSpec = tween(easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween()))
                },
                onBack = { MainNavigator.navigateBack(this@MainActivity, backStack) },
                entryProvider = mainEntryProvider(
                    backStack = backStack,
                    configuration = configuration,
                    showMangaUi = mangaSettings.showMangaUi,
                    useRail = useRail,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    onNavigateToRoute = { route ->
                        MainNavigator.navigateToRoute(
                            backStack,
                            route
                        )
                    },
                    onNavigateBack = { MainNavigator.navigateBack(this@MainActivity, backStack) },
                    onRegisterVariableSetter = { setter -> bookInfoVariableSetter = setter }
                )
            )
            BackHandler(
                enabled = !configuration.appShell.predictiveBackEnabled
            ) {
                MainNavigator.navigateBack(this@MainActivity, backStack)
            }
        }
    }

    private fun checkStartupRoute(): Boolean {
        return when {
            LocalConfig.isFirstOpenApp -> {
                startActivity<WelcomeActivity>()
                finish()
                true
            }
            else -> false
        }
    }

    private fun checkUpdateOnStart() {
        AppUpdateGitHub.check(lifecycleScope)
            .onSuccess { updateInfo ->
                showDialogFragment(UpdateDialog(updateInfo))
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
        if (!backupSettingsGateway.currentSettings.autoCheckNewBackup) {
            return
        }
        lifecycleScope.launch {
            val lastBackupFile = try {
                withContext(IO) { viewModel.getLatestWebDavBackup() }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                return@launch
            } ?: return@launch
            if (lastBackupFile.lastModify - LocalConfig.lastBackup > DateUtils.MINUTE_IN_MILLIS) {
                LocalConfig.lastBackup = lastBackupFile.lastModify
                alert(R.string.restore, R.string.webdav_after_local_restore_confirm) {
                    cancelButton()
                    okButton {
                        viewModel.restoreWebDav(lastBackupFile.name)
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (otherSettingsGateway.currentSettings.autoRefresh) {
            outState.putBoolean("isAutoRefreshedBook", true)
        }
        val readRoute = latestBackStack.lastOrNull() as? MainRouteReadBook
            ?: activeReadBookRoute
        if (readRoute != null) {
            outState.putBoolean(KEY_RESTORE_READ_ROUTE, true)
            outState.putString(KEY_RESTORE_READ_BOOK_URL, readRoute.bookUrl)
            outState.putBoolean(KEY_RESTORE_READ_ALOUD, readRoute.readAloud)
            outState.putBoolean(KEY_RESTORE_READ_IN_BOOKSHELF, readRoute.inBookshelf)
            outState.putBoolean(KEY_RESTORE_READ_CHAPTER_CHANGED, readRoute.chapterChanged)
        }
    }

    private fun Bundle.restoreReadBookRoute(): MainRouteReadBook? {
        if (!getBoolean(KEY_RESTORE_READ_ROUTE, false)) return null
        return MainRouteReadBook(
            bookUrl = getString(KEY_RESTORE_READ_BOOK_URL),
            readAloud = getBoolean(KEY_RESTORE_READ_ALOUD, false),
            inBookshelf = getBoolean(KEY_RESTORE_READ_IN_BOOKSHELF, true),
            chapterChanged = getBoolean(KEY_RESTORE_READ_CHAPTER_CHANGED, false),
        )
    }

    private fun Intent.hasExplicitStartRoute(): Boolean {
        return hasExtra(MainIntent.EXTRA_START_ROUTE)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val isDown = event.action == KeyEvent.ACTION_DOWN
        if (keyCode == KeyEvent.KEYCODE_MENU && isDown) {
            activeReadBookInputHandler?.toggleMenu()
            if (activeReadBookInputHandler != null) return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        val controller = activeReadBookInputHandler ?: return super.onGenericMotionEvent(event)
        if (0 != (event.source and InputDevice.SOURCE_CLASS_POINTER) &&
            event.action == MotionEvent.ACTION_SCROLL
        ) {
            val axisValue = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            LogUtils.d("onGenericMotionEvent", "axisValue = $axisValue")
            controller.mouseWheelPage(
                if (axisValue < 0.0f) PageDirection.NEXT else PageDirection.PREV
            )
            return true
        }
        if (0 != (event.source and InputDevice.SOURCE_CLASS_JOYSTICK) &&
            event.action == MotionEvent.ACTION_MOVE
        ) {
            val yAxis = event.getAxisValue(MotionEvent.AXIS_Y)
            if (kotlin.math.abs(yAxis) > 0.5f) {
                controller.handleKeyPage(
                    if (yAxis > 0) PageDirection.NEXT else PageDirection.PREV
                )
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (activeReadBookInputHandler?.onKeyDown(keyCode, event) == true) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (activeReadBookInputHandler?.onKeyUp(keyCode, event) == true) return true
        return super.onKeyUp(keyCode, event)
    }

    override fun setupSystemBar() {
        val host = activeReadBookInputHandler as? ReadBookRouteHost
        if (host != null) {
            host.upSystemUiVisibility()
        } else {
            super.setupSystemBar()
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

    override fun setVariable(key: String, variable: String?) {
        bookInfoVariableSetter?.invoke(key, variable)
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
