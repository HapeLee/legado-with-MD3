package io.legado.app.ui.book.info

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.size.Size
import io.legado.app.R
import io.legado.app.constant.BookType
import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.help.WebCacheManager
import io.legado.app.help.coil.CoverExtras
import io.legado.app.help.webView.WebJsExtensions
import io.legado.app.ui.association.OnLineImportActivity
import io.legado.app.ui.main.homepage.modules.BannerModule
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LocalHazeState
import io.legado.app.ui.theme.LocalLegadoThemeColors
import io.legado.app.ui.theme.ProvideColorSchemeOverride
import io.legado.app.ui.theme.ThemeOverrideState
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.theme.animateColorSchemeAsState
import io.legado.app.ui.theme.fadingEdge
import io.legado.app.ui.theme.rememberImageSeedColor
import io.legado.app.ui.theme.rememberThemeOverride
import io.legado.app.ui.theme.responsiveHazeEffectFixedStyle
import io.legado.app.ui.widget.components.AppPullToRefresh
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.series.SmallTonalButton
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.card.HighlightTagRow
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.changeSource.ChangeSourceSheet
import io.legado.app.ui.widget.components.conflict.BookshelfConflictSheet
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.image.cover.BookCoverImage
import io.legado.app.ui.widget.components.image.cover.CoilBookCover
import io.legado.app.ui.widget.components.image.cover.buildCoverImageRequest
import io.legado.app.ui.widget.components.image.cover.usesDefaultBookCover
import io.legado.app.ui.widget.components.log.AppLogSheet
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.progressIndicator.AppCircularProgressIndicator
import io.legado.app.ui.widget.components.text.AnimatedTextLine
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.text.HtmlContent
import io.legado.app.ui.widget.components.text.MarkdownBlock
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarScrollBehavior
import io.legado.app.ui.widget.components.topbar.M3GlassScrollBehavior
import io.legado.app.ui.widget.components.topbar.MiuixGlassScrollBehavior
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.topbar.TopBarActionsRow
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.ui.widget.components.topbar.miuixTopBarActionsEndPadding
import io.legado.app.ui.widget.components.topbar.miuixTopBarSlotPadding
import io.legado.app.ui.widget.components.variable.VariableEditorSheet
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.HtmlFormatter
import io.legado.app.utils.openUrl
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import kotlin.math.abs
import io.legado.app.model.BookCover as BookCoverModel
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookInfoScreen(
    state: BookInfoUiState,
    groups: ImmutableList<BookGroup>,
    onIntent: (BookInfoIntent) -> Unit,
    onBack: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedCoverKey: String? = null,
) {
    val waitForSharedTransition = sharedCoverKey != null && animatedVisibilityScope != null
    val transition = animatedVisibilityScope?.transition
    val transitionSettled = transition?.let {
        it.currentState == EnterExitState.Visible &&
                it.targetState == EnterExitState.Visible &&
                !it.isRunning
    } == true
    val sharedTransitionFinished = !waitForSharedTransition || transitionSettled
    var canApplyCoverTheme by remember(
        sharedCoverKey,
        state.book?.bookUrl,
        waitForSharedTransition,
    ) {
        mutableStateOf(!waitForSharedTransition)
    }
    LaunchedEffect(sharedTransitionFinished) {
        if (sharedTransitionFinished) {
            canApplyCoverTheme = true
        }
    }
    val initiallyUsesDefaultCover = state.book?.let { usesDefaultBookCover(it.coverPath) } ?: true
    var usesDefaultCover by remember(
        state.book?.bookUrl,
        state.book?.coverPath,
        initiallyUsesDefaultCover,
    ) {
        mutableStateOf(initiallyUsesDefaultCover)
    }
    val backdropStyle = state.book?.let {
        resolveBookInfoBackdropStyle(
            book = it,
            usesDefaultCover = usesDefaultCover,
            defaultCoverBackground = state.bookInfoDefaultCoverBackground,
            networkCoverBackground = state.bookInfoNetworkCoverBackground,
        )
    }
    val bookColorTheme = rememberBookInfoColorTheme(
        book = state.book,
        enabled = backdropStyle?.showCover == true,
        usesDefaultCover = usesDefaultCover,
        followCoverColor = state.bookInfoFollowCoverColor,
        defaultCover = state.defaultCover,
        defaultCoverDark = state.defaultCoverDark,
        loadCoverOnlyOnWifi = state.loadCoverOnlyOnWifi,
    )

    BookInfoColorTheme(theme = bookColorTheme.takeIf {
        canApplyCoverTheme && backdropStyle?.showCover == true
    }) {
        BookInfoScreenContent(
            state = state,
            groups = groups,
            onIntent = onIntent,
            onBack = onBack,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            sharedCoverKey = sharedCoverKey,
            backdropStyle = backdropStyle,
            usesDefaultCover = usesDefaultCover,
            onNetworkCoverLoadError = { failedCoverPath ->
                if (failedCoverPath == state.book?.coverPath) {
                    usesDefaultCover = true
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
private fun BookInfoScreenContent(
    state: BookInfoUiState,
    groups: ImmutableList<BookGroup>,
    onIntent: (BookInfoIntent) -> Unit,
    onBack: () -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    sharedCoverKey: String?,
    backdropStyle: BookInfoBackdropStyle?,
    usesDefaultCover: Boolean,
    onNetworkCoverLoadError: (String?) -> Unit,
) {
    val isMiuix = ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)
    val scrollBehavior = if (isMiuix) {
        MiuixGlassScrollBehavior(MiuixScrollBehavior())
    } else {
        M3GlassScrollBehavior(TopAppBarDefaults.exitUntilCollapsedScrollBehavior())
    }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // 资源串在 composable 作用域内解析：LocalContext.current.getString 不感知配置变化，
    // 语言/字体缩放切换后可能拿到过期值（LocalContextGetResourceValueCall）。
    val jumpToAnotherAppMessage = stringResource(R.string.jump_to_another_app)
    val confirmLabel = stringResource(R.string.confirm)
    val jumpToAnotherApp: (Uri) -> Unit = { uri ->
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = jumpToAnotherAppMessage,
                actionLabel = confirmLabel,
            )
            if (result == SnackbarResult.ActionPerformed) {
                context.openUrl(uri)
            }
        }
    }

    AppScaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            BookInfoTransparentTopAppBar(
                state = state,
                onMenuAction = { onIntent(BookInfoIntent.MenuAction(it)) },
                onBackPressed = onBack,
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onIntent(BookInfoIntent.ReadClick) },
                containerColor = LegadoTheme.colorScheme.primaryContainer,
                contentColor = LegadoTheme.colorScheme.onPrimaryContainer,
                icon = { Icon(Icons.Default.Book, null) },
                text = { Text(stringResource(R.string.reading)) },
            )
        },
        alwaysDrawBehindBars = true,
    ) { paddingValues ->
        val book = state.book
        if (book == null) {
            Box(modifier = Modifier.fillMaxSize())
        } else {
            val resolvedBackdropStyle = requireNotNull(backdropStyle)
            Box(modifier = Modifier.fillMaxSize()) {
                BookInfoBackdrop(
                    book = book,
                    style = resolvedBackdropStyle,
                    usesDefaultCover = usesDefaultCover,
                    onNetworkCoverLoadError = onNetworkCoverLoadError,
                )
                AppPullToRefresh(
                    modifier = Modifier.fillMaxSize(),
                    isRefreshing = state.isTocLoading,
                    onRefresh = { onIntent(BookInfoIntent.MenuAction(BookInfoMenuAction.Refresh)) },
                    topPadding = paddingValues.calculateTopPadding(),
                    scrollBehavior = scrollBehavior
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
                                highlightedTags = state.highlightedTags,
                                kindLabels = state.kindLabels,
                                groupNames = state.groupNames,
                                onCoverClick = { onIntent(BookInfoIntent.CoverClick) },
                                onCoverLongClick = { onIntent(BookInfoIntent.CoverLongClick) },
                                onAuthorClick = { onIntent(BookInfoIntent.AuthorClick(it)) },
                                onBookNameClick = { onIntent(BookInfoIntent.BookNameClick(it)) },
                                onOriginClick = { onIntent(BookInfoIntent.OriginClick) },
                                onNetworkCoverLoadError = {
                                    onNetworkCoverLoadError(book.coverPath)
                                },
                                usesDefaultCover = usesDefaultCover,
                                applySeedOverlay = resolvedBackdropStyle.applySeedOverlay,
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
                                sharedCoverKey = sharedCoverKey,
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
                                    onReadRecordClick = { onIntent(BookInfoIntent.ReadRecordClick) },
                                )
                                if (
                                    state.characters.isNotEmpty() ||
                                    state.knowledgeEntries.isNotEmpty() ||
                                    state.recentEvents.isNotEmpty()
                                ) {
                                    BookInfoCharacters(
                                        characters = state.characters,
                                        onCharacterClick = {
                                            onIntent(BookInfoIntent.CharacterClick(it))
                                        },
                                        onNetworkClick = {
                                            onIntent(BookInfoIntent.CharacterNetworkClick)
                                        },
                                        onViewAllClick = {
                                            onIntent(BookInfoIntent.CharacterListClick)
                                        },
                                        onKnowledgeClick = {
                                            onIntent(BookInfoIntent.KnowledgeListClick)
                                        },
                                        onEventsClick = {
                                            onIntent(BookInfoIntent.EventListClick)
                                        },
                                    )
                                }
                                state.relatedBooks.forEach { module ->
                                    RelatedBooksBanner(
                                        title = module.title,
                                        books = module.books,
                                        onBookClick = { book, _ ->
                                            onIntent(BookInfoIntent.RelatedBookClick(book))
                                        },
                                        onMoreClick = {
                                            onIntent(BookInfoIntent.RelatedBooksMore(module.title, module.resolvedUrl))
                                        },
                                    )
                                }
                                BookInfoSummary(
                                    book = book,
                                    tocLoadFailed = state.tocLoadFailed,
                                    onRemarkClick = { onIntent(BookInfoIntent.RemarkClick) },
                                    bookSource = state.bookSource,
                                    onJumpToAnotherApp = jumpToAnotherApp,
                                    onIntroButtonClick = { name, click ->
                                        onIntent(BookInfoIntent.IntroButtonClick(name, click))
                                    },
                                    onIntroImageClick = { click ->
                                        onIntent(BookInfoIntent.IntroImageClick(click))
                                    },
                                    onIntroImageLongClick = { source ->
                                        onIntent(BookInfoIntent.IntroImageLongClick(source))
                                    },
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
        BookInfoSheet.GroupPicker -> {
            GroupSelectSheet(
                show = currentSheet == BookInfoSheet.GroupPicker,
                groups = groups,
                currentGroupId = state.book?.group ?: 0L,
                onDismissRequest = { onIntent(BookInfoIntent.DismissSheet) },
                onConfirm = { onIntent(BookInfoIntent.SelectGroup(it)) },
            )
        }
        is BookInfoSheet.SourcePicker -> {
            ChangeSourceSheet(
                show = currentSheet is BookInfoSheet.SourcePicker,
                oldBook = sheet.oldBook,
                onDismissRequest = { onIntent(BookInfoIntent.DismissSheet) },
                onReplace = { source, newBook, toc, options ->
                    onIntent(BookInfoIntent.ReplaceWithSource(source, newBook, toc, options))
                },
                onAddAsNew = { newBook, toc ->
                    onIntent(BookInfoIntent.AddSourceAsNewBook(newBook, toc))
                },
                onReplaceConflict = { oldBook, source, newBook, toc, options ->
                    onIntent(
                        BookInfoIntent.ReplaceConflictingBook(
                            oldBook = oldBook,
                            source = source,
                            book = newBook,
                            toc = toc,
                            options = options,
                        )
                    )
                },
            )
        }
        BookInfoSheet.ReadRecord -> BookReadRecordSheet(
            show = currentSheet == BookInfoSheet.ReadRecord,
            totalReadTime = state.readRecordTotalTime,
            timelineDays = state.readRecordTimelineDays,
            onDismissRequest = { onIntent(BookInfoIntent.DismissSheet) },
        )
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
        is BookInfoSheet.Variable -> VariableEditorSheet(
            state = sheet.editor.takeIf { currentSheet is BookInfoSheet.Variable },
            onValueChange = { onIntent(BookInfoIntent.UpdateVariable(it)) },
            onSave = { onIntent(BookInfoIntent.SaveVariable) },
            onDismissRequest = { onIntent(BookInfoIntent.DismissSheet) },
        )
    }

    BookshelfConflictSheet(
        conflict = state.shelfConflict,
        isResolving = state.isResolvingShelfConflict,
        onDismissRequest = { onIntent(BookInfoIntent.DismissShelfConflict) },
        onOpenExistingBook = { onIntent(BookInfoIntent.OpenShelfConflictBook(it)) },
        onCoexist = { existingBookUrl, options ->
            onIntent(BookInfoIntent.CoexistWithShelfConflict(existingBookUrl, options))
        },
        onMigrate = { existingBookUrl, options ->
            onIntent(BookInfoIntent.MigrateShelfConflict(existingBookUrl, options))
        },
    )

    BookInfoDialogs(state = state, onIntent = onIntent)
}

@Composable
private fun BookInfoColorTheme(
    theme: ThemeOverrideState?,
    content: @Composable () -> Unit,
) {
    val baseTheme = LocalLegadoThemeColors.current
    val animationSpec = tween<Color>(
        durationMillis = 400,
        easing = FastOutSlowInEasing,
    )
    val targetColorScheme = theme?.colorScheme ?: baseTheme.colorScheme
    val targetSeedColor = theme?.seedColor ?: baseTheme.seedColor
    val animatedColorScheme = targetColorScheme.animateColorSchemeAsState(animationSpec)
    val animatedSeedColor by animateColorAsState(
        targetValue = targetSeedColor,
        animationSpec = animationSpec,
        label = "book_info_theme_seed",
    )

    ProvideColorSchemeOverride(
        colorScheme = animatedColorScheme,
        seedColor = animatedSeedColor,
        overrideIsDark = theme?.isDark ?: baseTheme.isDark,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BookInfoTransparentTopAppBar(
    state: BookInfoUiState,
    onMenuAction: (BookInfoMenuAction) -> Unit,
    onBackPressed: () -> Unit,
    scrollBehavior: GlassTopAppBarScrollBehavior,
) {
    val hazeState = LocalHazeState.current
    val isMiuix = ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)
    val collapsedColor = if (isMiuix) {
        GlassTopAppBarDefaults.getMiuixAppBarColor()
    } else {
        GlassTopAppBarDefaults.scrolledContainerColor()
    }
    val isAtTop = scrollBehavior.collapsedFraction <= 0.001f
    val resolvedColor = if (isAtTop) Color.Transparent else collapsedColor
    val topBarColors = TopAppBarDefaults.topAppBarColors(
        containerColor = resolvedColor,
        scrolledContainerColor = resolvedColor,
    )

    if (isMiuix) {
        MiuixTopAppBar(
            modifier = hazeState?.let { Modifier.responsiveHazeEffectFixedStyle(it) } ?: Modifier,
            title = "",
            subtitle = "",
            navigationIcon = {
                TopBarNavigationButton(onClick = onBackPressed)
            },
            actions = {
                TopBarActionsRow(
                    modifier = Modifier.padding(
                        end = miuixTopBarActionsEndPadding()
                    )
                ) {
                    BookInfoTopBarActions(
                        state = state,
                        onMenuAction = onMenuAction,
                    )
                }
            },
            color = resolvedColor,
            navigationIconPadding = miuixTopBarSlotPadding(),
            actionIconPadding = miuixTopBarSlotPadding(),
            scrollBehavior = (scrollBehavior as? MiuixGlassScrollBehavior)?.miuixBehavior,
        )
    } else {
        MediumFlexibleTopAppBar(
            modifier = hazeState?.let { Modifier.responsiveHazeEffectFixedStyle(it) } ?: Modifier,
            title = { Text(text = "", maxLines = 1) },
            navigationIcon = {
                TopBarNavigationButton(onClick = onBackPressed)
            },
            actions = {
                Box(modifier = Modifier.padding(end = 12.dp)) {
                    TopBarActionsRow {
                        BookInfoTopBarActions(
                            state = state,
                            onMenuAction = onMenuAction,
                        )
                    }
                }
            },
            scrollBehavior = (scrollBehavior as? M3GlassScrollBehavior)?.m3Behavior,
            colors = topBarColors,
        )
    }
}

@Composable
private fun rememberBookInfoColorTheme(
    book: BookInfoBookUi?,
    enabled: Boolean,
    usesDefaultCover: Boolean,
    followCoverColor: Boolean,
    defaultCover: String,
    defaultCoverDark: String,
    loadCoverOnlyOnWifi: Boolean,
): ThemeOverrideState? {
    if (
        book == null ||
        !enabled ||
        !followCoverColor
    ) {
        return null
    }

    val imageLoader = koinInject<ImageLoader>()
    val isNight = LegadoTheme.isDark
    val defaultCoverPaths =
        if (isNight) defaultCoverDark else defaultCover
    val coverPath = remember(
        book.name,
        book.author,
        book.coverPath,
        usesDefaultCover,
        isNight,
        defaultCoverPaths,
    ) {
        if (usesDefaultCover) {
            BookCoverModel.getRandomDefaultPath(
                seed = book.name,
                isNight = isNight,
            )
        } else {
            book.coverPath
        }
    } ?: return null
    val sourceOrigin = if (usesDefaultCover) null else book.origin
    val loadOnlyWifi = !usesDefaultCover && loadCoverOnlyOnWifi
    val requestKey = remember(coverPath, sourceOrigin, loadOnlyWifi) {
        listOf(coverPath, sourceOrigin, loadOnlyWifi)
    }

    val seedColor = rememberImageSeedColor(
        imageLoader = imageLoader,
        data = coverPath,
        requestKey = requestKey,
    ) {
        extras[CoverExtras.SourceOrigin] = sourceOrigin
        extras[CoverExtras.LoadOnlyWifi] = loadOnlyWifi
    }

    return rememberThemeOverride(seedColor)
}

private fun resolveBookInfoBackdropStyle(
    book: BookInfoBookUi,
    usesDefaultCover: Boolean,
    defaultCoverBackground: String,
    networkCoverBackground: String,
): BookInfoBackdropStyle {
    val backgroundMode = if (usesDefaultCover) {
        defaultCoverBackground
    } else {
        networkCoverBackground
    }
    return resolveBookInfoBackdropStyle(backgroundMode)
}

@Composable
private fun BookInfoTopBarActions(
    state: BookInfoUiState,
    onMenuAction: (BookInfoMenuAction) -> Unit,
) {
    if (state.inBookshelf) {
        TopBarActionButton(
            onClick = { onMenuAction(BookInfoMenuAction.Edit) },
            imageVector = Icons.Default.Edit,
            contentDescription = stringResource(R.string.edit)
        )
    }
    TopBarActionButton(
        onClick = { onMenuAction(BookInfoMenuAction.Share) },
        imageVector = Icons.Default.Share,
        contentDescription = stringResource(R.string.share)
    )
    BookInfoOverflowAction(
        state = state,
        onMenuAction = onMenuAction,
    )
}

@Composable
private fun BookInfoOverflowAction(
    state: BookInfoUiState,
    onMenuAction: (BookInfoMenuAction) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        TopBarActionButton(
            onClick = { expanded = true },
            imageVector = AppIcons.MoreVert,
            contentDescription = stringResource(R.string.more_actions),
        )
        BookInfoOverflowMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            state = state,
            onMenuAction = {
                expanded = false
                onMenuAction(it)
            },
        )
    }
}

@Composable
private fun BookInfoBackdrop(
    book: BookInfoBookUi,
    style: BookInfoBackdropStyle,
    usesDefaultCover: Boolean,
    onNetworkCoverLoadError: (String?) -> Unit,
) {
    val backdropState = remember(
        book.name,
        book.author,
        book.coverPath,
        book.origin,
        usesDefaultCover,
        style,
    ) {
        BookInfoBackdropState(
            name = book.name,
            author = book.author,
            coverPath = if (usesDefaultCover) null else book.coverPath,
            sourceOrigin = if (usesDefaultCover) null else book.origin,
            style = style,
        )
    }
    val seedOverlay = lerp(
        LegadoTheme.colorScheme.secondaryContainer,
        LegadoTheme.seedColor,
        0.42f
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clearAndSetSemantics { }
    ) {
        Crossfade(
            targetState = backdropState,
            animationSpec = tween(800),
            label = "BackdropCrossfade"
        ) { currentBook ->
            if (currentBook.style.showCover) {
                BookCoverImage(
                    name = currentBook.name,
                    author = currentBook.author,
                    path = currentBook.coverPath,
                    sourceOrigin = currentBook.sourceOrigin,
                    memoryCacheKey = currentBook.coverPath?.let { "$it#book-info-backdrop" },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(480.dp)
                        .then(
                            if (currentBook.style.blurCover) {
                                Modifier.blur(24.dp)
                            } else {
                                Modifier
                            }
                        ),
                    contentScale = ContentScale.Crop,
                    showLoadingPlaceholder = false,
                    onError = { onNetworkCoverLoadError(currentBook.coverPath) },
                    requestBuilder = {
                        size(Size(384, 384))
                    }
                )
            }
        }
        if (style.applySeedOverlay) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(seedOverlay.copy(alpha = 0.34f))
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.20f to if (style.applySeedOverlay) {
                                seedOverlay.copy(alpha = 0.10f)
                            } else {
                                Color.Transparent
                            },
                            0.40f to if (style.applySeedOverlay) {
                                seedOverlay.copy(alpha = 0.18f)
                            } else {
                                LegadoTheme.colorScheme.surface.copy(alpha = 0.35f)
                            },
                            0.60f to LegadoTheme.colorScheme.surface.copy(alpha = 0.85f),
                            0.80f to LegadoTheme.colorScheme.surface,
                            1f to LegadoTheme.colorScheme.surface,
                        )
                    )
                )
        )
    }
}

private data class BookInfoBackdropState(
    val name: String,
    val author: String,
    val coverPath: String?,
    val sourceOrigin: String?,
    val style: BookInfoBackdropStyle,
)

@Composable
private fun BookInfoOverflowMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    state: BookInfoUiState,
    onMenuAction: (BookInfoMenuAction) -> Unit,
) {
    val book = state.book
    RoundDropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest) {
        if (state.bookSourceUi?.hasCustomButton == true) {
            RoundDropdownMenuItem(
                text = stringResource(R.string.custom_button),
                onClick = { onMenuAction(BookInfoMenuAction.CustomButton) }
            )
        }
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
        RoundDropdownMenuItem(
            text = stringResource(R.string.read_record),
            onClick = { onMenuAction(BookInfoMenuAction.ReadRecord) }
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
        if (state.bookSourceUi?.hasLogin == true) {
            RoundDropdownMenuItem(
                text = stringResource(R.string.login),
                onClick = { onMenuAction(BookInfoMenuAction.Login) }
            )
        }
        if (state.bookSourceUi != null) {
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
        if (book?.isLocal == false ){
            RoundDropdownMenuItem(
                text = stringResource(R.string.allow_update),
                onClick = { onMenuAction(BookInfoMenuAction.ToggleCanUpdate) },
                isSelected = book.canUpdate
            )
        }
        if (book?.isLocal == true && book.type and BookType.text > 0) {
            RoundDropdownMenuItem(
                text = stringResource(R.string.split_long_chapter),
                onClick = { onMenuAction(BookInfoMenuAction.ToggleSplitLongChapter) },
                isSelected = book.splitLongChapter
            )
        }
        RoundDropdownMenuItem(
            text = stringResource(R.string.delete_alert),
            onClick = { onMenuAction(BookInfoMenuAction.ToggleDeleteAlert) },
            isSelected = state.deleteAlertEnabled
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
    book: BookInfoBookUi,
    highlightedTags: List<HighlightedTag>,
    kindLabels: List<String>,
    groupNames: String?,
    onCoverClick: () -> Unit,
    onCoverLongClick: () -> Unit,
    onAuthorClick: (Boolean) -> Unit,
    onBookNameClick: (Boolean) -> Unit,
    onOriginClick: () -> Unit,
    onNetworkCoverLoadError: () -> Unit,
    usesDefaultCover: Boolean,
    applySeedOverlay: Boolean,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    sharedCoverKey: String?,
) {
    val coverDescription = stringResource(R.string.a11y_book_cover_actions, book.name)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        if (applySeedOverlay) {
                            lerp(LegadoTheme.colorScheme.surface, LegadoTheme.seedColor, 0.08f)
                                .copy(alpha = 0.5f)
                        } else {
                            LegadoTheme.colorScheme.surface.copy(alpha = 0.5f)
                        },
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
                        .semantics {
                            role = Role.Button
                            contentDescription = coverDescription
                        }
                ) {
                    CoilBookCover(
                        name = book.name,
                        author = book.author,
                        path = if (usesDefaultCover) null else book.coverPath,
                        sourceOrigin = if (usesDefaultCover) null else book.origin,
                        // 传 bookUrl 供别名缓存键。详情页故意不设 preferCache：
                        // 在线时仍走完整链路拉新链接并刷新别名，保证封面换图后书架也能更新；
                        // 精确命中时同样不跑脚本。
                        bookUrl = book.bookUrl,
                        onError = onNetworkCoverLoadError,
                        modifier = Modifier
                            .width(112.dp)
                            .aspectRatio(5f / 7f),
                        showLoadingPlaceholder = sharedCoverKey == null,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        sharedCoverKey = sharedCoverKey
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .align(Alignment.CenterVertically)
                        .padding(top = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    var showTitleMenu by remember { mutableStateOf(false) }
                    var isTitleExpanded by rememberSaveable { mutableStateOf(false) }
                    Box {
                        AnimatedTextLine(
                            text = book.name,
                            style = LegadoTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = if (isTitleExpanded) Int.MAX_VALUE else 2,
                            modifier = Modifier.combinedClickable(
                                onClick = { onBookNameClick(false) },
                                onLongClick = { showTitleMenu = true }
                            )
                        )
                        RoundDropdownMenu(
                            expanded = showTitleMenu,
                            onDismissRequest = { showTitleMenu = false }
                        ) {
                            RoundDropdownMenuItem(
                                text = stringResource(R.string.search),
                                onClick = {
                                    showTitleMenu = false
                                    onBookNameClick(true)
                                }
                            )
                            RoundDropdownMenuItem(
                                text = stringResource(if (isTitleExpanded) R.string.collapse else R.string.expand),
                                onClick = {
                                    showTitleMenu = false
                                    isTitleExpanded = !isTitleExpanded
                                }
                            )
                        }
                    }
                    AnimatedTextLine(
                        text = stringResource(R.string.author_show, book.realAuthor),
                        style = LegadoTheme.typography.bodyLarge,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.combinedClickable(
                            onClick = { onAuthorClick(false) },
                            onLongClick = { onAuthorClick(true) }
                        )
                    )
                    AnimatedTextLine(
                        text = stringResource(R.string.origin_show, book.originName),
                        style = LegadoTheme.typography.labelMedium,
                        color = LegadoTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = onOriginClick)
                    )
                }
            }
            if (highlightedTags.isNotEmpty()) {
                HighlightTagRow(tags = highlightedTags)
            }
            if (kindLabels.isNotEmpty() || !groupNames.isNullOrBlank()) {
                val kindListState = rememberLazyListState()
                LazyRow(
                    state = kindListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fadingEdge(kindListState),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    groupNames?.takeIf { it.isNotBlank() }?.let {
                        item(key = "group-$it") {
                            TextCard(
                                text = stringResource(R.string.group_s, it),
                                textStyle = LegadoTheme.typography.labelLargeEmphasized,
                                backgroundColor = LegadoTheme.colorScheme.surfaceContainer,
                                contentColor = LegadoTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    itemsIndexed(
                        items = kindLabels,
                        key = { index, label -> "kind-$index-$label" }
                    ) { _, label ->
                        TextCard(
                            text = label,
                            textStyle = LegadoTheme.typography.labelLargeEmphasized,
                            backgroundColor = LegadoTheme.colorScheme.surfaceContainer,
                            contentColor = LegadoTheme.colorScheme.onSurfaceVariant,
                        )
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
    onReadRecordClick: () -> Unit,
) {
    var awaitingShelfAddition by rememberSaveable { mutableStateOf(false) }
    var showShelfRemoveHint by rememberSaveable { mutableStateOf(false) }
    var showLongPressGroupHint by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(inBookshelf) {
        if (awaitingShelfAddition && inBookshelf) {
            awaitingShelfAddition = false
            showShelfRemoveHint = true
            delay(1000)
            showShelfRemoveHint = false
            showLongPressGroupHint = true
            delay(1000)
            showLongPressGroupHint = false
        } else if (!inBookshelf) {
            awaitingShelfAddition = false
            showShelfRemoveHint = false
            showLongPressGroupHint = false
        }
    }

    val shelfLabel = when {
        showShelfRemoveHint -> stringResource(R.string.click_to_remove)
        showLongPressGroupHint -> stringResource(R.string.long_press_group)
        inBookshelf -> stringResource(R.string.already_in_bookshelf)
        else -> stringResource(R.string.add_to_bookshelf)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(LegadoTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BookInfoActionCard(
            modifier = Modifier.weight(1f),
            icon = if (inBookshelf) Icons.Outlined.Book else Icons.Default.BookmarkAdd,
            label = shelfLabel,
            onLongClick = onGroupClick,
            onClick = {
                if (!inBookshelf) {
                    awaitingShelfAddition = true
                } else {
                    awaitingShelfAddition = false
                    showShelfRemoveHint = false
                    showLongPressGroupHint = false
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
            icon = Icons.Default.Code,
            label = stringResource(R.string.change_origin),
            onClick = onSourceClick
        )
        BookInfoActionCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Timeline,
            label = stringResource(R.string.read_record),
            onClick = onReadRecordClick
        )
    }
}

@Composable
private fun BookInfoActionCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = modifier.semantics(mergeDescendants = true) {
            role = Role.Button
            contentDescription = label
        },
        onLongClick = onLongClick,
        onClick = onClick,
        containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
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
    book: BookInfoBookUi,
    tocLoadFailed: Boolean,
    onRemarkClick: () -> Unit,
    bookSource: BookSource?,
    onJumpToAnotherApp: (Uri) -> Unit,
    onIntroButtonClick: (name: String, click: String) -> Unit,
    onIntroImageClick: (click: String) -> Unit,
    onIntroImageLongClick: (source: String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LegadoTheme.colorScheme.surface)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AnimatedTextLine(
            text = stringResource(R.string.toc_s, book.durChapterTitle ?: stringResource(R.string.loading)),
            style = LegadoTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        AnimatedTextLine(
            text = stringResource(R.string.lasted_show, book.latestChapterTitle ?: ""),
            style = LegadoTheme.typography.bodyMedium,
            color = LegadoTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedTextLine(
                text = stringResource(R.string.read_chapter_total, book.totalChapterNum),
                style = LegadoTheme.typography.labelMedium,
                color = LegadoTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            AppText(
                text = "|",
                color = LegadoTheme.colorScheme.secondary
            )
            AnimatedTextLine(
                text = when {
                    book.durChapterIndex == 0 && book.durChapterPos == 0 -> stringResource(R.string.is_unread)
                    book.durChapterIndex + 1 == book.totalChapterNum && book.totalChapterNum > 0 -> "已读完"
                    else -> stringResource(R.string.read_chapter_index, book.durChapterIndex + 1)
                },
                style = LegadoTheme.typography.labelMedium,
                color = LegadoTheme.colorScheme.secondary,
            )
            if (tocLoadFailed) {
                AppText(
                    text = " · ",
                    color = LegadoTheme.colorScheme.secondary
                )
                AnimatedTextLine(
                    text = stringResource(R.string.error_load_toc),
                    style = LegadoTheme.typography.labelMedium,
                    color = LegadoTheme.colorScheme.error
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        book.remark?.takeIf { it.isNotBlank() }?.let { remark ->
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = onRemarkClick,
                containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AnimatedTextLine(
                        text = remark,
                        style = LegadoTheme.typography.labelMediumEmphasized
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        BookInfoIntro(
            intro = book.intro,
            baseUrl = book.bookUrl
                .takeIf { it.startsWith("http", true) }
                ?.substringBefore(","),
            bookSource = bookSource,
            onJumpToAnotherApp = onJumpToAnotherApp,
            onButtonClick = onIntroButtonClick,
            onImageClick = onIntroImageClick,
            onImageLongClick = onIntroImageLongClick,
        )
    }
}

/**
 * 上游书籍详情页的简介渲染：
 * - `<useweb>...<` 用 WebView 渲染（注入缓存/书源/Java 桥接 JS，处理 legado/yuedu 等 scheme）
 * - `<usehtml>...<` 用 HTML 渲染（含行内图片/链接/样式）
 * - `<md>...<` 用 Markdown 渲染
 * - 其余纯文本
 */
@Composable
private fun BookInfoIntro(
    intro: String?,
    baseUrl: String?,
    bookSource: BookSource?,
    onJumpToAnotherApp: (Uri) -> Unit,
    onButtonClick: (name: String, click: String) -> Unit,
    onImageClick: (click: String) -> Unit,
    onImageLongClick: (source: String) -> Unit,
) {
    val context = LocalContext.current
    val content = remember(intro) { parseBookInfoIntro(intro) }
    if (content == null) {
        AnimatedTextLine(
            text = stringResource(R.string.intro_show_null),
            style = LegadoTheme.typography.bodyMedium,
        )
        return
    }
    when (val c = content) {
        is BookInfoIntroContent.Web -> BookInfoWebIntro(
            html = c.html,
            baseUrl = baseUrl,
            bookSource = bookSource,
            onJumpToAnotherApp = onJumpToAnotherApp,
        )

        is BookInfoIntroContent.Html -> HtmlContent(
            html = c.html,
            interactive = true,
            onButtonClick = onButtonClick,
            onImageClick = onImageClick,
            onImageLongClick = onImageLongClick,
            imageModel = { imageUrl ->
                buildCoverImageRequest(
                    context = context,
                    data = imageUrl,
                    sourceOrigin = bookSource?.bookSourceUrl,
                    loadOnlyWifi = false,
                )
            },
        )

        is BookInfoIntroContent.Markdown -> MarkdownBlock(
            content = c.markdown,
            imageModel = { imageUrl ->
                buildCoverImageRequest(
                    context = context,
                    data = imageUrl,
                    sourceOrigin = bookSource?.bookSourceUrl,
                    loadOnlyWifi = false,
                )
            },
            onImageClick = onImageClick,
            onImageLongClick = onImageLongClick,
        )

        is BookInfoIntroContent.Plain -> AnimatedTextLine(
            text = c.text,
            style = LegadoTheme.typography.bodyMedium,
        )
    }
}

private sealed interface BookInfoIntroContent {
    data class Web(val html: String) : BookInfoIntroContent
    data class Html(val html: String) : BookInfoIntroContent
    data class Markdown(val markdown: String) : BookInfoIntroContent
    data class Plain(val text: String) : BookInfoIntroContent
}

/**
 * 解析简介前缀，与上游 showBookIntro 一致：
 * 前缀 `<useweb>`/`<usehtml>`/`<md>`（忽略大小写）后直到最后一个 `<` 之间的内容为待渲染文本；
 * 前缀残缺时按纯文本回退。
 */
private fun parseBookInfoIntro(intro: String?): BookInfoIntroContent? {
    if (intro.isNullOrBlank()) return null
    return when {
        intro.startsWith("<useweb>", ignoreCase = true) -> {
            val lastIndex = intro.lastIndexOf("<")
            if (lastIndex < 8) {
                BookInfoIntroContent.Plain(HtmlFormatter.formatDisplayText(intro))
            } else {
                BookInfoIntroContent.Web(intro.substring(8, lastIndex))
            }
        }

        intro.startsWith("<usehtml>", ignoreCase = true) -> {
            val lastIndex = intro.lastIndexOf("<")
            if (lastIndex < 9) {
                BookInfoIntroContent.Plain(HtmlFormatter.formatDisplayText(intro))
            } else {
                BookInfoIntroContent.Html(intro.substring(9, lastIndex))
            }
        }

        intro.startsWith("<md>", ignoreCase = true) -> {
            val lastIndex = intro.lastIndexOf("<")
            if (lastIndex < 4) {
                BookInfoIntroContent.Plain(HtmlFormatter.formatDisplayText(intro))
            } else {
                BookInfoIntroContent.Markdown(intro.substring(4, lastIndex))
            }
        }

        else -> BookInfoIntroContent.Plain(HtmlFormatter.formatDisplayText(intro))
    }
}

/**
 * `<useweb>` 简介：用 WebView 渲染，注入与上游一致的 JS 桥接
 * （WebCacheManager 缓存、书源对象、WebJsExtensions），
 * 并处理 legado/yuedu scheme（导入）与其他 scheme（确认后跳转）。
 */
@Composable
@SuppressLint("SetJavaScriptEnabled")
private fun BookInfoWebIntro(
    html: String,
    baseUrl: String?,
    bookSource: BookSource?,
    onJumpToAnotherApp: (Uri) -> Unit,
) {
    val context = LocalContext.current
    // 初始高度先给一屏，否则 WebView 在 0 高度下不会被布局，也就无法测量内容高度
    val pageHeight = LocalConfiguration.current.screenHeightDp.dp.coerceAtLeast(320.dp)
    val textColor = LegadoTheme.colorScheme.onSurface
    val textColorHex = remember(textColor) {
        ColorUtils.intToString(textColor.toArgb())
    }
    val wrappedHtml = remember(html, textColorHex) {
        buildBookInfoWebIntroHtml(html, textColorHex)
    }
    val loadKey = remember(baseUrl, wrappedHtml) { "${baseUrl.orEmpty()}\n$wrappedHtml" }
    val contentHeightState = remember(loadKey) { mutableStateOf<Dp?>(null) }
    val loadedKeyState = remember { mutableStateOf<String?>(null) }
    val isCurrentLoad = { loadedKeyState.value == loadKey }
    // evaluateJavascript 返回的是 CSS px，与 Compose dp 等值，不能再乘/除 density
    val onCssHeight: (Float) -> Unit = { cssHeight ->
        val next = cssHeight.dp
        val current = contentHeightState.value
        if (current == null || abs(current.value - next.value) > 8f) {
            contentHeightState.value = next
        }
    }
    val webView = remember(bookSource?.bookSourceUrl) {
        WebView(context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadsImagesAutomatically = true
                blockNetworkImage = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                mediaPlaybackRequiresUserGesture = false
                builtInZoomControls = false
                displayZoomControls = false
                textZoom = 100
            }
            // useweb 简介可能是整页 HTML：禁用 overscroll 与滚动条，滚动交给外层 LazyColumn
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            webChromeClient = buildBookInfoWebChromeClient()
            addJavascriptInterface(WebCacheManager, WebJsExtensions.nameCache)
            bookSource?.let { source ->
                addJavascriptInterface(source as BaseSource, WebJsExtensions.nameSource)
                addJavascriptInterface(
                    WebJsExtensions(source, null, this),
                    WebJsExtensions.nameJava
                )
            }
        }
    }
    DisposableEffect(webView) {
        webView.onResume()
        onDispose {
            webView.stopLoading()
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
    }
    AndroidView(
        factory = { webView },
        modifier = Modifier
            .fillMaxWidth()
            .height(contentHeightState.value ?: pageHeight),
        update = { view ->
            view.webViewClient = buildBookInfoWebIntroClient(
                context = context,
                isCurrentLoad = isCurrentLoad,
                onCssHeight = onCssHeight,
                onJumpToAnotherApp = onJumpToAnotherApp,
            )
            view.setOnTouchListener { _, event ->
                if (
                    event.action == MotionEvent.ACTION_UP ||
                    event.action == MotionEvent.ACTION_CANCEL
                ) {
                    scheduleBookInfoWebIntroHeightMeasure(
                        webView = view,
                        isCurrentLoad = isCurrentLoad,
                        onCssHeight = onCssHeight,
                        delays = bookInfoWebIntroTouchMeasureDelays,
                    )
                }
                false
            }
            if (loadedKeyState.value != loadKey) {
                loadedKeyState.value = loadKey
                view.stopLoading()
                view.loadDataWithBaseURL(baseUrl, wrappedHtml, "text/html", "utf-8", baseUrl)
            }
        },
    )
}

/**
 * `<useweb>` 简介的 HTML 外壳：透明背景 + viewport + 主题文字色。
 * 缺少 viewport 时 WebView 会按 980px 视口布局，简介排版会塌缩。
 */
private fun buildBookInfoWebIntroHtml(html: String, textColorHex: String): String = """
    <html>
    <head>
      <meta name="viewport" content="width=device-width, initial-scale=1">
      <style>
        html, body {
          background: transparent !important;
          color: $textColorHex;
          margin: 0;
          padding: 0;
          font-size: 14px;
          line-height: 1.7;
          word-break: break-word;
          -webkit-user-select: text !important;
          user-select: text !important;
        }
        body * {
          -webkit-user-select: text !important;
          user-select: text !important;
        }
        img, video, iframe {
          max-width: 100%;
          height: auto;
        }
      </style>
    </head>
    <body>$html</body>
    </html>
""".trimIndent()

private fun buildBookInfoWebIntroClient(
    context: Context,
    isCurrentLoad: () -> Boolean,
    onCssHeight: (Float) -> Unit,
    onJumpToAnotherApp: (Uri) -> Unit,
): WebViewClient = object : WebViewClient() {

    private val injectionString = WebJsExtensions.getInjectionString

    override fun shouldOverrideUrlLoading(
        view: WebView?,
        request: WebResourceRequest?,
    ): Boolean {
        val url = request?.url ?: return super.shouldOverrideUrlLoading(view, request)
        return when (url.scheme) {
            "http", "https" -> false
            "legado", "yuedu" -> {
                context.startActivity(
                    Intent(context, OnLineImportActivity::class.java).apply {
                        data = url
                    }
                )
                true
            }

            else -> {
                onJumpToAnotherApp(url)
                true
            }
        }
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        view?.let { runCatching { it.evaluateJavascript(injectionString, null) } }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        val webView = view ?: return
        runCatching { webView.evaluateJavascript(injectionString, null) }
        scheduleBookInfoWebIntroHeightMeasure(
            webView = webView,
            isCurrentLoad = isCurrentLoad,
            onCssHeight = onCssHeight,
            delays = bookInfoWebIntroPageLoadMeasureDelays,
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun buildBookInfoWebChromeClient(): WebChromeClient = object : WebChromeClient() {

    override fun onCreateWindow(
        view: WebView?,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message?,
    ): Boolean {
        val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
        val host = view ?: return false
        val popup = WebView(host.context).apply {
            settings.javaScriptEnabled = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    popupView: WebView?,
                    request: WebResourceRequest?,
                ): Boolean {
                    request?.url?.let { host.loadUrl(it.toString()) }
                    popupView?.post { popupView.destroy() }
                    return true
                }
            }
        }
        transport.webView = popup
        resultMsg.sendToTarget()
        return true
    }
}

private val bookInfoWebIntroPageLoadMeasureDelays = longArrayOf(0L, 120L, 360L, 720L, 1200L)

private val bookInfoWebIntroTouchMeasureDelays = longArrayOf(120L, 360L, 720L)

/**
 * 读取文档真实高度（CSS px）。只统计可见子元素底部，避免 useweb 页面把 body 撑满视口
 * 导致高度被拉到整屏。
 */
private val bookInfoWebIntroHeightJs = """
    (function() {
      var body = document.body;
      var doc = document.documentElement;
      var bottom = 0;
      if (body) {
        Array.prototype.forEach.call(body.children || [], function(el) {
          var style = window.getComputedStyle ? window.getComputedStyle(el) : null;
          if (style && (style.display === 'none' || style.visibility === 'hidden')) return;
          var rect = el.getBoundingClientRect ? el.getBoundingClientRect() : null;
          if (!rect) return;
          bottom = Math.max(bottom, rect.bottom + window.pageYOffset);
        });
      }
      var documentHeight = Math.max(
        body ? body.scrollHeight || 0 : 0,
        body ? body.offsetHeight || 0 : 0,
        doc ? doc.scrollHeight || 0 : 0,
        doc ? doc.offsetHeight || 0 : 0
      );
      return bottom > 1 ? bottom : documentHeight;
    })();
""".trimIndent()

private fun scheduleBookInfoWebIntroHeightMeasure(
    webView: WebView,
    isCurrentLoad: () -> Boolean,
    onCssHeight: (Float) -> Unit,
    delays: LongArray,
) {
    if (!isCurrentLoad()) return
    delays.forEach { delayMillis ->
        webView.postDelayed({
            if (!isCurrentLoad() || webView.handler == null || !webView.isAttachedToWindow) {
                return@postDelayed
            }
            runCatching {
                webView.evaluateJavascript(bookInfoWebIntroHeightJs) { result ->
                    if (!isCurrentLoad()) return@evaluateJavascript
                    val cssHeight = result?.trim()?.trim('"')?.toFloatOrNull()
                        ?: return@evaluateJavascript
                    if (cssHeight > 1f) {
                        onCssHeight(cssHeight)
                    }
                }
            }
        }, delayMillis)
    }
}
@Composable
private fun BookInfoDialogs(
    state: BookInfoUiState,
    onIntent: (BookInfoIntent) -> Unit,
) {
    val dialog = state.dialog
    var deleteOriginal by remember(dialog, state.deleteOriginal) { mutableStateOf(state.deleteOriginal) }
    var remarkText by remember(dialog) { mutableStateOf((dialog as? BookInfoDialog.EditRemark)?.remark.orEmpty()) }

    AppAlertDialog(
        data = dialog as? BookInfoDialog.DeleteBook,
        onDismissRequest = { onIntent(BookInfoIntent.DismissDialog) },
        title = stringResource(R.string.draw),
        text = stringResource(R.string.sure_del),
        confirmText = stringResource(android.R.string.ok),
        onConfirm = {
            onIntent(BookInfoIntent.ConfirmDelete(deleteOriginal))
        },
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = { onIntent(BookInfoIntent.DismissDialog) },
        content = { d ->
            if (d.isLocal) {
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

    AppAlertDialog(
        data = dialog as? BookInfoDialog.EditRemark,
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
                label = stringResource(R.string.book_remark),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    )

    val unsupportedWebFile = dialog as? BookInfoDialog.UnsupportedWebFile
    AppAlertDialog(
        data = unsupportedWebFile,
        onDismissRequest = { onIntent(BookInfoIntent.DismissDialog) },
        title = stringResource(R.string.draw),
        text = unsupportedWebFile?.let {
            stringResource(
                R.string.file_not_supported,
                it.webFile.name
            )
        },
        confirmText = stringResource(R.string.open_fun),
        onConfirm = { onIntent(BookInfoIntent.OpenUnsupportedWebFile(it.webFile)) },
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = { onIntent(BookInfoIntent.DismissDialog) },
    )

    AppAlertDialog(
        data = dialog as? BookInfoDialog.PhotoPreview,
        onDismissRequest = { onIntent(BookInfoIntent.DismissDialog) },
        title = stringResource(R.string.img_cover),
        confirmText = "保存到相册",
        onConfirm = { d ->
            onIntent(BookInfoIntent.SaveCover(d.path))
            onIntent(BookInfoIntent.DismissDialog)
        },
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = { onIntent(BookInfoIntent.DismissDialog) },
        content = { d ->
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CoilBookCover(
                    name = state.book?.name,
                    author = state.book?.author,
                    path = d.path,
                    sourceOrigin = state.book?.origin,
                    ignoreUseDefaultCover = true,
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .fillMaxWidth(0.6f)
                )
            }
        }
    )

    AppAlertDialog(
        show = state.isBusy,
        onDismissRequest = {},
        content = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                AppCircularProgressIndicator()
            }
        }
    )

    AppLogSheet(show = state.showAppLogSheet, onDismissRequest = { onIntent(BookInfoIntent.DismissAppLogSheet) })
}

@Composable
private fun RelatedBooksBanner(
    title: String,
    books: ImmutableList<SearchBook>,
    onBookClick: (SearchBook, String?) -> Unit,
    onMoreClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        if (title.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppText(
                    text = title,
                    style = LegadoTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                SmallTonalButton(
                    onClick = onMoreClick,
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(R.string.a11y_related_books_more, title),
                )
            }
        }
        BannerModule(
            books = books.map { io.legado.app.ui.main.homepage.HomepageBookItemUi(book = it) }
                .toImmutableList(),
            onClick = onBookClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun BookInfoCharacters(
    characters: ImmutableList<BookInfoCharacterUi>,
    onCharacterClick: (String) -> Unit,
    onNetworkClick: () -> Unit,
    onViewAllClick: () -> Unit,
    onKnowledgeClick: () -> Unit,
    onEventsClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LegadoTheme.colorScheme.surface)
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(
                text = stringResource(R.string.book_info_knowledge),
                style = LegadoTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            SmallTonalButton(
                onClick = onViewAllClick,
                icon = Icons.AutoMirrored.Outlined.FormatListBulleted,
                contentDescription = stringResource(R.string.book_characters),
            )
            Spacer(modifier = Modifier.width(4.dp))
            SmallTonalButton(
                onClick = onNetworkClick,
                icon = Icons.Default.Group,
                contentDescription = stringResource(R.string.character_network),
            )
            Spacer(modifier = Modifier.width(4.dp))
            SmallTonalButton(
                onClick = onKnowledgeClick,
                icon = Icons.Default.Book,
                contentDescription = stringResource(R.string.book_knowledge),
            )
            Spacer(modifier = Modifier.width(4.dp))
            SmallTonalButton(
                onClick = onEventsClick,
                icon = Icons.Default.Timeline,
                contentDescription = stringResource(R.string.plot_events),
            )
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                items = characters,
                key = { it.id },
            ) { character ->
                CharacterEntryCard(
                    character = character,
                    onClick = { onCharacterClick(character.id) },
                )
            }
        }
    }
}

@Composable
private fun CharacterEntryCard(
    character: BookInfoCharacterUi,
    onClick: () -> Unit,
) {
    val avatarLoadFailed = remember(character.avatarUri) { mutableStateOf(false) }
    val roleDisplayName = when (character.role) {
        io.legado.app.data.entities.BookCharacterProfile.ROLE_MALE_LEAD -> stringResource(R.string.role_male_lead)
        io.legado.app.data.entities.BookCharacterProfile.ROLE_FEMALE_LEAD -> stringResource(R.string.role_female_lead)
        io.legado.app.data.entities.BookCharacterProfile.ROLE_MALE_SUPPORTING -> stringResource(R.string.role_male_supporting)
        io.legado.app.data.entities.BookCharacterProfile.ROLE_FEMALE_SUPPORTING -> stringResource(R.string.role_female_supporting)
        else -> ""
    }

    GlassCard(
        modifier = Modifier.width(160.dp),
        onClick = onClick,
        containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(LegadoTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!character.avatarUri.isNullOrBlank() && !avatarLoadFailed.value) {
                        AsyncImage(
                            model = character.avatarUri,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop,
                            onError = { avatarLoadFailed.value = true },
                        )
                    } else {
                        AppIcon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = LegadoTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    AnimatedTextLine(
                        text = character.name,
                        style = LegadoTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                    if (roleDisplayName.isNotBlank()) {
                        AnimatedTextLine(
                            text = roleDisplayName,
                            style = LegadoTheme.typography.labelSmall,
                            color = LegadoTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
            if (character.tags.isNotBlank()) {
                AnimatedTextLine(
                    text = character.tags,
                    style = LegadoTheme.typography.labelSmall,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun BookInfoKnowledge(
    entries: ImmutableList<BookInfoKnowledgeUi>,
    onViewAllClick: () -> Unit,
) {
    if (entries.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LegadoTheme.colorScheme.surface)
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(
                text = stringResource(R.string.book_knowledge),
                style = LegadoTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            SmallTonalButton(
                onClick = onViewAllClick,
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = stringResource(R.string.view_all),
            )
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                items = entries,
                key = { it.id },
            ) { entry ->
                KnowledgeInfoCard(
                    title = entry.title,
                    summary = entry.summary,
                )
            }
        }
    }
}

@Composable
private fun KnowledgeInfoCard(
    title: String,
    summary: String,
) {
    GlassCard(
        modifier = Modifier.width(140.dp),
        containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AnimatedTextLine(
                text = title,
                style = LegadoTheme.typography.titleSmall,
                maxLines = 1,
            )
            AnimatedTextLine(
                text = summary.ifBlank { stringResource(R.string.knowledge_content) },
                style = LegadoTheme.typography.bodySmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun BookInfoEvents(
    events: ImmutableList<BookInfoEventUi>,
    onViewAllClick: () -> Unit,
) {
    if (events.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LegadoTheme.colorScheme.surface)
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(
                text = stringResource(R.string.plot_events),
                style = LegadoTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            SmallTonalButton(
                onClick = onViewAllClick,
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = stringResource(R.string.view_all),
            )
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                items = events,
                key = { it.id },
            ) { event ->
                EventInfoCard(
                    title = listOfNotNull(
                        event.characterName.takeIf { it.isNotBlank() },
                        event.chapterTitle.takeIf { it.isNotBlank() },
                    ).joinToString(" · ").ifBlank { stringResource(R.string.event_detail) },
                    content = event.content,
                )
            }
        }
    }
}

@Composable
private fun EventInfoCard(
    title: String,
    content: String,
) {
    GlassCard(
        modifier = Modifier.width(160.dp),
        containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AnimatedTextLine(
                text = title,
                style = LegadoTheme.typography.titleSmall,
                maxLines = 1,
            )
            AnimatedTextLine(
                text = content.ifBlank { stringResource(R.string.event_content) },
                style = LegadoTheme.typography.bodySmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}
