package io.legado.app.ui.main

import android.content.Intent
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import io.legado.app.R
import io.legado.app.ui.main.bookshelf.BookshelfRouteScreen
import io.legado.app.ui.main.bookshelf.BookShelfItem
import io.legado.app.ui.main.bookshelf.BookshelfViewModel
import io.legado.app.ui.main.explore.ExploreRouteScreen
import io.legado.app.ui.main.home.HomeRouteScreen
import io.legado.app.ui.main.my.MyRouteScreen
import io.legado.app.ui.main.my.PrefClickEvent
import io.legado.app.ui.main.rss.RssRouteScreen
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.FloatingBottomBar
import io.legado.app.ui.widget.components.FloatingBottomBarItem
import io.legado.app.ui.widget.components.GlassDefaults
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.navigation.AppNavigationBar
import io.legado.app.ui.widget.components.navigation.AppNavigationBarItem
import io.legado.app.ui.widget.components.pager.rememberPagerFlingPassThroughConnection
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.utils.sendToClip
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivityForBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.NavigationRailDefaults
import top.yukonga.miuix.kmp.basic.NavigationRailValue
import top.yukonga.miuix.kmp.basic.rememberNavigationRailState
import top.yukonga.miuix.kmp.basic.NavigationRail as MiuixNavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem as MiuixNavigationRailItem

@OptIn(
    ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class
)
@Composable
fun MainScreen(
    mainUiState: MainUiState,
    onIntent: (MainUiIntent) -> Unit,
    effects: kotlinx.coroutines.flow.Flow<MainEffect>,
    useRail: Boolean,
    onOpenSettings: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToSearch: (String?) -> Unit,
    onNavigateToRemoteImport: () -> Unit,
    onNavigateToLocalImport: () -> Unit,
    onNavigateToCache: (Long) -> Unit,
    onNavigateToBookCacheManage: () -> Unit,
    onOpenBookshelfBook: (BookShelfItem) -> Unit,
    onNavigateToBackupSettings: () -> Unit,
    onNavigateToBookInfo: (name: String, author: String, bookUrl: String, origin: String?, coverPath: String?, sharedCoverKey: String?) -> Unit,
    onNavigateToExploreShow: (title: String?, sourceUrl: String, exploreUrl: String?) -> Unit,
    onNavigateToRssSort: (sourceUrl: String, sortUrl: String?, key: String?) -> Unit,
    onNavigateToRssRead: (
        title: String?,
        origin: String,
        link: String?,
        openUrl: String?,
        startPage: Boolean
    ) -> Unit,
    onNavigateToRssFavorites: () -> Unit,
    onNavigateToRuleSub: () -> Unit,
    onNavigateToReadRecord: () -> Unit,
    onNavigateToReadRecordOverview: () -> Unit,
    onNavigateToHighlightTagRule: () -> Unit,
    onNavigateToAbout: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val defaultHelpTitle = stringResource(R.string.help)

    LaunchedEffect(effects, context) {
        effects.collectLatest { effect ->
            when (effect) {
                is MainEffect.OpenUrl -> {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, effect.url.toUri())
                    )
                }

                is MainEffect.CopyUrl -> context.sendToClip(effect.url)
                is MainEffect.ShowMarkdown -> {
                    val activity = context as? AppCompatActivity ?: return@collectLatest
                    val title = effect.title.ifBlank { defaultHelpTitle }
                    val mdText = withContext(Dispatchers.IO) {
                        context.assets
                            .open("web/help/md/${effect.path}.md")
                            .bufferedReader()
                            .use { it.readText() }
                    }
                    activity.showDialogFragment(TextDialog(title, mdText, TextDialog.Mode.MD))
                }

                is MainEffect.StartActivity -> {
                    context.startActivity(Intent(context, effect.destination).apply {
                        effect.configTag?.let { putExtra("configTag", it) }
                    })
                }

                MainEffect.ExitApp -> (context as? ComponentActivity)?.finish()
                MainEffect.NavigateToReadRecord -> onNavigateToReadRecord()
                MainEffect.NavigateToHighlightTagRule -> onNavigateToHighlightTagRule()
                MainEffect.NavigateToAbout -> onNavigateToAbout()
            }
        }
    }

    val hazeState = remember { HazeState() }
    val customSecondaryColor = if (LegadoTheme.isDark) {
        mainUiState.secondaryThemeColorNight.takeIf { it != 0 }
            ?: mainUiState.secondaryThemeColor
    } else {
        mainUiState.secondaryThemeColor
    }
    val floatingBarSurfaceColor = if (mainUiState.deepPersonalizationActive && customSecondaryColor != 0) {
        Color(customSecondaryColor)
    } else {
        LegadoTheme.colorScheme.surface
    }
    val floatingBarBackdrop = rememberLayerBackdrop {
        drawRect(floatingBarSurfaceColor)
        drawContent()
    }
    val destinations = mainUiState.destinations

    val initialPage = remember(destinations, mainUiState.defaultHomePage) {
        val index = destinations.indexOfFirst {
            it.route == mainUiState.defaultHomePage
        }
        if (index != -1) index else 0
    }
    val pagerState = rememberPagerState(initialPage = initialPage) { destinations.size }
    val pagerNestedScrollConnection = rememberPagerFlingPassThroughConnection(
        state = pagerState,
        orientation = Orientation.Horizontal,
    )
    var bookshelfScrollToTopRequest by remember { mutableLongStateOf(0L) }
    var homeOverflowMenuRequest by remember { mutableLongStateOf(0L) }
    fun requestBookshelfScrollToTop() {
        bookshelfScrollToTopRequest++
    }

    fun handleMainDestinationClick(index: Int, destination: MainDestination) {
        if (
            destination == MainDestination.Home &&
            pagerState.currentPage == index &&
            pagerState.targetPage == index
        ) {
            homeOverflowMenuRequest++
            return
        }
        if (
            destination == MainDestination.Bookshelf &&
            pagerState.currentPage == index &&
            pagerState.targetPage == index
        ) {
            requestBookshelfScrollToTop()
            return
        }
        coroutineScope.launch {
            pagerState.animateScrollToPage(index)
        }
    }
    LaunchedEffect(destinations) {
        if (destinations.isNotEmpty() && pagerState.currentPage !in destinations.indices) {
            pagerState.scrollToPage(destinations.lastIndex)
        }
    }
    val labelVisibilityMode = mainUiState.labelVisibilityMode
    val isUnlabeled = labelVisibilityMode == "unlabeled"
    val useFloatingBottomBar =
        !useRail && mainUiState.showBottomView && mainUiState.useFloatingBottomBar
    val useLiquidGlass = useFloatingBottomBar &&
            mainUiState.useFloatingBottomBarLiquidGlass &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val alwaysShowLabel = labelVisibilityMode == "labeled"
    val showLabel = !isUnlabeled

    val navState = rememberWideNavigationRailState(
        initialValue = if (mainUiState.navExtended)
            WideNavigationRailValue.Expanded
        else
            WideNavigationRailValue.Collapsed
    )

    Row(modifier = Modifier.fillMaxSize()) {
        if (useRail && mainUiState.showBottomView) {
            if (ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)) {
                val miuixNavState = rememberNavigationRailState(
                    initialValue = if (mainUiState.navExtended) {
                        NavigationRailValue.Expanded
                    } else {
                        NavigationRailValue.Collapsed
                    }
                )
                LaunchedEffect(miuixNavState.currentValue) {
                    onIntent(MainUiIntent.SetNavigationRailExpanded(miuixNavState.isExpanded))
                }
                MiuixNavigationRail(
                    state = miuixNavState,
                    header = {
                        FloatingActionButton(
                            modifier = Modifier
                                .align(Alignment.Start)
                                .padding(start = NavigationRailDefaults.ExpandedItemHorizontalMargin),
                            onClick = { onNavigateToSearch(null) },
                        ) {
                            AppIcon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                    }
                ) {
                    destinations.forEachIndexed { index, destination ->
                        val selected = pagerState.targetPage == index
                        var showGroupMenu by remember { mutableStateOf(false) }
                        val haptic = LocalHapticFeedback.current
                        val destinationLabel = stringResource(destination.labelId)
                        Box {
                            MiuixNavigationRailItem(
                                modifier = Modifier
                                    .semantics(mergeDescendants = true) {
                                        contentDescription = destinationLabel
                                    }
                                    .then(
                                        if (destination == MainDestination.Bookshelf) {
                                            Modifier.combinedClickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = {
                                                    handleMainDestinationClick(
                                                        index,
                                                        destination
                                                    )
                                                },
                                                onLongClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    showGroupMenu = true
                                                }
                                            )
                                        } else Modifier
                                    ),
                                selected = selected,
                                onClick = { handleMainDestinationClick(index, destination) },
                                icon = AppIcons.mainDestination(destination, selected),
                                label = destinationLabel,
                            )
                            if (destination == MainDestination.Bookshelf && showGroupMenu) {
                                BookshelfRailGroupMenuRoute(
                                    expanded = showGroupMenu,
                                    onDismissRequest = { showGroupMenu = false },
                                    onBeforeSelectGroup = {
                                        if (pagerState.currentPage != index) {
                                            pagerState.scrollToPage(index)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            } else WideNavigationRail(
                state = navState,
                header = {
                    val expanded = navState.targetValue == WideNavigationRailValue.Expanded

                    Column {
                        IconButton(
                            modifier = Modifier.padding(start = 24.dp),
                            onClick = {
                                coroutineScope.launch {
                                    val targetExpanded = !expanded
                                    if (targetExpanded) navState.expand()
                                    else navState.collapse()
                                    onIntent(MainUiIntent.SetNavigationRailExpanded(targetExpanded))
                                }
                            }
                        ) {
                            Icon(
                                if (expanded)
                                    Icons.AutoMirrored.Filled.MenuOpen
                                else
                                    Icons.Default.Menu,
                                contentDescription = stringResource(
                                    if (expanded) R.string.collapse else R.string.expand
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        ExtendedFloatingActionButton(
                            modifier = Modifier.padding(start = 20.dp),
                            onClick = { onNavigateToSearch(null) },
                            expanded = expanded,
                            icon = { AppIcon(Icons.Default.Search, contentDescription = null) },
                            text = { AppText(stringResource(R.string.search)) }
                        )
                    }
                }
            ) {
                destinations.forEachIndexed { index, destination ->
                    val selected = pagerState.targetPage == index
                    var showGroupMenu by remember { mutableStateOf(false) }
                    val haptic = LocalHapticFeedback.current
                    val destinationLabel = stringResource(destination.labelId)

                    WideNavigationRailItem(
                        modifier = Modifier.semantics(mergeDescendants = true) {
                            contentDescription = destinationLabel
                        },
                        railExpanded = navState.targetValue == WideNavigationRailValue.Expanded,
                        selected = selected,
                        onClick = {
                            handleMainDestinationClick(index, destination)
                        },
                        icon = {
                            Box {
                                NavigationIcon(
                                    destination = destination,
                                    selected = selected,
                                    customIconPath = mainUiState.customIconPath(destination),
                                    modifier = if (destination == MainDestination.Bookshelf) {
                                        Modifier.combinedClickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = {
                                                handleMainDestinationClick(index, destination)
                                            },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                showGroupMenu = true
                                            }
                                        )
                                    } else Modifier
                                )

                                if (destination == MainDestination.Bookshelf && showGroupMenu) {
                                    BookshelfRailGroupMenuRoute(
                                        expanded = showGroupMenu,
                                        onDismissRequest = { showGroupMenu = false },
                                        onBeforeSelectGroup = {
                                            if (pagerState.currentPage != index) {
                                                pagerState.scrollToPage(index)
                                            }
                                        }
                                    )
                                }
                            }
                        },
                        label = if (labelVisibilityMode != "unlabeled") {
                            val hasCustomIcon = mainUiState.customIconPath(destination).isNotEmpty()
                            if (hasCustomIcon) null else {{ AppText(stringResource(destination.labelId)) }}
                        } else null
                    )
                }
            }
        }

        AppScaffold(
            modifier = Modifier.weight(1f),
            bottomBar = {
                if (!useRail && mainUiState.showBottomView && !useFloatingBottomBar) {
                    AppNavigationBar(
                        showLabel = showLabel,
                        alwaysShowLabel = alwaysShowLabel
                    ) {
                        destinations.forEachIndexed { index, destination ->
                            val selected = pagerState.targetPage == index
                            val customIconPath = mainUiState.customIconPath(destination)
                            val destinationLabel = stringResource(destination.labelId)
                            AppNavigationBarItem(
                                modifier = Modifier.semantics(mergeDescendants = true) {
                                    contentDescription = destinationLabel
                                },
                                selected = selected,
                                onClick = {
                                    handleMainDestinationClick(index, destination)
                                },
                                labelString = stringResource(destination.labelId),
                                iconVector = AppIcons.mainDestination(destination, selected),
                                m3Icon = {
                                    NavigationIcon(
                                        destination = destination,
                                        selected = selected,
                                        customIconPath = mainUiState.customIconPath(destination),
                                    )
                                },
                                m3IndicatorColor = GlassDefaults.glassColor(
                                    noBlurColor = LegadoTheme.colorScheme.secondaryContainer,
                                    blurAlpha = GlassDefaults.ThickBlurAlpha
                                ),
                                m3ShowLabel = showLabel && !customIconPath.isNotEmpty(),
                                m3AlwaysShowLabel = alwaysShowLabel,
                                useCustomIcon = customIconPath.isNotEmpty()
                            )
                        }
                    }
                }
            },
            contentWindowInsets = WindowInsets(0)
        ) { _ ->
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier.then(
                        if (useLiquidGlass) {
                            Modifier
                                .hazeSource(hazeState)
                                .layerBackdrop(floatingBarBackdrop)
                        } else {
                            Modifier
                        }
                    )
                ) {
                    HorizontalPager(
                        state = pagerState,
                        pageNestedScrollConnection = pagerNestedScrollConnection,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                with(sharedTransitionScope) {
                                    if (this != null) Modifier.skipToLookaheadSize() else Modifier
                                }
                            ),
                        userScrollEnabled = true,
                        beyondViewportPageCount = 4
                    ) { page ->
                        val destination = destinations.getOrNull(page) ?: return@HorizontalPager
                        val pageLifecycleOwner = rememberMainPageLifecycleOwner(
                            isActive = page == pagerState.currentPage
                        )
                        CompositionLocalProvider(LocalLifecycleOwner provides pageLifecycleOwner) {
                            when (destination) {
                            MainDestination.Home -> HomeRouteScreen(
                                showOverflowMenuRequest = homeOverflowMenuRequest,
                                onOpenBook = { book ->
                                    context.startActivityForBook(book)
                                },
                                onNavigateToBookInfo = { name, author, bookUrl, origin, coverPath, sharedCoverKey ->
                                    onNavigateToBookInfo(
                                        name ?: "",
                                        author ?: "",
                                        bookUrl,
                                        origin,
                                        coverPath,
                                        sharedCoverKey,
                                    )
                                },
                                onOpenExploreShow = onNavigateToExploreShow,
                                onOpenBackupSettings = onNavigateToBackupSettings,
                                onNavigateToReadRecord = onNavigateToReadRecord,
                                onNavigateToReadRecordOverview = onNavigateToReadRecordOverview,
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
                            )

                            MainDestination.Bookshelf -> BookshelfRouteScreen(
                                scrollToTopRequest = bookshelfScrollToTopRequest,
                                onScrollToTopRequestHandled = { handledRequest ->
                                    if (bookshelfScrollToTopRequest == handledRequest) {
                                        bookshelfScrollToTopRequest = 0L
                                    }
                                },
                                onBookClick = { book ->
                                    onOpenBookshelfBook(book)
                                },
                                onBookLongClick = { book, sharedCoverKey ->
                                    onNavigateToBookInfo(
                                        book.name,
                                        book.author,
                                        book.bookUrl,
                                        book.origin,
                                        book.getDisplayCover(),
                                        sharedCoverKey
                                    )
                                },
                                onNavigateToSearch = { query -> onNavigateToSearch(query) },
                                onNavigateToRemoteImport = onNavigateToRemoteImport,
                                onNavigateToLocalImport = onNavigateToLocalImport,
                                onNavigateToCache = onNavigateToCache,
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
                            )

                            MainDestination.Explore -> ExploreRouteScreen(
                                onOpenExploreShow = onNavigateToExploreShow,
                            )
                            MainDestination.Rss -> RssRouteScreen(
                                onOpenSort = { sourceUrl, sortUrl, key ->
                                    onNavigateToRssSort(sourceUrl, sortUrl, key)
                                },
                                onOpenRead = { title, origin, link, openUrl, startPage ->
                                    onNavigateToRssRead(title, origin, link, openUrl, startPage)
                                },
                                onOpenFavorites = onNavigateToRssFavorites,
                                onOpenRuleSub = onNavigateToRuleSub
                            )
                            MainDestination.My -> MyRouteScreen(
                                onOpenSettings = onOpenSettings,
                                onNavigateToChat = onNavigateToChat,
                                onNavigate = { event ->
                                    when (event) {
                                        PrefClickEvent.OpenBookCacheManage -> onNavigateToBookCacheManage()
                                        PrefClickEvent.OpenReadRecord -> onNavigateToReadRecord()
                                        else -> onIntent(MainUiIntent.HandlePreferenceClick(event))
                                    }
                                }
                            )
                        }
                        }
                    }
                }

                if (!useRail && mainUiState.showBottomView && useFloatingBottomBar) {
                    Box(modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                    ) {
                        FloatingBottomBar(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {}
                                )
                                .padding(
                                    start = 16.dp,
                                    end = 16.dp,
                                    bottom = 12.dp + WindowInsets.navigationBars
                                        .asPaddingValues()
                                        .calculateBottomPadding()
                                ),
                            selectedIndex = { pagerState.targetPage },
                            onSelected = { index ->
                                destinations.getOrNull(index)?.let { destination ->
                                    handleMainDestinationClick(index, destination)
                                }
                            },
                            onReselected = { index ->
                                destinations.getOrNull(index)?.let { destination ->
                                    handleMainDestinationClick(index, destination)
                                }
                            },
                            backdrop = floatingBarBackdrop,
                            tabsCount = destinations.size,
                            isBlurEnabled = useLiquidGlass,
                            hasCustomIcons = destinations.any { dest ->
                                mainUiState.customIconPath(dest).isNotEmpty()
                            }
                        ) {
                            destinations.forEachIndexed { index, destination ->
                                val selected = pagerState.targetPage == index
                                val hasCustomIcon = mainUiState.customIconPath(destination).isNotEmpty()
                                val destinationLabel = stringResource(destination.labelId)
                                FloatingBottomBarItem(
                                    onClick = {
                                        handleMainDestinationClick(index, destination)
                                    },
                                    modifier = Modifier
                                        .defaultMinSize(minWidth = 76.dp)
                                        .semantics(mergeDescendants = true) {
                                            contentDescription = destinationLabel
                                        }
                                ) {
                                    NavigationIcon(
                                        destination = destination,
                                        customIconPath = mainUiState.customIconPath(destination),
                                        selected = selected
                                    )
                                    if (!hasCustomIcon && showLabel && (alwaysShowLabel || selected)) {
                                        AppText(
                                            text = stringResource(destination.labelId),
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberMainPageLifecycleOwner(isActive: Boolean): LifecycleOwner {
    val parentLifecycle = LocalLifecycleOwner.current.lifecycle
    val currentActive by rememberUpdatedState(isActive)
    val owner = remember(parentLifecycle) { MainPageLifecycleOwner() }

    DisposableEffect(parentLifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            owner.update(parentLifecycle.currentState, currentActive)
        }
        parentLifecycle.addObserver(observer)
        owner.update(parentLifecycle.currentState, currentActive)
        onDispose {
            parentLifecycle.removeObserver(observer)
            owner.destroy()
        }
    }

    LaunchedEffect(isActive, parentLifecycle) {
        owner.update(parentLifecycle.currentState, isActive)
    }

    return owner
}

private class MainPageLifecycleOwner : LifecycleOwner {

    private val registry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle = registry

    fun update(parentState: Lifecycle.State, isActive: Boolean) {
        registry.currentState = when {
            parentState == Lifecycle.State.DESTROYED -> Lifecycle.State.DESTROYED
            parentState == Lifecycle.State.INITIALIZED -> Lifecycle.State.INITIALIZED
            parentState == Lifecycle.State.CREATED -> Lifecycle.State.CREATED
            isActive -> parentState
            else -> Lifecycle.State.STARTED
        }
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}

@Composable
private fun BookshelfRailGroupMenuRoute(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onBeforeSelectGroup: suspend () -> Unit,
    viewModel: BookshelfViewModel = koinViewModel()
) {
    val groupState by viewModel.groupSelectorState.collectAsStateWithLifecycle()
    BookshelfRailGroupMenu(
        expanded = expanded,
        state = groupState,
        onDismissRequest = onDismissRequest,
        onSelectGroup = { groupId ->
            onBeforeSelectGroup()
            viewModel.onIntent(
                io.legado.app.ui.main.bookshelf.BookshelfIntent.ChangeGroup(groupId)
            )
        },
    )
}

@Composable
private fun BookshelfRailGroupMenu(
    expanded: Boolean,
    state: io.legado.app.ui.main.bookshelf.BookshelfGroupSelectorState,
    onDismissRequest: () -> Unit,
    onSelectGroup: suspend (Long) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()

    RoundDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) { dismiss ->
        state.groups.forEachIndexed { groupIndex, group ->
            RoundDropdownMenuItem(
                text = group.groupName,
                onClick = {
                    coroutineScope.launch {
                        onSelectGroup(group.groupId)
                        dismiss()
                    }
                },
                trailingIcon = {
                    if (state.selectedGroupIndex == groupIndex) {
                        Icon(
                            Icons.Default.Check,
                            null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun NavigationIcon(
    destination: MainDestination,
    selected: Boolean,
    customIconPath: String,
    modifier: Modifier = Modifier
) {
    if (customIconPath.isNotEmpty()) {
        AsyncImage(
            model = customIconPath,
            contentDescription = null,
            modifier = modifier.size(40.dp)
        )
    } else {
        val icon = AppIcons.mainDestination(destination, selected)
        AppIcon(icon, contentDescription = null, modifier = modifier)
    }
}
