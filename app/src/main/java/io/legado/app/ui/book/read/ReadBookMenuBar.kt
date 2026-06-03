package io.legado.app.ui.book.read

import android.content.Context
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import coil.compose.AsyncImage
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadStyleResolver
import io.legado.app.ui.book.read.sheet.PaddingConfigContent
import io.legado.app.ui.book.read.sheet.ReadAloudContent
import io.legado.app.ui.book.read.sheet.ReadMenuButtonInfo
import io.legado.app.ui.book.read.sheet.loadMenuCustomIcons
import io.legado.app.ui.book.read.sheet.readMenuButtonInfos
import io.legado.app.ui.book.read.sheet.ReadStyleContent
import io.legado.app.ui.book.read.sheet.ReadStyleTextTitleContent
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppSlider
import io.legado.app.ui.widget.components.bookmark.BookmarkEditContent
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.button.series.SmallTonalButton
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.divider.PillDivider
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.text.AppText
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Compose replacement for ReadMenu — main reading menu overlay.
 */
@Composable
fun ReadBookMenuBar(
    state: ReadBookUiState,
    onIntent: (ReadBookIntent) -> Unit,
    backdrop: Backdrop? = null,
) {
    val context = LocalContext.current
    val currentRoute = state.menuState.currentRoute
    val dialogLikeRoute = currentRoute == ReadBookMenuRoute.PaddingConfig
    val menuColors = readMenuColors()

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = state.menuVisible,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { onIntent(ReadBookIntent.HideMenu) }
            )
        }

        // Top title bar + floating icon row (top positions)
        AnimatedVisibility(
            visible = state.menuVisible && !dialogLikeRoute,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Column {
                MenuTitleBar(
                    state = state,
                    colors = menuColors,
                    onIntent = onIntent,
                    backdrop = backdrop,
                )
                if (state.menuConfig.titleBarIconPosition <= 1) {
                    FloatingIconRow(
                        state = state,
                        colors = menuColors,
                        alignment = if (state.menuConfig.titleBarIconPosition == 0) {
                            Alignment.Start
                        } else {
                            Alignment.End
                        },
                        onIntent = onIntent,
                        backdrop = backdrop,
                    )
                }
            }
        }

        // Floating icon row (bottom positions)
        if (state.menuConfig.titleBarIconPosition >= 2) {
            AnimatedVisibility(
                visible = state.menuVisible && !dialogLikeRoute,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
            ) {
                FloatingIconRow(
                    state = state,
                    colors = menuColors,
                    alignment = if (state.menuConfig.titleBarIconPosition == 2) {
                        Alignment.Start
                    } else {
                        Alignment.End
                    },
                    onIntent = onIntent,
                    backdrop = backdrop,
                )
            }
        }

        // Bottom menu
        AnimatedVisibility(
            visible = state.menuVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ReadBookMenuSurface(
                route = currentRoute,
                state = state,
                colors = menuColors,
                onIntent = onIntent,
                context = context,
                backdrop = backdrop,
            )
        }
    }
}

@Composable
private fun ReadBookMenuSurface(
    route: ReadBookMenuRoute,
    state: ReadBookUiState,
    colors: ReadMenuColors,
    onIntent: (ReadBookIntent) -> Unit,
    context: Context,
    backdrop: Backdrop?,
) {
    val expanded = route != ReadBookMenuRoute.Main
    val dialogLikeRoute = route == ReadBookMenuRoute.PaddingConfig
    val density = LocalDensity.current
    val windowSize = LocalWindowInfo.current.containerSize
    var surfaceHeightPx by remember { mutableIntStateOf(0) }
    val morphProgress by animateFloatAsState(
        targetValue = if (dialogLikeRoute) 1f else 0f,
        label = "ReadBookMenuMorph",
    )
    val maxHeight = with(density) {
        windowSize.height.toDp() * 0.64f
    }
    val screenWidth = with(density) { windowSize.width.toDp() }
    val dialogAvailableWidth = screenWidth - 48.dp
    val dialogWidth = if (dialogAvailableWidth < 560.dp) {
        dialogAvailableWidth
    } else {
        560.dp
    }
    val isFloating = state.menuConfig.readMenuFloatingBottomBar
    val navBarHeight = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val floatingHorizontalMargin = if (isFloating) 16.dp else 0.dp
    val floatingBottomMargin = if (isFloating) 16.dp + navBarHeight else 0.dp
    val mainHorizontalMargin =
        if (expanded && !isFloating) 0.dp else floatingHorizontalMargin
    val mainBottomMargin =
        if (expanded && !isFloating) 0.dp else floatingBottomMargin
    val mainCorner = state.menuConfig.readMenuBottomCornerRadius.dp
    val mainWidth = (screenWidth - mainHorizontalMargin * 2).coerceAtLeast(0.dp)
    val surfaceWidth = if (expanded) {
        if (isFloating && !dialogLikeRoute) mainWidth
        else lerp(screenWidth, dialogWidth, morphProgress)
    } else {
        mainWidth
    }
    val bottomTopCorner by animateDpAsState(
        targetValue = if (expanded && !isFloating) 24.dp else 0.dp,
        label = "ReadBookMenuCorner",
    )
    val corner = lerp(bottomTopCorner, 28.dp, morphProgress)
    val bottomCorner = lerp(0.dp, 28.dp, morphProgress)
    val surfaceShape = if (expanded) {
        if (isFloating && !dialogLikeRoute) {
            RoundedCornerShape(mainCorner)
        } else {
            RoundedCornerShape(
                topStart = corner,
                topEnd = corner,
                bottomStart = bottomCorner,
                bottomEnd = bottomCorner,
            )
        }
    } else if (isFloating) {
        RoundedCornerShape(mainCorner)
    } else {
        RoundedCornerShape(topStart = mainCorner, topEnd = mainCorner)
    }

    val bottomBarBorderWidth = state.menuConfig.readMenuBorderWidth
    val bottomBarBorderColor = (if (ReadStyleResolver.isNightTheme()) {
        state.menuConfig.readMenuBorderColorNight
    } else {
        state.menuConfig.readMenuBorderColor
    }).takeIf { it != 0 }
        ?: LegadoTheme.colorScheme.outlineVariant.hashCode()
    val extendSurfaceToNavigationBar = !isFloating && !dialogLikeRoute
    val useLiquidGlass = readMenuLiquidGlassEnabled(backdrop, state.menuConfig)
    val useLens = useLiquidGlass && isFloating && mainCorner > 0.dp

    Surface(
        modifier = Modifier
            .padding(
                start = mainHorizontalMargin,
                end = mainHorizontalMargin,
                bottom = mainBottomMargin,
            )
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    if (isFloating || extendSurfaceToNavigationBar) WindowInsetsSides.Horizontal
                    else WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                )
            )
            .width(surfaceWidth)
            .heightIn(max = maxHeight)
            .onSizeChanged { surfaceHeightPx = it.height }
            .offset {
                val liftPx = ((windowSize.height - surfaceHeightPx) / 2f) * morphProgress
                IntOffset(x = 0, y = -liftPx.roundToInt())
            }
            .readMenuLiquidGlass(
                backdrop = backdrop,
                colors = colors,
                shape = surfaceShape,
                useTopBarStyle = false,
                useLens = useLens,
                menuConfig = state.menuConfig,
            )
            .drawWithCache {
                val strokeWidthPx = bottomBarBorderWidth.dp.toPx()
                val outline = surfaceShape.createOutline(size, layoutDirection, this)
                val strokeStyle = Stroke(width = strokeWidthPx * 2)
                val outlinePath = when (outline) {
                    is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
                    is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
                    is Outline.Generic -> outline.path
                }
                onDrawBehind {
                    if (bottomBarBorderWidth > 0) {
                        drawPath(
                            path = outlinePath,
                            color = Color(bottomBarBorderColor),
                            style = strokeStyle,
                        )
                    }
                }
            },
        shape = surfaceShape,
        color = if (useLiquidGlass) Color.Transparent else colors.background.copy(
            alpha = state.menuConfig.readMenuBlurAlpha.coerceIn(0, 100) / 100f
        ),
        contentColor = colors.content
    ) {
        AnimatedContent(
            targetState = route,
            transitionSpec = {
                (slideInVertically { it / 4 } + fadeIn())
                    .togetherWith(slideOutVertically { -it / 4 } + fadeOut())
                    .using(SizeTransform(clip = true))
            },
            label = "ReadBookMenuRoute",
        ) { targetRoute ->
            when (targetRoute) {
                ReadBookMenuRoute.Main -> {
                    MenuBottomBar(
                        state = state,
                        colors = colors,
                        onIntent = onIntent,
                        context = context,
                        bottomPadding = if (extendSurfaceToNavigationBar) navBarHeight + 16.dp else 16.dp,
                        glassEnabled = useLiquidGlass,
                    )
                }

                    ReadBookMenuRoute.ReadStyle -> {
                        ReadBookMenuRoutePage(
                            title = stringResource(R.string.read_config),
                            maxHeight = maxHeight,
                            bottomPadding = if (extendSurfaceToNavigationBar) navBarHeight else 0.dp,
                            onBack = { onIntent(ReadBookIntent.ReadMenuBack) },
                        ) {
                            ReadStyleContent(
                                onOpenPaddingConfig = {
                                    onIntent(ReadBookIntent.OpenReadMenuRoute(ReadBookMenuRoute.PaddingConfig))
                                },
                                onOpenMoreConfig = {
                                    onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.MoreConfig))
                                },
                                onOpenBgTextConfig = { index ->
                                    onIntent(ReadBookIntent.OpenBgTextConfig(index))
                                },
                                onOpenTextTitle = {
                                    onIntent(ReadBookIntent.OpenReadMenuRoute(ReadBookMenuRoute.TextTitle))
                                },
                                onOpenFontSelect = {
                                    onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.FontSelect))
                                },
                                onToggleDayNight = {
                                    onIntent(ReadBookIntent.ToggleDayNight)
                                },
                                onIntent = onIntent,
                            )
                        }
                    }

                    ReadBookMenuRoute.PaddingConfig -> {
                        ReadBookMenuRoutePage(
                            title = stringResource(R.string.padding),
                            maxHeight = maxHeight,
                            scrollContent = true,
                            bottomPadding = if (extendSurfaceToNavigationBar) navBarHeight else 0.dp,
                            onBack = { onIntent(ReadBookIntent.ReadMenuBack) },
                        ) {
                            PaddingConfigContent(
                                onIntent = onIntent,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }

                    ReadBookMenuRoute.TextTitle -> {
                        ReadBookMenuRoutePage(
                            title = stringResource(R.string.read_config_text_effects),
                            maxHeight = maxHeight,
                            bottomPadding = if (extendSurfaceToNavigationBar) navBarHeight else 0.dp,
                            onBack = { onIntent(ReadBookIntent.ReadMenuBack) },
                        ) {
                            ReadStyleTextTitleContent(
                                onOpenShadowSet = {
                                    onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.ShadowSet))
                                },
                                onOpenUnderlineConfig = {
                                    onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.UnderlineConfig))
                                },
                                onOpenRegexColor = {
                                    onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.RegexColorConfig))
                                },
                                onOpenFontSelect = {
                                    onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.FontSelect))
                                },
                                modifier = Modifier.padding(horizontal = 16.dp),
                                onIntent = onIntent,
                            )
                        }
                    }

                    ReadBookMenuRoute.ReadAloud -> {
                        ReadBookMenuRoutePage(
                            title = stringResource(R.string.aloud_config),
                            maxHeight = maxHeight,
                            scrollContent = true,
                            bottomPadding = if (extendSurfaceToNavigationBar) navBarHeight else 0.dp,
                            onBack = { onIntent(ReadBookIntent.ReadMenuBack) },
                        ) {
                            ReadAloudContent(
                                onDismissRequest = { onIntent(ReadBookIntent.HideMenu) },
                                onOpenChapterList = {
                                    onIntent(ReadBookIntent.HideMenu)
                                    onIntent(ReadBookIntent.OpenChapterList)
                                },
                                onShowMainMenu = {
                                    onIntent(ReadBookIntent.ReadMenuBack)
                                },
                                onStopAutoPage = { onIntent(ReadBookIntent.StopAutoPage) },
                                onShowReadAloudConfig = {
                                    onIntent(ReadBookIntent.ShowReadAloudConfig)
                                },
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }

                    is ReadBookMenuRoute.Bookmark -> {
                        ReadBookMenuRoutePage(
                            title = targetRoute.bookmark.chapterName,
                            maxHeight = maxHeight,
                            scrollContent = true,
                            bottomPadding = if (extendSurfaceToNavigationBar) navBarHeight else 0.dp,
                            onBack = { onIntent(ReadBookIntent.ReadMenuBack) },
                        ) {
                            Box(Modifier.padding(horizontal = 16.dp)) {
                                BookmarkEditContent(
                                    bookmark = targetRoute.bookmark,
                                    onSave = { onIntent(ReadBookIntent.SaveBookmark(it)) },
                                    onDelete = { onIntent(ReadBookIntent.DeleteBookmark(it)) },
                                )
                            }
                        }
                    }
            }
        }
    }
}

@Composable
private fun ReadBookMenuRoutePage(
    title: String,
    maxHeight: Dp,
    scrollContent: Boolean = false,
    bottomPadding: Dp = 0.dp,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .animateContentSize()
            .padding(top = 16.dp, bottom = 16.dp + bottomPadding),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SmallTonalButton(
                onClick = onBack,
                icon = Icons.AutoMirrored.Filled.ArrowBack
            )
            Text(
                text = title,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                style = LegadoTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(48.dp))
        }

        if (scrollContent) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                content()
            }
        } else {
            content()
        }
    }
}

@Composable
private fun MenuTitleBar(
    state: ReadBookUiState,
    colors: ReadMenuColors,
    onIntent: (ReadBookIntent) -> Unit,
    backdrop: Backdrop?,
) {
    var expanded by remember { mutableStateOf(false) }

    val topBarBorderWidth = state.menuConfig.readMenuBorderWidth
    val topBarBorderColor = (if (ReadStyleResolver.isNightTheme()) {
        state.menuConfig.readMenuBorderColorNight
    } else {
        state.menuConfig.readMenuBorderColor
    }).takeIf { it != 0 }
        ?: LegadoTheme.colorScheme.outlineVariant.hashCode()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (readMenuLiquidGlassEnabled(backdrop, state.menuConfig)) {
                    Modifier.readMenuLiquidGlass(
                        backdrop = backdrop,
                        colors = colors,
                        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                        useTopBarStyle = true,
                        useLens = true,
                        menuConfig = state.menuConfig,
                    )
                } else {
                    Modifier.background(colors.background.copy(
                        alpha = state.menuConfig.readMenuBlurAlpha.coerceIn(0, 100) / 100f
                    ))
                }
            )
            .then(
                if (topBarBorderWidth > 0) {
                    Modifier.drawBehind {
                        val strokeWidth = topBarBorderWidth.dp.toPx()
                        drawLine(
                            color = Color(topBarBorderColor),
                            start = Offset(0f, size.height),
                            end = Offset(size.width, size.height),
                            strokeWidth = strokeWidth,
                        )
                    }
                } else Modifier
            )
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                )
            )
    ) {
        // Title row: back + title + actions + overflow
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Back button
            MediumTonalButton(
                onClick = { onIntent(ReadBookIntent.ReadMenuBack) },
                modifier = Modifier.padding(start = 4.dp),
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
            )

            AppText(
                text = state.bookName,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onIntent(ReadBookIntent.OpenBookInfo) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                style = LegadoTheme.typography.titleMedium,
                color = colors.content,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            // Source action button (non-local books only)
            if (!state.isLocalBook) {
                SourceActionButton(onIntent = onIntent)
                Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                RefreshActionButton(onIntent = onIntent)
            }

            Box {
                MediumTonalButton(
                    onClick = { expanded = true },
                    icon = Icons.Default.MoreVert
                )
                OverflowDropdownMenu(
                    state = state,
                    onIntent = onIntent,
                    expanded = expanded,
                    onDismiss = { expanded = false },
                )
            }
        }

        // Chapter name + source action
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.chapterName,
                modifier = Modifier.weight(1f),
                style = LegadoTheme.typography.bodyMedium,
                color = colors.content.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (!state.isLocalBook && state.bookSource != null) {
                Text(
                    text = state.bookSource.bookSourceName,
                    modifier = Modifier
                        .clickable { onIntent(ReadBookIntent.OpenSourceEdit) }
                        .padding(start = 8.dp),
                    style = LegadoTheme.typography.bodySmall,
                    color = colors.content,
                    maxLines = 1,
                )
            }
        }

        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun SourceActionButton(
    onIntent: (ReadBookIntent) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        MediumTonalButton(
            onClick = { onIntent(ReadBookIntent.MenuChangeSource) },
            onLongClick = { expanded = true },
            icon = Icons.Default.SwapHoriz,
            contentDescription = stringResource(R.string.change_origin),
        )

        RoundDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) { dismiss ->
            RoundDropdownMenuItem(
                text = stringResource(R.string.change_origin),
                onClick = { dismiss(); onIntent(ReadBookIntent.MenuChangeSource) },
            )
            RoundDropdownMenuItem(
                text = stringResource(R.string.chapter_change_source),
                onClick = { dismiss(); onIntent(ReadBookIntent.MenuChapterChangeSource) },
            )
        }
    }
}

@Composable
private fun RefreshActionButton(
    onIntent: (ReadBookIntent) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        MediumTonalButton(
            onClick = { onIntent(ReadBookIntent.MenuRefreshAfter) },
            onLongClick = { expanded = true },
            icon = Icons.Default.Refresh,
            contentDescription = stringResource(R.string.menu_refresh_after),
        )

        RoundDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) { dismiss ->
            RoundDropdownMenuItem(
                text = stringResource(R.string.menu_refresh_dur),
                onClick = { dismiss(); onIntent(ReadBookIntent.MenuRefreshDur) },
            )
            RoundDropdownMenuItem(
                text = stringResource(R.string.menu_refresh_after),
                onClick = { dismiss(); onIntent(ReadBookIntent.MenuRefreshAfter) },
            )
        }
    }
}

@Composable
private fun FloatingIconRow(
    state: ReadBookUiState,
    colors: ReadMenuColors,
    alignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    onIntent: (ReadBookIntent) -> Unit,
    backdrop: Backdrop?,
) {
    val context = LocalContext.current
    val titleBarIcons = remember { loadFloatingIcons(context, onIntent) }

    if (titleBarIcons.isEmpty()) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(all = 16.dp),
        horizontalArrangement = when (alignment) {
            Alignment.Start -> Arrangement.Start
            Alignment.End -> Arrangement.End
            else -> Arrangement.Center
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        titleBarIcons.forEach { iconDef ->
            val customPath = remember { state.menuConfig.titleBarCustomIcons[iconDef.id] }
            val isCustom = !customPath.isNullOrBlank()
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(40.dp)
                    .then(
                        when {
                            isCustom -> Modifier
                            readMenuLiquidGlassEnabled(backdrop, state.menuConfig) -> Modifier.readMenuLiquidGlass(
                                backdrop = backdrop,
                                colors = colors,
                                shape = CircleShape,
                                useTopBarStyle = true,
                                useLens = true,
                                menuConfig = state.menuConfig,
                            )
                            else -> Modifier.background(colors.background.copy(
                                alpha = state.menuConfig.readMenuBlurAlpha.coerceIn(0, 100) / 100f
                            ), CircleShape)
                        }
                    )
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { iconDef.onClick() },
            ) {
                if (isCustom) {
                    AsyncImage(
                        model = customPath,
                        contentDescription = iconDef.label,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape),
                    )
                } else {
                    Icon(
                        painter = painterResource(iconDef.iconRes),
                        contentDescription = iconDef.label,
                        tint = colors.content,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun OverflowDropdownMenu(
    state: ReadBookUiState,
    onIntent: (ReadBookIntent) -> Unit,
    expanded: Boolean,
    onDismiss: () -> Unit,
) {
    RoundDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) { dismiss ->
        var imageStyleExpanded by remember { mutableStateOf(false) }

        // Source actions
        if (!state.isLocalBook) {
            RoundDropdownMenuItem(
                text = stringResource(R.string.menu_refresh_all),
                onClick = { dismiss(); onIntent(ReadBookIntent.MenuRefreshAll) },
            )
        }

        // TXT
        if (state.isLocalTxt) {
            RoundDropdownMenuItem(
                text = stringResource(R.string.txt_toc_rule),
                onClick = { dismiss(); onIntent(ReadBookIntent.MenuTocRegex) },
            )
        }

        // Local book
        if (state.isLocalBook) {
            RoundDropdownMenuItem(
                text = stringResource(R.string.set_charset),
                onClick = {
                    dismiss()
                    onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.Charset))
                },
            )
        }

        PillDivider()

        // Content operations
        RoundDropdownMenuItem(
            text = stringResource(R.string.bookmark_add),
            onClick = { dismiss(); onIntent(ReadBookIntent.AddBookmark) },
        )
        RoundDropdownMenuItem(
            text = stringResource(R.string.edit_content),
            onClick = {
                dismiss()
                onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.ContentEdit))
            },
        )
        if (!state.isLocalBook) {
            RoundDropdownMenuItem(
                text = stringResource(R.string.offline_cache),
                onClick = {
                    dismiss()
                    onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.Download))
                },
            )
        }
        RoundDropdownMenuItem(
            text = stringResource(R.string.update_toc),
            onClick = { dismiss(); onIntent(ReadBookIntent.MenuUpdateToc) },
        )
        RoundDropdownMenuItem(
            text = stringResource(R.string.simulated_reading),
            onClick = {
                dismiss()
                onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.SimulatedReading))
            },
        )
        RoundDropdownMenuItem(
            text = stringResource(R.string.reverse_content),
            onClick = { dismiss(); onIntent(ReadBookIntent.MenuReverseContent) },
        )

        PillDivider()

        // Checkable items
        RoundDropdownMenuItem(
            text = stringResource(R.string.replace_rule_title),
            isSelected = state.useReplaceRule,
            onClick = { onIntent(ReadBookIntent.MenuEnableReplace) },
        )
        RoundDropdownMenuItem(
            text = stringResource(R.string.replace_rule_title_setting),
            onClick = { dismiss(); onIntent(ReadBookIntent.MenuSettingReplace) },
        )
        RoundDropdownMenuItem(
            text = stringResource(R.string.effective_replaces),
            onClick = {
                dismiss()
                onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.EffectiveReplaces))
            },
        )
        RoundDropdownMenuItem(
            text = stringResource(R.string.same_title_removed),
            isSelected = state.sameTitleRemoved,
            onClick = { onIntent(ReadBookIntent.MenuSameTitleRemoved) },
        )
        RoundDropdownMenuItem(
            text = stringResource(R.string.re_segment),
            isSelected = state.reSegment,
            onClick = { onIntent(ReadBookIntent.MenuReSegment) },
        )

        // EPUB
        if (state.isEpub) {
            RoundDropdownMenuItem(
                text = stringResource(R.string.del_ruby_tag),
                isSelected = state.delRubyTag,
                onClick = { onIntent(ReadBookIntent.MenuDelRubyTag) },
            )
            RoundDropdownMenuItem(
                text = stringResource(R.string.del_h_tag),
                isSelected = state.delHTag,
                onClick = { onIntent(ReadBookIntent.MenuDelHTag) },
            )
        }

        PillDivider()

        // Config
        Box {
            RoundDropdownMenuItem(
                text = stringResource(R.string.image_style),
                onClick = { imageStyleExpanded = true },
            )
            RoundDropdownMenu(
                expanded = imageStyleExpanded,
                onDismissRequest = { imageStyleExpanded = false },
            ) { subDismiss ->
                RoundDropdownMenuItem(
                    text = stringResource(R.string.btn_default_s),
                    onClick = {
                        subDismiss()
                        onIntent(ReadBookIntent.MenuImageStyle(Book.imgStyleDefault))
                    },
                )
                RoundDropdownMenuItem(
                    text = stringResource(R.string.image_style_full),
                    onClick = {
                        subDismiss()
                        onIntent(ReadBookIntent.MenuImageStyle(Book.imgStyleFull))
                    },
                )
                RoundDropdownMenuItem(
                    text = stringResource(R.string.image_style_text),
                    onClick = {
                        subDismiss()
                        onIntent(ReadBookIntent.MenuImageStyle(Book.imgStyleText))
                    },
                )
                RoundDropdownMenuItem(
                    text = stringResource(R.string.image_style_single),
                    onClick = {
                        subDismiss()
                        onIntent(ReadBookIntent.MenuImageStyle(Book.imgStyleSingle))
                    },
                )
            }
        }
        RoundDropdownMenuItem(
            text = stringResource(R.string.book_page_anim),
            onClick = {
                dismiss()
                onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.PageAnim))
            },
        )
        RoundDropdownMenuItem(
            text = stringResource(R.string.config_btn),
            onClick = {
                dismiss()
                onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.ToolButtonConfig))
            },
        )

        // Progress sync
        if (state.isReadingProgressSyncConfigured) {
            RoundDropdownMenuItem(
                text = stringResource(R.string.get_book_progress),
                onClick = { dismiss(); onIntent(ReadBookIntent.MenuGetProgress) },
            )
            RoundDropdownMenuItem(
                text = stringResource(R.string.cover_book_progress),
                onClick = { dismiss(); onIntent(ReadBookIntent.MenuCoverProgress) },
            )
        }

        PillDivider()

        RoundDropdownMenuItem(
            text = stringResource(R.string.log),
            onClick = {
                dismiss()
                onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.AppLog))
            },
        )
    }
}

@Composable
private fun MenuBottomBar(
    state: ReadBookUiState,
    colors: ReadMenuColors,
    onIntent: (ReadBookIntent) -> Unit,
    context: Context,
    bottomPadding: Dp = 0.dp,
    glassEnabled: Boolean = false,
) {
    val seekMax = state.seekMax.coerceAtLeast(0)
    val sliderMax = seekMax.toFloat().coerceAtLeast(1f)
    var sliderValue by remember { mutableFloatStateOf(state.seekProgress.coerceIn(0, seekMax).toFloat()) }
    var sliderDragging by remember { mutableStateOf(false) }

    LaunchedEffect(state.seekProgress, seekMax) {
        if (!sliderDragging) {
            sliderValue = state.seekProgress.coerceIn(0, seekMax).toFloat()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (glassEnabled) Color.Transparent else colors.background.copy(
                alpha = state.menuConfig.readMenuBlurAlpha.coerceIn(0, 100) / 100f
            ))
            .padding(top = 12.dp, bottom = bottomPadding)
            .animateContentSize(),
    ) {
        // Seek bar row: prev + slider + next
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MediumTonalButton(
                onClick = { onIntent(ReadBookIntent.PrevChapter) },
                icon = Icons.AutoMirrored.Filled.ArrowBack
            )

            AppSlider(
                value = sliderValue.coerceIn(0f, sliderMax),
                onValueChange = { value ->
                    sliderDragging = true
                    sliderValue = value.coerceIn(0f, sliderMax)
                },
                onValueChangeFinished = {
                    val target = sliderValue.roundToInt().coerceIn(0, seekMax)
                    sliderDragging = false
                    val behavior = AppConfig.progressBarBehavior
                    if (behavior == "page") {
                        onIntent(ReadBookIntent.SkipToPage(target))
                    } else {
                        onIntent(ReadBookIntent.SeekToChapter(target))
                    }
                },
                valueRange = 0f..sliderMax,
                steps = (seekMax - 1).coerceAtLeast(0),
                enabled = seekMax > 0,
                modifier = Modifier.weight(1f)
            )

            MediumTonalButton(
                onClick = { onIntent(ReadBookIntent.NextChapter) },
                icon = Icons.AutoMirrored.Filled.ArrowForward
            )
        }

        Spacer(Modifier.height(8.dp))

        // Tool buttons
        val toolButtons = remember(context, state.configUpdateTrigger) {
            loadToolButtons(context, onIntent)
        }
        val itemsPerRow = state.menuConfig.readMenuIconItemsPerRow
        val rowCount = state.menuConfig.readMenuIconRowCount
        val pageSize = (itemsPerRow * rowCount).coerceAtLeast(1)
        val pageCount = ceil(toolButtons.size / pageSize.toFloat()).roundToInt().coerceAtLeast(1)
        val pagerState = rememberPagerState(pageCount = { pageCount })

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth(),
        ) { page ->
            val pageButtons = toolButtons.drop(page * pageSize).take(pageSize)
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                pageButtons.chunked(itemsPerRow).forEach { rowButtons ->
                    Row(
                        horizontalArrangement = when {
                            rowButtons.size > 3 -> Arrangement.SpaceBetween
                            else -> Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally)
                        },
                        modifier = Modifier
                            .fillMaxWidth(),
                    ) {
                        rowButtons.forEach { button ->
                            ToolButtonItem(
                                button = button,
                                state = state,
                                colors = colors,
                                modifier = Modifier.width(40.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolButtonItem(
    button: ToolButtonDef,
    state: ReadBookUiState,
    colors: ReadMenuColors,
    modifier: Modifier = Modifier,
) {
    val iconTint = colors.content
    val badgeCount = when (button.id) {
        "replace_badge" -> state.effectiveReplaceCount
        else -> 0
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        NormalCard(
            cornerRadius = 16.dp,
            containerColor = when (state.menuConfig.readMenuIconStyle) {
                1 -> LegadoTheme.colorScheme.surfaceContainerLow
                else -> Color.Transparent
            },
            border = if (state.menuConfig.readMenuIconStyle == 2) {
                BorderStroke(1.dp, iconTint.copy(alpha = 0.45f))
            } else {
                null
            },
            modifier = Modifier.size(40.dp),
            onClick = { button.onClick() }
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                if (button.customIconPath.isNullOrBlank()) {
                    Icon(
                        painter = painterResource(button.iconRes),
                        contentDescription = button.description,
                        modifier = Modifier.size(20.dp),
                        tint = iconTint,
                    )
                } else {
                    AsyncImage(
                        model = button.customIconPath,
                        contentDescription = button.description,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(20.dp),
                    )
                }
                if (badgeCount > 0) {
                    Text(
                        text = badgeCount.toString(),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .background(
                                LegadoTheme.colorScheme.error,
                                RoundedCornerShape(8.dp),
                            )
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                        style = LegadoTheme.typography.labelSmall,
                        color = LegadoTheme.colorScheme.onError,
                    )
                }
            }
        }
        if (state.menuConfig.readMenuIconShowText) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = button.description,
                style = LegadoTheme.typography.labelSmall,
                color = iconTint.copy(alpha = 0.78f),
                maxLines = 1,
                modifier = Modifier.wrapContentWidth(
                    align = Alignment.CenterHorizontally,
                    unbounded = true,
                ),
            )
        }
    }
}

private data class ToolButtonDef(
    val id: String,
    val iconRes: Int,
    val description: String,
    val customIconPath: String?,
    val onClick: () -> Unit,
)

private fun loadToolButtons(
    context: Context,
    onIntent: (ReadBookIntent) -> Unit,
): List<ToolButtonDef> {
    val customIcons = loadMenuCustomIcons(context)
    fun ReadMenuButtonInfo.toButton(onClick: () -> Unit): ToolButtonDef {
        return ToolButtonDef(id, iconRes, label, customIcons[id], onClick)
    }
    val infoMap = readMenuButtonInfos(context).associateBy { it.id }
    val allButtons = listOf(
        infoMap.getValue("search").toButton {
            onIntent(ReadBookIntent.OpenSearch(null))
        },
        infoMap.getValue("catalog").toButton {
            onIntent(ReadBookIntent.OpenChapterList)
        },
        infoMap.getValue("read_aloud").toButton {
            onIntent(ReadBookIntent.ToggleReadAloud)
        },
        infoMap.getValue("setting").toButton {
            onIntent(ReadBookIntent.OpenReadMenuRoute(ReadBookMenuRoute.ReadStyle))
        },
        infoMap.getValue("addBookmark").toButton {
            onIntent(ReadBookIntent.AddBookmark)
        },
        infoMap.getValue("theme").toButton {
            onIntent(ReadBookIntent.ToggleDayNight)
        },
        infoMap.getValue("prev_chapter").toButton {
            onIntent(ReadBookIntent.PrevChapter)
        },
        infoMap.getValue("next_chapter").toButton {
            onIntent(ReadBookIntent.NextChapter)
        },
        infoMap.getValue("replace").toButton {
            onIntent(ReadBookIntent.ChangeReplaceRule(true))
        },
        infoMap.getValue("replace_badge").toButton {
            onIntent(ReadBookIntent.ChangeReplaceRule(true))
        },
        infoMap.getValue("auto_page").toButton {
            onIntent(ReadBookIntent.ToggleAutoPage)
        },
        infoMap.getValue("translate").toButton {
            onIntent(ReadBookIntent.ToggleTranslation)
        },
    )

    val prefs = context.getSharedPreferences("tool_button_config", Context.MODE_PRIVATE)
    val str = prefs.getString("tool_buttons", null)
    val savedList = str?.split(";")?.mapNotNull {
        val parts = it.split(",")
        if (parts.size == 2) parts[0] to parts[1].toBoolean() else null
    } ?: emptyList()

    val allMap = allButtons.associateBy { it.id }

    return if (savedList.isNotEmpty()) {
        val result = mutableListOf<ToolButtonDef>()
        savedList.forEach { (id, enabled) ->
            if (enabled) allMap[id]?.let { result.add(it) }
        }
        // Add any buttons not in saved config
        allButtons.forEach { btn ->
            if (savedList.none { it.first == btn.id }) {
                result.add(btn)
            }
        }
        result
    } else {
        allButtons.take(5)
    }
}

private data class ReadMenuColors(
    val background: Color,
    val content: Color,
)

private fun readMenuLiquidGlassEnabled(backdrop: Backdrop?, menuConfig: ReadMenuConfig): Boolean {
    return backdrop != null &&
            menuConfig.readMenuLiquidGlass &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}

@Composable
private fun Modifier.readMenuLiquidGlass(
    backdrop: Backdrop?,
    colors: ReadMenuColors,
    shape: Shape,
    useTopBarStyle: Boolean,
    useLens: Boolean,
    menuConfig: ReadMenuConfig,
): Modifier {
    if (!readMenuLiquidGlassEnabled(backdrop, menuConfig)) return this
    val blurRadius = menuConfig.readMenuBlurRadius
    val blurAlpha = menuConfig.readMenuBlurAlpha
    val containerColor = colors.background.copy(
        alpha = (blurAlpha.coerceIn(0, 100) / 100f).coerceAtMost(0.6f)
    )

    return drawBackdrop(
        backdrop = backdrop!!,
        shape = { shape },
        effects = {
            vibrancy()
            blur(blurRadius.coerceAtLeast(0).dp.toPx())
            if (useLens) {
                val lensRadius = menuConfig.readMenuLensRadius
                lens(lensRadius.dp.toPx(), lensRadius.dp.toPx())
            }
        },
        highlight = {
            Highlight.Default
        },
        shadow = null,
        onDrawSurface = {
            drawRect(containerColor)
        },
    )
}

@Composable
private fun readMenuColors(): ReadMenuColors {
    val themeBackground = LegadoTheme.colorScheme.surfaceContainerHigh
    val themeContent = LegadoTheme.colorScheme.onSurface
    return when (AppConfig.readBarStyle) {
        1 -> ReadMenuColors(
            background = themeBackground,
            content = themeContent,
        )

        2 -> ReadMenuColors(
            background = LegadoTheme.colorScheme.surfaceContainerHigh,
            content = LegadoTheme.colorScheme.primary,
        )

        else -> ReadMenuColors(themeBackground, themeContent)
    }
}

// ========== Title Bar Icons ==========

private data class TitleBarIconDef(
    val id: String,
    val iconRes: Int,
    val label: String,
    val onClick: () -> Unit,
)

private fun loadFloatingIcons(
    context: Context,
    onIntent: (ReadBookIntent) -> Unit,
): List<TitleBarIconDef> {
    val prefs = context.getSharedPreferences("title_bar_icons", Context.MODE_PRIVATE)
    val str = prefs.getString("icons", null)
    val infoMap = readMenuButtonInfos(context).associateBy { it.id }

    val savedList = if (str.isNullOrBlank()) {
        // Default: first 5 enabled
        readMenuButtonInfos(context).mapIndexed { index, info ->
            Triple(info.id, index < 5, info)
        }
    } else {
        str.split(";").mapNotNull {
            val parts = it.split(",")
            if (parts.size == 2) {
                val id = parts[0]
                val enabled = parts[1].toBoolean()
                val info = infoMap[id]
                if (info != null) Triple(id, enabled, info) else null
            } else null
        }
    }

    val actionMap: Map<String, () -> Unit> = mapOf(
        "search" to { onIntent(ReadBookIntent.OpenSearch(null)) },
        "catalog" to { onIntent(ReadBookIntent.OpenChapterList) },
        "read_aloud" to { onIntent(ReadBookIntent.ToggleReadAloud) },
        "setting" to { onIntent(ReadBookIntent.OpenReadMenuRoute(ReadBookMenuRoute.ReadStyle)) },
        "addBookmark" to { onIntent(ReadBookIntent.AddBookmark) },
        "theme" to { onIntent(ReadBookIntent.ToggleDayNight) },
        "prev_chapter" to { onIntent(ReadBookIntent.PrevChapter) },
        "next_chapter" to { onIntent(ReadBookIntent.NextChapter) },
        "replace" to { onIntent(ReadBookIntent.ChangeReplaceRule(true)) },
        "replace_badge" to { onIntent(ReadBookIntent.ChangeReplaceRule(true)) },
        "auto_page" to { onIntent(ReadBookIntent.ToggleAutoPage) },
        "translate" to { onIntent(ReadBookIntent.ToggleTranslation) },
    )

    return savedList
        .filter { (_, enabled, _) -> enabled }
        .map { (id, _, info) ->
            TitleBarIconDef(
                id = id,
                iconRes = info.iconRes,
                label = info.label,
                onClick = actionMap[id] ?: {},
            )
        }
}
