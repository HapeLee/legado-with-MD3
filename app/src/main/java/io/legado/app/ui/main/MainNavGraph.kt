package io.legado.app.ui.main

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import io.legado.app.help.config.AppConfig
import io.legado.app.model.Download
import io.legado.app.ui.about.AboutEffect
import io.legado.app.ui.about.AboutScreen
import io.legado.app.ui.about.AboutViewModel
import io.legado.app.ui.ai.chat.AiChatRouteScreen
import io.legado.app.ui.book.cache.manage.BookCacheManageRouteScreen
import io.legado.app.ui.book.explore.ExploreShowIntent
import io.legado.app.ui.book.explore.ExploreShowRouteScreen
import io.legado.app.ui.book.explore.ExploreShowViewModel
import io.legado.app.ui.book.import.local.ImportBookRouteScreen
import io.legado.app.ui.book.import.remote.RemoteBookRouteScreen
import io.legado.app.ui.book.info.BookInfoRouteScreen
import io.legado.app.ui.book.info.BookInfoViewModel
import io.legado.app.ui.book.manage.BookshelfManageRouteScreen
import io.legado.app.ui.book.read.ReadBookController
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.book.read.ReadBookRouteScreen
import io.legado.app.ui.book.read.ReadBookViewModel
import io.legado.app.ui.book.readRecord.ReadRecordOverviewRouteScreen
import io.legado.app.ui.book.readRecord.ReadRecordRouteScreen
import io.legado.app.ui.book.search.SearchIntent
import io.legado.app.ui.book.search.SearchRouteScreen
import io.legado.app.ui.book.search.SearchViewModel
import io.legado.app.ui.book.searchContent.SearchContentRouteScreen
import io.legado.app.ui.book.searchContent.SearchContentViewModel
import io.legado.app.ui.book.source.manage.BookSourceActivity
import io.legado.app.ui.config.ConfigNavScreen
import io.legado.app.ui.config.ai.AiConfigRouteScreen
import io.legado.app.ui.config.ai.AiModelEditRouteScreen
import io.legado.app.ui.config.ai.AiProviderEditRouteScreen
import io.legado.app.ui.config.ai.summary.AiSummaryConfigRouteScreen
import io.legado.app.ui.config.backupConfig.BackupConfigRouteScreen
import io.legado.app.ui.config.coverConfig.CoverAlbumManageRouteScreen
import io.legado.app.ui.config.coverConfig.CoverConfigRouteScreen
import io.legado.app.ui.config.customTheme.CustomThemeRouteScreen
import io.legado.app.ui.config.downloadCacheConfig.DownloadCacheConfigRouteScreen
import io.legado.app.ui.config.labConfig.LabConfigRouteScreen
import io.legado.app.ui.config.otherConfig.OtherConfigRouteScreen
import io.legado.app.ui.config.readConfig.ReadConfigRouteScreen
import io.legado.app.ui.config.themeConfig.ThemeConfigRouteScreen
import io.legado.app.ui.config.themeManage.ThemeManageRouteScreen
import io.legado.app.ui.config.translation.TranslationConfigRouteScreen
import io.legado.app.ui.highlightTagRule.HighlightTagRuleScreen
import io.legado.app.ui.rss.article.MainRouteRssSort
import io.legado.app.ui.rss.article.RssSortRouteScreen
import io.legado.app.ui.rss.favorites.RssFavoritesRouteScreen
import io.legado.app.ui.rss.read.MainRouteRssRead
import io.legado.app.ui.rss.read.RssReadRouteScreen
import io.legado.app.ui.rss.subscription.RuleSubRouteScreen
import io.legado.app.utils.openUrl
import io.legado.app.utils.startActivity
import io.legado.app.utils.startActivityForBook
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.toggleSystemBar
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalSharedTransitionApi::class)
fun MainActivity.mainEntryProvider(
    backStack: MutableList<NavKey>,
    useRail: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    onNavigateToRoute: (NavKey) -> Unit,
    onNavigateBack: () -> Unit,
    onRegisterVariableSetter: (((String, String?) -> Unit)?) -> Unit
) = entryProvider {
    entry<MainRouteHome> {
        val mainViewModel = koinViewModel<MainViewModel>()
        val mainUiState by mainViewModel.uiState.collectAsStateWithLifecycle()
        MainScreen(
            mainUiState = mainUiState,
            onIntent = mainViewModel::onIntent,
            effects = mainViewModel.effects,
            useRail = useRail,
            onOpenSettings = {
                onNavigateToRoute(MainRouteSettings)
            },
            onNavigateToChat = {
                onNavigateToRoute(MainRouteAiChat)
            },
            onNavigateToSearch = { key ->
                onNavigateToRoute(
                    MainRouteSearch(
                        key = key?.trim()?.takeIf { it.isNotEmpty() }
                    )
                )
            },
            onNavigateToRemoteImport = {
                onNavigateToRoute(MainRouteImportRemote)
            },
            onNavigateToLocalImport = {
                onNavigateToRoute(MainRouteImportLocal)
            },
            onNavigateToCache = { groupId ->
                onNavigateToRoute(MainRouteCache(groupId))
            },
            onNavigateToBookCacheManage = {
                onNavigateToRoute(MainRouteBookCacheManage)
            },
            onNavigateToBackupSettings = {
                onNavigateToRoute(MainRouteSettingsBackup)
            },
            onNavigateToBookInfo = { name, author, bookUrl, origin, coverPath, sharedCoverKey ->
                onNavigateToRoute(
                    MainRouteBookInfo(
                        name = name,
                        author = author,
                        bookUrl = bookUrl,
                        origin = origin,
                        coverPath = coverPath,
                        sharedCoverKey = sharedCoverKey
                    )
                )
            },
            onNavigateToExploreShow = { title, sourceUrl, exploreUrl ->
                onNavigateToRoute(
                    MainRouteExploreShow(
                        title = title,
                        sourceUrl = sourceUrl,
                        exploreUrl = exploreUrl
                    )
                )
            },
            onNavigateToRssSort = { sourceUrl, sortUrl, key ->
                onNavigateToRoute(
                    MainRouteRssSort(
                        sourceUrl = sourceUrl,
                        sortUrl = sortUrl,
                        key = key
                    )
                )
            },
            onNavigateToRssRead = { title, origin, link, openUrl, startPage ->
                onNavigateToRoute(
                    MainRouteRssRead(
                        title = title,
                        origin = origin,
                        link = link,
                        openUrl = openUrl,
                        startPage = startPage
                    )
                )
            },
            onNavigateToRssFavorites = {
                onNavigateToRoute(MainRouteRssFavorites)
            },
            onNavigateToRuleSub = {
                onNavigateToRoute(MainRouteRuleSub)
            },
            onNavigateToReadRecord = {
                onNavigateToRoute(MainRouteReadRecord)
            },
            onNavigateToReadRecordOverview = {
                onNavigateToRoute(MainRouteReadRecordOverview)
            },
            onNavigateToHighlightTagRule = {
                onNavigateToRoute(MainRouteHighlightTagRule)
            },
            onNavigateToAbout = {
                onNavigateToRoute(MainRouteAbout)
            },
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = LocalNavAnimatedContentScope.current,
        )
    }

    entry<MainRouteSettings> {
        ConfigNavScreen(
            onBackClick = { onNavigateBack() },
            onNavigateToOther = { backStack.add(MainRouteSettingsOther) },
            onNavigateToRead = { backStack.add(MainRouteSettingsRead) },
            onNavigateToCover = { backStack.add(MainRouteSettingsCover) },
            onNavigateToTheme = { backStack.add(MainRouteSettingsTheme) },
            onNavigateToBackup = { backStack.add(MainRouteSettingsBackup) },
            onNavigateToAi = { backStack.add(MainRouteSettingsAi) },
            onNavigateToDownloadCache = { backStack.add(MainRouteSettingsDownloadCache) },
            onNavigateToTranslation = { backStack.add(MainRouteSettingsTranslation) },
            onNavigateToLab = { backStack.add(MainRouteSettingsLabConfig) }
        )
    }

    entry<MainRouteSettingsOther> {
        OtherConfigRouteScreen(onBackClick = { onNavigateBack() })
    }

    entry<MainRouteSettingsRead> {
        ReadConfigRouteScreen(onBackClick = { onNavigateBack() })
    }

    entry<MainRouteSettingsCover> {
        CoverConfigRouteScreen(
            onBackClick = { onNavigateBack() },
            onNavigateToCoverAlbums = {
                backStack.add(MainRouteSettingsCoverAlbums)
            },
        )
    }

    entry<MainRouteSettingsCoverAlbums> {
        CoverAlbumManageRouteScreen(onBackClick = { onNavigateBack() })
    }

    entry<MainRouteSettingsTheme> {
        ThemeConfigRouteScreen(
            onBackClick = { onNavigateBack() },
            onNavigateToCustomTheme = { backStack.add(MainRouteSettingsCustomTheme) },
            onNavigateToThemeManage = { backStack.add(MainRouteSettingsThemeManage) }
        )
    }

    entry<MainRouteSettingsBackup> {
        BackupConfigRouteScreen(onBackClick = { onNavigateBack() })
    }

    entry<MainRouteSettingsAi> {
        AiConfigRouteScreen(
            onBackClick = { onNavigateBack() },
            onNavigateToProviderEdit = { providerId ->
                backStack.add(MainRouteSettingsAiProviderEdit(providerId = providerId))
            },
            onNavigateToModelEdit = { providerId, modelProfileId ->
                backStack.add(
                    MainRouteSettingsAiModelEdit(
                        providerId = providerId,
                        modelProfileId = modelProfileId
                    )
                )
            },
            onNavigateToTranslation = { backStack.add(MainRouteSettingsTranslation) },
            onNavigateToAiSummary = { backStack.add(MainRouteSettingsAiSummary) }
        )
    }

    entry<MainRouteSettingsAiSummary> {
        AiSummaryConfigRouteScreen(onBackClick = { onNavigateBack() })
    }

    entry<MainRouteSettingsAiProviderEdit> { route ->
        AiProviderEditRouteScreen(
            providerId = route.providerId,
            onBackClick = { onNavigateBack() }
        )
    }

    entry<MainRouteAiChat> {
        AiChatRouteScreen(
            onBackClick = { onNavigateBack() },
            onOpenBookInfo = { book ->
                onNavigateToRoute(
                    MainRouteBookInfo(
                        name = book.name,
                        author = book.author,
                        bookUrl = book.bookUrl,
                        origin = book.origin,
                        coverPath = book.coverPath
                    )
                )
            }
        )
    }

    entry<MainRouteSettingsAiModelEdit> { route ->
        AiModelEditRouteScreen(
            providerId = route.providerId,
            modelProfileId = route.modelProfileId,
            onBackClick = { onNavigateBack() }
        )
    }

    entry<MainRouteSettingsDownloadCache> {
        DownloadCacheConfigRouteScreen(onBackClick = { onNavigateBack() })
    }

    entry<MainRouteSettingsTranslation> {
        TranslationConfigRouteScreen(
            onBackClick = { onNavigateBack() },
            onNavigateToAi = { backStack.add(MainRouteSettingsAi) }
        )
    }

    entry<MainRouteSettingsLabConfig> {
        LabConfigRouteScreen(onBackClick = { onNavigateBack() })
    }

    entry<MainRouteSettingsCustomTheme> {
        CustomThemeRouteScreen(
            onBackClick = { onNavigateBack() }
        )
    }

    entry<MainRouteSettingsThemeManage> {
        ThemeManageRouteScreen(onBackClick = { onNavigateBack() })
    }

    entry<MainRouteImportLocal> {
        ImportBookRouteScreen(
            onBackClick = { onNavigateBack() }
        )
    }

    entry<MainRouteImportRemote> {
        RemoteBookRouteScreen(
            onBackClick = { onNavigateBack() }
        )
    }

    entry<MainRouteCache> { route ->
        BookshelfManageRouteScreen(
            groupId = route.groupId,
            onBackClick = { onNavigateBack() },
            onOpenBookInfo = { name, author, bookUrl ->
                onNavigateToRoute(
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
            onBackClick = { onNavigateBack() }
        )
    }

    entry<MainRouteReadBook> { route ->
        val readBookViewModel = koinViewModel<ReadBookViewModel>(
            key = "ReadBook:${route.bookUrl ?: "last-read"}"
        )
        val controller = remember(readBookViewModel) {
            ReadBookController(this@mainEntryProvider, readBookViewModel)
        }
        val lifecycleOwner = LocalLifecycleOwner.current
        val readIntent = remember(route) {
            MainActivity.createReadBookIntent(
                context = this@mainEntryProvider,
                bookUrl = route.bookUrl,
                readAloud = route.readAloud,
                inBookshelf = route.inBookshelf,
                chapterChanged = route.chapterChanged,
            )
        }
        val effectsReady = remember(readBookViewModel) { CompletableDeferred<Unit>() }
        val readerResumeState = remember(controller, lifecycleOwner) { booleanArrayOf(false) }
        val collectorReady = remember(readBookViewModel) { booleanArrayOf(false) }
        fun resumeReader() {
            if (readerResumeState[0]) return
            readerResumeState[0] = true
            controller.onResume()
            readBookViewModel.onIntent(ReadBookIntent.OnResume)
        }

        fun pauseReader() {
            if (!readerResumeState[0]) return
            readerResumeState[0] = false
            controller.onPause()
            readBookViewModel.onIntent(ReadBookIntent.OnPause)
        }

        ReadBookRouteScreen(
            viewModel = readBookViewModel,
            host = controller,
            controller = controller,
            onEffectsReady = { effectsReady.complete(Unit) },
            onOpenSearch = { word, bookUrl ->
                onNavigateToRoute(
                    MainRouteSearchContent(
                        bookUrl = bookUrl,
                        searchWord = word,
                        searchResultIndex = readBookViewModel.uiState.value.searchResultIndex
                    )
                )
            },
        )

        DisposableEffect(controller, lifecycleOwner, route.readAloud) {
            activeReadBookInputHandler = controller
            activeReadBookRoute = route
            MainActivity.hasActiveReadBookRoute = true
            controller.onClose = { onNavigateBack() }
            controller.onStartContentLoadFinish = {
                if (route.readAloud) {
                    io.legado.app.model.ReadBook.readAloud()
                }
            }

            val lifecycleObserver = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        if (collectorReady[0]) resumeReader()
                    }
                    Lifecycle.Event.ON_PAUSE -> pauseReader()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
            onDispose {
                pauseReader()
                readBookViewModel.onIntent(ReadBookIntent.OnDispose)
                lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
                if (activeReadBookInputHandler === controller) {
                    activeReadBookInputHandler = null
                }
                if (activeReadBookRoute == route) {
                    activeReadBookRoute = null
                }
                MainActivity.hasActiveReadBookRoute = false
                controller.clearTts()
                this@mainEntryProvider.toggleSystemBar(AppConfig.showStatusBar)
            }
        }

        LaunchedEffect(route, readBookViewModel, lifecycleOwner) {
            effectsReady.await()
            collectorReady[0] = true
            readBookViewModel.initReadBookConfig(readIntent)
            readBookViewModel.initData(readIntent) {
                readBookViewModel.markJustInitData()
                controller.onRouteInitialized()
                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    resumeReader()
                }
            }
        }
    }

    entry<MainRouteSearchContent> { route ->
        val viewModel = koinViewModel<SearchContentViewModel>(
            key = "SearchContent:${route.bookUrl}",
            parameters = { parametersOf(route) }
        )
        SearchContentRouteScreen(
            viewModel = viewModel,
            onBack = { onNavigateBack() },
        )
    }

    entry<MainRouteSearch> { route ->
        val searchViewModel = koinViewModel<SearchViewModel>()

        LaunchedEffect(route.key, route.scopeRaw, searchViewModel) {
            searchViewModel.onIntent(
                SearchIntent.Initialize(
                    key = route.key,
                    scopeRaw = route.scopeRaw
                )
            )
        }

        SearchRouteScreen(
            viewModel = searchViewModel,
            onBack = {
                onNavigateBack()
            },
            onOpenBookInfo = { name, author, bookUrl, origin, coverPath, sharedCoverKey ->
                onNavigateToRoute(
                    MainRouteBookInfo(
                        name = name,
                        author = author,
                        bookUrl = bookUrl,
                        origin = origin,
                        coverPath = coverPath,
                        sharedCoverKey = sharedCoverKey
                    )
                )
            },
            onOpenSourceManage = {
                this@mainEntryProvider.startActivity<BookSourceActivity>()
            },
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = LocalNavAnimatedContentScope.current,
        )
    }

    entry<MainRouteRssSort> { route ->
        RssSortRouteScreen(
            sourceUrl = route.sourceUrl,
            initialSortUrl = route.sortUrl,
            initialSearchKey = route.key,
            onBackClick = { onNavigateBack() },
            onSearch = { key ->
                onNavigateToRoute(
                    MainRouteRssSort(
                        sourceUrl = route.sourceUrl,
                        key = key
                    )
                )
            },
            onOpenRead = { title, origin, link, openUrl ->
                if (link?.contains("@js:") == true) {
                    onNavigateToRoute(
                        MainRouteRssSort(
                            sourceUrl = origin,
                            sortUrl = link
                        )
                    )
                } else {
                    onNavigateToRoute(
                        MainRouteRssRead(
                            title = title,
                            origin = origin,
                            link = link,
                            openUrl = openUrl
                        )
                    )
                }
            }
        )
    }

    entry<MainRouteRssRead> { route ->
        RssReadRouteScreen(
            title = route.title,
            origin = route.origin,
            link = route.link,
            openUrl = route.openUrl,
            startPage = route.startPage,
            onBackClick = { onNavigateBack() },
            onOpenArticles = { sortUrl ->
                onNavigateToRoute(
                    MainRouteRssSort(
                        sourceUrl = route.origin,
                        sortUrl = sortUrl
                    )
                )
            }
        )
    }

    entry<MainRouteRssFavorites> {
        RssFavoritesRouteScreen(
            onBackClick = { onNavigateBack() },
            onOpenRead = { title, origin, link, openUrl ->
                onNavigateToRoute(
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

    entry<MainRouteRuleSub> {
        RuleSubRouteScreen(
            onBackClick = { onNavigateBack() }
        )
    }

    entry<MainRouteReadRecord> {
        ReadRecordRouteScreen(
            onBackClick = { onNavigateBack() },
            onBookClick = { name, author ->
                lifecycleScope.launch {
                    val book = withContext(IO) {
                        io.legado.app.data.appDb.bookDao.getBook(name, author)
                    }
                    if (book != null) this@mainEntryProvider.startActivityForBook(book)
                    else {
                        onNavigateToRoute(MainRouteSearch(key = name))
                    }
                }
            },
            onSummaryClick = {
                onNavigateToRoute(MainRouteReadRecordOverview)
            }
        )
    }

    entry<MainRouteReadRecordOverview> {
        ReadRecordOverviewRouteScreen(
            onBackClick = { onNavigateBack() },
            onBookClick = { name, author ->
                lifecycleScope.launch {
                    val book = withContext(IO) {
                        io.legado.app.data.appDb.bookDao.getBook(name, author)
                    }
                    if (book != null) this@mainEntryProvider.startActivityForBook(book)
                    else {
                        onNavigateToRoute(MainRouteSearch(key = name))
                    }
                }
            }
        )
    }

    entry<MainRouteBookInfo>(
        metadata = NavDisplay.transitionSpec {
            val from = initialState.key
            val fromStr = from.toString()
            if (from is MainRouteHome || from is MainRouteExploreShow || from is MainRouteSearch ||
                fromStr.startsWith("MainRouteHome") || fromStr.startsWith("MainRouteExploreShow") || fromStr.startsWith(
                    "MainRouteSearch"
                )
            ) {
                fadeIn(animationSpec = tween(300)) togetherWith
                        fadeOut(animationSpec = tween(300))
            } else null
        } + NavDisplay.popTransitionSpec {
            val to = targetState.key
            val toStr = to.toString()
            if (to is MainRouteHome || to is MainRouteExploreShow || to is MainRouteSearch ||
                toStr.startsWith("MainRouteHome") || toStr.startsWith("MainRouteExploreShow") || toStr.startsWith(
                    "MainRouteSearch"
                )
            ) {
                fadeIn(animationSpec = tween(300)) togetherWith
                        fadeOut(animationSpec = tween(300))
            } else null
        } + NavDisplay.predictivePopTransitionSpec { _ ->
            if (!AppConfig.isPredictiveBackEnabled) {
                null
            } else {
                val to = targetState.key
                val toStr = to.toString()
                if (to is MainRouteHome || to is MainRouteExploreShow || to is MainRouteSearch ||
                    toStr.startsWith("MainRouteHome") || toStr.startsWith("MainRouteExploreShow") || toStr.startsWith(
                        "MainRouteSearch"
                    )
                ) {
                    fadeIn(animationSpec = tween(300)) togetherWith
                            fadeOut(animationSpec = tween(300))
                } else null
            }
        }
    ) { route ->
        val bookInfoViewModel = koinViewModel<BookInfoViewModel>(key = "BookInfo:${route.bookUrl}")
        BookInfoRouteScreen(
            bookUrl = route.bookUrl,
            name = route.name,
            author = route.author,
            origin = route.origin,
            coverPath = route.coverPath,
            viewModel = bookInfoViewModel,
            onBack = { onNavigateBack() },
            onFinish = { _, _ -> onNavigateBack() },
            onOpenSearch = { keyword ->
                onNavigateToRoute(MainRouteSearch(key = keyword))
            },
            onOpenReader = { bookUrl, inBookshelf, chapterChanged ->
                onNavigateToRoute(
                    MainRouteReadBook(
                        bookUrl = bookUrl,
                        inBookshelf = inBookshelf,
                        chapterChanged = chapterChanged,
                    )
                )
            },
            onNavigateToBookInfo = { name, author, bookUrl, origin, coverPath ->
                onNavigateToRoute(MainRouteBookInfo(name, author, bookUrl, origin, coverPath))
            },
            onNavigateToExploreShow = { title, sourceUrl, exploreUrl ->
                onNavigateToRoute(MainRouteExploreShow(title, sourceUrl, exploreUrl))
            },
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = LocalNavAnimatedContentScope.current,
            sharedCoverKey = route.sharedCoverKey ?: bookCoverSharedElementKey(route.bookUrl),
            onRegisterVariableSetter = { setter ->
                onRegisterVariableSetter(setter)
            }
        )
    }

    entry<MainRouteExploreShow> { route ->
        val exploreViewModel = koinViewModel<ExploreShowViewModel>()

        LaunchedEffect(route.sourceUrl, route.exploreUrl, exploreViewModel) {
            exploreViewModel.onIntent(
                ExploreShowIntent.InitData(route.sourceUrl, route.exploreUrl)
            )
        }

        ExploreShowRouteScreen(
            viewModel = exploreViewModel,
            title = route.title ?: "探索",
            onBack = { onNavigateBack() },
            onBookClick = { book, sharedCoverKey ->
                onNavigateToRoute(
                    MainRouteBookInfo(
                        name = book.name,
                        author = book.author,
                        bookUrl = book.bookUrl,
                        origin = book.origin,
                        coverPath = book.coverUrl,
                        sharedCoverKey = sharedCoverKey
                    )
                )
            },
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = LocalNavAnimatedContentScope.current,
        )
    }

    entry<MainRouteHighlightTagRule> {
        HighlightTagRuleScreen(
            onBackClick = { onNavigateBack() }
        )
    }

    entry<MainRouteAbout> {
        val viewModel = koinViewModel<AboutViewModel>()
        val context = LocalContext.current
        LaunchedEffect(viewModel) {
            viewModel.effects.collectLatest { effect ->
                when (effect) {
                    is AboutEffect.OpenUrl -> context.openUrl(effect.url)
                    is AboutEffect.ShowToast -> context.toastOnUi(effect.message)
                    is AboutEffect.StartDownload -> Download.start(
                        context,
                        effect.url,
                        effect.fileName
                    )
                }
            }
        }
        AboutScreen(
            state = viewModel.uiState.collectAsStateWithLifecycle().value,
            onIntent = viewModel::onIntent,
            onBack = { onNavigateBack() },
        )
    }
}
