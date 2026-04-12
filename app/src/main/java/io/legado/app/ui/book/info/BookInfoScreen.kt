package io.legado.app.ui.book.info

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.ImageLoader
import coil.compose.AsyncImage
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.help.book.isLocal
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.about.AppLogSheet
import io.legado.app.ui.book.changecover.ChangeCoverViewModel
import io.legado.app.ui.book.changesource.ChangeBookSourceViewModel
import io.legado.app.ui.book.group.GroupEditSheet
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.book.source.manage.BookSourceActivity
import io.legado.app.ui.config.coverConfig.CoverConfig
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ProvideThemeOverride
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.theme.ThemeOverrideState
import io.legado.app.ui.theme.rememberImageSeedColor
import io.legado.app.ui.theme.rememberThemeOverride
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.TopBarActionButton
import io.legado.app.ui.widget.components.button.TopBarButtonVariant
import io.legado.app.ui.widget.components.button.TopBarNavigationButton
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.cover.BookCover
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AnimatedTextLine
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.MiuixGlassScrollBehavior
import io.legado.app.utils.StartActivityContract
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookInfoScreen(
    state: BookInfoUiState,
    onIntent: (BookInfoIntent) -> Unit,
    onBack: () -> Unit,
) {
    val bookColorTheme = rememberBookInfoColorTheme(state.book)

    BookInfoColorTheme(theme = bookColorTheme) {
        BookInfoScreenContent(
            state = state,
            onIntent = onIntent,
            onBack = onBack,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookInfoScreenContent(
    state: BookInfoUiState,
    onIntent: (BookInfoIntent) -> Unit,
    onBack: () -> Unit,
) {
    val isMiuix = ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)
    val miuixScrollBehavior = if (isMiuix) {
        GlassTopAppBarDefaults.defaultScrollBehavior() as? MiuixGlassScrollBehavior
    } else {
        null
    }
    val miuixCollapsedFraction = miuixScrollBehavior?.collapsedFraction ?: 0f
    val listState = rememberLazyListState()
    val colorThresholdPx = with(LocalDensity.current) { 6.dp.roundToPx() }
    val showAppBarColor by remember(listState, isMiuix, colorThresholdPx, miuixCollapsedFraction) {
        derivedStateOf {
            if (isMiuix) {
                miuixCollapsedFraction > 0.01f
            } else {
                listState.firstVisibleItemIndex > 0 ||
                    listState.firstVisibleItemScrollOffset > colorThresholdPx
            }
        }
    }
    val appBarColor by animateColorAsState(
        targetValue = if (showAppBarColor) {
            LegadoTheme.colorScheme.surfaceContainer
        } else {
            LegadoTheme.colorScheme.surfaceContainer.copy(alpha = 0f)
        },
        label = "book-info-top-bar-color",
    )
    val pullState = rememberPullToRefreshState()
    var showMenu by rememberSaveable { mutableStateOf(false) }

    BackHandler { onIntent(BookInfoIntent.BackPressed) }

    AppScaffold(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (miuixScrollBehavior != null) {
                    Modifier.nestedScroll(miuixScrollBehavior.nestedScrollConnection)
                } else {
                    Modifier
                }
            ),
        topBar = {
            if (isMiuix) {
                MiuixTopAppBar(
                    title = "",
                    color = appBarColor,
                    navigationIcon = {
                        TopBarNavigationButton(
                            onClick = { onIntent(BookInfoIntent.BackPressed) }
                        )
                    },
                    actions = {
                        BookInfoTopBarActions(
                            state = state,
                            showMenu = showMenu,
                            onShowMenuChange = { showMenu = it },
                            onMenuAction = { onIntent(BookInfoIntent.MenuAction(it)) },
                        )
                    },
                    scrollBehavior = miuixScrollBehavior?.miuixBehavior
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = "",
                            maxLines = 1,
                        )
                    },
                    navigationIcon = {
                        TopBarNavigationButton(
                            onClick = { onIntent(BookInfoIntent.BackPressed) }
                        )
                    },
                    actions = {
                        BookInfoTopBarActions(
                            state = state,
                            showMenu = showMenu,
                            onShowMenuChange = { showMenu = it },
                            onMenuAction = { onIntent(BookInfoIntent.MenuAction(it)) },
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = appBarColor,
                        scrolledContainerColor = appBarColor,
                        navigationIconContentColor = LegadoTheme.colorScheme.onSurface,
                        titleContentColor = LegadoTheme.colorScheme.onSurface,
                        actionIconContentColor = LegadoTheme.colorScheme.onSurface,
                    ),
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onIntent(BookInfoIntent.ReadClick) },
                containerColor = LegadoTheme.colorScheme.primaryContainer,
                contentColor = LegadoTheme.colorScheme.onPrimaryContainer,
                icon = { Icon(Icons.Default.Book, null) },
                text = { Text(stringResource(R.string.reading)) },
            )
        },
    ) { paddingValues ->
        val book = state.book
        if (book == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    color = LegadoTheme.colorScheme.primary,
                    trackColor = LegadoTheme.colorScheme.surfaceContainerHighest,
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                BookInfoBackdrop(book)
                PullToRefreshBox(
                    modifier = Modifier.fillMaxSize(),
                    state = pullState,
                    isRefreshing = state.isTocLoading,
                    onRefresh = { onIntent(BookInfoIntent.MenuAction(BookInfoMenuAction.Refresh)) },
                    indicator = {
                        PullToRefreshDefaults.LoadingIndicator(
                            state = pullState,
                            isRefreshing = state.isTocLoading,
                            containerColor = LegadoTheme.colorScheme.surfaceContainerHigh,
                            color = LegadoTheme.colorScheme.primary,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = paddingValues.calculateTopPadding())
                        )
                    }
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = paddingValues.calculateTopPadding() + 8.dp,
                            bottom = paddingValues.calculateBottomPadding() + 88.dp,
                        ),
                    ) {
                        item {
                            BookInfoHeader(
                                book = book,
                                kindLabels = state.kindLabels,
                                groupNames = state.groupNames,
                                onCoverClick = { onIntent(BookInfoIntent.CoverClick) },
                                onCoverLongClick = { onIntent(BookInfoIntent.CoverLongClick) },
                                onAuthorClick = { onIntent(BookInfoIntent.AuthorClick(it)) },
                                onBookNameClick = { onIntent(BookInfoIntent.BookNameClick(it)) },
                                onOriginClick = { onIntent(BookInfoIntent.OriginClick) },
                            )
                        }
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = LegadoTheme.colorScheme.surface
                                    )
                                    .padding(bottom = 24.dp)
                            ) {
                                BookInfoActions(
                                    inBookshelf = state.inBookshelf,
                                    onShelfClick = { onIntent(BookInfoIntent.ShelfClick) },
                                    onTocClick = { onIntent(BookInfoIntent.TocClick) },
                                    onGroupClick = { onIntent(BookInfoIntent.GroupClick) },
                                    onSourceClick = { onIntent(BookInfoIntent.ChangeSourceClick) },
                                )
                                BookInfoSummary(
                                    book = book,
                                    chapterList = state.chapterList,
                                    onRemarkClick = { onIntent(BookInfoIntent.RemarkClick) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val currentSheet = state.sheet
    var renderedSheet by remember { mutableStateOf<BookInfoSheet>(BookInfoSheet.None) }

    LaunchedEffect(currentSheet) {
        if (currentSheet == BookInfoSheet.None) {
            delay(300)
            renderedSheet = BookInfoSheet.None
        } else {
            renderedSheet = currentSheet
        }
    }

    when (val sheet = renderedSheet) {
        BookInfoSheet.None -> Unit
        BookInfoSheet.CoverPicker -> ChangeCoverSheet(
            show = currentSheet == BookInfoSheet.CoverPicker,
            name = state.book?.name.orEmpty(),
            author = state.book?.author.orEmpty(),
            onDismissRequest = { onIntent(BookInfoIntent.DismissSheet) },
            onSelect = { onIntent(BookInfoIntent.SelectCover(it)) },
        )
        BookInfoSheet.GroupPicker -> GroupSelectSheet(
            show = currentSheet == BookInfoSheet.GroupPicker,
            currentGroupId = state.book?.group ?: 0L,
            onDismissRequest = { onIntent(BookInfoIntent.DismissSheet) },
            onConfirm = { onIntent(BookInfoIntent.SelectGroup(it)) },
        )
        BookInfoSheet.SourcePicker -> state.book?.let { book ->
            ChangeSourceSheet(
                show = currentSheet == BookInfoSheet.SourcePicker,
                oldBook = book,
                onDismissRequest = { onIntent(BookInfoIntent.DismissSheet) },
                onReplace = { source, newBook, toc ->
                    onIntent(BookInfoIntent.ReplaceWithSource(source, newBook, toc))
                },
                onAddAsNew = { newBook, toc ->
                    onIntent(BookInfoIntent.AddSourceAsNewBook(newBook, toc))
                },
            )
        }
        is BookInfoSheet.WebFiles -> WebFileSheet(
            show = currentSheet is BookInfoSheet.WebFiles,
            files = state.webFiles,
            title = stringResource(R.string.download_and_import_file),
            onDismissRequest = { onIntent(BookInfoIntent.DismissSheet) },
            onSelect = { onIntent(BookInfoIntent.SelectWebFile(it, sheet.openAfterImport)) },
        )
        is BookInfoSheet.ArchiveEntries -> WebFileSheet(
            show = currentSheet is BookInfoSheet.ArchiveEntries,
            files = sheet.entries.map { BookInfoWebFile(it, it) },
            title = stringResource(R.string.import_select_book),
            onDismissRequest = { onIntent(BookInfoIntent.DismissSheet) },
            onSelect = {
                onIntent(
                    BookInfoIntent.SelectArchiveEntry(
                        archiveUri = sheet.archiveUri,
                        entryName = it.name,
                        openAfterImport = sheet.openAfterImport,
                    )
                )
            },
        )
    }

    BookInfoDialogs(state = state, onIntent = onIntent, onBack = onBack)
}

@Composable
private fun BookInfoColorTheme(
    theme: ThemeOverrideState?,
    content: @Composable () -> Unit,
) {
    ProvideThemeOverride(theme = theme, content = content)
}

@Composable
private fun rememberBookInfoColorTheme(book: Book?): ThemeOverrideState? {
    val imageLoader = koinInject<ImageLoader>()
    val coverPath = book?.getDisplayCover()
    val sourceOrigin = book?.origin
    val loadOnlyWifi = CoverConfig.loadCoverOnlyWifi

    val seedColor = rememberImageSeedColor(
        imageLoader = imageLoader,
        data = coverPath,
        requestKey = listOf(coverPath, sourceOrigin, loadOnlyWifi),
    ) {
        setParameter("sourceOrigin", sourceOrigin)
        setParameter("loadOnlyWifi", loadOnlyWifi)
    }

    return rememberThemeOverride(seedColor)
}

@Composable
private fun BookInfoTopBarActions(
    state: BookInfoUiState,
    showMenu: Boolean,
    onShowMenuChange: (Boolean) -> Unit,
    onMenuAction: (BookInfoMenuAction) -> Unit,
) {
    if (state.inBookshelf) {
        TopBarActionButton(
            onClick = { onMenuAction(BookInfoMenuAction.Edit) },
            imageVector = Icons.Default.Edit,
        )
    }
    TopBarActionButton(
        onClick = { onMenuAction(BookInfoMenuAction.Share) },
        imageVector = Icons.Default.Share,
    )
    TopBarActionButton(
        onClick = { onShowMenuChange(true) },
        imageVector = Icons.Default.MoreVert,
    )
    BookInfoOverflowMenu(
        expanded = showMenu,
        onDismissRequest = { onShowMenuChange(false) },
        state = state,
        onMenuAction = {
            onShowMenuChange(false)
            onMenuAction(it)
        }
    )
}

@Composable
private fun BookInfoBackdrop(book: Book) {
    val cover = book.getDisplayCover()
    val seedOverlay = lerp(
        LegadoTheme.colorScheme.secondaryContainer,
        LegadoTheme.seedColor,
        0.42f
    )
    Box(modifier = Modifier.fillMaxSize()) {
        if (!cover.isNullOrBlank()) {
            AsyncImage(
                model = cover,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(32.dp),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(seedOverlay.copy(alpha = 0.34f))
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.18f to seedOverlay.copy(alpha = 0.10f),
                            0.34f to seedOverlay.copy(alpha = 0.18f),
                            0.52f to LegadoTheme.colorScheme.surface.copy(alpha = 0.82f),
                            0.72f to LegadoTheme.colorScheme.surface,
                            1f to LegadoTheme.colorScheme.surface,
                        )
                    )
                )
        )
    }
}

@Composable
private fun BookInfoOverflowMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    state: BookInfoUiState,
    onMenuAction: (BookInfoMenuAction) -> Unit,
) {
    val book = state.book
    RoundDropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest) {
        if (state.inBookshelf) {
            RoundDropdownMenuItem(
                text = stringResource(R.string.edit),
                onClick = { onMenuAction(BookInfoMenuAction.Edit) }
            )
        }
        RoundDropdownMenuItem(
            text = stringResource(R.string.refresh),
            onClick = { onMenuAction(BookInfoMenuAction.Refresh) }
        )
        if (book?.isLocal == true) {
            RoundDropdownMenuItem(
                text = stringResource(R.string.re_sync_webdav),
                onClick = { onMenuAction(BookInfoMenuAction.SyncRemote) }
            )
            RoundDropdownMenuItem(
                text = stringResource(R.string.upload_to_remote),
                onClick = { onMenuAction(BookInfoMenuAction.Upload) }
            )
        }
        if (!state.bookSource?.loginUrl.isNullOrBlank()) {
            RoundDropdownMenuItem(
                text = stringResource(R.string.login),
                onClick = { onMenuAction(BookInfoMenuAction.Login) }
            )
        }
        if (state.bookSource != null) {
            RoundDropdownMenuItem(
                text = stringResource(R.string.set_source_variable),
                onClick = { onMenuAction(BookInfoMenuAction.SetSourceVariable) }
            )
            RoundDropdownMenuItem(
                text = stringResource(R.string.set_book_variable),
                onClick = { onMenuAction(BookInfoMenuAction.SetBookVariable) }
            )
            RoundDropdownMenuItem(
                text = stringResource(R.string.copy_book_url),
                onClick = { onMenuAction(BookInfoMenuAction.CopyBookUrl) }
            )
            RoundDropdownMenuItem(
                text = stringResource(R.string.copy_toc_url),
                onClick = { onMenuAction(BookInfoMenuAction.CopyTocUrl) }
            )
        }
        RoundDropdownMenuItem(
            text = stringResource(R.string.to_top),
            onClick = { onMenuAction(BookInfoMenuAction.Top) }
        )
        RoundDropdownMenuItem(
            text = stringResource(R.string.allow_update),
            onClick = { onMenuAction(BookInfoMenuAction.ToggleCanUpdate) },
            trailingIcon = { if (book?.canUpdate == true) Icon(Icons.Default.Star, null) }
        )
        if (book?.isLocal == true && book.type and io.legado.app.constant.BookType.text > 0) {
            RoundDropdownMenuItem(
                text = stringResource(R.string.split_long_chapter),
                onClick = { onMenuAction(BookInfoMenuAction.ToggleSplitLongChapter) },
                trailingIcon = { if (book.getSplitLongChapter()) Icon(Icons.Default.Star, null) }
            )
        }
        RoundDropdownMenuItem(
            text = stringResource(R.string.delete_alert),
            onClick = { onMenuAction(BookInfoMenuAction.ToggleDeleteAlert) },
            trailingIcon = { if (io.legado.app.help.config.LocalConfig.bookInfoDeleteAlert) Icon(Icons.Default.Star, null) }
        )
        RoundDropdownMenuItem(
            text = stringResource(R.string.clear_cache),
            onClick = { onMenuAction(BookInfoMenuAction.ClearCache) }
        )
        RoundDropdownMenuItem(
            text = stringResource(R.string.log),
            onClick = { onMenuAction(BookInfoMenuAction.ShowLog) }
        )
    }
}

@Composable
private fun BookInfoHeader(
    book: Book,
    kindLabels: List<String>,
    groupNames: String?,
    onCoverClick: () -> Unit,
    onCoverLongClick: () -> Unit,
    onAuthorClick: (Boolean) -> Unit,
    onBookNameClick: (Boolean) -> Unit,
    onOriginClick: () -> Unit,
) {
    val labelBackground = lerp(
        LegadoTheme.colorScheme.surface,
        LegadoTheme.seedColor,
        0.1f
    ).copy(alpha = 0.82f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        lerp(LegadoTheme.colorScheme.surface, LegadoTheme.seedColor, 0.08f)
                            .copy(alpha = 0.5f),
                        LegadoTheme.colorScheme.surface,
                    )
                )
            )
            .padding(top = 16.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .width(112.dp)
                        .combinedClickable(onClick = onCoverClick, onLongClick = onCoverLongClick)
                ) {
                    BookCover(
                        name = book.name,
                        author = book.author,
                        path = book.getDisplayCover(),
                        sourceOrigin = book.origin,
                        modifier = Modifier.width(112.dp)
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .align(Alignment.CenterVertically)
                        .padding(top = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AppText(
                        text = book.name,
                        style = LegadoTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        modifier = Modifier.combinedClickable(
                            onClick = { onBookNameClick(false) },
                            onLongClick = { onBookNameClick(true) }
                        )
                    )
                    AppText(
                        text = stringResource(R.string.author_show, book.getRealAuthor()),
                        style = LegadoTheme.typography.bodyLarge,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.combinedClickable(
                            onClick = { onAuthorClick(false) },
                            onLongClick = { onAuthorClick(true) }
                        )
                    )
                    AppText(
                        text = stringResource(R.string.origin_show, book.originName),
                        style = LegadoTheme.typography.labelMedium,
                        color = LegadoTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = onOriginClick)
                    )
                }
            }
            if (kindLabels.isNotEmpty() || !groupNames.isNullOrBlank()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(
                        items = kindLabels,
                        key = { index, label -> "kind-$index-$label" }
                    ) { _, label ->
                        TextCard(
                            text = label,
                            textStyle = LegadoTheme.typography.labelLargeEmphasized,
                            backgroundColor = labelBackground,
                            contentColor = LegadoTheme.colorScheme.onSurface,
                        )
                    }
                    groupNames?.takeIf { it.isNotBlank() }?.let {
                        item(key = "group-$it") {
                            TextCard(
                                text = stringResource(R.string.group_s, it),
                                textStyle = LegadoTheme.typography.labelLargeEmphasized,
                                backgroundColor = labelBackground,
                                contentColor = LegadoTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookInfoActions(
    inBookshelf: Boolean,
    onShelfClick: () -> Unit,
    onTocClick: () -> Unit,
    onGroupClick: () -> Unit,
    onSourceClick: () -> Unit,
) {
    var awaitingShelfAddition by rememberSaveable { mutableStateOf(false) }
    var showShelfRemoveHint by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(inBookshelf, awaitingShelfAddition) {
        if (awaitingShelfAddition && inBookshelf) {
            awaitingShelfAddition = false
            showShelfRemoveHint = true
            delay(1000)
            showShelfRemoveHint = false
        } else if (!inBookshelf) {
            showShelfRemoveHint = false
        }
    }

    val shelfLabel = when {
        !inBookshelf -> stringResource(R.string.add_to_bookshelf)
        showShelfRemoveHint -> stringResource(R.string.click_to_remove)
        else -> stringResource(R.string.remove_from_bookshelf)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(LegadoTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BookInfoActionCard(
            modifier = Modifier.weight(1f),
            icon = if (inBookshelf) Icons.Outlined.Book else Icons.Default.BookmarkAdd,
            label = shelfLabel,
            onClick = {
                if (!inBookshelf) {
                    awaitingShelfAddition = true
                } else {
                    awaitingShelfAddition = false
                    showShelfRemoveHint = false
                }
                onShelfClick()
            },
        )
        BookInfoActionCard(
            modifier = Modifier.weight(1f),
            icon = Icons.AutoMirrored.Outlined.FormatListBulleted,
            label = stringResource(R.string.view_toc),
            onClick = onTocClick
        )
        BookInfoActionCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Bookmark,
            label = stringResource(R.string.change_group),
            onClick = onGroupClick
        )
        BookInfoActionCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Code,
            label = stringResource(R.string.book_source),
            onClick = onSourceClick
        )
    }
}

@Composable
private fun BookInfoActionCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = modifier,
        onClick = onClick,
        containerColor = LegadoTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f),
        contentColor = LegadoTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppIcon(icon, null)
            AnimatedTextLine(
                text = label,
                style = LegadoTheme.typography.bodySmall,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun BookInfoSummary(
    book: Book,
    chapterList: List<BookChapter>,
    onRemarkClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LegadoTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AppText(
            text = stringResource(R.string.toc_s, book.durChapterTitle ?: stringResource(R.string.loading)),
            style = LegadoTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        AppText(
            text = stringResource(R.string.lasted_show, book.latestChapterTitle ?: ""),
            style = LegadoTheme.typography.bodyMedium,
            color = LegadoTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(
                text = stringResource(R.string.read_chapter_total, book.totalChapterNum),
                style = LegadoTheme.typography.bodyMedium,
                color = LegadoTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            AppText(text = "|", color = LegadoTheme.colorScheme.secondary)
            AppText(
                text = if (book.durChapterIndex + 1 == book.totalChapterNum && book.totalChapterNum > 0) "已读完" else stringResource(R.string.read_chapter_index, book.durChapterIndex + 1),
                style = LegadoTheme.typography.labelMedium,
                color = LegadoTheme.colorScheme.secondary,
            )
        }
        if (chapterList.isEmpty()) {
            AppText(
                text = stringResource(R.string.error_load_toc),
                style = LegadoTheme.typography.bodySmall,
                color = LegadoTheme.colorScheme.error
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        book.remark?.takeIf { it.isNotBlank() }?.let { remark ->
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = onRemarkClick,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = LegadoTheme.colorScheme.surfaceContainerHigh,
                    contentColor = LegadoTheme.colorScheme.onSurface,
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AppText(text = stringResource(R.string.book_remark), style = LegadoTheme.typography.titleSmall)
                    AppText(text = remark, style = LegadoTheme.typography.labelMediumEmphasized)
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        AppText(
            text = book.getDisplayIntro().orEmpty().ifBlank { stringResource(R.string.intro_show_null) },
            style = LegadoTheme.typography.bodyMedium,
        )
    }
}
@Composable
private fun BookInfoDialogs(
    state: BookInfoUiState,
    onIntent: (BookInfoIntent) -> Unit,
    onBack: () -> Unit,
) {
    val dialog = state.dialog
    var deleteOriginal by remember(dialog) { mutableStateOf(io.legado.app.help.config.LocalConfig.deleteBookOriginal) }
    var remarkText by remember(dialog) { mutableStateOf((dialog as? BookInfoDialog.EditRemark)?.remark.orEmpty()) }

    if (dialog is BookInfoDialog.AddToShelfOnBack) {
        AppAlertDialog(
            show = true,
            onDismissRequest = { onIntent(BookInfoIntent.DismissDialog) },
            title = stringResource(R.string.add_to_bookshelf),
            text = stringResource(R.string.check_add_bookshelf, state.book?.name.orEmpty()),
            confirmText = stringResource(android.R.string.ok),
            onConfirm = { onIntent(BookInfoIntent.ConfirmBackAddToShelf) },
            dismissText = stringResource(android.R.string.cancel),
            onDismiss = onBack,
        )
    }

    if (dialog is BookInfoDialog.DeleteBook) {
        AppAlertDialog(
            show = true,
            onDismissRequest = { onIntent(BookInfoIntent.DismissDialog) },
            title = stringResource(R.string.draw),
            text = stringResource(R.string.sure_del),
            confirmText = stringResource(android.R.string.ok),
            onConfirm = {
                io.legado.app.help.config.LocalConfig.deleteBookOriginal = deleteOriginal
                onIntent(BookInfoIntent.ConfirmDelete(deleteOriginal))
            },
            dismissText = stringResource(android.R.string.cancel),
            onDismiss = { onIntent(BookInfoIntent.DismissDialog) },
            content = {
                if (dialog.isLocal) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(
                            checked = deleteOriginal,
                            onCheckedChange = { deleteOriginal = it },
                            colors = androidx.compose.material3.CheckboxDefaults.colors(
                                checkedColor = LegadoTheme.colorScheme.primary,
                                checkmarkColor = LegadoTheme.colorScheme.onPrimary,
                                uncheckedColor = LegadoTheme.colorScheme.onSurfaceVariant,
                            )
                        )
                        Text(text = stringResource(R.string.delete_book_file))
                    }
                }
            }
        )
    }

    if (dialog is BookInfoDialog.EditRemark) {
        AppAlertDialog(
            show = true,
            onDismissRequest = { onIntent(BookInfoIntent.DismissDialog) },
            title = stringResource(R.string.edit_remark),
            confirmText = stringResource(android.R.string.ok),
            onConfirm = { onIntent(BookInfoIntent.UpdateRemark(remarkText)) },
            dismissText = stringResource(android.R.string.cancel),
            onDismiss = { onIntent(BookInfoIntent.DismissDialog) },
            content = {
                AppTextField(
                    value = remarkText,
                    onValueChange = { remarkText = it },
                    label = "备注",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        )
    }

    if (dialog is BookInfoDialog.UnsupportedWebFile) {
        AppAlertDialog(
            show = true,
            onDismissRequest = { onIntent(BookInfoIntent.DismissDialog) },
            title = stringResource(R.string.draw),
            text = stringResource(R.string.file_not_supported, dialog.webFile.name),
            confirmText = stringResource(R.string.open_fun),
            onConfirm = { onIntent(BookInfoIntent.OpenUnsupportedWebFile(dialog.webFile)) },
            dismissText = stringResource(android.R.string.cancel),
            onDismiss = { onIntent(BookInfoIntent.DismissDialog) },
        )
    }

    if (dialog is BookInfoDialog.PhotoPreview) {
        AppAlertDialog(
            show = true,
            onDismissRequest = { onIntent(BookInfoIntent.DismissDialog) },
            title = stringResource(R.string.img_cover),
            confirmText = stringResource(android.R.string.ok),
            onConfirm = { onIntent(BookInfoIntent.DismissDialog) },
            content = {
                AsyncImage(
                    model = dialog.path,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        )
    }

    if (state.isBusy) {
        AppAlertDialog(
            show = true,
            onDismissRequest = {},
            content = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = LegadoTheme.colorScheme.primary,
                        trackColor = LegadoTheme.colorScheme.surfaceContainerHighest,
                    )
                }
            }
        )
    }

    AppLogSheet(show = state.showAppLogSheet, onDismissRequest = { onIntent(BookInfoIntent.DismissAppLogSheet) })
}
