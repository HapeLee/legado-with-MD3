package io.legado.app.ui.main

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.DisposableEffect
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
import io.legado.app.ui.book.cache.manage.BookCacheManageRouteScreen
import io.legado.app.ui.book.explore.ExploreShowIntent
import io.legado.app.ui.book.explore.ExploreShowScreen
import io.legado.app.ui.book.explore.ExploreShowViewModel
import io.legado.app.ui.book.import.local.ImportBookScreen
import io.legado.app.ui.book.import.remote.RemoteBookScreen
import io.legado.app.ui.book.info.BookInfoRouteScreen
import io.legado.app.ui.book.info.BookInfoViewModel
import io.legado.app.ui.book.manage.BookshelfManageRouteScreen
import io.legado.app.ui.book.read.ReadBookController
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.book.read.ReadBookRouteScreen
import io.legado.app.ui.book.read.ReadBookViewModel
import io.legado.app.ui.book.readRecord.ReadRecordOverviewScreen
import io.legado.app.ui.book.readRecord.ReadRecordScreen
import io.legado.app.ui.book.search.SearchIntent
import io.legado.app.ui.book.search.SearchScreen
import io.legado.app.ui.book.search.SearchViewModel
import io.legado.app.ui.book.searchContent.SearchContentScreen
import io.legado.app.ui.book.searchContent.SearchContentViewModel
import io.legado.app.ui.book.source.manage.BookSourceActivity
import io.legado.app.ui.config.ConfigNavScreen
import io.legado.app.ui.config.backupConfig.BackupConfigScreen
import io.legado.app.ui.config.coverConfig.CoverConfigScreen
import io.legado.app.ui.config.customTheme.CustomThemeScreen
import io.legado.app.ui.config.downloadCacheConfig.DownloadCacheConfigScreen
import io.legado.app.ui.config.labConfig.LabConfigScreen
import io.legado.app.ui.config.otherConfig.OtherConfigScreen
import io.legado.app.ui.config.readConfig.ReadConfigScreen
import io.legado.app.ui.config.themeConfig.ThemeConfigScreen
import io.legado.app.ui.config.themeManage.ThemeManageScreen
import io.legado.app.ui.config.translation.TranslationConfigScreen
import io.legado.app.ui.rss.article.MainRouteRssSort
import io.legado.app.ui.rss.article.RssSortRouteScreen
import io.legado.app.ui.rss.favorites.RssFavoritesScreen
import io.legado.app.ui.rss.read.MainRouteRssRead
import io.legado.app.ui.rss.read.RssReadRouteScreen
import io.legado.app.ui.rss.subscription.RuleSubScreen
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
        MainScreen(
            useRail = useRail,
            onOpenSettings = {
                onNavigateToRoute(MainRouteSettings)
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
            onNavigateToRssRead = { title, origin, link, openUrl ->
                onNavigateToRoute(
                    MainRouteRssRead(
                        title = title,
                        origin = origin,
                        link = link,
                        openUrl = openUrl
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
            onNavigateToAbout = {
                onNavigateToRoute(MainRouteAbout)
            },
            onNavigateToAiConsole = {
                onNavigateToRoute(MainRouteAiConsole)
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
            onNavigateToDownloadCache = { backStack.add(MainRouteSettingsDownloadCache) },
            onNavigateToTranslation = { backStack.add(MainRouteSettingsTranslation) },
            onNavigateToLab = { backStack.add(MainRouteSettingsLabConfig) }
        )
    }

    entry<MainRouteSettingsOther> {
        OtherConfigScreen(onBackClick = { onNavigateBack() })
    }

    entry<MainRouteSettingsRead> {
        ReadConfigScreen(onBackClick = { onNavigateBack() })
    }

    entry<MainRouteSettingsCover> {
        CoverConfigScreen(onBackClick = { onNavigateBack() })
    }

    entry<MainRouteSettingsTheme> {
        ThemeConfigScreen(
            onBackClick = { onNavigateBack() },
            onNavigateToCustomTheme = { backStack.add(MainRouteSettingsCustomTheme) },
            onNavigateToThemeManage = { backStack.add(MainRouteSettingsThemeManage) }
        )
    }

    entry<MainRouteSettingsBackup> {
        BackupConfigScreen(onBackClick = { onNavigateBack() })
    }

    entry<MainRouteSettingsDownloadCache> {
        DownloadCacheConfigScreen(onBackClick = { onNavigateBack() })
    }

    entry<MainRouteSettingsTranslation> {
        TranslationConfigScreen(onBackClick = { onNavigateBack() })
    }

    entry<MainRouteSettingsLabConfig> {
        LabConfigScreen(onBackClick = { onNavigateBack() })
    }

    entry<MainRouteSettingsCustomTheme> {
        CustomThemeScreen(
            onBackClick = { onNavigateBack() }
        )
    }

    entry<MainRouteSettingsThemeManage> {
        ThemeManageScreen(onBackClick = { onNavigateBack() })
    }

    entry<MainRouteImportLocal> {
        ImportBookScreen(
            onBackClick = { onNavigateBack() }
        )
    }

    entry<MainRouteImportRemote> {
        RemoteBookScreen(
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
            key = route.bookUrl ?: "last-read"
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
            readBookViewModel.initData(readIntent)
            controller.onRouteInitialized()
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                resumeReader()
            }
        }
    }

    entry<MainRouteSearchContent> { route ->
        val viewModel = koinViewModel<SearchContentViewModel>(
            key = route.bookUrl,
            parameters = { parametersOf(route) }
        )
        SearchContentScreen(
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

        SearchScreen(
            viewModel = searchViewModel,
            onBack = {
                searchViewModel.onIntent(SearchIntent.ClearSearchResults)
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
            onBackClick = { onNavigateBack() }
        )
    }

    entry<MainRouteRssFavorites> {
        RssFavoritesScreen(
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
        RuleSubScreen(
            onBackClick = { onNavigateBack() }
        )
    }

    entry<MainRouteReadRecord> {
        ReadRecordScreen(
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
        ReadRecordOverviewScreen(
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
    ) { route ->
        val bookInfoViewModel = koinViewModel<BookInfoViewModel>(key = route.bookUrl)
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

        ExploreShowScreen(
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

    // ========== AI 模块入口 ==========
    entry<MainRouteAiConsole> {
        io.legado.app.ui.ai.AiConsoleScreen(
            onBack = { onNavigateBack() },
            onOpenChat = { onNavigateToRoute(MainRouteAiChat) },
            onOpenImage = { onNavigateToRoute(MainRouteAiImage) },
            onOpenVideo = { onNavigateToRoute(MainRouteAiVideo) },
            onOpenVision = { onNavigateToRoute(MainRouteAiVision) },
            onOpenTextTools = { onNavigateToRoute(MainRouteAiTextTools) },
            onOpenSource = { onNavigateToRoute(MainRouteAiSource) },
            onOpenSourceAdvanced = { onNavigateToRoute(MainRouteAiSourceAdvanced) },
            onOpenShelfAnalyze = { onNavigateToRoute(MainRouteAiBookshelf) },
            onOpenRecommend = { onNavigateToRoute(MainRouteAiRecommend) },
            onOpenArchive = { onNavigateToRoute(MainRouteAiArchive) },
            onOpenContentTools = { onNavigateToRoute(MainRouteAiContentTools) },
            onOpenArt = { onNavigateToRoute(MainRouteAiArt) },
            onOpenSettings = { onNavigateToRoute(MainRouteAiSettings) }
        )
    }

    entry<MainRouteAiChat> {
        io.legado.app.ui.ai.AiChatScreen(
            onBack = { onNavigateBack() }
        )
    }

    entry<MainRouteAiImage> {
        io.legado.app.ui.ai.AiChatScreen(
            onBack = { onNavigateBack() },
            initialPrompt = "请帮我生成一张图片。描述你想要的图片内容、风格，我会使用 AI 为你生成。"
        )
    }

    entry<MainRouteAiVideo> {
        io.legado.app.ui.ai.AiChatScreen(
            onBack = { onNavigateBack() },
            initialPrompt = "请描述你想要的视频内容、风格和时长。我会用 AI 帮你生成视频概念和脚本。"
        )
    }

    entry<MainRouteAiVision> {
        io.legado.app.ui.ai.AiChatScreen(
            onBack = { onNavigateBack() },
            initialPrompt = "请描述你想要分析的图片内容。可以上传图片链接或描述文字内容，我会帮你分析。"
        )
    }

    entry<MainRouteAiTextTools> {
        io.legado.app.ui.ai.AiChatScreen(
            onBack = { onNavigateBack() },
            initialPrompt = "请粘贴你要处理的文本，告诉我需要做什么（润色、翻译、摘要、重写、校对等），我来帮你处理。"
        )
    }

    entry<MainRouteAiContentTools> {
        io.legado.app.ui.ai.AiChatScreen(
            onBack = { onNavigateBack() },
            initialPrompt = "告诉我你需要对书籍内容做什么处理（翻译章节、生成摘要、查找引用、重写段落等），我会调用工具帮你处理。"
        )
    }

    entry<MainRouteAiArt> {
        io.legado.app.ui.ai.AiChatScreen(
            onBack = { onNavigateBack() },
            initialPrompt = "请描述你想要生成的艺术作品（书籍封面、角色头像、场景插画等）的风格和内容，我会用 AI 为你生成。"
        )
    }

    entry<MainRouteAiSource> {
        io.legado.app.ui.ai.AiChatScreen(
            onBack = { onNavigateBack() },
            initialPrompt = "告诉我你想要爬取的书籍网站，我会帮你分析网站结构并生成 Legado 书源规则。请提供网站 URL 和简单描述。"
        )
    }

    entry<MainRouteAiSourceAdvanced> {
        io.legado.app.ui.ai.AiChatScreen(
            onBack = { onNavigateBack() },
            initialPrompt = "提供你现有的书源 URL 或规则描述，我会帮你分析、调试、优化规则，或查找更好的替代书源。"
        )
    }

    entry<MainRouteAiBookshelf> {
        io.legado.app.ui.ai.AiChatScreen(
            onBack = { onNavigateBack() },
            initialPrompt = "我可以分析你的本地书架数据。请问你想了解什么？例如：最近在读什么、各分类统计、阅读偏好、推荐新书等。"
        )
    }

    entry<MainRouteAiRecommend> {
        io.legado.app.ui.ai.AiChatScreen(
            onBack = { onNavigateBack() },
            initialPrompt = "告诉我你喜欢的作家、作品类型或阅读偏好，我会基于你的书架数据为你推荐合适的书籍。"
        )
    }

    entry<MainRouteAiArchive> {
        io.legado.app.ui.ai.AiChatScreen(
            onBack = { onNavigateBack() },
            initialPrompt = "这里可以处理对话历史归档相关的查询。告诉我你想回顾之前的哪些对话或主题。"
        )
    }

    entry<MainRouteAiSettings> {
        io.legado.app.ui.ai.AiChatScreen(
            onBack = { onNavigateBack() },
            initialPrompt = "点击右上角「设置」按钮，配置你的 API Base URL、API Key 和模型名称即可开始使用 AI 功能。支持 OpenAI 兼容协议、本地 Ollama、各种代理转发。"
        )
    }
}
