package io.legado.app.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import io.legado.app.R
import io.legado.app.ui.main.bookshelf.BookshelfScreen
import io.legado.app.ui.main.bookshelf.BookshelfViewModel
import io.legado.app.ui.main.explore.ExploreScreen
import io.legado.app.ui.main.my.MyScreen
import io.legado.app.ui.main.my.PrefClickEvent
import io.legado.app.ui.main.rss.RssScreen
import io.legado.app.ui.theme.regularHazeEffect
import io.legado.app.ui.widget.components.AppNavigationBar
import io.legado.app.ui.widget.components.AppNavigationBarItem
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.FloatingBottomBar
import io.legado.app.ui.widget.components.FloatingBottomBarItem
import io.legado.app.ui.widget.components.GlassDefaults
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.utils.sendToClip
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivityForBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel

@OptIn(
    ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class
)
@Composable
fun MainScreen(
    viewModel: MainViewModel = koinViewModel(),
    useRail: Boolean,
    onOpenSettings: () -> Unit,
    onNavigateToSearch: (String?) -> Unit,
    onNavigateToRemoteImport: () -> Unit,
    onNavigateToLocalImport: () -> Unit,
    onNavigateToCache: (Long) -> Unit,
    onNavigateToBookCacheManage: () -> Unit,
    onNavigateToBookInfo: (name: String, author: String, bookUrl: String) -> Unit,
    onNavigateToExploreShow: (title: String?, sourceUrl: String, exploreUrl: String?) -> Unit,
    onNavigateToRssSort: (sourceUrl: String, sortUrl: String?, key: String?) -> Unit,
    onNavigateToRssRead: (title: String?, origin: String, link: String?, openUrl: String?) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val mainUiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel, context) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is MainEffect.OpenUrl -> {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(effect.url))
                    )
                }

                is MainEffect.CopyUrl -> context.sendToClip(effect.url)
                is MainEffect.ShowMarkdown -> {
                    val activity = context as? AppCompatActivity ?: return@collect
                    val title = effect.title.ifBlank { context.getString(R.string.help) }
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
            }
        }
    }

    val hazeState = remember { HazeState() }
    val floatingBarSurfaceColor = MaterialTheme.colorScheme.surface
    val floatingBarBackdrop = rememberLayerBackdrop {
        drawRect(floatingBarSurfaceColor)
        drawContent()
    }
    val destinations = mainUiState.destinations

    val initialPage = remember(destinations, mainUiState.defaultHomePage) {
        val index = destinations.indexOfFirst { it.route == mainUiState.defaultHomePage }
        if (index != -1) index else 0
    }
    val pagerState = rememberPagerState(initialPage = initialPage) { destinations.size }
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
            WideNavigationRail(
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
                                    viewModel.setNavExtended(targetExpanded)
                                }
                            }
                        ) {
                            Icon(
                                if (expanded)
                                    Icons.AutoMirrored.Filled.MenuOpen
                                else
                                    Icons.Default.Menu,
                                contentDescription = null
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        ExtendedFloatingActionButton(
                            modifier = Modifier.padding(start = 20.dp),
                            onClick = { onNavigateToSearch(null) },
                            expanded = expanded,
                            icon = { Icon(Icons.Default.Search, contentDescription = null) },
                            text = { AppText(stringResource(R.string.search)) }
                        )
                    }
                }
            ) {
                destinations.forEachIndexed { index, destination ->
                    val selected = pagerState.targetPage == index
                    var showGroupMenu by remember { mutableStateOf(false) }
                    val haptic = LocalHapticFeedback.current

                    WideNavigationRailItem(
                        railExpanded = navState.targetValue == WideNavigationRailValue.Expanded,
                        selected = selected,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        icon = {
                            Box {
                                NavigationIcon(
                                    destination = destination,
                                    selected = selected,
                                    modifier = if (destination == MainDestination.Bookshelf) {
                                        Modifier.combinedClickable(
                                            onClick = {
                                                coroutineScope.launch {
                                                    pagerState.animateScrollToPage(index)
                                                }
                                            },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                showGroupMenu = true
                                            }
                                        )
                                    } else Modifier
                                )

                                if (destination == MainDestination.Bookshelf && showGroupMenu) {
                                    BookshelfRailGroupMenu(
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
                            { AppText(stringResource(destination.labelId)) }
                        } else null
                    )
                }
            }
        }

        AppScaffold(
            modifier = Modifier.weight(1f),
            bottomBar = {
                if (!useRail && mainUiState.showBottomView) {
                    if (useFloatingBottomBar) {
                        Box(modifier = Modifier.fillMaxWidth()) {
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
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                },
                                backdrop = floatingBarBackdrop,
                                tabsCount = destinations.size,
                                isBlurEnabled = useLiquidGlass
                            ) {
                                destinations.forEachIndexed { index, destination ->
                                    val selected = pagerState.targetPage == index
                                    FloatingBottomBarItem(
                                        onClick = {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(index)
                                            }
                                        },
                                        modifier = Modifier.defaultMinSize(minWidth = 76.dp)
                                    ) {
                                        NavigationIcon(
                                            destination = destination,
                                            selected = selected
                                        )
                                        if (showLabel && (alwaysShowLabel || selected)) {
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
                    } else {
                        AppNavigationBar(
                            modifier = Modifier
                                .regularHazeEffect(state = hazeState)
                        ) {
                            destinations.forEachIndexed { index, destination ->
                                val selected = pagerState.targetPage == index
                                AppNavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        coroutineScope.launch { pagerState.animateScrollToPage(index) }
                                    },
                                    labelString = stringResource(destination.labelId),
                                    iconVector = AppIcons.mainDestination(destination, selected),
                                    m3Icon = {
                                        NavigationIcon(
                                            destination = destination,
                                            selected = selected
                                        )
                                    },
                                    m3IndicatorColor = GlassDefaults.glassColor(
                                        noBlurColor = MaterialTheme.colorScheme.secondaryContainer,
                                        blurAlpha = GlassDefaults.ThickBlurAlpha
                                    ),
                                    m3ShowLabel = showLabel,
                                    m3AlwaysShowLabel = alwaysShowLabel
                                )
                            }
                        }
                    }
                }
            },
            contentWindowInsets = WindowInsets(0)
        ) { _ ->
            Box(
                modifier = Modifier
                    .hazeSource(hazeState)
                    .then(
                        if (useFloatingBottomBar) {
                            Modifier.layerBackdrop(floatingBarBackdrop)
                        } else {
                            Modifier
                        }
                    )
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = true,
                    beyondViewportPageCount = 1
                ) { page ->
                    val destination = destinations.getOrNull(page) ?: return@HorizontalPager
                    when (destination) {
                        MainDestination.Bookshelf -> BookshelfScreen(
                            onBookClick = { book ->
                                context.startActivityForBook(book)
                            },
                            onBookLongClick = { book ->
                                onNavigateToBookInfo(book.name, book.author, book.bookUrl)
                            },
                            onNavigateToSearch = { query -> onNavigateToSearch(query) },
                            onNavigateToRemoteImport = onNavigateToRemoteImport,
                            onNavigateToLocalImport = onNavigateToLocalImport,
                            onNavigateToCache = onNavigateToCache,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                        )

                        MainDestination.Explore -> ExploreScreen(
                            onOpenExploreShow = onNavigateToExploreShow
                        )
                        MainDestination.Rss -> RssScreen(
                            onOpenSort = { sourceUrl, sortUrl, key ->
                                onNavigateToRssSort(sourceUrl, sortUrl, key)
                            },
                            onOpenRead = { title, origin, link, openUrl ->
                                onNavigateToRssRead(title, origin, link, openUrl)
                            }
                        )
                        MainDestination.My -> MyScreen(
                            onOpenSettings = onOpenSettings,
                            onNavigate = { event ->
                                if (event == PrefClickEvent.OpenBookCacheManage) {
                                    onNavigateToBookCacheManage()
                                } else {
                                    viewModel.onPrefClickEvent(event)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookshelfRailGroupMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onBeforeSelectGroup: suspend () -> Unit,
    viewModel: BookshelfViewModel = koinViewModel()
) {
    val groupState by viewModel.groupSelectorState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    RoundDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) { dismiss ->
        groupState.groups.forEachIndexed { groupIndex, group ->
            RoundDropdownMenuItem(
                text = group.groupName,
                onClick = {
                    coroutineScope.launch {
                        onBeforeSelectGroup()
                        viewModel.changeGroup(group.groupId)
                        dismiss()
                    }
                },
                trailingIcon = {
                    if (groupState.selectedGroupIndex == groupIndex) {
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
    modifier: Modifier = Modifier
) {
    val icon = AppIcons.mainDestination(destination, selected)
    AppIcon(icon, contentDescription = null, modifier = modifier)
}
