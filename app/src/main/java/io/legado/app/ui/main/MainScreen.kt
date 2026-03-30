package io.legado.app.ui.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import io.legado.app.R
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.config.mainConfig.MainConfig
import io.legado.app.ui.main.bookshelf.BookshelfScreen
import io.legado.app.ui.main.bookshelf.BookshelfViewModel
import io.legado.app.ui.main.explore.ExploreScreen
import io.legado.app.ui.main.my.MyScreen
import io.legado.app.ui.main.rss.RssScreen
import io.legado.app.ui.theme.regularHazeEffect
import io.legado.app.ui.widget.components.AppNavigationBar
import io.legado.app.ui.widget.components.AppNavigationBarItem
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.GlassDefaults
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.startActivity
import io.legado.app.utils.startActivityForBook
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(
    ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class
)
@Composable
fun MainScreen(
    viewModel: MainViewModel = koinViewModel(),
    useRail: Boolean,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    val bookshelfViewModel: BookshelfViewModel = koinViewModel()
    val bookshelfUiState by bookshelfViewModel.uiState.collectAsState()

    val hazeState = remember { HazeState() }
    val destinations = remember(MainConfig.showDiscovery, MainConfig.showRSS) {
        MainDestination.mainDestinations.filter {
            when (it) {
                MainDestination.Explore -> MainConfig.showDiscovery
                MainDestination.Rss -> MainConfig.showRSS
                else -> true
            }
        }
    }

    val initialPage = remember(destinations) {
        val index = destinations.indexOfFirst { it.route == MainConfig.defaultHomePage }
        if (index != -1) index else 0
    }
    val pagerState = rememberPagerState(initialPage = initialPage) { destinations.size }

    val navState = rememberWideNavigationRailState(
        initialValue = if (MainConfig.navExtended)
            WideNavigationRailValue.Expanded
        else
            WideNavigationRailValue.Collapsed
    )

    LaunchedEffect(navState.currentValue) {
        MainConfig.navExtended =
            navState.currentValue == WideNavigationRailValue.Expanded
    }

    Row(modifier = Modifier.fillMaxSize()) {
        if (useRail && MainConfig.showBottomView) {
            WideNavigationRail(
                state = navState,
                header = {
                    val expanded = navState.targetValue == WideNavigationRailValue.Expanded

                    Column {
                        IconButton(
                            modifier = Modifier.padding(start = 24.dp),
                            onClick = {
                                coroutineScope.launch {
                                    if (expanded) navState.collapse()
                                    else navState.expand()
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
                            onClick = { context.startActivity<SearchActivity>() },
                            expanded = expanded,
                            icon = { Icon(Icons.Default.Search, contentDescription = null) },
                            text = { AppText(stringResource(R.string.search)) }
                        )
                    }
                }
            ) {
                val labelVisibilityMode = MainConfig.labelVisibilityMode
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
                                    upBooksCount = uiState.upBooksCount,
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
                                    RoundDropdownMenu(
                                        expanded = showGroupMenu,
                                        onDismissRequest = { showGroupMenu = false }
                                    ) { dismiss ->
                                        bookshelfUiState.groups.forEachIndexed { groupIndex, group ->
                                            RoundDropdownMenuItem(
                                                text = { AppText(group.groupName) },
                                                onClick = {
                                                    coroutineScope.launch {
                                                        if (pagerState.currentPage != index) {
                                                            pagerState.scrollToPage(index)
                                                        }
                                                        bookshelfViewModel.changeGroup(group.groupId)
                                                        dismiss()
                                                    }
                                                },
                                                trailingIcon = {
                                                    if (bookshelfUiState.selectedGroupIndex == groupIndex) {
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
                if (!useRail && MainConfig.showBottomView) {
                    val labelVisibilityMode = MainConfig.labelVisibilityMode
                    val isUnlabeled = labelVisibilityMode == "unlabeled"

                    AppNavigationBar(
                        modifier = Modifier
                            .regularHazeEffect(state = hazeState)
                            .height(if (isUnlabeled) 64.dp else 80.dp)
                    ) {
                        val alwaysShowLabel = when (labelVisibilityMode) {
                            "labeled" -> true
                            else -> false
                        }
                        destinations.forEachIndexed { index, destination ->
                            val selected = pagerState.targetPage == index
                            AppNavigationBarItem(
                                selected = selected,
                                onClick = {
                                    coroutineScope.launch { pagerState.animateScrollToPage(index) }
                                },
                                labelString = stringResource(destination.labelId),
                                miuixIcon = if (selected) destination.miuixSelectedIcon else destination.miuixIcon,
                                m3Icon = {
                                    if (selected) destination.m3SelectedIcon else destination.m3Icon
                                },
                                m3IndicatorColor = GlassDefaults.glassColor(
                                    noBlurColor = MaterialTheme.colorScheme.secondaryContainer,
                                    blurAlpha = GlassDefaults.ThickBlurAlpha
                                ),
                                m3ShowLabel = !isUnlabeled,
                                m3AlwaysShowLabel = alwaysShowLabel
                            )
                        }
                    }
                }
            },
            contentWindowInsets = WindowInsets(0)
        ) { _ ->
            Box(modifier = Modifier.hazeSource(hazeState)) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = true,
                    beyondViewportPageCount = 3
                ) { page ->
                    val destination = destinations.getOrNull(page) ?: return@HorizontalPager
                    when (destination) {
                        MainDestination.Bookshelf -> BookshelfScreen(
                            onBookClick = { book ->
                                context.startActivityForBook(book)
                            },
                            onBookLongClick = { book ->
                                context.startActivity<BookInfoActivity> {
                                    putExtra("name", book.name)
                                    putExtra("author", book.author)
                                    putExtra("bookUrl", book.bookUrl)
                                }
                            }
                        )

                        MainDestination.Explore -> ExploreScreen()
                        MainDestination.Rss -> RssScreen()
                        MainDestination.My -> MyScreen(
                            viewModel = koinViewModel(),
                            onOpenSettings = onOpenSettings,
                            onNavigate = { event ->
                                viewModel.onPrefClickEvent(context, event)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavigationIcon(
    destination: MainDestination,
    selected: Boolean,
    upBooksCount: Int,
    modifier: Modifier = Modifier
) {
    val icon = if (selected) destination.m3SelectedIcon else destination.m3Icon
    Box(modifier = modifier) {
        if (destination == MainDestination.Bookshelf && upBooksCount > 0) {
            BadgedBox(badge = { Badge { AppText(upBooksCount.toString()) } }) {
                Icon(icon, contentDescription = null)
            }
        } else {
            Icon(icon, contentDescription = null)
        }
    }
}
