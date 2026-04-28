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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import io.legado.app.ui.book.cache.manage.BookCacheManageRouteScreen
import io.legado.app.ui.book.explore.ExploreShowScreen
import io.legado.app.ui.book.info.BookInfoRouteScreen
import io.legado.app.ui.book.info.BookInfoViewModel
import io.legado.app.ui.book.import.local.ImportBookScreen
import io.legado.app.ui.book.import.remote.RemoteBookScreen
import io.legado.app.ui.book.search.SearchIntent
import io.legado.app.ui.book.search.SearchScreen
import io.legado.app.ui.book.search.SearchViewModel
import io.legado.app.ui.book.source.manage.BookSourceActivity
import io.legado.app.ui.book.manage.BookshelfManageRouteScreen
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.config.ConfigNavScreen
import io.legado.app.ui.config.ConfigTag
import io.legado.app.ui.config.backupConfig.BackupConfigScreen
import io.legado.app.ui.config.coverConfig.CoverConfigScreen
import io.legado.app.ui.config.mainConfig.MainConfig
import io.legado.app.ui.config.otherConfig.OtherConfigScreen
import io.legado.app.ui.config.readConfig.ReadConfigScreen
import io.legado.app.ui.config.themeConfig.ThemeConfigScreen
import io.legado.app.ui.rss.article.MainRouteRssSort
import io.legado.app.ui.rss.article.RssSortRouteScreen
import io.legado.app.ui.rss.read.MainRouteRssRead
import io.legado.app.ui.rss.read.RssReadRouteScreen
import io.legado.app.ui.welcome.WelcomeActivity
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.ui.widget.dialog.VariableDialog
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * 主界面
 */
open class MainActivity : BaseComposeActivity(), VariableDialog.Callback {

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
        private const val ROUTE_CACHE = "cache"
        private const val ROUTE_BOOK_CACHE_MANAGE = "book/cache/manage"
        private const val ROUTE_SEARCH = "search"
        private const val ROUTE_BOOK_INFO = "book/info"
        private const val ROUTE_EXPLORE_SHOW = "explore/show"
        private const val ROUTE_RSS_SORT = "rss/sort"
        private const val ROUTE_RSS_READ = "rss/read"
        private const val EXTRA_CACHE_GROUP_ID = "extra_cache_group_id"
        private const val EXTRA_SEARCH_KEY = "extra_search_key"
        private const val EXTRA_SEARCH_SCOPE = "extra_search_scope"
        private const val EXTRA_BOOK_NAME = "name"
        private const val EXTRA_BOOK_AUTHOR = "author"
        private const val EXTRA_BOOK_URL = "bookUrl"
        private const val EXTRA_EXPLORE_NAME = "exploreName"
        private const val EXTRA_SOURCE_URL = "sourceUrl"
        private const val EXTRA_EXPLORE_URL = "exploreUrl"

        private const val EXTRA_RSS_SOURCE_URL = "extra_rss_source_url"
        private const val EXTRA_RSS_SORT_URL = "extra_rss_sort_url"
        private const val EXTRA_RSS_KEY = "extra_rss_key"

        private const val EXTRA_RSS_READ_TITLE = "extra_rss_read_title"
        private const val EXTRA_RSS_READ_ORIGIN = "extra_rss_read_origin"
        private const val EXTRA_RSS_READ_LINK = "extra_rss_read_link"
        private const val EXTRA_RSS_READ_OPEN_URL = "extra_rss_read_open_url"

        fun createLauncherIntent(context: Context): Intent {
            val launcherComponent =
                context.packageManager.getLaunchIntentForPackage(context.packageName)?.component
            return if (launcherComponent != null) {
                Intent().setComponent(launcherComponent)
            } else {
                Intent(context, MainActivity::class.java)
            }
        }

        fun createHomeIntent(context: Context): Intent {
            return createLauncherIntent(context).apply {
                putExtra(EXTRA_START_ROUTE, ROUTE_MAIN)
            }
        }

        fun createIntent(context: Context, configTag: String? = null): Intent {
            return createLauncherIntent(context).apply {
                putExtra(EXTRA_START_ROUTE, routeForConfigTag(configTag))
            }
        }

        fun createRssSortIntent(
            context: Context,
            sourceUrl: String,
            sortUrl: String? = null,
            key: String? = null
        ): Intent {
            return createLauncherIntent(context).apply {
                putExtra(EXTRA_START_ROUTE, ROUTE_RSS_SORT)
                putExtra(EXTRA_RSS_SOURCE_URL, sourceUrl)
                putExtra(EXTRA_RSS_SORT_URL, sortUrl)
                putExtra(EXTRA_RSS_KEY, key)
            }
        }

        fun createRssReadIntent(
            context: Context,
            title: String? = null,
            origin: String,
            link: String? = null,
            openUrl: String? = null
        ): Intent {
            return createLauncherIntent(context).apply {
                putExtra(EXTRA_START_ROUTE, ROUTE_RSS_READ)
                putExtra(EXTRA_RSS_READ_TITLE, title)
                putExtra(EXTRA_RSS_READ_ORIGIN, origin)
                putExtra(EXTRA_RSS_READ_LINK, link)
                putExtra(EXTRA_RSS_READ_OPEN_URL, openUrl)
            }
        }

        fun createBookshelfManageScreenIntent(
            context: Context,
            groupId: Long = -1L
        ): Intent {
            return createLauncherIntent(context).apply {
                putExtra(EXTRA_START_ROUTE, ROUTE_CACHE)
                putExtra(EXTRA_CACHE_GROUP_ID, groupId)
            }
        }

        fun createCacheIntent(
            context: Context,
            groupId: Long = -1L
        ): Intent = createBookshelfManageScreenIntent(context, groupId)

        fun createBookCacheManageIntent(context: Context): Intent {
            return createLauncherIntent(context).apply {
                putExtra(EXTRA_START_ROUTE, ROUTE_BOOK_CACHE_MANAGE)
            }
        }

        fun createSearchIntent(
            context: Context,
            key: String? = null,
            scopeRaw: String? = null
        ): Intent {
            return createLauncherIntent(context).apply {
                putExtra(EXTRA_START_ROUTE, ROUTE_SEARCH)
                putExtra(EXTRA_SEARCH_KEY, key)
                putExtra(EXTRA_SEARCH_SCOPE, scopeRaw)
            }
        }

        fun createBookInfoIntent(
            context: Context,
            name: String? = null,
            author: String? = null,
            bookUrl: String
        ): Intent {
            return createLauncherIntent(context).apply {
                putExtra(EXTRA_START_ROUTE, ROUTE_BOOK_INFO)
                putExtra(EXTRA_BOOK_NAME, name)
                putExtra(EXTRA_BOOK_AUTHOR, author)
                putExtra(EXTRA_BOOK_URL, bookUrl)
            }
        }

        fun createExploreShowIntent(
            context: Context,
            exploreName: String? = null,
            sourceUrl: String,
            exploreUrl: String? = null
        ): Intent {
            return createLauncherIntent(context).apply {
                putExtra(EXTRA_START_ROUTE, ROUTE_EXPLORE_SHOW)
                putExtra(EXTRA_EXPLORE_NAME, exploreName)
                putExtra(EXTRA_SOURCE_URL, sourceUrl)
                putExtra(EXTRA_EXPLORE_URL, exploreUrl)
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
    private val routeEvents = MutableSharedFlow<NavKey>(extraBufferCapacity = 1)
    private var bookInfoVariableSetter: ((String, String?) -> Unit)? = null

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

    @Serializable
    private data class MainRouteCache(val groupId: Long) : MainRoute

    @Serializable
    private data object MainRouteBookCacheManage : MainRoute

    @Serializable
    private data class MainRouteSearch(
        val key: String?,
        val scopeRaw: String? = null
    ) : MainRoute

    @Serializable
    private data class MainRouteBookInfo(
        val name: String?,
        val author: String?,
        val bookUrl: String,
    ) : MainRoute

    @Serializable
    private data class MainRouteExploreShow(
        val title: String?,
        val sourceUrl: String,
        val exploreUrl: String?,
    ) : MainRoute

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
                        onNavigateToSearch = { key ->
                            navigateToRoute(
                                backStack,
                                MainRouteSearch(
                                    key = key?.trim()?.takeIf { it.isNotEmpty() }
                                )
                            )
                        },
                        onNavigateToRemoteImport = {
                            navigateToRoute(backStack, MainRouteImportRemote)
                        },
                        onNavigateToLocalImport = {
                            navigateToRoute(backStack, MainRouteImportLocal)
                        },
                        onNavigateToCache = { groupId ->
                            navigateToRoute(backStack, MainRouteCache(groupId))
                        },
                        onNavigateToBookCacheManage = {
                            navigateToRoute(backStack, MainRouteBookCacheManage)
                        },
                        onNavigateToBookInfo = { name, author, bookUrl ->
                            navigateToRoute(
                                backStack,
                                MainRouteBookInfo(
                                    name = name,
                                    author = author,
                                    bookUrl = bookUrl
                                )
                            )
                        },
                        onNavigateToExploreShow = { title, sourceUrl, exploreUrl ->
                            navigateToRoute(
                                backStack,
                                MainRouteExploreShow(
                                    title = title,
                                    sourceUrl = sourceUrl,
                                    exploreUrl = exploreUrl
                                )
                            )
                        },
                        onNavigateToRssSort = { sourceUrl, sortUrl, key ->
                            navigateToRoute(
                                backStack,
                                MainRouteRssSort(
                                    sourceUrl = sourceUrl,
                                    sortUrl = sortUrl,
                                    key = key
                                )
                            )
                        },
                        onNavigateToRssRead = { title, origin, link, openUrl ->
                            navigateToRoute(
                                backStack,
                                MainRouteRssRead(
                                    title = title,
                                    origin = origin,
                                    link = link,
                                    openUrl = openUrl
                                )
                            )
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

                entry<MainRouteCache> { route ->
                    BookshelfManageRouteScreen(
                        groupId = route.groupId,
                        onBackClick = { navigateBack(backStack) },
                        onOpenBookInfo = { name, author, bookUrl ->
                            navigateToRoute(
                                backStack,
                                MainRouteBookInfo(
                                    name = name,
                                    author = author,
                                    bookUrl = bookUrl
                                )
                            )
                        }
                    )
                }

                entry<MainRouteBookCacheManage> {
                    BookCacheManageRouteScreen(
                        onBackClick = { navigateBack(backStack) }
                    )
                }

                entry<MainRouteSearch> { route ->
                    val searchViewModel = koinViewModel<SearchViewModel>()
                    val lifecycleOwner = LocalLifecycleOwner.current

                    LaunchedEffect(route.key, route.scopeRaw, searchViewModel) {
                        searchViewModel.onIntent(
                            SearchIntent.Initialize(
                                key = route.key,
                                scopeRaw = route.scopeRaw
                            )
                        )
                    }

                    DisposableEffect(lifecycleOwner, searchViewModel) {
                        searchViewModel.onIntent(SearchIntent.ResumeEngine)
                        val observer = LifecycleEventObserver { _, event ->
                            when (event) {
                                Lifecycle.Event.ON_RESUME -> {
                                    searchViewModel.onIntent(SearchIntent.ResumeEngine)
                                }

                                Lifecycle.Event.ON_PAUSE -> {
                                    searchViewModel.onIntent(SearchIntent.PauseEngine)
                                }

                                else -> Unit
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                            searchViewModel.onIntent(SearchIntent.PauseEngine)
                        }
                    }

                    SearchScreen(
                        viewModel = searchViewModel,
                        onBack = { navigateBack(backStack) },
                        onOpenBookInfo = { name, author, bookUrl ->
                            navigateToRoute(
                                backStack,
                                MainRouteBookInfo(
                                    name = name,
                                    author = author,
                                    bookUrl = bookUrl
                                )
                            )
                        },
                        onOpenSourceManage = {
                            this@MainActivity.startActivity<BookSourceActivity>()
                        }
                    )
                }

                entry<MainRouteRssSort> { route ->
                    RssSortRouteScreen(
                        sourceUrl = route.sourceUrl,
                        initialSortUrl = route.sortUrl,
                        onBackClick = { navigateBack(backStack) },
                        onOpenRead = { title, origin, link, openUrl ->
                            navigateToRoute(
                                backStack,
                                MainRouteRssRead(
                                    title = title,
                                    origin = origin,
                                    link = link,
                                    openUrl = openUrl
                                )
                            )
                        }
                    )
                }

                entry<MainRouteRssRead> { route ->
                    RssReadRouteScreen(
                        title = route.title,
                        origin = route.origin,
                        link = route.link,
                        openUrl = route.openUrl,
                        onBackClick = { navigateBack(backStack) }
                    )
                }

                entry<MainRouteBookInfo> { route ->
                    val bookInfoViewModel = koinViewModel<BookInfoViewModel>()
                    BookInfoRouteScreen(
                        bookUrl = route.bookUrl,
                        viewModel = bookInfoViewModel,
                        onBack = { navigateBack(backStack) },
                        onFinish = { _, _ -> navigateBack(backStack) },
                        onOpenSearch = { keyword ->
                            navigateToRoute(backStack, MainRouteSearch(key = keyword))
                        },
                        onRegisterVariableSetter = { setter ->
                            bookInfoVariableSetter = setter
                        }
                    )
                }

                entry<MainRouteExploreShow> { route ->
                    ExploreShowScreen(
                        title = route.title ?: "探索",
                        sourceUrl = route.sourceUrl,
                        exploreUrl = route.exploreUrl,
                        onBack = { navigateBack(backStack) },
                        onBookClick = { book ->
                            navigateToRoute(
                                backStack,
                                MainRouteBookInfo(
                                    name = book.name,
                                    author = book.author,
                                    bookUrl = book.bookUrl
                                )
                            )
                        }
                    )
                }
            }
        )
    }

    private fun navigateToRoute(backStack: MutableList<NavKey>, route: NavKey) {
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
            MainRouteImportRemote,
            is MainRouteCache,
            MainRouteBookCacheManage -> {
                if (currentRoute == MainRouteHome) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(route)
                }
            }

            is MainRouteSearch -> {
                if (currentRoute == MainRouteHome) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(route)
                }
            }

            is MainRouteBookInfo -> {
                if (
                    currentRoute == MainRouteHome ||
                    currentRoute is MainRouteSearch ||
                    currentRoute is MainRouteExploreShow
                ) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(route)
                }
            }

            is MainRouteExploreShow -> {
                if (currentRoute == MainRouteHome) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(route)
                }
            }

            is MainRouteRssSort -> {
                if (currentRoute == MainRouteHome) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(route)
                }
            }

            is MainRouteRssRead -> {
                if (currentRoute == MainRouteHome || currentRoute is MainRouteRssSort) {
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
                withContext(IO) { viewModel.getLatestWebDavBackup() } ?: return@launch
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

    private fun resolveStartRoute(intent: Intent?): NavKey {
        val route = intent?.getStringExtra(EXTRA_START_ROUTE)
        resolveRssStartRoute(route, intent)?.let { return it }
        return resolveStartRoute(route)
    }

    private fun resolveRssStartRoute(route: String?, intent: Intent?): NavKey? {
        return when (route) {
            ROUTE_RSS_SORT -> {
                val sourceUrl = intent?.getStringExtra(EXTRA_RSS_SOURCE_URL)
                if (sourceUrl.isNullOrBlank()) {
                    null
                } else {
                    MainRouteRssSort(
                        sourceUrl = sourceUrl,
                        sortUrl = intent.getStringExtra(EXTRA_RSS_SORT_URL),
                        key = intent.getStringExtra(EXTRA_RSS_KEY)
                    )
                }
            }

            ROUTE_RSS_READ -> {
                val origin = intent?.getStringExtra(EXTRA_RSS_READ_ORIGIN)
                if (origin.isNullOrBlank()) {
                    null
                } else {
                    MainRouteRssRead(
                        title = intent.getStringExtra(EXTRA_RSS_READ_TITLE),
                        origin = origin,
                        link = intent.getStringExtra(EXTRA_RSS_READ_LINK),
                        openUrl = intent.getStringExtra(EXTRA_RSS_READ_OPEN_URL)
                    )
                }
            }

            else -> null
        }
    }

    private fun resolveStartRoute(route: String?): MainRoute {
        return when (route) {
            ROUTE_MAIN -> MainRouteHome
            ROUTE_SETTINGS -> MainRouteSettings
            ROUTE_SETTINGS_OTHER -> MainRouteSettingsOther
            ROUTE_SETTINGS_READ -> MainRouteSettingsRead
            ROUTE_SETTINGS_COVER -> MainRouteSettingsCover
            ROUTE_SETTINGS_THEME -> MainRouteSettingsTheme
            ROUTE_SETTINGS_BACKUP -> MainRouteSettingsBackup
            ROUTE_IMPORT_LOCAL -> MainRouteImportLocal
            ROUTE_IMPORT_REMOTE -> MainRouteImportRemote
            ROUTE_CACHE -> MainRouteCache(intent?.getLongExtra(EXTRA_CACHE_GROUP_ID, -1L) ?: -1L)
            ROUTE_BOOK_CACHE_MANAGE -> MainRouteBookCacheManage
            ROUTE_SEARCH -> MainRouteSearch(
                key = intent?.getStringExtra(EXTRA_SEARCH_KEY),
                scopeRaw = intent?.getStringExtra(EXTRA_SEARCH_SCOPE)
            )
            ROUTE_BOOK_INFO -> intent?.getStringExtra(EXTRA_BOOK_URL)
                ?.takeIf { it.isNotBlank() }
                ?.let { bookUrl ->
                    MainRouteBookInfo(
                        name = intent.getStringExtra(EXTRA_BOOK_NAME),
                        author = intent.getStringExtra(EXTRA_BOOK_AUTHOR),
                        bookUrl = bookUrl
                    )
                } ?: MainRouteHome
            ROUTE_EXPLORE_SHOW -> intent?.getStringExtra(EXTRA_SOURCE_URL)
                ?.takeIf { it.isNotBlank() }
                ?.let { sourceUrl ->
                    MainRouteExploreShow(
                        title = intent.getStringExtra(EXTRA_EXPLORE_NAME),
                        sourceUrl = sourceUrl,
                        exploreUrl = intent.getStringExtra(EXTRA_EXPLORE_URL)
                    )
                } ?: MainRouteHome
            else -> MainRouteHome
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
