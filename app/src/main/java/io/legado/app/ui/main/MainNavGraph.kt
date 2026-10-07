package io.legado.app.ui.main

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.metadata
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import io.legado.app.R
import io.legado.app.constant.BookType
import io.legado.app.core.ui.morph.BookCoverMorphAnchors
import io.legado.app.core.ui.morph.isAnchorVisibleInScreen
import io.legado.app.core.ui.player.PlayerMorphAppearance
import io.legado.app.core.ui.player.PlayerMorphHost
import io.legado.app.domain.model.BookSearchScope
import io.legado.app.domain.model.PlaybackCapsuleSource
import io.legado.app.domain.model.PlaybackCapsuleState
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.feature.reader.platform.ReaderPerfTrace
import io.legado.app.model.AudioPlay
import io.legado.app.model.Download
import io.legado.app.model.ReadAloudSessionStore
import io.legado.app.model.SourceCallBack
import io.legado.app.service.AudioPlayService
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.about.AboutEffect
import io.legado.app.ui.about.AboutScreen
import io.legado.app.ui.about.AboutViewModel
import io.legado.app.ui.ai.chat.AiChatRouteScreen
import io.legado.app.ui.book.audio.AudioPlayEffect
import io.legado.app.ui.book.audio.AudioPlayIntent
import io.legado.app.ui.book.audio.AudioPlayScreenContent
import io.legado.app.ui.book.audio.AudioPlayViewModel
import io.legado.app.ui.book.cache.manage.BookCacheManageRouteScreen
import io.legado.app.ui.book.explore.ExploreShowIntent
import io.legado.app.ui.book.explore.ExploreShowRouteScreen
import io.legado.app.ui.book.explore.ExploreShowViewModel
import io.legado.app.ui.book.import.local.ImportBookRouteScreen
import io.legado.app.ui.book.import.remote.RemoteBookRouteScreen
import io.legado.app.ui.book.info.BookInfoRouteScreen
import io.legado.app.ui.book.info.BookInfoViewModel
import io.legado.app.ui.book.knowledge.BookCharacterDetailScreen
import io.legado.app.ui.book.knowledge.BookCharacterDetailViewModel
import io.legado.app.ui.book.knowledge.BookCharacterListScreen
import io.legado.app.ui.book.knowledge.BookCharacterListViewModel
import io.legado.app.ui.book.knowledge.BookCharacterNetworkScreen
import io.legado.app.ui.book.knowledge.BookCharacterNetworkViewModel
import io.legado.app.ui.book.knowledge.BookEventDetailScreen
import io.legado.app.ui.book.knowledge.BookEventDetailViewModel
import io.legado.app.ui.book.knowledge.BookEventListScreen
import io.legado.app.ui.book.knowledge.BookEventListViewModel
import io.legado.app.ui.book.knowledge.BookKnowledgeDetailScreen
import io.legado.app.ui.book.knowledge.BookKnowledgeDetailViewModel
import io.legado.app.ui.book.knowledge.BookKnowledgeListScreen
import io.legado.app.ui.book.knowledge.BookKnowledgeListViewModel
import io.legado.app.ui.book.knowledge.CharacterAvatarCropDialog
import io.legado.app.ui.book.knowledge.CharacterAvatarSourceSheet
import io.legado.app.ui.book.knowledge.CharacterDetailIntent
import io.legado.app.ui.book.knowledge.deleteCharacterAvatar
import io.legado.app.ui.book.knowledge.saveCharacterAvatar
import io.legado.app.ui.book.manage.BookshelfManageRouteScreen
import io.legado.app.ui.book.manga.MangaReaderRouteScreen
import io.legado.app.ui.book.manga.MangaReaderViewModel
import io.legado.app.ui.book.read.ReadBookController
import io.legado.app.ui.book.read.ReadBookInitRequest
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.book.read.ReadBookRouteScreen
import io.legado.app.ui.book.read.ReadBookViewModel
import io.legado.app.ui.book.read.ReaderSessionViewModel
import io.legado.app.ui.book.readRecord.ReadRecordOverviewRouteScreen
import io.legado.app.ui.book.readRecord.ReadRecordRouteScreen
import io.legado.app.ui.book.readaloud.ReadAloudPlayerOverlayBus
import io.legado.app.ui.book.readaloud.cache.TtsCacheRouteScreen
import io.legado.app.ui.book.readaloud.casting.BookVoiceCastingScreen
import io.legado.app.ui.book.readaloud.casting.BookVoiceCastingViewModel
import io.legado.app.ui.book.readaloud.cloudtts.CloudTtsEffect
import io.legado.app.ui.book.readaloud.cloudtts.CloudTtsIntent
import io.legado.app.ui.book.readaloud.cloudtts.CloudTtsScreen
import io.legado.app.ui.book.readaloud.cloudtts.CloudTtsViewModel
import io.legado.app.ui.book.readaloud.morph.ReadAloudMorphState
import io.legado.app.ui.book.readaloud.player.rememberPlayerThemeOverride
import io.legado.app.ui.book.search.SearchIntent
import io.legado.app.ui.book.search.SearchRouteScreen
import io.legado.app.ui.book.search.SearchViewModel
import io.legado.app.ui.book.searchContent.SearchContentRouteScreen
import io.legado.app.ui.book.searchContent.SearchContentViewModel
import io.legado.app.ui.book.source.debug.BookSourceDebugRoute
import io.legado.app.ui.book.source.debug.BookSourceDebugViewModel
import io.legado.app.ui.book.source.edit.BookSourceEditRoute
import io.legado.app.ui.book.source.edit.BookSourceEditViewModel
import io.legado.app.ui.book.source.manage.BookSourceRouteScreen
import io.legado.app.ui.browser.WebViewModel
import io.legado.app.ui.browser.WebViewRouteScreen
import io.legado.app.ui.config.ConfigNavScreen
import io.legado.app.ui.config.ai.AiConfigRouteScreen
import io.legado.app.ui.config.ai.AiModelEditRouteScreen
import io.legado.app.ui.config.ai.AiProviderEditRouteScreen
import io.legado.app.ui.config.ai.prompt.AiPromptConfigRouteScreen
import io.legado.app.ui.config.ai.summary.AiSummaryConfigRouteScreen
import io.legado.app.ui.config.backupConfig.BackupConfigRouteScreen
import io.legado.app.ui.config.coverConfig.CoverAlbumManageRouteScreen
import io.legado.app.ui.config.coverConfig.CoverConfigRouteScreen
import io.legado.app.ui.config.customTheme.CustomThemeRouteScreen
import io.legado.app.ui.config.downloadCacheConfig.DownloadCacheConfigRouteScreen
import io.legado.app.ui.config.labConfig.LabConfigRouteScreen
import io.legado.app.ui.config.otherConfig.OtherConfigRouteScreen
import io.legado.app.ui.config.privateConfig.PrivateConfigRouteScreen
import io.legado.app.ui.config.readConfig.ReadConfigRouteScreen
import io.legado.app.ui.config.themeConfig.ThemeConfigRouteScreen
import io.legado.app.ui.config.themeManage.ThemeManageRouteScreen
import io.legado.app.ui.config.translation.TranslationConfigRouteScreen
import io.legado.app.ui.highlightTagRule.HighlightTagRuleRouteScreen
import io.legado.app.ui.login.SourceLoginIntent
import io.legado.app.ui.login.SourceLoginRoute
import io.legado.app.ui.login.SourceLoginType
import io.legado.app.ui.login.SourceLoginViewModel
import io.legado.app.ui.rss.article.MainRouteRssSort
import io.legado.app.ui.rss.article.RssSortRouteScreen
import io.legado.app.ui.rss.favorites.RssFavoritesRouteScreen
import io.legado.app.ui.rss.read.MainRouteRssRead
import io.legado.app.ui.rss.read.RssReadRouteScreen
import io.legado.app.ui.rss.source.debug.RssSourceDebugRoute
import io.legado.app.ui.rss.source.debug.RssSourceDebugViewModel
import io.legado.app.ui.rss.source.edit.RssSourceEditRoute
import io.legado.app.ui.rss.source.edit.RssSourceEditViewModel
import io.legado.app.ui.rss.source.manage.RssSourceRouteScreen
import io.legado.app.ui.rss.subscription.RuleSubRouteScreen
import io.legado.app.ui.widget.components.changeSource.ChangeSourceSheet
import io.legado.app.ui.widget.components.privacy.PrivateReadGate
import io.legado.app.ui.widget.components.privacy.PrivateVerifyGate
import io.legado.app.utils.openUrl
import io.legado.app.utils.sendToClip
import io.legado.app.utils.startActivityForBook
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.toggleSystemBar
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import io.legado.app.ui.book.readaloud.cast.BgmPoolRouteScreen
import io.legado.app.ui.book.readaloud.cast.CastCapsuleStyleRouteScreen
import io.legado.app.ui.book.readaloud.cast.MultiRoleRecognitionRouteScreen
import io.legado.app.ui.book.readaloud.cast.MultiRoleRuleRouteScreen
import io.legado.app.ui.book.readaloud.cast.RegexCastRuleRouteScreen
import io.legado.app.ui.book.readaloud.cast.VoiceEffectRouteScreen
import io.legado.app.ui.book.readaloud.cast.VoicePoolRouteScreen

/**
 * WebView 类页面（内置浏览器、订阅阅读）只做位移转场。
 *
 * WebView 是 AndroidView interop view：所在子树一旦被加上 graphicsLayer（fade 的 alpha、
 * scaleOut 的缩放），Compose 会把网页一并画进离屏 RenderNode
 * （`AndroidViewHolder.draw` → `AndroidComposeView.drawAndroidView`），Chromium 在这条绘制路径上
 * 不稳定，部分设备会表现为网页闪烁。`slideIntoContainer` / `slideOutOfContainer` 只改 layout
 * offset、不产生图层，所以这里保留默认的位移与时长，去掉 fade 与 scale。
 */
private fun webViewEntryMetadata(predictiveBackEnabled: Boolean) = metadata {
    put(NavDisplay.TransitionKey) {
        slideIntoContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Start,
            animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
            initialOffset = { fullWidth -> fullWidth }
        ) togetherWith slideOutOfContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Start,
            animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
            targetOffset = { fullWidth -> fullWidth / 4 }
        )
    }
    put(NavDisplay.PopTransitionKey) {
        slideIntoContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Start,
            animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
            initialOffset = { fullWidth -> -fullWidth / 4 }
        ) togetherWith slideOutOfContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Start,
            animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
            targetOffset = { fullWidth -> fullWidth }
        )
    }
    if (predictiveBackEnabled) {
        put(NavDisplay.PredictivePopTransitionKey) { _ ->
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(easing = FastOutSlowInEasing),
                initialOffset = { fullWidth -> -fullWidth / 4 }
            ) togetherWith slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(easing = FastOutSlowInEasing),
                targetOffset = { fullWidth -> fullWidth }
            )
        }
    }
}

/** Full-screen book destinations use modal overlay scene strategy and zero nav display transitions; animations are managed by BookMorphHost. */
private fun readerEntryMetadata(predictiveBackEnabled: Boolean) =
    ModalOverlaySceneStrategy.modalOverlay() + metadata {
        put(NavDisplay.TransitionKey) { EnterTransition.None togetherWith ExitTransition.None }
        put(NavDisplay.PopTransitionKey) { EnterTransition.None togetherWith ExitTransition.None }
        put(NavDisplay.PredictivePopTransitionKey) { _ ->
            EnterTransition.None togetherWith ExitTransition.None
        }
    }

/** Keep parent overlays composed while NavDisplay leaves animation to the search scene or player host. */
private fun modalOverlayEntryMetadata(): Map<String, Any> =
    ModalOverlaySceneStrategy.modalOverlay() + metadata {
        put(NavDisplay.TransitionKey) { EnterTransition.None togetherWith ExitTransition.None }
        put(NavDisplay.PopTransitionKey) { EnterTransition.None togetherWith ExitTransition.None }
        put(NavDisplay.PredictivePopTransitionKey) { _ ->
            EnterTransition.None togetherWith ExitTransition.None
        }
    }

/**
 * 听书播放弹层「经典控制」的目标判定：栈顶是阅读界面时把请求交给它，
 * 否则新开阅读界面。抽成纯函数以便单测覆盖这条分支。
 *
 * 播放弹层不在导航栈上，所以判据是「当前顶层」而不是「上一站」。
 */
internal fun isReaderOnTop(backStack: List<NavKey>): Boolean =
    backStack.lastOrNull() is MainRouteReadBook

@OptIn(ExperimentalSharedTransitionApi::class)
fun MainActivity.mainEntryProvider(
    backStack: MutableList<NavKey>,
    configuration: AppUiConfiguration,
    showMangaUi: Boolean,
    useRail: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    onNavigateToRoute: (NavKey) -> Unit,
    onNavigateBack: () -> Unit,
    /**
     * 「胶囊 ↔ 听书播放页」的形变状态。
     *
     * 主界面要把听书胶囊排在悬浮底栏同一行里，需要把胶囊的锚点上报给同一个进度源，
     * 所以这里透传（其余目的地不用）。
     */
    readAloudMorph: ReadAloudMorphState?,
    homePlaybackCapsuleEnabled: Boolean,
    capsuleAnchorPreview: io.legado.app.domain.model.PlaybackCapsuleState?,
) = entryProvider {
    entry<MainRouteWebView>(
        metadata = webViewEntryMetadata(configuration.appShell.predictiveBackEnabled)
    ) { route ->
        val viewModel = koinViewModel<WebViewModel>(
            key = "WebView:${route.url}:${route.sourceOrigin}:${route.sourceVerificationEnable}",
        )
        val browserIntent = remember(route) {
            Intent().apply {
                putExtra("title", route.title)
                putExtra("url", route.url)
                putExtra("sourceOrigin", route.sourceOrigin)
                putExtra("sourceName", route.sourceName)
                route.sourceType?.let { putExtra("sourceType", it) }
                putExtra("sourceVerificationEnable", route.sourceVerificationEnable)
                putExtra("refetchAfterSuccess", route.refetchAfterSuccess)
                putExtra("html", route.html)
            }
        }
        WebViewRouteScreen(
            intent = browserIntent,
            viewModel = viewModel,
            onFinish = onNavigateBack,
            onImportBookSource = { importUrl ->
                onNavigateToRoute(MainRouteBookSourceManage(importUrl))
            },
        )
    }
    entry<MainRouteSourceLogin>(
        metadata = ModalOverlaySceneStrategy.modalOverlay(),
    ) { route ->
        DisposableEffect(route) {
            MainActivity.hasActiveSourceLoginRoute = true
            onDispose {
                MainActivity.hasActiveSourceLoginRoute = false
            }
        }
        val viewModel = koinViewModel<SourceLoginViewModel>(
            key = "SourceLogin:${route.type}:${route.sourceKey}:${route.bookUrl}",
        )
        SourceLoginRoute(
            request = SourceLoginIntent.Initialize(route.type, route.sourceKey, route.bookUrl),
            viewModel = viewModel,
            host = this@mainEntryProvider,
            onBack = onNavigateBack,
        )
    }
    entry<MainRouteBookSourceManage> { route ->
        BookSourceRouteScreen(
            initialImportUrl = route.importUrl,
            closeAfterImport = route.importUrl != null,
            onImportClosed = onNavigateBack,
            onBackClick = onNavigateBack,
            onAddSource = { onNavigateToRoute(MainRouteBookSourceEdit()) },
            onEditSource = { onNavigateToRoute(MainRouteBookSourceEdit(it)) },
            onLoginSource = {
                onNavigateToRoute(MainRouteSourceLogin(SourceLoginType.BookSource, it))
            },
            onSearchSource = { sourceName, sourceUrl ->
                val scopeRaw = BookSearchScope.encodeSource(sourceName, sourceUrl)
                onNavigateToRoute(MainRouteSearch(null, scopeRaw))
            },
            onDebugSource = { sourceUrl ->
                onNavigateToRoute(MainRouteBookSourceDebug(sourceUrl))
            },
        )
    }
    entry<MainRouteBookSourceEdit> { route ->
        val viewModel = koinViewModel<BookSourceEditViewModel>(
            key = "BookSourceEdit:${route.sourceUrl.orEmpty()}",
        )
        BookSourceEditRoute(
            sourceUrl = route.sourceUrl,
            viewModel = viewModel,
            onBack = { savedSourceUrl ->
                if (backStack.size == 1) {
                    savedSourceUrl?.let {
                        this@mainEntryProvider.setResult(
                            android.app.Activity.RESULT_OK,
                            Intent().putExtra("origin", it),
                        )
                    }
                    this@mainEntryProvider.finish()
                } else onNavigateBack()
            },
            onLogin = {
                onNavigateToRoute(MainRouteSourceLogin(SourceLoginType.BookSource, it))
            },
            onDebug = { onNavigateToRoute(MainRouteBookSourceDebug(it)) },
            onSearch = { onNavigateToRoute(MainRouteSearch(null, it.toString())) },
        )
    }
    entry<MainRouteRssSourceManage> {
        RssSourceRouteScreen(
            onBackClick = onNavigateBack,
            onEditSource = { onNavigateToRoute(MainRouteRssSourceEdit(it.sourceUrl)) },
            onAddSource = { onNavigateToRoute(MainRouteRssSourceEdit()) },
        )
    }
    entry<MainRouteRssSourceEdit> { route ->
        val viewModel = koinViewModel<RssSourceEditViewModel>(
            key = "RssSourceEdit:${route.sourceUrl.orEmpty()}",
        )
        RssSourceEditRoute(
            sourceUrl = route.sourceUrl,
            viewModel = viewModel,
            onBack = { savedSourceUrl ->
                if (backStack.size == 1) {
                    savedSourceUrl?.let {
                        this@mainEntryProvider.setResult(
                            android.app.Activity.RESULT_OK,
                            Intent().putExtra("origin", it),
                        )
                    }
                    this@mainEntryProvider.finish()
                } else onNavigateBack()
            },
            onLogin = {
                onNavigateToRoute(MainRouteSourceLogin(SourceLoginType.RssSource, it))
            },
            onDebug = { onNavigateToRoute(MainRouteRssSourceDebug(it)) },
        )
    }
    entry<MainRouteBookSourceDebug> { route ->
        val viewModel = koinViewModel<BookSourceDebugViewModel>(
            key = "BookSourceDebug:${route.sourceUrl.orEmpty()}",
        )
        BookSourceDebugRoute(route.sourceUrl, viewModel, onNavigateBack)
    }
    entry<MainRouteRssSourceDebug> { route ->
        val viewModel = koinViewModel<RssSourceDebugViewModel>(
            key = "RssSourceDebug:${route.sourceUrl.orEmpty()}",
        )
        RssSourceDebugRoute(route.sourceUrl, viewModel, onNavigateBack)
    }
    entry<MainRouteHome> {
        val mainViewModel = koinViewModel<MainViewModel>()
        val mainUiState by mainViewModel.uiState.collectAsStateWithLifecycle()
        MainScreen(
            mainUiState = mainUiState,
            onIntent = mainViewModel::onIntent,
            effects = mainViewModel.effects,
            useRail = useRail,
            readAloudMorph = readAloudMorph,
            homePlaybackCapsuleEnabled = homePlaybackCapsuleEnabled,
            capsuleAnchorPreview = capsuleAnchorPreview,
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
            onNavigateToScopedSearch = { scopeRaw ->
                onNavigateToRoute(MainRouteSearch(key = null, scopeRaw = scopeRaw))
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
            onOpenBookshelfBook = { book, sharedCoverKey ->
                if (book.isAudio) {
                    onNavigateToRoute(
                        MainRouteAudioPlay(
                            bookUrl = book.bookUrl,
                            sharedCoverKey = sharedCoverKey
                        )
                    )
                } else if (!book.isLocal && book.isImage && showMangaUi) {
                    onNavigateToRoute(
                        MainRouteReadManga(
                            bookUrl = book.bookUrl,
                            sharedCoverKey = sharedCoverKey
                        )
                    )
                } else {
                    onNavigateToRoute(
                        MainRouteReadBook(
                            bookUrl = book.bookUrl,
                            sharedCoverKey = sharedCoverKey,
                        )
                    )
                }
            },
            onNavigateToMultiRoleRule = {
                onNavigateToRoute(MainRouteMultiRoleRule)
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
            onNavigateToSourceLogin = { type, sourceUrl ->
                onNavigateToRoute(MainRouteSourceLogin(type, sourceUrl))
            },
            onNavigateToBookSourceManage = {
                onNavigateToRoute(MainRouteBookSourceManage())
            },
            onNavigateToBookSourceEdit = {
                onNavigateToRoute(MainRouteBookSourceEdit(it))
            },
            onNavigateToRssSourceManage = {
                onNavigateToRoute(MainRouteRssSourceManage)
            },
            onNavigateToRssSourceEdit = {
                onNavigateToRoute(MainRouteRssSourceEdit(it))
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
            onNavigateToLab = { backStack.add(MainRouteSettingsLabConfig) },
            onNavigateToPrivate = { backStack.add(MainRouteSettingsPrivate) }
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
            onNavigateToAiSummary = { backStack.add(MainRouteSettingsAiSummary) },
            onNavigateToAiPrompt = { backStack.add(MainRouteSettingsAiPrompt) }
        )
    }

    entry<MainRouteSettingsAiSummary> {
        AiSummaryConfigRouteScreen(onBackClick = { onNavigateBack() })
    }

    entry<MainRouteSettingsAiPrompt> {
        AiPromptConfigRouteScreen(onBackClick = { onNavigateBack() })
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

    entry<MainRouteSettingsPrivate> {
        // 隐私设置页可以关掉三种验证时机，所以进入前要求确认身份（生物或密码）。
        // 只校验、不授予：进来一次不该顺带解锁私密内容。
        PrivateVerifyGate(onCancel = { onNavigateBack() }) {
            PrivateConfigRouteScreen(onBackClick = { onNavigateBack() })
        }
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

    entry<MainRouteReadBook>(
        metadata = readerEntryMetadata(configuration.appShell.predictiveBackEnabled)
    ) { route ->
        // 私密闸门：阅读记录 / 通知栏 / 深链等直开阅读器的路径全都在这里被拦一次，
        // 未授权时阅读器根本不会被组合（也就没有加载正文的机会）
        PrivateReadGate(
            bookUrl = route.bookUrl,
            onExit = {
                if (backStack.size > 1) {
                    MainNavigator.navigateBack(
                        this@mainEntryProvider,
                        backStack,
                        navRouteTracker,
                        fromRoute = route,
                    )
                } else {
                    // 通知 / 深链可能让阅读器成为栈里唯一一项：没有"当前页"可退，
                    // 按约定落到书架，而不是把应用关掉
                    backStack.clear()
                    backStack.add(MainRouteHome)
                }
            },
            onOpenLocalPasswordSettings = {
                onNavigateToRoute(MainRouteSettingsPrivate)
            },
        ) {
            ReaderPerfTrace.marker("nav.entry.begin")
            val readBookViewModel = koinViewModel<ReadBookViewModel>(
                key = "ReadBook:${route.bookUrl ?: "last-read"}"
            )
            ReaderPerfTrace.marker("nav.book-vm.ready")
            val readerSessionViewModel = koinViewModel<ReaderSessionViewModel>(
                key = "ReaderSession:${route.bookUrl ?: "last-read"}"
            )
            ReaderPerfTrace.marker("nav.viewmodels.ready")
            val controller = remember(readBookViewModel, readerSessionViewModel) {
                ReadBookController(
                    this@mainEntryProvider,
                    readBookViewModel,
                    readerSessionViewModel,
                    // 画布在首次组合就会请求分页，本路由要开哪本书必须随构造就位：
                    // 那期间 ReadBook 单例里可能还是上一本书的章节。
                    routeBookUrl = route.bookUrl,
                )
            }
            ReaderPerfTrace.marker("nav.controller.ready")
            val lifecycleOwner = LocalLifecycleOwner.current
            val initRequest = remember(route) {
                ReadBookInitRequest(
                    bookUrl = route.bookUrl,
                    inBookshelf = route.inBookshelf,
                    chapterChanged = route.chapterChanged,
                )
            }
            val effectsReady = remember(readBookViewModel) { CompletableDeferred<Unit>() }
            val readerResumeState = remember(controller, lifecycleOwner) { booleanArrayOf(false) }
            val collectorReady = remember(readBookViewModel) { booleanArrayOf(false) }
            // 是否跟随朗读位置：用户手动翻页/跳章后为 false，此时回阅读界面不回拉可见页。
            val readAloudSessionStore: ReadAloudSessionStore = org.koin.compose.koinInject()
            val readAloudFollow = remember(readBookViewModel) { booleanArrayOf(true) }
            LaunchedEffect(readAloudSessionStore) {
                readAloudSessionStore.state
                    .map { it.followReadAloudPosition }
                    .distinctUntilChanged()
                    .collect { readAloudFollow[0] = it }
            }
            fun resumeReader() {
                if (readerResumeState[0]) return
                readerResumeState[0] = true
                controller.onResume()
                readBookViewModel.onIntent(ReadBookIntent.OnResume)
                // 回到阅读界面时把可见页对齐到当前朗读位置：朗读期间用户可能进听书页跳段/跳章，
                // 服务驱动的是内部页游标，可见页需要显式回位，否则停在离开时那一页。
                // 只在跟随状态下做（用户手动翻页脱离后不回拉），语义与「回到朗读位置」一致。
                if (BaseReadAloudService.isRun && readAloudFollow[0]) {
                    readBookViewModel.onIntent(ReadBookIntent.BackToSpeakingPosition)
                }
            }

            fun pauseReader() {
                if (!readerResumeState[0]) return
                readerResumeState[0] = false
                readBookViewModel.onIntent(ReadBookIntent.OnPause)
                controller.onPause()
            }

            ReadBookRouteScreen(
                viewModel = readBookViewModel,
                readerSessionViewModel = readerSessionViewModel,
                host = controller,
                controller = controller,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                sharedCoverKey = route.sharedCoverKey,
                isTopRoute = (backStack.lastOrNull() as? MainRouteReadBook)?.let {
                    route.bookUrl == null || it.bookUrl == route.bookUrl
                } ?: false,
                onEffectsReady = { effectsReady.complete(Unit) },
                onOpenSearch = { word, bookUrl, autoFocus ->
                    onNavigateToRoute(
                        MainRouteSearchContent(
                            bookUrl = bookUrl,
                            searchWord = word,
                            searchResultIndex = readBookViewModel.uiState.value.searchResultIndex,
                            autoFocus = autoFocus,
                        )
                    )
                },
                onOpenVoiceCasting = { bookUrl ->
                    onNavigateToRoute(MainRouteBookVoiceCasting(bookUrl))
                },
                onOpenTtsEnginesAndVoices = {
                    onNavigateToRoute(MainRouteCloudTtsEngines(route.bookUrl))
                },
                onOpenTtsCache = {
                    onNavigateToRoute(MainRouteTtsCache)
                },
                onNavigateBack = {
                    MainNavigator.navigateBack(
                        this@mainEntryProvider,
                        backStack,
                        navRouteTracker,
                        fromRoute = route,
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
                    controller.onClose = null
                    controller.clearTts()
                    this@mainEntryProvider.toggleSystemBar(configuration.appShell.showStatusBar)
                }
            }

            LaunchedEffect(route, readBookViewModel, lifecycleOwner) {
                // Resolving the book and applying its read style do not depend on launcher effects.
                // Start that I/O immediately; initData still waits below because it can emit effects.
                val initialBook = readBookViewModel.initReadBookConfig(initRequest)
                effectsReady.await()
                collectorReady[0] = true
                readBookViewModel.initData(initRequest, initialBook) {
                    readBookViewModel.markJustInitData()
                    controller.onRouteInitialized()
                    if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        resumeReader()
                    }
                }
            }
        }
    }

    entry<MainRouteReadManga>(
        metadata = readerEntryMetadata(configuration.appShell.predictiveBackEnabled)
    ) { route ->
        val mangaViewModel = koinViewModel<MangaReaderViewModel>(
            key = "ReadManga:${route.bookUrl ?: "last-read"}",
        )
        MangaReaderRouteScreen(
            bookUrl = route.bookUrl,
            inBookshelf = route.inBookshelf,
            chapterChanged = route.chapterChanged,
            openRequestId = route.openRequestId,
            viewModel = mangaViewModel,
            restoreSystemBarsVisible = configuration.appShell.showStatusBar,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = LocalNavAnimatedContentScope.current,
            sharedCoverKey = route.sharedCoverKey,
            isTopRoute = (backStack.lastOrNull() as? MainRouteReadManga)?.let {
                route.bookUrl == null || it.bookUrl == route.bookUrl
            } ?: false,
            onFinish = {
                MainNavigator.navigateBack(
                    this@mainEntryProvider,
                    backStack,
                    navRouteTracker,
                    fromRoute = route,
                )
            },
            onOpenBookInfo = { name, author, bookUrl ->
                onNavigateToRoute(MainRouteBookInfo(name, author, bookUrl))
            },
            onOpenSourceLogin = { sourceUrl ->
                onNavigateToRoute(MainRouteSourceLogin(SourceLoginType.BookSource, sourceUrl))
            },
            onOpenSourceEdit = { sourceUrl ->
                onNavigateToRoute(MainRouteBookSourceEdit(sourceUrl))
            },
            onOpenWebView = { title, url, sourceOrigin, sourceName, sourceType ->
                onNavigateToRoute(
                    MainRouteWebView(title, url, sourceOrigin, sourceName, sourceType)
                )
            },
        )
    }

    entry<MainRouteReadAloudPlayer>(metadata = modalOverlayEntryMetadata()) {
        LaunchedEffect(Unit) {
            ReadAloudPlayerOverlayBus.request(PlaybackCapsuleState(source = PlaybackCapsuleSource.ReadAloud))
            if (backStack.size > 1) onNavigateBack() else backStack[0] = MainRouteHome
        }
    }

    // 兼容已保存的导航栈。新入口在 MainActivity 直接打开同窗口播放浮层。
    entry<MainRouteAudioPlay>(metadata = modalOverlayEntryMetadata()) { route ->
        LaunchedEffect(route) {
            ReadAloudPlayerOverlayBus.request(
                PlaybackCapsuleState(
                    source = PlaybackCapsuleSource.AudioBook,
                    bookUrl = route.bookUrl.orEmpty(),
                    inBookshelf = route.inBookshelf,
                )
            )
            if (backStack.size > 1) onNavigateBack() else backStack[0] = MainRouteHome
        }
    }

    entry<MainRouteSearchContent>(
        metadata = modalOverlayEntryMetadata() + ModalOverlaySceneStrategy.searchSlide()
    ) { route ->
        val viewModel = koinViewModel<SearchContentViewModel>(
            key = "SearchContent:${route.bookUrl}",
            parameters = { parametersOf(route) }
        )
        SearchContentRouteScreen(
            viewModel = viewModel,
            isTopRoute = backStack.lastOrNull() == route,
            predictiveBackEnabled = configuration.appShell.predictiveBackEnabled,
            autoFocus = route.autoFocus,
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
                onNavigateToRoute(MainRouteBookSourceManage())
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
            },
            onEditSource = { onNavigateToRoute(MainRouteRssSourceEdit(it)) },
            onLogin = {
                onNavigateToRoute(MainRouteSourceLogin(SourceLoginType.RssSource, it))
            },
        )
    }

    entry<MainRouteRssRead>(
        metadata = webViewEntryMetadata(configuration.appShell.predictiveBackEnabled)
    ) { route ->
        RssReadRouteScreen(
            title = route.title,
            origin = route.origin,
            link = route.link,
            openUrl = route.openUrl,
            startPage = route.startPage,
            onBackClick = { onNavigateBack() },
            onOpenArticles = { sortUrl, targetOrigin ->
                onNavigateToRoute(
                    MainRouteRssSort(
                        sourceUrl = targetOrigin ?: route.origin,
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
            onBackClick = { onNavigateBack() },
            onImportBookSource = {
                onNavigateToRoute(MainRouteBookSourceManage(it))
            },
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
        metadata = readerEntryMetadata(configuration.appShell.predictiveBackEnabled)
    ) { route ->
        val bookInfoViewModel = koinViewModel<BookInfoViewModel>(key = "BookInfo:${route.bookUrl}")
        BookInfoRouteScreen(
            bookUrl = route.bookUrl,
            name = route.name,
            author = route.author,
            origin = route.origin,
            coverPath = route.coverPath,
            viewModel = bookInfoViewModel,
            onBack = {
                MainNavigator.navigateBack(
                    this@mainEntryProvider,
                    backStack,
                    navRouteTracker,
                    fromRoute = route,
                )
            },
            onFinish = { _, _ ->
                MainNavigator.navigateBack(
                    this@mainEntryProvider,
                    backStack,
                    navRouteTracker,
                    fromRoute = route,
                )
            },
            onOpenSearch = { keyword ->
                onNavigateToRoute(MainRouteSearch(key = keyword))
            },
            onOpenBookSourceEdit = { sourceUrl ->
                onNavigateToRoute(MainRouteBookSourceEdit(sourceUrl))
            },
            onOpenSourceLogin = { sourceUrl ->
                onNavigateToRoute(MainRouteSourceLogin(SourceLoginType.BookSource, sourceUrl))
            },
            // 私密内容需要本地密码：直接把人送到私密设置页，而不是只弹一句提示
            onOpenSettings = {
                onNavigateToRoute(MainRouteSettingsPrivate)
            },
            onOpenReader = { bookUrl, inBookshelf, chapterChanged ->
                onNavigateToRoute(
                    MainRouteReadBook(
                        bookUrl = bookUrl,
                        inBookshelf = inBookshelf,
                        chapterChanged = chapterChanged,
                        sharedCoverKey = bookInfoCoverSharedElementKey(bookUrl),
                    )
                )
            },
            onOpenMangaReader = { bookUrl, inBookshelf, chapterChanged ->
                onNavigateToRoute(
                    MainRouteReadManga(
                        bookUrl = bookUrl,
                        inBookshelf = inBookshelf,
                        chapterChanged = chapterChanged,
                        openRequestId = System.nanoTime(),
                        sharedCoverKey = bookInfoCoverSharedElementKey(bookUrl),
                    )
                )
            },
            onOpenAudioPlay = { bookUrl, inBookshelf ->
                onNavigateToRoute(
                    MainRouteAudioPlay(
                        bookUrl = bookUrl,
                        inBookshelf = inBookshelf,
                        sharedCoverKey = bookInfoCoverSharedElementKey(bookUrl),
                    )
                )
            },
            onNavigateToBookInfo = { name, author, bookUrl, origin, coverPath ->
                onNavigateToRoute(MainRouteBookInfo(name, author, bookUrl, origin, coverPath))
            },
            onNavigateToExploreShow = { title, sourceUrl, exploreUrl ->
                onNavigateToRoute(MainRouteExploreShow(title, sourceUrl, exploreUrl))
            },
            onOpenCharacterDetail = { bookUrl, characterId ->
                onNavigateToRoute(MainRouteBookCharacterDetail(bookUrl, characterId))
            },
            onOpenCharacterNetwork = { bookUrl ->
                onNavigateToRoute(MainRouteBookCharacterNetwork(bookUrl))
            },
            onOpenCharacterList = { bookUrl ->
                onNavigateToRoute(MainRouteBookCharacterList(bookUrl))
            },
            onOpenKnowledgeList = { bookUrl ->
                onNavigateToRoute(MainRouteBookKnowledgeList(bookUrl))
            },
            onOpenEventList = { bookUrl ->
                onNavigateToRoute(MainRouteBookEventList(bookUrl))
            },
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = LocalNavAnimatedContentScope.current,
            sharedCoverKey = route.sharedCoverKey ?: bookCoverSharedElementKey(route.bookUrl),
            isTopRoute = (backStack.lastOrNull() as? MainRouteBookInfo)?.bookUrl == route.bookUrl,
        )
    }

    entry<MainRouteBookCharacterDetail>(metadata = ModalOverlaySceneStrategy.pageSlide(onNavigateBack)) { route ->
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var pendingAvatarUri by rememberSaveable { mutableStateOf<String?>(null) }
        val viewModel = koinViewModel<BookCharacterDetailViewModel>(
            key = "BookCharacterDetail:${route.bookUrl}:${route.characterId.orEmpty()}",
            parameters = { parametersOf(route.bookUrl, route.characterId) }
        )
        val state = viewModel.uiState.collectAsStateWithLifecycle().value
        val imagePicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->
            pendingAvatarUri = uri?.toString()
        }
        var showAvatarSource by remember { mutableStateOf(false) }
        BookCharacterDetailScreen(
            state = state,
            onIntent = viewModel::onIntent,
            effects = viewModel.effects,
            onBack = { onNavigateBack() },
            onPickAvatar = { showAvatarSource = true },
        )
        // 头像有两种来源（本地文件 / 图片链接），选择器同时提供「编辑当前头像」重进裁剪。
        CharacterAvatarSourceSheet(
            show = showAvatarSource,
            onDismissRequest = { showAvatarSource = false },
            onPickLocal = {
                showAvatarSource = false
                imagePicker.launch(arrayOf("image/*"))
            },
            onUrl = { url ->
                showAvatarSource = false
                // 旧头像文件由 ViewModel 在落库成功后再删（见 save 的 avatarChanged 分支）：
                // 在这里删的话，用户不保存就退出，档案里留的是个已被删掉的地址。
                viewModel.onIntent(CharacterDetailIntent.SetAvatarUri(url))
            },
            hasAvatar = state.avatarUri.isNotBlank(),
            onEditAvatar = {
                showAvatarSource = false
                pendingAvatarUri = state.avatarUri
            },
        )
        CharacterAvatarCropDialog(
            sourceUri = pendingAvatarUri?.let(Uri::parse),
            onDismissRequest = { pendingAvatarUri = null },
            onConfirm = { crop ->
                val sourceUri =
                    pendingAvatarUri?.let(Uri::parse) ?: return@CharacterAvatarCropDialog
                pendingAvatarUri = null
                scope.launch {
                    runCatching {
                        withContext(IO) {
                            saveCharacterAvatar(context, sourceUri, crop)
                        }
                    }.onSuccess { avatarUri ->
                        deleteCharacterAvatar(context, state.avatarUri)
                        viewModel.onIntent(CharacterDetailIntent.SetAvatarUri(avatarUri))
                    }.onFailure {
                        context.toastOnUi(
                            it.localizedMessage ?: context.getString(R.string.save_failed)
                        )
                    }
                }
            },
        )
    }

    entry<MainRouteBookCharacterNetwork>(metadata = ModalOverlaySceneStrategy.pageSlide(onNavigateBack)) { route ->
        val viewModel = koinViewModel<BookCharacterNetworkViewModel>(
            key = "BookCharacterNetwork:${route.bookUrl}",
            parameters = { parametersOf(route.bookUrl) }
        )
        BookCharacterNetworkScreen(
            state = viewModel.uiState.collectAsStateWithLifecycle().value,
            onIntent = viewModel::onIntent,
            effects = viewModel.effects,
            onBack = { onNavigateBack() },
            onOpenCharacterDetail = { characterId ->
                onNavigateToRoute(MainRouteBookCharacterDetail(route.bookUrl, characterId))
            },
            onRefresh = viewModel::refresh,
        )
    }

    entry<MainRouteBookCharacterList>(metadata = ModalOverlaySceneStrategy.pageSlide(onNavigateBack)) { route ->
        val viewModel = koinViewModel<BookCharacterListViewModel>(
            key = "CharacterList:${route.bookUrl}",
            parameters = { parametersOf(route.bookUrl) }
        )
        BookCharacterListScreen(
            state = viewModel.uiState.collectAsStateWithLifecycle().value,
            onIntent = viewModel::onIntent,
            effects = viewModel.effects,
            onBack = { onNavigateBack() },
            onOpenDetail = { characterId ->
                onNavigateToRoute(MainRouteBookCharacterDetail(route.bookUrl, characterId))
            },
            onRefresh = viewModel::refresh,
        )
    }

    entry<MainRouteBookVoiceCasting>(metadata = ModalOverlaySceneStrategy.pageSlide(onNavigateBack)) { route ->
        val viewModel = koinViewModel<BookVoiceCastingViewModel>(
            key = "BookVoiceCasting:${route.bookUrl}",
            parameters = { parametersOf(route.bookUrl) },
        )
        BookVoiceCastingScreen(
            state = viewModel.uiState.collectAsStateWithLifecycle().value,
            onIntent = viewModel::onIntent,
            effects = viewModel.effects,
            onBack = { onNavigateBack() },
            onManageCloudTts = { onNavigateToRoute(MainRouteCloudTtsEngines(route.bookUrl)) },
            onOpenCharacterDetail = { characterId ->
                onNavigateToRoute(MainRouteBookCharacterDetail(route.bookUrl, characterId))
            },
        )
    }

    entry<MainRouteCloudTtsEngines>(metadata = ModalOverlaySceneStrategy.pageSlide(onNavigateBack)) { route ->
        val viewModel = koinViewModel<CloudTtsViewModel>()
        LaunchedEffect(route.bookUrl) {
            viewModel.onIntent(CloudTtsIntent.SetBookContext(route.bookUrl))
        }
        val context = LocalContext.current
        val importLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri -> uri?.let { viewModel.onIntent(CloudTtsIntent.ImportHttpTtsFileSelected(it)) } }
        val exportLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri -> uri?.let { viewModel.onIntent(CloudTtsIntent.ExportHttpTtsFileSelected(it)) } }
        LaunchedEffect(viewModel) {
            viewModel.effects.collectLatest { effect ->
                when (effect) {
                    CloudTtsEffect.OpenHttpTtsImportPicker -> importLauncher.launch(
                        arrayOf("application/json", "text/plain")
                    )

                    CloudTtsEffect.OpenHttpTtsExportPicker -> exportLauncher.launch("httpTTS.json")
                    is CloudTtsEffect.OpenHttpTtsLogin -> onNavigateToRoute(
                        MainRouteSourceLogin(
                            type = SourceLoginType.HttpTts,
                            sourceKey = effect.engineId.toString(),
                        )
                    )

                    else -> Unit
                }
            }
        }
        CloudTtsScreen(
            state = viewModel.uiState.collectAsStateWithLifecycle().value,
            onIntent = viewModel::onIntent,
            effects = viewModel.effects,
            onBack = { onNavigateBack() },
        )
    }

    entry<MainRouteTtsCache>(metadata = ModalOverlaySceneStrategy.pageSlide(onNavigateBack)) {
        TtsCacheRouteScreen(
            onBackClick = { onNavigateBack() },
        )
    }

    entry<MainRouteBookKnowledgeList>(metadata = ModalOverlaySceneStrategy.pageSlide(onNavigateBack)) { route ->
        val viewModel = koinViewModel<BookKnowledgeListViewModel>(
            key = "KnowledgeList:${route.bookUrl}",
            parameters = { parametersOf(route.bookUrl) }
        )
        BookKnowledgeListScreen(
            state = viewModel.uiState.collectAsStateWithLifecycle().value,
            onIntent = viewModel::onIntent,
            effects = viewModel.effects,
            onBack = { onNavigateBack() },
            onOpenDetail = { entryId ->
                onNavigateToRoute(MainRouteBookKnowledgeDetail(route.bookUrl, entryId))
            },
            onRefresh = viewModel::refresh,
        )
    }

    entry<MainRouteBookKnowledgeDetail>(metadata = ModalOverlaySceneStrategy.pageSlide(onNavigateBack)) { route ->
        val viewModel = koinViewModel<BookKnowledgeDetailViewModel>(
            key = "KnowledgeDetail:${route.bookUrl}:${route.entryId.orEmpty()}",
            parameters = { parametersOf(route.bookUrl, route.entryId) }
        )
        BookKnowledgeDetailScreen(
            state = viewModel.uiState.collectAsStateWithLifecycle().value,
            onIntent = viewModel::onIntent,
            effects = viewModel.effects,
            onBack = { onNavigateBack() },
        )
    }

    entry<MainRouteBookEventList>(metadata = ModalOverlaySceneStrategy.pageSlide(onNavigateBack)) { route ->
        val viewModel = koinViewModel<BookEventListViewModel>(
            key = "EventList:${route.bookUrl}",
            parameters = { parametersOf(route.bookUrl) }
        )
        BookEventListScreen(
            state = viewModel.uiState.collectAsStateWithLifecycle().value,
            onIntent = viewModel::onIntent,
            effects = viewModel.effects,
            onBack = { onNavigateBack() },
            onOpenDetail = { eventId ->
                onNavigateToRoute(MainRouteBookEventDetail(route.bookUrl, eventId))
            },
            onRefresh = viewModel::refresh,
        )
    }

    entry<MainRouteBookEventDetail>(metadata = ModalOverlaySceneStrategy.pageSlide(onNavigateBack)) { route ->
        val viewModel = koinViewModel<BookEventDetailViewModel>(
            key = "EventDetail:${route.bookUrl}:${route.eventId.orEmpty()}",
            parameters = { parametersOf(route.bookUrl, route.eventId) }
        )
        BookEventDetailScreen(
            state = viewModel.uiState.collectAsStateWithLifecycle().value,
            onIntent = viewModel::onIntent,
            effects = viewModel.effects,
            onBack = { onNavigateBack() },
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
        HighlightTagRuleRouteScreen(
            onBackClick = { onNavigateBack() }
        )
    }

    entry<MainRouteMultiRoleRule> {
        MultiRoleRuleRouteScreen(
            onBackClick = { onNavigateBack() },
            onNavigateToVoicePool = { backStack.add(MainRouteVoicePool) },
            onNavigateToBgmPool = { backStack.add(MainRouteBgmPool) },
            onNavigateToVoiceEffect = { backStack.add(MainRouteVoiceEffect) },
            onNavigateToCapsuleStyle = { backStack.add(MainRouteCastCapsuleStyle) },
            onNavigateToEngines = { backStack.add(MainRouteCloudTtsEngines()) },
            onNavigateToRecognition = { backStack.add(MainRouteMultiRoleRecognition) },
            onNavigateToRegexCast = { backStack.add(MainRouteRegexCastRule) },
        )
    }

    entry<MainRouteCastCapsuleStyle> {
        CastCapsuleStyleRouteScreen(
            onBackClick = { onNavigateBack() },
        )
    }

    entry<MainRouteVoicePool> {
        VoicePoolRouteScreen(
            onBackClick = { onNavigateBack() }
        )
    }

    entry<MainRouteBgmPool> {
        BgmPoolRouteScreen(
            onBackClick = { onNavigateBack() }
        )
    }

    entry<MainRouteVoiceEffect> {
        VoiceEffectRouteScreen(
            onBackClick = { onNavigateBack() },
        )
    }

    entry<MainRouteMultiRoleRecognition> {
        MultiRoleRecognitionRouteScreen(
            onBackClick = { onNavigateBack() }
        )
    }

    entry<MainRouteRegexCastRule> {
        RegexCastRuleRouteScreen(
            onBackClick = { onNavigateBack() },
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

/** 有声书的 Android 宿主动作；播放页与听书页共用同窗口形变组件。 */
@Composable
internal fun MainActivity.AudioPlayerMorphOverlay(
    bookUrl: String,
    inBookshelf: Boolean,
    morph: ReadAloudMorphState,
    visible: Boolean,
    awaitCapsuleAnchor: Boolean,
    predictiveBackEnabled: Boolean,
    onDismiss: () -> Unit,
) {
    // 浮层关闭后释放页面 VM；播放服务和会话继续保留，重新打开会重新同步书籍。
    val playerStoreOwner = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(playerStoreOwner) {
        onDispose { playerStoreOwner.viewModelStore.clear() }
    }
    val audioPlayViewModel = koinViewModel<AudioPlayViewModel>(
        key = "AudioPlay:${bookUrl}",
        viewModelStoreOwner = playerStoreOwner,
    )
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by audioPlayViewModel.uiState.collectAsStateWithLifecycle()
    var showAudioChangeSource by remember { mutableStateOf(false) }
    val sourceEditResult = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            audioPlayViewModel.onIntent(AudioPlayIntent.SourceEdited)
        }
    }

    fun copyAudioPlayUrl() {
        AudioPlay.book?.let {
            SourceCallBack.callBackBtn(
                this@AudioPlayerMorphOverlay,
                SourceCallBack.CLICK_COPY_PLAY_URL,
                AudioPlay.bookSource,
                it,
                AudioPlay.durChapter,
                BookType.audio,
                result = AudioPlayService.url,
            ) {
                sendToClip(AudioPlayService.url)
            }
        }
    }

    fun finishAudioPlay() {
        if (AudioPlay.inBookshelf) {
            SourceCallBack.callBackBook(
                SourceCallBack.END_READ,
                AudioPlay.bookSource,
                AudioPlay.book,
                AudioPlay.durChapter
            )
        }
        onDismiss()
    }

    LaunchedEffect(bookUrl, inBookshelf, audioPlayViewModel) {
        audioPlayViewModel.onIntent(
            AudioPlayIntent.Init(bookUrl.orEmpty(), inBookshelf)
        )
    }
    DisposableEffect(audioPlayViewModel, lifecycleOwner) {
        MainActivity.hasActiveAudioPlayRoute = true
        this@AudioPlayerMorphOverlay.activeAudioPlayViewModel = audioPlayViewModel
        AudioPlay.register(this@AudioPlayerMorphOverlay)
        onDispose {
            AudioPlay.unregister(this@AudioPlayerMorphOverlay)
            if (this@AudioPlayerMorphOverlay.activeAudioPlayViewModel === audioPlayViewModel) {
                this@AudioPlayerMorphOverlay.activeAudioPlayViewModel = null
            }
            MainActivity.hasActiveAudioPlayRoute = false
        }
    }
    LaunchedEffect(audioPlayViewModel) {
        audioPlayViewModel.effects.collectLatest { effect ->
            when (effect) {
                is AudioPlayEffect.OpenChangeSource -> showAudioChangeSource = true

                is AudioPlayEffect.OpenLogin -> startActivity(
                    MainActivity.createSourceLoginIntent(
                        this@AudioPlayerMorphOverlay,
                        SourceLoginType.BookSource,
                        effect.sourceUrl
                    )
                )

                AudioPlayEffect.CopyPlayUrl -> copyAudioPlayUrl()
                is AudioPlayEffect.OpenEditSource -> sourceEditResult.launch(
                    MainActivity.createBookSourceEditIntent(
                        this@AudioPlayerMorphOverlay,
                        effect.sourceUrl
                    )
                )

                is AudioPlayEffect.ShowToast -> toastOnUi(effect.message)
                is AudioPlayEffect.OpenBookReader -> {
                    startActivity(
                        MainActivity.createReadBookIntent(
                            this@AudioPlayerMorphOverlay,
                            effect.bookUrl
                        )
                    )
                    onDismiss()
                }

                AudioPlayEffect.Finish -> {
                    if (!morph.hasCapsuleAnchors) {
                        val anchor = BookCoverMorphAnchors.get(bookUrl)
                        if (anchor != null && !anchor.bounds.isEmpty && isAnchorVisibleInScreen(
                                anchor.bounds,
                                morph.screenBounds
                            )
                        ) {
                            morph.reportBookCoverAnchor(anchor.bounds, anchor.cornerRadiusPx)
                        }
                    }
                    morph.animateTo(0f)
                    finishAudioPlay()
                }
            }
        }
    }
    val refreshAudioAnchor = {
        if (!morph.hasCapsuleAnchors) {
            val anchor = BookCoverMorphAnchors.get(bookUrl)
            if (anchor != null && !anchor.bounds.isEmpty && isAnchorVisibleInScreen(
                    anchor.bounds,
                    morph.screenBounds
                )
            ) {
                morph.reportBookCoverAnchor(anchor.bounds, anchor.cornerRadiusPx)
            }
        }
    }
    LaunchedEffect(bookUrl, visible) {
        if (visible && morph.progress.value == 0f) {
            refreshAudioAnchor()
        }
    }
    PlayerMorphHost(
        appearance = PlayerMorphAppearance(
            uiState.bookName, uiState.author, uiState.coverPath,
            uiState.sourceOrigin, uiState.bgMode,
        ),
        playerTheme = rememberPlayerThemeOverride(
            uiState.bookName, uiState.author, uiState.coverPath, uiState.sourceOrigin,
        ),
        morph = morph,
        visible = visible,
        awaitCapsuleAnchor = awaitCapsuleAnchor,
        predictiveBackEnabled = predictiveBackEnabled,
        verticalDragEnabled = true,
        onBeforeCollapse = refreshAudioAnchor,
        backEnabled = uiState.activeSheet == null && !showAudioChangeSource,
        onDismiss = ::finishAudioPlay,
    ) { onCollapse ->
        AudioPlayScreenContent(
            state = uiState,
            onIntent = audioPlayViewModel::onIntent,
            onBack = onCollapse,
        )
    }
    val audioBook = AudioPlay.book
    if (showAudioChangeSource && audioBook != null) {
        ChangeSourceSheet(
            show = true,
            oldBook = audioBook,
            onDismissRequest = { showAudioChangeSource = false },
            onReplace = { source, book, toc, _ ->
                audioPlayViewModel.changeTo(source, book, toc)
            },
            onAddAsNew = { book, toc ->
                audioPlayViewModel.addToBookshelf(book, toc)
            },
        )
    }
}
