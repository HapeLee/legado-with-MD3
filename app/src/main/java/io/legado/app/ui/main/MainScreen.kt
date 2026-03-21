package io.legado.app.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import io.legado.app.ui.main.explore.ExploreScreen
import io.legado.app.ui.main.my.MyScreen
import io.legado.app.ui.main.rss.RssScreen
import io.legado.app.ui.theme.regularHazeEffect
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.GlassDefaults
import io.legado.app.utils.startActivity
import io.legado.app.utils.startActivityForBook
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = koinViewModel(),
    useRail: Boolean
) {
    val context = LocalContext.current
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

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
                            text = { Text(stringResource(R.string.search)) }
                        )
                    }
                }
            ) {
                val labelVisibilityMode = MainConfig.labelVisibilityMode
                destinations.forEachIndexed { index, destination ->
                    val selected = pagerState.targetPage == index
                    WideNavigationRailItem(
                        railExpanded = navState.targetValue == WideNavigationRailValue.Expanded,
                        selected = selected,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        icon = {
                            NavigationIcon(destination, selected, uiState.upBooksCount)
                        },
                        label = if (labelVisibilityMode != "unlabeled") {
                            { Text(stringResource(destination.labelId)) }
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
                    NavigationBar(
                        modifier = Modifier
                            .regularHazeEffect(state = hazeState)
                            .height(if (isUnlabeled) 64.dp else 80.dp),
                        containerColor = GlassDefaults.glassColor(
                            noBlurColor = BottomAppBarDefaults.containerColor,
                            blurAlpha = GlassDefaults.DefaultBlurAlpha
                        )
                    ) {
                        val alwaysShowLabel = when (labelVisibilityMode) {
                            "labeled" -> true
                            "selected" -> false
                            "unlabeled" -> false
                            else -> false
                        }
                        destinations.forEachIndexed { index, destination ->
                            val selected = pagerState.targetPage == index
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                },
                                icon = {
                                    NavigationIcon(destination, selected, uiState.upBooksCount)
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = GlassDefaults.glassColor(
                                        noBlurColor = MaterialTheme.colorScheme.secondaryContainer,
                                        blurAlpha = GlassDefaults.ThickBlurAlpha
                                    ),
                                ),
                                label = if (labelVisibilityMode != "unlabeled") {
                                    { Text(stringResource(destination.labelId)) }
                                } else null,
                                alwaysShowLabel = alwaysShowLabel
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
                    when (destinations[page]) {
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
    upBooksCount: Int
) {
    val icon = if (selected) destination.selectedIcon else destination.icon
    if (destination == MainDestination.Bookshelf && upBooksCount > 0) {
        BadgedBox(badge = { Badge { Text(upBooksCount.toString()) } }) {
            Icon(icon, contentDescription = null)
        }
    } else {
        Icon(icon, contentDescription = null)
    }
}
