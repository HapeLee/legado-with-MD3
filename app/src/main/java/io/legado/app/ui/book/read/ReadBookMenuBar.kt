package io.legado.app.ui.book.read

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CleanHands
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Toc
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import coil.compose.AsyncImage
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousCapsule
import dev.chrisbanes.haze.HazeState
import io.legado.app.R
import io.legado.app.constant.ReadMenuBlurMode
import io.legado.app.constant.ReadMenuBlurStyle
import io.legado.app.data.entities.Book
import io.legado.app.data.repository.ReadPreferences
import io.legado.app.help.config.ReadStyleResolver
import io.legado.app.ui.animation.DampedDragAnimation
import io.legado.app.ui.book.read.sheet.AutoReadContent
import io.legado.app.ui.book.read.sheet.HeaderFooterPage
import io.legado.app.ui.book.read.sheet.PaddingConfigContent
import io.legado.app.ui.book.read.sheet.ReadAloudContent
import io.legado.app.ui.book.read.sheet.ReadMenuButtonInfo
import io.legado.app.ui.book.read.sheet.ReadStyleContent
import io.legado.app.ui.book.read.sheet.ReadStyleTextTitleContent
import io.legado.app.ui.book.read.sheet.readMenuButtonInfos
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppSlider
import io.legado.app.ui.widget.components.AppVerticalSlider
import io.legado.app.ui.widget.components.button.series.SmallTonalButton
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.divider.PillDivider
import io.legado.app.ui.widget.components.menuItem.MenuItemIcon
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.reader.ReaderMenuEffect
import io.legado.app.ui.widget.components.reader.ReaderMenuPlacement
import io.legado.app.ui.widget.components.reader.ReaderMenuTintStyle
import io.legado.app.ui.widget.components.reader.ReaderMenuVisualState
import io.legado.app.ui.widget.components.reader.readerMenuHazeEffect
import io.legado.app.ui.widget.components.reader.readerMenuLiquidGlass
import io.legado.app.ui.widget.components.reader.readerMenuLiquidGlassAvailable
import io.legado.app.ui.widget.components.reader.readerMenuSurfaceBrush
import io.legado.app.ui.widget.components.text.AnimatedText
import io.legado.app.ui.widget.components.text.AppText
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Compose replacement for ReadMenu — main reading menu overlay.
 */
@Composable
fun ReadBookMenuBar(
    state: ReadBookUiState,
    preferences: ReadPreferences,
    onIntent: (ReadBookIntent) -> Unit,
    onBrightnessPreview: (Int) -> Unit,
    backdrop: Backdrop? = null,
    hazeState: HazeState? = null,
) {
    val context = LocalContext.current
    val currentRoute = state.menuState.currentRoute
    val searchMenuVisible = state.isShowingSearchResult &&
            state.searchMenuVisible &&
            !state.menuVisible
    val contentTarget = if (searchMenuVisible) {
        ReadBookMenuContent.Search
    } else {
        ReadBookMenuContent.Route(currentRoute)
    }
    val dialogLikeRoute = currentRoute == ReadBookMenuRoute.PaddingConfig || currentRoute == ReadBookMenuRoute.HeaderFooterConfig
    var readStylePage by remember { mutableIntStateOf(0) }
    LaunchedEffect(currentRoute) {
        if (currentRoute != ReadBookMenuRoute.ReadStyle) {
            readStylePage = 0
        }
    }
    val hideTopBar = dialogLikeRoute ||
            currentRoute == ReadBookMenuRoute.TextTitle
    val menuColors = readMenuColors(preferences.readBarStyle)

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = state.menuVisible || searchMenuVisible,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) {
                        if (searchMenuVisible) {
                            onIntent(ReadBookIntent.HideSearchMenu)
                        } else {
                            onIntent(ReadBookIntent.HideMenu)
                        }
                    }
            )
        }

        // Top title bar + floating icon row (top positions)
        AnimatedVisibility(
            visible = state.menuVisible && !hideTopBar,
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
                    hazeState = hazeState,
                    titleBarMode = preferences.titleBarMode,
                )
                if (state.menuConfig.showTitleBarIcons && state.menuConfig.titleBarIconPosition <= 1) {
                    FloatingIconRow(
                        state = state,
                        preferences = preferences,
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

        // Vertical brightness bar (right or left side)
        val brightnessMode = state.menuConfig.showBrightnessView
        val brightnessVwPos = state.menuConfig.brightnessVwPos
        val brightnessIsLeft = brightnessVwPos == "0"
        val brightnessShape = RoundedCornerShape(40.dp)
        val useBrightnessHaze = readMenuBottomBarHazeEnabled(
            hazeState = hazeState,
            menuConfig = state.menuConfig,
            isFloating = false,
        )
        val brightnessVisualState = ReaderMenuVisualState(
            effect = if (useBrightnessHaze) ReaderMenuEffect.Haze else ReaderMenuEffect.None,
            tintStyle = ReaderMenuTintStyle.Fill,
            styleEnabled = false,
            tintAllowed = true,
            tintFill = false,
        )
        AnimatedVisibility(
            visible = brightnessMode == "2" && state.menuVisible && currentRoute == ReadBookMenuRoute.Main,
            enter = slideInHorizontally(
                initialOffsetX = { if (brightnessIsLeft) -it else it }
            ) + fadeIn(),
            exit = slideOutHorizontally(
                targetOffsetX = { if (brightnessIsLeft) -it else it }
            ) + fadeOut(),
            modifier = Modifier.align(
                if (brightnessIsLeft) Alignment.CenterStart else Alignment.CenterEnd
            ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = if (brightnessIsLeft) 8.dp else 0.dp,
                        end = if (brightnessIsLeft) 0.dp else 8.dp,
                    ),
                contentAlignment = if (brightnessIsLeft) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Surface(
                    modifier = if (useBrightnessHaze && hazeState != null) {
                        Modifier.readMenuBottomBarHazeEffect(
                            state = hazeState,
                            colors = menuColors,
                            shape = brightnessShape,
                            menuConfig = state.menuConfig,
                            visualState = brightnessVisualState,
                        )
                    } else {
                        Modifier
                    },
                    shape = brightnessShape,
                    color = if (useBrightnessHaze) {
                        Color.Transparent
                    } else {
                        menuColors.background.copy(
                            alpha = state.menuConfig.readMenuBlurAlpha.coerceIn(0, 100) / 100f
                        )
                    },
                    contentColor = LegadoTheme.colorScheme.onSurface,
                ) {
                    BrightnessBar(
                        brightness = state.menuConfig.readBrightness,
                        onBrightnessChange = { value ->
                            onIntent(ReadBookIntent.SetBrightness(value))
                        },
                        brightnessAuto = state.menuConfig.brightnessAuto,
                        onToggleAuto = {
                            onIntent(ReadBookIntent.ToggleBrightnessAuto(!state.menuConfig.brightnessAuto))
                        },
                        onTogglePosition = {
                            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.BrightnessVwPos(if (brightnessIsLeft) "1" else "0")))
                        },
                        vertical = true,
                        colors = menuColors,
                        menuConfig = state.menuConfig,
                        backdrop = backdrop,
                        buttonGlassEnabled = readMenuBottomBarButtonLiquidGlassEnabled(
                            backdrop = backdrop,
                            menuConfig = state.menuConfig,
                        ),
                        glassThumbEnabled = false,
                        onBrightnessPreview = onBrightnessPreview,
                    )
                }
            }
        }

        // Bottom menu + floating icon row (bottom positions)
        AnimatedVisibility(
            visible = state.menuVisible || searchMenuVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (state.menuVisible &&
                    state.menuConfig.showTitleBarIcons &&
                    state.menuConfig.titleBarIconPosition >= 2
                ) {
                    FloatingIconRow(
                        state = state,
                        preferences = preferences,
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
                ReadBookMenuSurface(
                    contentTarget = contentTarget,
                    state = state,
                    preferences = preferences,
                    colors = menuColors,
                    onIntent = onIntent,
                    context = context,
                    backdrop = backdrop,
                    hazeState = hazeState,
                    readStylePage = readStylePage,
                    onReadStylePageChanged = { readStylePage = it },
                    progressBarBehavior = preferences.progressBarBehavior,
                    onBrightnessPreview = onBrightnessPreview,
                )
            }
        }
    }
}

private sealed interface ReadBookMenuContent {
    data object Search : ReadBookMenuContent
    data class Route(val route: ReadBookMenuRoute) : ReadBookMenuContent
}

@Composable
private fun ReadBookMenuSurface(
    contentTarget: ReadBookMenuContent,
    state: ReadBookUiState,
    preferences: ReadPreferences,
    colors: ReadMenuColors,
    onIntent: (ReadBookIntent) -> Unit,
    context: Context,
    backdrop: Backdrop?,
    hazeState: HazeState?,
    readStylePage: Int,
    onReadStylePageChanged: (Int) -> Unit,
    progressBarBehavior: String,
    onBrightnessPreview: (Int) -> Unit,
) {
    val route = when (contentTarget) {
        ReadBookMenuContent.Search -> ReadBookMenuRoute.Main
        is ReadBookMenuContent.Route -> contentTarget.route
    }
    val expanded = route != ReadBookMenuRoute.Main
    val dialogLikeRoute = route == ReadBookMenuRoute.PaddingConfig || route == ReadBookMenuRoute.HeaderFooterConfig
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
    val orientation = LocalConfiguration.current.orientation
    val currentNavBarHeight = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    var lastValidNavBarHeightValue by rememberSaveable(orientation) { mutableFloatStateOf(currentNavBarHeight.value) }
    if (currentNavBarHeight.value > 0f && lastValidNavBarHeightValue != currentNavBarHeight.value) {
        lastValidNavBarHeightValue = currentNavBarHeight.value
    }
    val navBarHeight = if (currentNavBarHeight.value > 0f) currentNavBarHeight else lastValidNavBarHeightValue.dp
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
    val useLiquidGlass = readMenuBottomBarLiquidGlassEnabled(
        backdrop = backdrop,
        menuConfig = state.menuConfig,
        isFloating = isFloating,
    )
    val useHaze = readMenuBottomBarHazeEnabled(
        hazeState = hazeState,
        menuConfig = state.menuConfig,
        isFloating = isFloating,
    )
    val useBottomBarButtonGlass = readMenuBottomBarButtonLiquidGlassEnabled(
        backdrop = backdrop,
        menuConfig = state.menuConfig,
    )
    val useLens = useLiquidGlass && isFloating && mainCorner > 0.dp
    val bottomBarVisualState = ReaderMenuVisualState(
        effect = when {
            useLiquidGlass -> ReaderMenuEffect.LiquidGlass
            useHaze -> ReaderMenuEffect.Haze
            else -> ReaderMenuEffect.None
        },
        tintStyle = state.menuConfig.readMenuBottomBarBlurStyle.toReaderMenuTintStyle(),
        styleEnabled = route == ReadBookMenuRoute.Main && !isFloating,
        tintAllowed = route == ReadBookMenuRoute.Main,
        tintFill = false,
    )
    val bottomBarMenuTintColor = readMenuTintColor(state.menuConfig)
        .takeIf { bottomBarVisualState.useTint }
    val bottomBarSurfaceAlpha = state.menuConfig.readMenuBlurAlpha.coerceIn(0, 100) / 100f
    val bottomBarFillAlpha = if (expanded) {
        bottomBarSurfaceAlpha.coerceAtLeast(0.85f)
    } else {
        bottomBarSurfaceAlpha
    }
    val bottomBarTextColor = Color(
        if (ReadStyleResolver.isNightTheme()) {
            state.menuConfig.readMenuTextColorNight
        } else {
            state.menuConfig.readMenuTextColor
        }
    ).takeUnless { it == Color.Unspecified || it.alpha == 0f }
        ?: LegadoTheme.colorScheme.onSurface
    val surfaceWindowInsetSides = when {
        isFloating || extendSurfaceToNavigationBar -> WindowInsetsSides.Horizontal
        else -> WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
    }

    Surface(
        modifier = Modifier
            .padding(
                start = mainHorizontalMargin,
                end = mainHorizontalMargin,
                bottom = mainBottomMargin,
            )
            .then(
                if (route == ReadBookMenuRoute.Main) {
                    Modifier
                } else {
                    Modifier.windowInsetsPadding(
                        WindowInsets.safeDrawing.only(surfaceWindowInsetSides)
                    )
                }
            )
            .width(surfaceWidth)
            .heightIn(max = maxHeight)
            .onSizeChanged { surfaceHeightPx = it.height }
            .offset {
                val dialogLiftPx = ((windowSize.height - surfaceHeightPx) / 2f) * morphProgress
                IntOffset(x = 0, y = -dialogLiftPx.roundToInt())
            }
            .then(
                if (useLiquidGlass) {
                    Modifier.readMenuLiquidGlass(
                        backdrop = backdrop,
                        colors = colors,
                        shape = surfaceShape,
                        useTopBarStyle = false,
                        useLens = useLens,
                        menuConfig = state.menuConfig,
                    )
                } else {
                    Modifier
                }
            )
            .then(
                if (useHaze && hazeState != null) {
                    Modifier.readMenuBottomBarHazeEffect(
                        state = hazeState,
                        colors = colors,
                        shape = surfaceShape,
                        menuConfig = state.menuConfig,
                        visualState = bottomBarVisualState,
                        blurRadiusDp = if (expanded) 32 else null,
                    )
                } else {
                    Modifier
                }
            )
            .then(
                if (!useLiquidGlass && !useHaze) {
                    Modifier
                        .clip(surfaceShape)
                        .background(
                            if (bottomBarVisualState.isGradient) {
                                readerMenuSurfaceBrush(
                                    style = ReaderMenuTintStyle.Gradient,
                                    placement = ReaderMenuPlacement.Bottom,
                                    color = bottomBarMenuTintColor ?: colors.background,
                                    alpha = bottomBarFillAlpha,
                                )
                            } else {
                                readerMenuSurfaceBrush(
                                    style = ReaderMenuTintStyle.Fill,
                                    placement = ReaderMenuPlacement.Bottom,
                                    color = colors.background,
                                    alpha = bottomBarFillAlpha,
                                )
                            }
                        )
                } else {
                    Modifier
                }
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
        color = Color.Transparent,
        contentColor = colors.content
    ) {
        AnimatedContent(
            targetState = contentTarget,
            transitionSpec = {
                (slideInVertically { it / 4 } + fadeIn())
                    .togetherWith(slideOutVertically { -it / 4 } + fadeOut())
                    .using(SizeTransform(clip = true))
            },
            label = "ReadBookMenuRoute",
        ) { target ->
            when (target) {
                ReadBookMenuContent.Search -> {
                    SearchBottomMenuContent(
                        state = state,
                        colors = colors,
                        onIntent = onIntent,
                        bottomPadding = if (extendSurfaceToNavigationBar) navBarHeight + 16.dp else 16.dp,
                        surfaceEffectEnabled = useLiquidGlass || useHaze,
                    )
                }

                is ReadBookMenuContent.Route -> when (val targetRoute = target.route) {
                    ReadBookMenuRoute.Main -> {
                    MenuBottomBar(
                        state = state,
                        eyeProtectionEnabled = preferences.eyeProtectionEnabled,
                        colors = colors,
                        onIntent = onIntent,
                        context = context,
                        bottomPadding = if (extendSurfaceToNavigationBar) navBarHeight + 16.dp else 16.dp,
                        surfaceEffectEnabled = useLiquidGlass || useHaze,
                        buttonGlassEnabled = useBottomBarButtonGlass,
                        backdrop = backdrop,
                        labelColor = bottomBarTextColor,
                        progressBarBehavior = progressBarBehavior,
                        onBrightnessPreview = onBrightnessPreview,
                    )
                }

                    ReadBookMenuRoute.ReadStyle -> {
                        ReadBookMenuRoutePage(
                            title = stringResource(R.string.read_config),
                            maxHeight = maxHeight,
                            bottomPadding = if (extendSurfaceToNavigationBar) navBarHeight else 0.dp,
                            animateSize = false,
                            onBack = { onIntent(ReadBookIntent.ReadMenuBack) },
                        ) {
                            ReadStyleContent(
                                onOpenPaddingConfig = {
                                    onIntent(ReadBookIntent.OpenReadMenuRoute(ReadBookMenuRoute.PaddingConfig))
                                },
                                onOpenHeaderFooterConfig = {
                                    onIntent(ReadBookIntent.OpenReadMenuRoute(ReadBookMenuRoute.HeaderFooterConfig))
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
                                onPageChanged = onReadStylePageChanged,
                                readMenuCustomIcons = state.menuConfig.readMenuCustomIcons,
                                bottomBarButtons = state.menuConfig.bottomBarButtons,
                                preferences = preferences,
                                onIntent = onIntent,
                                styleConfig = state.styleConfig,
                            )
                        }
                    }

                    ReadBookMenuRoute.PaddingConfig -> {
                        ReadBookMenuRoutePage(
                            title = stringResource(R.string.padding),
                            maxHeight = maxHeight,
                            scrollContent = false,
                            animateSize = false,
                            bottomPadding = if (extendSurfaceToNavigationBar) navBarHeight else 0.dp,
                            onBack = { onIntent(ReadBookIntent.ReadMenuBack) },
                        ) {
                            PaddingConfigContent(
                                config = state.sheetConfig,
                                onIntent = onIntent,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }

                    ReadBookMenuRoute.HeaderFooterConfig -> {
                        ReadBookMenuRoutePage(
                            title = stringResource(R.string.header_footer),
                            maxHeight = maxHeight,
                            scrollContent = false,
                            animateSize = false,
                            bottomPadding = if (extendSurfaceToNavigationBar) navBarHeight else 0.dp,
                            onBack = { onIntent(ReadBookIntent.ReadMenuBack) },
                        ) {
                            HeaderFooterPage(
                                onIntent = onIntent,
                            )
                        }
                    }

                    ReadBookMenuRoute.TextTitle -> {
                        ReadBookMenuRoutePage(
                            title = stringResource(R.string.read_config_text_effects),
                            maxHeight = maxHeight,
                            bottomPadding = if (extendSurfaceToNavigationBar) navBarHeight else 0.dp,
                            animateSize = false,
                            onBack = { onIntent(ReadBookIntent.ReadMenuBack) },
                        ) {
                            ReadStyleTextTitleContent(
                                config = state.sheetConfig,
                                onOpenShadowSet = {
                                    onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.ShadowSet))
                                },
                                onOpenUnderlineConfig = {
                                    onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.UnderlineConfig))
                                },
                                onOpenHighlightRule = {
                                    onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.HighlightRuleConfig))
                                },
                                onOpenFontSelect = {
                                    onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.FontSelect))
                                },
                                onOpenTitleFontSelect = {
                                    onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.TitleFontSelect))
                                },
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
                                state = state,
                                onIntent = onIntent,
                                onDismissRequest = { onIntent(ReadBookIntent.HideMenu) },
                                onOpenChapterList = {
                                    onIntent(ReadBookIntent.HideMenu)
                                    onIntent(ReadBookIntent.OpenChapterList)
                                },
                                onGoToBackground = {
                                    onIntent(ReadBookIntent.CloseReadBook(keepReadAloud = true))
                                },
                                onOpenMainMenu = {
                                    onIntent(ReadBookIntent.ReadMenuBack)
                                },
                                onShowReadAloudConfig = {
                                    onIntent(ReadBookIntent.ShowReadAloudConfig)
                                },
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }

                ReadBookMenuRoute.AutoRead -> {
                    ReadBookMenuRoutePage(
                        title = stringResource(R.string.auto_page_speed),
                        maxHeight = maxHeight,
                        scrollContent = true,
                        bottomPadding = if (extendSurfaceToNavigationBar) navBarHeight else 0.dp,
                        onBack = { onIntent(ReadBookIntent.ReadMenuBack) },
                    ) {
                        AutoReadContent(
                            onDismissRequest = { onIntent(ReadBookIntent.HideMenu) },
                            onIntent = onIntent,
                            onOpenChapterList = {
                                onIntent(ReadBookIntent.HideMenu)
                                onIntent(ReadBookIntent.OpenChapterList)
                            },
                            onStopAutoPage = { onIntent(ReadBookIntent.StopAutoPage) },
                            onShowPageAnimConfig = {
                                onIntent(ReadBookIntent.ShowPageAnimConfig)
                            },
                            modifier = Modifier.padding(horizontal = 16.dp),
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
    animateSize: Boolean = true,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .let { if (animateSize) it.animateContentSize() else it }
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
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
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
    hazeState: HazeState?,
    titleBarMode: String,
) {
    var expanded by remember { mutableStateOf(false) }

    val topBarBorderWidth = state.menuConfig.readMenuBorderWidth
    val topBarBorderColor = (if (ReadStyleResolver.isNightTheme()) {
        state.menuConfig.readMenuBorderColorNight
    } else {
        state.menuConfig.readMenuBorderColor
    }).takeIf { it != 0 }
        ?: LegadoTheme.colorScheme.outlineVariant.hashCode()
    val topBarAlpha = state.menuConfig.readMenuBlurAlpha.coerceIn(0, 100) / 100f
    val useTopBarBlur = readMenuTopBarHazeEnabled(hazeState, state.menuConfig)
    val topBarVisualState = ReaderMenuVisualState(
        effect = if (useTopBarBlur) ReaderMenuEffect.Haze else ReaderMenuEffect.None,
        tintStyle = state.menuConfig.readMenuTopBarBlurStyle.toReaderMenuTintStyle(),
        styleEnabled = true,
        tintAllowed = true,
        tintFill = true,
    )
    val topBarTintColor = readMenuTintColor(state.menuConfig)
        .takeIf { topBarVisualState.useTint }
        ?: colors.background
    val titleTextColor = Color(
        if (ReadStyleResolver.isNightTheme()) {
            state.menuConfig.readMenuTextColorNight
        } else {
            state.menuConfig.readMenuTextColor
        }
    ).takeUnless { it == Color.Unspecified || it.alpha == 0f }
        ?: LegadoTheme.colorScheme.onSurface
    val labelStyle = LegadoTheme.typography.labelSmallEmphasized.copy(
        shadow = androidx.compose.ui.graphics.Shadow(
            color = Color.Black.copy(alpha = 0.12f),
            offset = Offset.Zero,
            blurRadius = 12f,
        )
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (useTopBarBlur && hazeState != null) {
                    Modifier.readerMenuHazeEffect(
                        state = hazeState,
                        visualState = topBarVisualState,
                        placement = ReaderMenuPlacement.Top,
                        baseColor = colors.background,
                        tintColor = readMenuTintColor(state.menuConfig),
                        blurRadius = state.menuConfig.readMenuBlurRadius,
                        surfaceAlpha = state.menuConfig.readMenuBlurAlpha,
                    )
                } else {
                    Modifier.background(
                        if (topBarVisualState.isGradient) {
                            readerMenuSurfaceBrush(
                                style = ReaderMenuTintStyle.Gradient,
                                placement = ReaderMenuPlacement.Top,
                                color = topBarTintColor,
                                alpha = topBarAlpha,
                            )
                        } else {
                            readerMenuSurfaceBrush(
                                style = ReaderMenuTintStyle.Fill,
                                placement = ReaderMenuPlacement.Top,
                                color = topBarTintColor,
                                alpha = topBarAlpha,
                            )
                        }
                    )
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
        val useTitleCapsule = readMenuTopBarTitleCapsuleEnabled(backdrop, state.menuConfig)
        val capsuleIconColor = LegadoTheme.colorScheme.onSurfaceVariant

        // Title row: left group (back + capsule/title) + right group (actions)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Left group: back + capsule/title — fills remaining space
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MenuTitleGlassButton(
                    onClick = { onIntent(ReadBookIntent.CloseReadBook()) },
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    state = state,
                    colors = colors,
                    backdrop = backdrop,
                )

                if (useTitleCapsule && titleBarMode != "1" && titleBarMode != "3") {
                    TitleCapsuleGlassLayout(
                        state = state,
                        colors = colors,
                        onIntent = onIntent,
                        backdrop = backdrop,
                        titleTextColor = capsuleIconColor,
                    )
                } else if (titleBarMode != "1" && titleBarMode != "3") {
                    AppText(
                        text = state.bookName,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onIntent(ReadBookIntent.OpenBookInfo) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        style = LegadoTheme.typography.titleMedium.copy(
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = Color.Black.copy(alpha = 0.12f),
                                offset = Offset.Zero,
                                blurRadius = 12f
                            )
                        ),
                        color = titleTextColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Right group: actions
            if (readMenuTopBarButtonLiquidGlassEnabled(backdrop, state.menuConfig)) {
                MenuTitleBarMergedGlassButton(
                    state = state,
                    colors = colors,
                    onIntent = onIntent,
                    backdrop = backdrop,
                )
            } else {
                val compact = state.menuConfig.titleBarCompact
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!state.isLocalBook) {
                        if (!compact && state.bookSource?.customButton == true) {
                            SourceCustomActionButton(
                                state = state,
                                colors = colors,
                                onIntent = onIntent,
                                backdrop = backdrop,
                            )
                        }
                        if (!compact) {
                            SourceActionButton(
                                state = state,
                                colors = colors,
                                onIntent = onIntent,
                                backdrop = backdrop,
                            )
                            RefreshActionButton(
                                state = state,
                                colors = colors,
                                onIntent = onIntent,
                                backdrop = backdrop,
                            )
                            DownloadActionButton(
                                state = state,
                                colors = colors,
                                onIntent = onIntent,
                                backdrop = backdrop,
                            )
                        }
                    } else if (state.isLocalBook && !compact) {
                        if (state.isLocalTxt) {
                            TxtTocRuleActionButton(
                                state = state,
                                colors = colors,
                                onIntent = onIntent,
                                backdrop = backdrop,
                            )
                        }
                        CharsetActionButton(
                            state = state,
                            colors = colors,
                            onIntent = onIntent,
                            backdrop = backdrop,
                        )
                    }

                    Box {
                        MenuTitleGlassButton(
                            onClick = { expanded = true },
                            icon = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.more_actions),
                            state = state,
                            colors = colors,
                            backdrop = backdrop,
                        )
                        OverflowDropdownMenu(
                            state = state,
                            onIntent = onIntent,
                            expanded = expanded,
                            onDismiss = { expanded = false },
                        )
                    }
                }
            }
        }

        // Book name on its own line (mode "1") — hidden when capsule is active
        if (titleBarMode == "1" && !useTitleCapsule) {
            AppText(
                text = state.bookName,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onIntent(ReadBookIntent.OpenBookInfo) }
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                style = LegadoTheme.typography.titleMedium.copy(
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = Color.Black.copy(alpha = 0.12f),
                        offset = Offset.Zero,
                        blurRadius = 12f
                    )
                ),
                color = titleTextColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Chapter name + source action (modes "0" and "1") — hidden when capsule is active
        if ((titleBarMode == "0" || titleBarMode == "1") && !useTitleCapsule) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppText(
                    text = state.chapterName,
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (!state.isLocalBook) {
                                Modifier.combinedClickable(
                                    onClick = { onIntent(ReadBookIntent.OpenChapterUrl) },
                                    onLongClick = {
                                        onIntent(ReadBookIntent.ToggleReadUrlInBrowser)
                                    },
                                )
                            } else {
                                Modifier
                            }
                        ),
                    style = labelStyle,
                    color = titleTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (!state.isLocalBook && state.bookSource != null) {
                    var sourceMenuExpanded by remember { mutableStateOf(false) }
                    Box {
                        AppText(
                            text = state.bookSource.bookSourceName,
                            modifier = Modifier
                                .clickable { sourceMenuExpanded = true }
                                .padding(start = 8.dp),
                            style = labelStyle,
                            color = titleTextColor,
                            maxLines = 1,
                        )
                        RoundDropdownMenu(
                            expanded = sourceMenuExpanded,
                            onDismissRequest = { sourceMenuExpanded = false },
                        ) {
                            if (!state.bookSource.loginUrl.isNullOrBlank()) {
                                RoundDropdownMenuItem(
                                    leadingIcon = { MenuItemIcon(Icons.AutoMirrored.Filled.Login) },
                                    text = stringResource(R.string.login),
                                    onClick = {
                                        sourceMenuExpanded = false
                                        onIntent(ReadBookIntent.ShowLogin)
                                    },
                                )
                            }
                            if (!state.bookSource.getContentRule().payAction.isNullOrBlank()) {
                                RoundDropdownMenuItem(
                                    leadingIcon = { MenuItemIcon(Icons.Default.Payment) },
                                    text = stringResource(R.string.chapter_pay),
                                    onClick = {
                                        sourceMenuExpanded = false
                                        onIntent(ReadBookIntent.PayAction)
                                    },
                                )
                            }
                            RoundDropdownMenuItem(
                                leadingIcon = { MenuItemIcon(Icons.Default.Edit) },
                                text = stringResource(R.string.edit_source),
                                onClick = {
                                    sourceMenuExpanded = false
                                    onIntent(ReadBookIntent.OpenSourceEdit)
                                },
                            )
                            RoundDropdownMenuItem(
                                leadingIcon = { MenuItemIcon(Icons.Default.Block) },
                                text = stringResource(R.string.disable_source),
                                onClick = {
                                    sourceMenuExpanded = false
                                    onIntent(ReadBookIntent.DisableSource)
                                },
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MenuTitleGlassButton(
    onClick: () -> Unit,
    icon: ImageVector,
    state: ReadBookUiState,
    colors: ReadMenuColors,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    contentDescription: String? = null,
) {
    ReadMenuGlassIconButton(
        onClick = onClick,
        icon = icon,
        colors = colors,
        backdrop = backdrop,
        menuConfig = state.menuConfig,
        glassEnabled = readMenuTopBarButtonLiquidGlassEnabled(backdrop, state.menuConfig),
        iconStyle = state.menuConfig.titleBarIconStyle,
        modifier = modifier,
        onLongClick = onLongClick,
        contentDescription = contentDescription,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReadMenuGlassIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    colors: ReadMenuColors,
    backdrop: Backdrop?,
    menuConfig: ReadMenuConfig,
    glassEnabled: Boolean,
    iconStyle: Int = menuConfig.readMenuIconStyle,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
    contentDescription: String? = null,
) {
    ReadMenuGlassButtonSurface(
        onClick = onClick,
        colors = colors,
        backdrop = backdrop,
        menuConfig = menuConfig,
        glassEnabled = glassEnabled,
        iconStyle = iconStyle,
        modifier = modifier,
        onLongClick = onLongClick,
        selected = selected,
        contentDescription = contentDescription,
    ) { tint ->
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReadMenuGlassButtonSurface(
    onClick: () -> Unit,
    colors: ReadMenuColors,
    backdrop: Backdrop?,
    menuConfig: ReadMenuConfig,
    glassEnabled: Boolean,
    iconStyle: Int = menuConfig.readMenuIconStyle,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
    contentDescription: String? = null,
    content: @Composable (Color) -> Unit,
) {
    val shape = CircleShape
    val tint = when {
        selected -> LegadoTheme.colorScheme.primary
        else -> LegadoTheme.colorScheme.onSurfaceVariant
    }
    val containerColor = when {
        selected -> LegadoTheme.colorScheme.secondaryContainer
        iconStyle == 1 -> LegadoTheme.colorScheme.surfaceContainerLow
        else -> Color.Transparent
    }
    val border = when {
        selected -> BorderStroke(1.5.dp, LegadoTheme.colorScheme.secondary)
        !glassEnabled && iconStyle == 2 -> BorderStroke(1.dp, tint.copy(alpha = 0.45f))
        else -> null
    }
    val outerSize = if (glassEnabled) 48.dp else 40.dp
    val innerSize = 40.dp

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(outerSize),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(innerSize)
                .then(
                    if (glassEnabled) {
                        Modifier.readMenuLiquidGlass(
                            backdrop = backdrop,
                            colors = colors,
                            shape = shape,
                            useTopBarStyle = true,
                            useLens = true,
                            blurRadius = 32.dp,
                            interactive = true,
                            menuConfig = menuConfig,
                        )
                    } else {
                        Modifier
                            .clip(shape)
                            .background(containerColor, shape)
                    }
                )
                .then(if (border != null) Modifier.border(border, shape) else Modifier)
                .combinedClickable(
                    indication = if (glassEnabled) null else LocalIndication.current,
                    interactionSource = remember { MutableInteractionSource() },
                    role = Role.Button,
                    onLongClick = onLongClick,
                    onClick = onClick,
                )
                .then(
                    if (contentDescription != null) {
                        Modifier.semantics {
                            this.contentDescription = contentDescription
                            role = Role.Button
                        }
                    } else {
                        Modifier
                    }
                ),
        ) {
            content(tint)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.TitleCapsuleGlassLayout(
    state: ReadBookUiState,
    colors: ReadMenuColors,
    onIntent: (ReadBookIntent) -> Unit,
    backdrop: Backdrop?,
    titleTextColor: Color,
) {
    val pillShape = RoundedCornerShape(50)
    val glassEnabled = readerMenuLiquidGlassAvailable(backdrop)
            && state.menuConfig.readMenuTopBarLiquidGlassButtons
    val iconStyle = state.menuConfig.titleBarIconStyle

    Row(
        modifier = Modifier
            .weight(1f)
            .padding(start = 12.dp)
            .height(40.dp)
            .then(
                if (glassEnabled) {
                    Modifier.readMenuLiquidGlass(
                        backdrop = backdrop,
                        colors = colors,
                        shape = pillShape,
                        useTopBarStyle = true,
                        useLens = false,
                        blurRadius = 32.dp,
                        menuConfig = state.menuConfig,
                    )
                } else {
                    val containerColor = when (iconStyle) {
                        1 -> LegadoTheme.colorScheme.surfaceContainerLow
                        else -> Color.Transparent
                    }
                    val border = when (iconStyle) {
                        2 -> BorderStroke(
                            1.dp,
                            LegadoTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        )

                        else -> null
                    }
                    Modifier
                        .clip(pillShape)
                        .background(containerColor, pillShape)
                        .then(if (border != null) Modifier.border(border, pillShape) else Modifier)
                }
            )
            .then(
                if (!state.isLocalBook) {
                    Modifier.combinedClickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { onIntent(ReadBookIntent.OpenBookInfo) },
                        onLongClick = { onIntent(ReadBookIntent.OpenChapterUrl) },
                    )
                } else {
                    Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { onIntent(ReadBookIntent.OpenBookInfo) }
                }
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            AppText(
                text = state.bookName,
                style = LegadoTheme.typography.labelMediumEmphasized,
                color = titleTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.chapterName.isNotBlank()) {
                AppText(
                    text = state.chapterName,
                    style = LegadoTheme.typography.labelSmall,
                    color = titleTextColor.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MenuTitleBarMergedGlassButton(
    state: ReadBookUiState,
    colors: ReadMenuColors,
    onIntent: (ReadBookIntent) -> Unit,
    backdrop: Backdrop?,
) {
    var sourceExpanded by remember { mutableStateOf(false) }
    var refreshExpanded by remember { mutableStateOf(false) }
    var overflowExpanded by remember { mutableStateOf(false) }

    val pillShape = RoundedCornerShape(50)
    val tint = LegadoTheme.colorScheme.onSurfaceVariant
    val compact = state.menuConfig.titleBarCompact

    Box {
        Row(
            modifier = Modifier
                .height(40.dp)
                .readMenuLiquidGlass(
                    backdrop = backdrop,
                    colors = colors,
                    shape = pillShape,
                    useTopBarStyle = true,
                    useLens = true,
                    blurRadius = 32.dp,
                    interactive = true,
                    menuConfig = state.menuConfig,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            // SwapHoriz - change source
            if (!state.isLocalBook && !compact) {
                if (state.bookSource?.customButton == true) {
                    val customButtonDescription = stringResource(R.string.custom_button)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .combinedClickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                role = Role.Button,
                                onClick = { onIntent(ReadBookIntent.SourceCustomButton(false)) },
                                onLongClick = { onIntent(ReadBookIntent.SourceCustomButton(true)) },
                            )
                            .semantics {
                                contentDescription = customButtonDescription
                                role = Role.Button
                            },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Extension,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(20.dp)
                            .background(tint.copy(alpha = 0.15f))
                            .clearAndSetSemantics { }
                    )
                }

                val changeSourceDescription = stringResource(R.string.change_origin)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .combinedClickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            role = Role.Button,
                            onClick = { onIntent(ReadBookIntent.MenuChangeSource) },
                            onLongClick = { sourceExpanded = true },
                        )
                        .semantics {
                            contentDescription = changeSourceDescription
                            role = Role.Button
                        },
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(20.dp)
                        .background(tint.copy(alpha = 0.15f))
                        .clearAndSetSemantics { }
                )

                // Refresh
                val refreshDescription = stringResource(R.string.menu_refresh_after)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .combinedClickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            role = Role.Button,
                            onClick = { onIntent(ReadBookIntent.MenuRefreshAfter) },
                            onLongClick = { refreshExpanded = true },
                        )
                        .semantics {
                            contentDescription = refreshDescription
                            role = Role.Button
                        },
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(20.dp)
                        .background(tint.copy(alpha = 0.15f))
                        .clearAndSetSemantics { }
                )

                // Download
                val downloadDescription = stringResource(R.string.offline_cache)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            role = Role.Button,
                            onClick = { onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.Download)) },
                        )
                        .semantics {
                            contentDescription = downloadDescription
                            role = Role.Button
                        },
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(20.dp)
                        .background(tint.copy(alpha = 0.15f))
                        .clearAndSetSemantics { }
                )
            } else if (state.isLocalBook && !compact) {
                // TXT directory rule
                if (state.isLocalTxt) {
                    val tocRuleDescription = stringResource(R.string.txt_toc_rule)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                role = Role.Button,
                                onClick = { onIntent(ReadBookIntent.MenuTocRegex) },
                            )
                            .semantics {
                                contentDescription = tocRuleDescription
                                role = Role.Button
                            },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Toc,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(20.dp)
                            .background(tint.copy(alpha = 0.15f))
                            .clearAndSetSemantics { }
                    )
                }

                // Text encoding
                val charsetDescription = stringResource(R.string.set_charset)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            role = Role.Button,
                            onClick = { onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.Charset)) },
                        )
                        .semantics {
                            contentDescription = charsetDescription
                            role = Role.Button
                        },
                ) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(20.dp)
                        .background(tint.copy(alpha = 0.15f))
                        .clearAndSetSemantics { }
                )
            }

            // MoreVert - overflow menu
            val moreActionsDescription = stringResource(R.string.more_actions)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        role = Role.Button,
                        onClick = { overflowExpanded = true },
                    )
                    .semantics {
                        contentDescription = moreActionsDescription
                        role = Role.Button
                    },
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // Dropdown menus
        if (!state.isLocalBook) {
            RoundDropdownMenu(
                expanded = sourceExpanded,
                onDismissRequest = { sourceExpanded = false },
            ) { dismiss ->
                RoundDropdownMenuItem(
                    text = stringResource(R.string.change_origin),
                    onClick = { dismiss(); onIntent(ReadBookIntent.MenuBookChangeSource) },
                )
                RoundDropdownMenuItem(
                    text = stringResource(R.string.chapter_change_source),
                    onClick = { dismiss(); onIntent(ReadBookIntent.MenuChapterChangeSource) },
                )
            }

            RoundDropdownMenu(
                expanded = refreshExpanded,
                onDismissRequest = { refreshExpanded = false },
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

        OverflowDropdownMenu(
            state = state,
            onIntent = onIntent,
            expanded = overflowExpanded,
            onDismiss = { overflowExpanded = false },
        )
    }
}

@Composable
private fun SourceCustomActionButton(
    state: ReadBookUiState,
    colors: ReadMenuColors,
    onIntent: (ReadBookIntent) -> Unit,
    backdrop: Backdrop?,
) {
    MenuTitleGlassButton(
        onClick = { onIntent(ReadBookIntent.SourceCustomButton(false)) },
        onLongClick = { onIntent(ReadBookIntent.SourceCustomButton(true)) },
        icon = Icons.Default.Extension,
        contentDescription = stringResource(R.string.custom_button),
        state = state,
        colors = colors,
        backdrop = backdrop,
    )
}

@Composable
private fun SourceActionButton(
    state: ReadBookUiState,
    colors: ReadMenuColors,
    onIntent: (ReadBookIntent) -> Unit,
    backdrop: Backdrop?,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        MenuTitleGlassButton(
            onClick = { onIntent(ReadBookIntent.MenuChangeSource) },
            onLongClick = { expanded = true },
            icon = Icons.Default.SwapHoriz,
            contentDescription = stringResource(R.string.change_origin),
            state = state,
            colors = colors,
            backdrop = backdrop,
        )

        RoundDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) { dismiss ->
            RoundDropdownMenuItem(
                text = stringResource(R.string.change_origin),
                onClick = { dismiss(); onIntent(ReadBookIntent.MenuBookChangeSource) },
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
    state: ReadBookUiState,
    colors: ReadMenuColors,
    onIntent: (ReadBookIntent) -> Unit,
    backdrop: Backdrop?,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        MenuTitleGlassButton(
            onClick = { onIntent(ReadBookIntent.MenuRefreshAfter) },
            onLongClick = { expanded = true },
            icon = Icons.Default.Refresh,
            contentDescription = stringResource(R.string.menu_refresh_after),
            state = state,
            colors = colors,
            backdrop = backdrop,
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
private fun DownloadActionButton(
    state: ReadBookUiState,
    colors: ReadMenuColors,
    onIntent: (ReadBookIntent) -> Unit,
    backdrop: Backdrop?,
) {
    MenuTitleGlassButton(
        onClick = { onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.Download)) },
        icon = Icons.Default.CloudDownload,
        contentDescription = stringResource(R.string.offline_cache),
        state = state,
        colors = colors,
        backdrop = backdrop,
    )
}

@Composable
private fun TxtTocRuleActionButton(
    state: ReadBookUiState,
    colors: ReadMenuColors,
    onIntent: (ReadBookIntent) -> Unit,
    backdrop: Backdrop?,
) {
    MenuTitleGlassButton(
        onClick = { onIntent(ReadBookIntent.MenuTocRegex) },
        icon = Icons.Default.Toc,
        contentDescription = stringResource(R.string.txt_toc_rule),
        state = state,
        colors = colors,
        backdrop = backdrop,
    )
}

@Composable
private fun CharsetActionButton(
    state: ReadBookUiState,
    colors: ReadMenuColors,
    onIntent: (ReadBookIntent) -> Unit,
    backdrop: Backdrop?,
) {
    MenuTitleGlassButton(
        onClick = { onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.Charset)) },
        icon = Icons.Default.Translate,
        contentDescription = stringResource(R.string.set_charset),
        state = state,
        colors = colors,
        backdrop = backdrop,
    )
}

@Composable
private fun FloatingIconRow(
    state: ReadBookUiState,
    preferences: ReadPreferences,
    colors: ReadMenuColors,
    alignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    onIntent: (ReadBookIntent) -> Unit,
    backdrop: Backdrop?,
) {
    val context = LocalContext.current
    val floatingIcons = remember(
        state.menuConfig.titleBarButtons,
        state.isReadAloudRunning,
        state.isAutoPage,
        state.translationMode,
        state.useReplaceRule,
        preferences.eyeProtectionEnabled,
    ) {
        loadFloatingIcons(
            context = context,
            state = state,
            preferences = preferences,
            onIntent = onIntent,
        )
    }

    if (floatingIcons.isEmpty()) return

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
        floatingIcons.forEach { iconDef ->
            val customPath = remember(state.menuConfig.titleBarCustomIcons, iconDef.id) {
                state.menuConfig.titleBarCustomIcons[iconDef.id]
            }
            val isCustom = !customPath.isNullOrBlank()
            val glassEnabled = !isCustom && state.menuConfig.readMenuFloatingIconLiquidGlass &&
                    readerMenuLiquidGlassAvailable(backdrop)
            ReadMenuGlassButtonSurface(
                onClick = iconDef.onClick,
                colors = colors,
                backdrop = backdrop,
                menuConfig = state.menuConfig,
                glassEnabled = glassEnabled,
                iconStyle = 1,
                selected = iconDef.isActive,
                modifier = Modifier.padding(horizontal = 4.dp),
                onLongClick = iconDef.onLongClick,
                contentDescription = iconDef.label,
            ) {
                if (isCustom) {
                    AsyncImage(
                        model = customPath,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape),
                    )
                } else {
                    Icon(
                        imageVector = iconDef.icon,
                        contentDescription = null,
                        tint = if (iconDef.isActive) LegadoTheme.colorScheme.primary else colors.content,
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
    val showIcon = state.menuConfig.showMenuIcon
    val menuIcon: (ImageVector) -> (@Composable () -> Unit)? = { imageVector ->
        if (showIcon) {
            { MenuItemIcon(imageVector = imageVector) }
        } else {
            null
        }
    }

    RoundDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) { dismiss ->
        var imageStyleExpanded by remember { mutableStateOf(false) }

        // Compact mode: moved from top bar buttons
        if (state.menuConfig.titleBarCompact) {
            if (!state.isLocalBook) {
                RoundDropdownMenuItem(
                    text = stringResource(R.string.change_origin),
                    leadingIcon = menuIcon(Icons.Default.SwapHoriz),
                    onClick = { dismiss(); onIntent(ReadBookIntent.MenuChangeSource) },
                )
                RoundDropdownMenuItem(
                    text = stringResource(R.string.menu_refresh_after),
                    leadingIcon = menuIcon(Icons.Default.Refresh),
                    onClick = { dismiss(); onIntent(ReadBookIntent.MenuRefreshAfter) },
                )
                RoundDropdownMenuItem(
                    text = stringResource(R.string.offline_cache),
                    leadingIcon = menuIcon(Icons.Default.CloudDownload),
                    onClick = { dismiss(); onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.Download)) },
                )
            } else {
                if (state.isLocalTxt) {
                    RoundDropdownMenuItem(
                        text = stringResource(R.string.txt_toc_rule),
                        leadingIcon = menuIcon(Icons.Default.Toc),
                        onClick = { dismiss(); onIntent(ReadBookIntent.MenuTocRegex) },
                    )
                }
                RoundDropdownMenuItem(
                    text = stringResource(R.string.set_charset),
                    leadingIcon = menuIcon(Icons.Default.Translate),
                    onClick = { dismiss(); onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.Charset)) },
                )
            }
            PillDivider()
        }

        // Source actions
        if (!state.isLocalBook) {
            RoundDropdownMenuItem(
                text = stringResource(R.string.menu_refresh_all),
                leadingIcon = menuIcon(Icons.Default.Replay),
                onClick = { dismiss(); onIntent(ReadBookIntent.MenuRefreshAll) },
            )
        }

        PillDivider()

        // Content operations
        RoundDropdownMenuItem(
            text = stringResource(R.string.bookmark_add),
            leadingIcon = menuIcon(Icons.Default.Bookmark),
            onClick = { dismiss(); onIntent(ReadBookIntent.AddBookmark) },
        )
        RoundDropdownMenuItem(
            text = stringResource(R.string.edit_content),
            leadingIcon = menuIcon(Icons.Default.Edit),
            onClick = {
                dismiss()
                onIntent(ReadBookIntent.OpenContentEdit)
            },
        )
        RoundDropdownMenuItem(
            text = stringResource(R.string.update_toc),
            leadingIcon = menuIcon(Icons.Default.Replay),
            onClick = { dismiss(); onIntent(ReadBookIntent.MenuUpdateToc) },
        )
        RoundDropdownMenuItem(
            text = stringResource(R.string.simulated_reading),
            leadingIcon = menuIcon(Icons.Default.AutoStories),
            onClick = {
                dismiss()
                onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.SimulatedReading))
            },
        )
        RoundDropdownMenuItem(
            text = stringResource(R.string.reverse_content),
            leadingIcon = menuIcon(Icons.Default.SwapVert),
            onClick = { dismiss(); onIntent(ReadBookIntent.MenuReverseContent) },
        )

        PillDivider()

        // Checkable items
        RoundDropdownMenuItem(
            text = stringResource(R.string.replace_rule_title),
            leadingIcon = menuIcon(Icons.Default.FindReplace),
            isSelected = state.useReplaceRule,
            onClick = { onIntent(ReadBookIntent.MenuEnableReplace) },
        )
        RoundDropdownMenuItem(
            text = stringResource(R.string.replace_rule_title_setting),
            leadingIcon = menuIcon(Icons.Default.Settings),
            onClick = { dismiss(); onIntent(ReadBookIntent.MenuSettingReplace) },
        )
        RoundDropdownMenuItem(
            text = stringResource(R.string.effective_replaces),
            leadingIcon = menuIcon(Icons.Default.Rule),
            onClick = {
                dismiss()
                onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.EffectiveReplaces))
            },
        )
        RoundDropdownMenuItem(
            text = stringResource(R.string.content_processes),
            leadingIcon = menuIcon(Icons.Default.Edit),
            onClick = {
                dismiss()
                onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.ContentProcesses))
            },
        )
        RoundDropdownMenuItem(
            text = stringResource(R.string.same_title_removed),
            leadingIcon = menuIcon(Icons.Default.CleanHands),
            isSelected = state.sameTitleRemoved,
            onClick = { onIntent(ReadBookIntent.MenuSameTitleRemoved) },
        )
        RoundDropdownMenuItem(
            text = stringResource(R.string.re_segment),
            leadingIcon = menuIcon(Icons.Default.Toc),
            isSelected = state.reSegment,
            onClick = { onIntent(ReadBookIntent.MenuReSegment) },
        )

        // EPUB
        if (state.isEpub) {
            RoundDropdownMenuItem(
                text = stringResource(R.string.del_ruby_tag),
                leadingIcon = menuIcon(Icons.Default.CleanHands),
                isSelected = state.delRubyTag,
                onClick = { onIntent(ReadBookIntent.MenuDelRubyTag) },
            )
            RoundDropdownMenuItem(
                text = stringResource(R.string.del_h_tag),
                leadingIcon = menuIcon(Icons.Default.CleanHands),
                isSelected = state.delHTag,
                onClick = { onIntent(ReadBookIntent.MenuDelHTag) },
            )
        }

        PillDivider()

        // Config
        Box {
            RoundDropdownMenuItem(
                text = stringResource(R.string.image_style),
                leadingIcon = menuIcon(Icons.Default.Image),
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
            leadingIcon = menuIcon(Icons.Default.Animation),
            onClick = {
                dismiss()
                onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.PageAnim))
            },
        )
        RoundDropdownMenuItem(
            text = stringResource(R.string.config_btn),
            leadingIcon = menuIcon(Icons.Default.Extension),
            onClick = {
                dismiss()
                onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.ToolButtonConfig))
            },
        )

        // Progress sync
        if (state.isReadingProgressSyncConfigured) {
            RoundDropdownMenuItem(
                text = stringResource(R.string.get_book_progress),
                leadingIcon = menuIcon(Icons.Default.Sync),
                onClick = { dismiss(); onIntent(ReadBookIntent.MenuGetProgress) },
            )
            RoundDropdownMenuItem(
                text = stringResource(R.string.cover_book_progress),
                leadingIcon = menuIcon(Icons.Default.Sync),
                onClick = { dismiss(); onIntent(ReadBookIntent.MenuCoverProgress) },
            )
        }

        PillDivider()

        RoundDropdownMenuItem(
            text = stringResource(R.string.log),
            leadingIcon = menuIcon(Icons.Default.BugReport),
            onClick = {
                dismiss()
                onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.AppLog))
            },
        )
    }
}

@Composable
private fun SearchBottomMenuContent(
    state: ReadBookUiState,
    colors: ReadMenuColors,
    onIntent: (ReadBookIntent) -> Unit,
    bottomPadding: Dp = 0.dp,
    surfaceEffectEnabled: Boolean = false,
) {
    val totalResults = state.searchResultList.size
    val currentIndex = state.searchResultIndex.coerceIn(0, (totalResults - 1).coerceAtLeast(0))
    val percent = if (totalResults > 0) {
        ((currentIndex + 1) * 100 / totalResults)
    } else {
        0
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
            )
            .padding(top = 12.dp, bottom = bottomPadding)
            .animateContentSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchInfoPill(
                modifier = Modifier.fillMaxWidth(),
            ) {
                AnimatedText(
                    text = if (totalResults > 0) "${currentIndex + 1} / $totalResults" else "0 / 0",
                    style = LegadoTheme.typography.labelSmallEmphasized,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(Modifier.width(8.dp))
                TextCard(
                    text = "$percent%"
                )
                Spacer(Modifier.width(16.dp))
                VerticalDivider(
                    color = LegadoTheme.colorScheme.outlineVariant,
                    modifier = Modifier
                        .height(8.dp)
                        .width(1.dp)
                )
                Spacer(Modifier.width(16.dp))
                AnimatedText(
                    text = state.chapterName.ifBlank { "-" },
                    style = LegadoTheme.typography.labelSmallEmphasized,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchMenuActionButton(
                modifier = Modifier.weight(2f),
                icon = Icons.Default.Search,
                text = stringResource(R.string.all_results),
                onClick = { onIntent(ReadBookIntent.OpenSearch(null)) },
            )
            SearchMenuActionButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Menu,
                text = stringResource(R.string.main_menu),
                onClick = {
                    onIntent(ReadBookIntent.HideSearchMenu)
                    onIntent(ReadBookIntent.ShowMenu)
                },
            )
            SearchMenuActionButton(
                modifier = Modifier.weight(0.55f),
                icon = Icons.Default.Close,
                text = null,
                iconContentDescription = stringResource(R.string.exit),
                onClick = { onIntent(ReadBookIntent.ExitSearch) },
            )
        }
    }
}

@Composable
private fun SearchInfoPill(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(LegadoTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        content = content,
    )
}

@Composable
private fun SearchMenuActionButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    text: String?,
    iconContentDescription: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(LegadoTheme.colorScheme.surfaceContainerLow)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = iconContentDescription,
            tint = LegadoTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        if (text != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                style = LegadoTheme.typography.labelMediumEmphasized,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MenuBottomBar(
    state: ReadBookUiState,
    eyeProtectionEnabled: Boolean,
    colors: ReadMenuColors,
    onIntent: (ReadBookIntent) -> Unit,
    context: Context,
    bottomPadding: Dp = 0.dp,
    surfaceEffectEnabled: Boolean = false,
    buttonGlassEnabled: Boolean = false,
    backdrop: Backdrop? = null,
    labelColor: Color = LegadoTheme.colorScheme.onSurface,
    progressBarBehavior: String,
    onBrightnessPreview: (Int) -> Unit,
) {
    val seekMax = state.seekMax.coerceAtLeast(0)
    val sliderMax = seekMax.toFloat().coerceAtLeast(1f)
    var sliderValue by remember { mutableFloatStateOf(state.seekProgress.coerceIn(0, seekMax).toFloat()) }
    var sliderDragging by remember { mutableStateOf(false) }
    var previewPageIndex by remember { mutableIntStateOf(state.seekProgress.coerceIn(0, seekMax)) }
    val toolButtonsBottomPadding = if (buttonGlassEnabled) 6.dp else 0.dp
    val contentBottomPadding = if (bottomPadding > toolButtonsBottomPadding) {
        bottomPadding - toolButtonsBottomPadding
    } else {
        0.dp
    }
    val progressCurrent = sliderValue.roundToInt().coerceIn(0, seekMax) + 1
    val progressTotal = seekMax + 1
    val progressValueDescription = stringResource(
        if (progressBarBehavior == "page") {
            R.string.a11y_read_progress_page
        } else {
            R.string.a11y_read_progress_chapter
        },
        progressCurrent,
        progressTotal,
    )

    fun commitSliderValue(value: Float) {
        val target = value.roundToInt().coerceIn(0, seekMax)
        sliderDragging = false
        sliderValue = target.toFloat()
        previewPageIndex = target
        if (progressBarBehavior == "page") {
            onIntent(ReadBookIntent.SkipToPage(target))
        } else {
            onIntent(ReadBookIntent.SeekToChapter(target))
        }
    }

    fun updateSliderValue(value: Float) {
        val coercedValue = value.coerceIn(0f, sliderMax)
        sliderDragging = true
        sliderValue = coercedValue
        if (progressBarBehavior == "page") {
            val target = coercedValue.roundToInt().coerceIn(0, seekMax)
            if (target != previewPageIndex) {
                previewPageIndex = target
                onIntent(ReadBookIntent.SkipToPage(target))
            }
        }
    }

    LaunchedEffect(state.seekProgress, seekMax, progressBarBehavior) {
        val progress = state.seekProgress.coerceIn(0, seekMax)
        previewPageIndex = progress
        if (progressBarBehavior == "page" || !sliderDragging) {
            sliderValue = progress.toFloat()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
            )
            .padding(top = 8.dp, bottom = contentBottomPadding)
            .animateContentSize(),
    ) {
        if (state.menuConfig.showBrightnessView == "1") {
            BrightnessBar(
                brightness = state.menuConfig.readBrightness,
                onBrightnessChange = { value ->
                    onIntent(ReadBookIntent.SetBrightness(value))
                },
                brightnessAuto = state.menuConfig.brightnessAuto,
                onToggleAuto = {
                    onIntent(ReadBookIntent.ToggleBrightnessAuto(!state.menuConfig.brightnessAuto))
                },
                onTogglePosition = {},
                vertical = false,
                colors = colors,
                menuConfig = state.menuConfig,
                backdrop = backdrop,
                buttonGlassEnabled = buttonGlassEnabled,
                glassThumbEnabled = buttonGlassEnabled,
                onBrightnessPreview = onBrightnessPreview,
            )
            Spacer(Modifier.height(4.dp))
        }

        // Seek bar row: prev + slider + next
        AnimatedVisibility(visible = state.menuConfig.readSliderMode != "1") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BottomBarGlassIconButton(
                    onClick = { onIntent(ReadBookIntent.PrevChapter) },
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    colors = colors,
                    backdrop = backdrop,
                    menuConfig = state.menuConfig,
                    glassEnabled = buttonGlassEnabled,
                    contentDescription = stringResource(R.string.previous_chapter),
                )

                ReadMenuSlider(
                    value = sliderValue.coerceIn(0f, sliderMax),
                    onValueChange = ::updateSliderValue,
                    onValueChangeFinished = {
                        commitSliderValue(sliderValue)
                    },
                    onValueCommit = ::commitSliderValue,
                    valueRange = 0f..sliderMax,
                    steps = (seekMax - 1).coerceAtLeast(0),
                    enabled = seekMax > 0,
                    backdrop = backdrop,
                    glassThumbEnabled = buttonGlassEnabled,
                    accessibilityLabel = stringResource(R.string.progress),
                    accessibilityValue = progressValueDescription,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )

                BottomBarGlassIconButton(
                    onClick = { onIntent(ReadBookIntent.NextChapter) },
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    colors = colors,
                    backdrop = backdrop,
                    menuConfig = state.menuConfig,
                    glassEnabled = buttonGlassEnabled,
                    contentDescription = stringResource(R.string.next_chapter),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Tool buttons
        val toolButtons = remember(
            context,
            state.menuConfig.bottomBarButtons,
            state.menuConfig.readMenuCustomIcons,
            state.isReadAloudRunning,
            state.isAutoPage,
            state.translationMode,
            state.useReplaceRule,
            eyeProtectionEnabled,
        ) {
            loadToolButtons(
                context = context,
                state = state,
                eyeProtectionEnabled = eyeProtectionEnabled,
                onIntent = onIntent,
            )
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
                    .padding(horizontal = 16.dp)
                    .padding(bottom = toolButtonsBottomPadding),
            ) {
                pageButtons.chunked(itemsPerRow).forEach { rowButtons ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        rowButtons.forEach { button ->
                            ToolButtonItem(
                                button = button,
                                state = state,
                                colors = colors,
                                backdrop = backdrop,
                                glassEnabled = buttonGlassEnabled,
                                labelColor = labelColor,
                                modifier = Modifier.width(if (buttonGlassEnabled) 48.dp else 40.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BottomBarGlassIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    colors: ReadMenuColors,
    backdrop: Backdrop?,
    menuConfig: ReadMenuConfig,
    glassEnabled: Boolean,
    contentDescription: String? = null,
) {
    ReadMenuGlassIconButton(
        onClick = onClick,
        icon = icon,
        colors = colors,
        backdrop = backdrop,
        menuConfig = menuConfig,
        glassEnabled = glassEnabled,
        contentDescription = contentDescription,
    )
}

@Composable
private fun ReadMenuSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    onValueCommit: ((Float) -> Unit)? = null,
    backdrop: Backdrop?,
    glassThumbEnabled: Boolean,
    accessibilityLabel: String? = null,
    accessibilityValue: String? = null,
) {
    if (glassThumbEnabled && backdrop != null) {
        ReadMenuLiquidSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            visibilityThreshold = 0.001f,
            backdrop = backdrop,
            modifier = modifier,
            enabled = enabled,
            onValueChangeFinished = onValueChangeFinished,
            onValueCommit = onValueCommit,
            accessibilityLabel = accessibilityLabel,
            accessibilityValue = accessibilityValue,
        )
        return
    }

    val commitAction = onValueChangeFinished ?: onValueCommit?.let { commit -> { commit(value) } }

    AppSlider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.padding(horizontal = 5.dp),
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = commitAction,
        accessibilityLabel = accessibilityLabel,
        accessibilityValue = accessibilityValue,
    )
}

@Composable
private fun ReadMenuLiquidSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    visibilityThreshold: Float,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    onValueCommit: ((Float) -> Unit)? = null,
    accessibilityLabel: String? = null,
    accessibilityValue: String? = null,
) {
    val accentColor = LegadoTheme.colorScheme.secondary
    val trackColor = LegadoTheme.colorScheme.surfaceContainerLow
    val thumbColor = Color.White.copy(alpha = 0.9f).compositeOver(LegadoTheme.colorScheme.surfaceContainerLow)
    val enabledAlpha = if (enabled) 1f else 0.38f

    val trackBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .semantics {
                accessibilityLabel?.let { contentDescription = it }
                accessibilityValue?.let { stateDescription = it }
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = value.coerceIn(valueRange),
                    range = valueRange,
                    steps = steps,
                )
                if (!enabled) {
                    disabled()
                }
                setProgress { target ->
                    if (!enabled) {
                        false
                    } else {
                        val nextValue = target.coerceIn(valueRange)
                        onValueChange(nextValue)
                        onValueCommit?.invoke(nextValue) ?: onValueChangeFinished?.invoke()
                        true
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val trackWidth = constraints.maxWidth
        val rangeStart = valueRange.start
        val rangeEnd = valueRange.endInclusive
        val range = rangeEnd - rangeStart
        val animationScope = rememberCoroutineScope()
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val dampedDragAnimation = remember(animationScope, trackWidth, rangeStart, rangeEnd, isLtr) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = value,
                valueRange = valueRange,
                visibilityThreshold = visibilityThreshold,
                initialScale = 1f,
                pressedScale = 1.5f,
                onDragStarted = {},
                onDragStopped = {
                    onValueChange(targetValue)
                    onValueCommit?.invoke(targetValue) ?: onValueChangeFinished?.invoke()
                },
                onDrag = { _, dragAmount ->
                    val delta = range * (dragAmount.x / trackWidth)
                    val nextValue = if (isLtr) {
                        (targetValue + delta).coerceIn(valueRange)
                    } else {
                        (targetValue - delta).coerceIn(valueRange)
                    }
                    updateValue(nextValue)
                    onValueChange(nextValue)
                },
            )
        }

        LaunchedEffect(dampedDragAnimation, value) {
            if (dampedDragAnimation.targetValue != value) {
                dampedDragAnimation.updateValue(value)
            }
        }

        val progress = if (range == 0f) {
            0f
        } else {
            ((dampedDragAnimation.value - rangeStart) / range).coerceIn(0f, 1f)
        }

        Box(Modifier.layerBackdrop(trackBackdrop)) {
            Box(
                Modifier
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { ContinuousCapsule },
                        effects = {},
                        highlight = null,
                        shadow = {
                            Shadow(
                                radius = 8.dp,
                                color = Color.Black.copy(alpha = 0.12f),
                            )
                        },
                        innerShadow = null,
                        onDrawSurface = {
                            drawRect(trackColor.copy(alpha = enabledAlpha))
                        },
                    )
                    .pointerInput(enabled, animationScope, isLtr, trackWidth) {
                        if (!enabled) return@pointerInput
                        detectTapGestures { position ->
                            val delta = range * (position.x / trackWidth)
                            val targetValue =
                                (if (isLtr) rangeStart + delta else rangeEnd - delta)
                                    .coerceIn(valueRange)
                            dampedDragAnimation.animateToValue(targetValue)
                            onValueChange(targetValue)
                            onValueCommit?.invoke(targetValue) ?: onValueChangeFinished?.invoke()
                        }
                    }
                    .height(6f.dp)
                    .fillMaxWidth(),
            )
            Box(
                Modifier
                    .clip(ContinuousCapsule)
                    .background(accentColor.copy(alpha = enabledAlpha))
                    .height(6f.dp)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val width = (constraints.maxWidth * progress).roundToInt()
                        layout(width, placeable.height) {
                            placeable.place(0, 0)
                        }
                    },
            )
        }

        Box(
            Modifier
                .graphicsLayer {
                    alpha = enabledAlpha
                    translationX =
                        (-size.width / 2f + trackWidth * progress)
                            .coerceIn(-size.width / 4f, trackWidth - size.width * 3f / 4f) *
                                if (isLtr) 1f else -1f
                }
                .then(if (enabled) dampedDragAnimation.modifier else Modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(
                        backdrop,
                        rememberBackdrop(trackBackdrop) { drawBackdrop ->
                            val pressProgress = dampedDragAnimation.pressProgress
                            val scaleX = 2f / 3f + (1f / 3f) * pressProgress
                            scale(scaleX, pressProgress) {
                                drawBackdrop()
                            }
                        },
                    ),
                    shape = { ContinuousCapsule },
                    effects = {
                        val pressProgress = dampedDragAnimation.pressProgress
                        blur(8.dp.toPx() * (1f - pressProgress))
                        lens(
                            10.dp.toPx() * pressProgress,
                            14.dp.toPx() * pressProgress,
                            chromaticAberration = true,
                        )
                    },
                    highlight = {
                        Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            alpha = dampedDragAnimation.pressProgress,
                        )
                    },
                    shadow = {
                        Shadow(
                            radius = 8.dp,
                            color = Color.Black.copy(alpha = 0.12f),
                        )
                    },
                    innerShadow = {
                        InnerShadow(
                            radius = 4.dp * dampedDragAnimation.pressProgress,
                            alpha = dampedDragAnimation.pressProgress,
                        )
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val pressProgress = dampedDragAnimation.pressProgress
                        drawRect(thumbColor.copy(alpha = 1f - pressProgress))
                    },
                )
                .size(40f.dp, 24f.dp),
        )
    }
}

@Composable
private fun ToolButtonItem(
    button: ToolButtonDef,
    state: ReadBookUiState,
    colors: ReadMenuColors,
    backdrop: Backdrop?,
    glassEnabled: Boolean,
    labelColor: Color,
    modifier: Modifier = Modifier,
) {
    val iconTint = if (button.isActive) LegadoTheme.colorScheme.primary else colors.content
    val badgeCount = when (button.id) {
        "replace_badge" -> state.effectiveReplaceCount
        else -> 0
    }
    val buttonShape = RoundedCornerShape(16.dp)
    val containerColor = when {
        button.isActive -> LegadoTheme.colorScheme.secondaryContainer
        state.menuConfig.readMenuIconStyle == 1 -> LegadoTheme.colorScheme.surfaceContainerLow
        else -> Color.Transparent
    }
    val borderStroke = when {
        button.isActive -> BorderStroke(1.5.dp, LegadoTheme.colorScheme.primary)
        state.menuConfig.readMenuIconStyle == 2 -> BorderStroke(1.dp, iconTint.copy(alpha = 0.45f))
        else -> null
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (glassEnabled) {
            ReadMenuGlassButtonSurface(
                onClick = button.onClick,
                colors = colors,
                backdrop = backdrop,
                menuConfig = state.menuConfig,
                glassEnabled = true,
                selected = button.isActive,
                onLongClick = button.onLongClick,
                contentDescription = button.description,
            ) { tint ->
                ToolButtonContent(
                    button = button,
                    tint = if (button.isActive) iconTint else tint,
                    badgeCount = badgeCount,
                )
            }
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(buttonShape)
                    .background(containerColor, buttonShape)
                    .then(
                        if (borderStroke != null) Modifier.border(
                            borderStroke,
                            buttonShape
                        ) else Modifier
                    )
                    .combinedClickable(
                        indication = LocalIndication.current,
                        interactionSource = remember { MutableInteractionSource() },
                        role = Role.Button,
                        onLongClick = button.onLongClick,
                        onClick = button.onClick,
                    )
                    .semantics {
                        contentDescription = button.description
                        role = Role.Button
                    },
            ) {
                ToolButtonContent(
                    button = button,
                    tint = iconTint,
                    badgeCount = badgeCount,
                )
            }
        }
        if (state.menuConfig.readMenuIconShowText) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = button.description,
                style = LegadoTheme.typography.labelSmall.copy(
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = Color.Black.copy(alpha = 0.12f),
                        offset = Offset.Zero,
                        blurRadius = 12f,
                    )
                ),
                color = labelColor,
                maxLines = 1,
                modifier = Modifier.wrapContentWidth(
                    align = Alignment.CenterHorizontally,
                    unbounded = true,
                ),
            )
        }
    }
}

@Composable
private fun ToolButtonContent(
    button: ToolButtonDef,
    tint: Color,
    badgeCount: Int,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize(),
    ) {
        if (button.customIconPath.isNullOrBlank()) {
            Icon(
                imageVector = button.icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = tint,
            )
        } else {
            AsyncImage(
                model = button.customIconPath,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape),
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

private data class ToolButtonDef(
    val id: String,
    val icon: ImageVector,
    val description: String,
    val customIconPath: String?,
    val isActive: Boolean = false,
    val onClick: () -> Unit,
    val onLongClick: (() -> Unit)? = null,
)

private fun loadToolButtons(
    context: Context,
    state: ReadBookUiState,
    eyeProtectionEnabled: Boolean,
    onIntent: (ReadBookIntent) -> Unit,
): List<ToolButtonDef> {
    val customIcons = state.menuConfig.readMenuCustomIcons
    fun ReadMenuButtonInfo.toButton(
        isActive: Boolean = false,
        onLongClick: (() -> Unit)? = null,
        onClick: () -> Unit,
    ): ToolButtonDef {
        return ToolButtonDef(id, icon, label, customIcons[id], isActive, onClick, onLongClick)
    }
    val infoMap = readMenuButtonInfos(context).associateBy { it.id }
    val allButtons = listOf(
        infoMap.getValue("search").toButton {
            onIntent(ReadBookIntent.OpenSearch(null))
        },
        infoMap.getValue("catalog").toButton {
            onIntent(ReadBookIntent.OpenChapterList)
        },
        infoMap.getValue("read_aloud").toButton(
            isActive = state.isReadAloudRunning,
            onLongClick = {
                onIntent(ReadBookIntent.OpenReadMenuRoute(ReadBookMenuRoute.ReadAloud))
            },
        ) {
            if (state.isReadAloudRunning) {
                onIntent(ReadBookIntent.ReadAloudAction)
            } else {
                onIntent(ReadBookIntent.ToggleReadAloud)
                onIntent(ReadBookIntent.HideMenu)
            }
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
        infoMap.getValue("eye_protection").toButton(
            isActive = eyeProtectionEnabled,
            onLongClick = { onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.EyeProtection)) },
        ) {
            onIntent(ReadBookIntent.ToggleEyeProtection)
        },
        infoMap.getValue("prev_chapter").toButton {
            onIntent(ReadBookIntent.PrevChapter)
        },
        infoMap.getValue("next_chapter").toButton {
            onIntent(ReadBookIntent.NextChapter)
        },
        infoMap.getValue("replace").toButton(
            isActive = state.useReplaceRule,
            onLongClick = { onIntent(ReadBookIntent.MenuSettingReplace) },
        ) {
            onIntent(ReadBookIntent.MenuEnableReplace)
        },
        infoMap.getValue("replace_badge").toButton {
            onIntent(ReadBookIntent.MenuSettingReplace)
        },
        infoMap.getValue("auto_page").toButton(isActive = state.isAutoPage) {
            if (state.isAutoPage) {
                onIntent(ReadBookIntent.OpenReadMenuRoute(ReadBookMenuRoute.AutoRead))
            } else {
                onIntent(ReadBookIntent.ToggleAutoPage)
                onIntent(ReadBookIntent.HideMenu)
            }
        },
        infoMap.getValue("translate").toButton(isActive = state.translationMode) {
            onIntent(ReadBookIntent.ToggleTranslation)
        },
        infoMap.getValue("ai_summary").toButton {
            onIntent(ReadBookIntent.OpenChapterSummary)
        },
        infoMap.getValue("ai_rewrite").toButton {
            onIntent(ReadBookIntent.OpenAiCurrentChapterRewrite)
        },
    )

    val allMap = allButtons.associateBy { it.id }
    return state.menuConfig.bottomBarButtons
        .asSequence()
        .filter { it.enabled }
        .mapNotNull { allMap[it.id] }
        .toList()
}

private data class ReadMenuColors(
    val background: Color,
    val content: Color,
)

private fun Int.toReaderMenuTintStyle(): ReaderMenuTintStyle {
    return if (this == ReadMenuBlurStyle.Progressive) {
        ReaderMenuTintStyle.Gradient
    } else {
        ReaderMenuTintStyle.Fill
    }
}

private fun readMenuTopBarButtonLiquidGlassEnabled(
    backdrop: Backdrop?,
    menuConfig: ReadMenuConfig,
): Boolean {
    return menuConfig.readMenuTopBarLiquidGlassButtons &&
            readerMenuLiquidGlassAvailable(backdrop)
}

private fun readMenuTopBarTitleCapsuleEnabled(
    backdrop: Backdrop?,
    menuConfig: ReadMenuConfig,
): Boolean {
    return menuConfig.readMenuTopBarTitleCapsule
}

private fun readMenuBottomBarButtonLiquidGlassEnabled(
    backdrop: Backdrop?,
    menuConfig: ReadMenuConfig,
): Boolean {
    return menuConfig.readMenuBottomBarLiquidGlassButtons &&
            readerMenuLiquidGlassAvailable(backdrop)
}

private fun readMenuTopBarHazeEnabled(
    hazeState: HazeState?,
    menuConfig: ReadMenuConfig,
): Boolean {
    return hazeState != null && menuConfig.readMenuTopBarBlurMode == ReadMenuBlurMode.Haze
}

private fun readMenuBottomBarEffectiveBlurMode(
    menuConfig: ReadMenuConfig,
    isFloating: Boolean,
): Int {
    val mode = menuConfig.readMenuBottomBarBlurMode
    return if (!isFloating && mode == ReadMenuBlurMode.LiquidGlass) {
        ReadMenuBlurMode.Haze
    } else {
        mode
    }
}

private fun readMenuBottomBarLiquidGlassEnabled(
    backdrop: Backdrop?,
    menuConfig: ReadMenuConfig,
    isFloating: Boolean,
): Boolean {
    return isFloating &&
            readMenuBottomBarEffectiveBlurMode(
                menuConfig,
                isFloating
            ) == ReadMenuBlurMode.LiquidGlass &&
            readerMenuLiquidGlassAvailable(backdrop)
}

private fun readMenuBottomBarHazeEnabled(
    hazeState: HazeState?,
    menuConfig: ReadMenuConfig,
    isFloating: Boolean,
): Boolean {
    return hazeState != null &&
            readMenuBottomBarEffectiveBlurMode(menuConfig, isFloating) == ReadMenuBlurMode.Haze
}

@Composable
private fun Modifier.readMenuLiquidGlass(
    backdrop: Backdrop?,
    colors: ReadMenuColors,
    shape: Shape,
    useTopBarStyle: Boolean,
    useLens: Boolean,
    blurRadius: Dp? = null,
    interactive: Boolean = false,
    menuConfig: ReadMenuConfig,
): Modifier {
    val resolvedBlurRadius = blurRadius ?: menuConfig.readMenuBlurRadius.dp
    val blurAlpha = menuConfig.readMenuBlurAlpha
    val surfaceColor = readMenuTintColor(menuConfig) ?: colors.background
    val containerColor = surfaceColor.copy(
        alpha = (blurAlpha.coerceIn(0, 100) / 100f).coerceAtMost(0.6f)
    )
    val topBarSurfaceBrush = readerMenuSurfaceBrush(
        style = ReaderMenuTintStyle.Gradient,
        placement = ReaderMenuPlacement.Top,
        color = surfaceColor,
        alpha = containerColor.alpha,
    )

    val surfaceBrush = if (useTopBarStyle) {
        topBarSurfaceBrush
    } else {
        readerMenuSurfaceBrush(
            style = ReaderMenuTintStyle.Fill,
            placement = ReaderMenuPlacement.Bottom,
            color = surfaceColor,
            alpha = containerColor.alpha,
        )
    }
    return readerMenuLiquidGlass(
        backdrop = backdrop,
        shape = shape,
        surfaceBrush = surfaceBrush,
        blurRadius = resolvedBlurRadius,
        lensRadius = menuConfig.readMenuLensRadius.dp,
        useLens = useLens,
        interactive = interactive,
    )
}

@Composable
private fun Modifier.readMenuBottomBarHazeEffect(
    state: HazeState,
    colors: ReadMenuColors,
    shape: Shape,
    menuConfig: ReadMenuConfig,
    visualState: ReaderMenuVisualState,
    blurRadiusDp: Int? = null,
): Modifier {
    return clip(shape)
        .readerMenuHazeEffect(
            state = state,
            visualState = visualState,
            placement = ReaderMenuPlacement.Bottom,
            baseColor = colors.background,
            tintColor = readMenuTintColor(menuConfig),
            blurRadius = blurRadiusDp ?: menuConfig.readMenuBlurRadius,
            surfaceAlpha = menuConfig.readMenuBlurAlpha,
        )
}

@Composable
private fun readMenuTintColor(menuConfig: ReadMenuConfig): Color? {
    return menuConfig.readMenuBlurColorNight
        .takeIf { it != 0 && ReadStyleResolver.isNightTheme() }
        ?.let(::Color)
        ?: menuConfig.readMenuBlurColor
            .takeIf { it != 0 && !ReadStyleResolver.isNightTheme() }
            ?.let(::Color)
        ?: menuConfig.readMenuBlurColor
            .takeIf { it != 0 }
            ?.let(::Color)
}

@Composable
private fun readMenuColors(readBarStyle: Int): ReadMenuColors {
    val themeBackground = LegadoTheme.colorScheme.surfaceContainerHigh
    val themeContent = LegadoTheme.colorScheme.onSurface
    return when (readBarStyle) {
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

private data class FloatingIconDef(
    val id: String,
    val icon: ImageVector,
    val label: String,
    val isActive: Boolean = false,
    val onClick: () -> Unit,
    val onLongClick: (() -> Unit)? = null,
)

private fun loadFloatingIcons(
    context: Context,
    state: ReadBookUiState,
    preferences: ReadPreferences,
    onIntent: (ReadBookIntent) -> Unit,
): List<FloatingIconDef> {
    val infoMap = readMenuButtonInfos(context).associateBy { it.id }

    val actionMap: Map<String, () -> Unit> = mapOf(
        "search" to { onIntent(ReadBookIntent.OpenSearch(null)) },
        "catalog" to { onIntent(ReadBookIntent.OpenChapterList) },
        "read_aloud" to {
            if (state.isReadAloudRunning) {
                onIntent(ReadBookIntent.ReadAloudAction)
            } else {
                onIntent(ReadBookIntent.ToggleReadAloud)
                onIntent(ReadBookIntent.HideMenu)
            }
        },
        "setting" to { onIntent(ReadBookIntent.OpenReadMenuRoute(ReadBookMenuRoute.ReadStyle)) },
        "addBookmark" to { onIntent(ReadBookIntent.AddBookmark) },
        "theme" to { onIntent(ReadBookIntent.ToggleDayNight) },
        "eye_protection" to { onIntent(ReadBookIntent.ToggleEyeProtection) },
        "prev_chapter" to { onIntent(ReadBookIntent.PrevChapter) },
        "next_chapter" to { onIntent(ReadBookIntent.NextChapter) },
        "replace" to { onIntent(ReadBookIntent.MenuEnableReplace) },
        "replace_badge" to { onIntent(ReadBookIntent.MenuSettingReplace) },
        "auto_page" to {
            if (state.isAutoPage) {
                onIntent(ReadBookIntent.OpenReadMenuRoute(ReadBookMenuRoute.AutoRead))
            } else {
                onIntent(ReadBookIntent.ToggleAutoPage)
                onIntent(ReadBookIntent.HideMenu)
            }
        },
        "translate" to { onIntent(ReadBookIntent.ToggleTranslation) },
        "ai_summary" to { onIntent(ReadBookIntent.OpenChapterSummary) },
        "ai_rewrite" to { onIntent(ReadBookIntent.OpenAiCurrentChapterRewrite) },
    )

    val activeIds = buildSet {
        if (state.isReadAloudRunning) add("read_aloud")
        if (state.isAutoPage) add("auto_page")
        if (state.translationMode) add("translate")
        if (state.useReplaceRule) add("replace")
        if (preferences.eyeProtectionEnabled) add("eye_protection")
    }

    return state.menuConfig.titleBarButtons
        .asSequence()
        .filter { it.enabled }
        .mapNotNull { item ->
            val id = item.id
            val info = infoMap[id] ?: return@mapNotNull null
            FloatingIconDef(
                id = id,
                icon = info.icon,
                label = info.label,
                isActive = id in activeIds,
                onClick = actionMap[id] ?: {},
                onLongClick = when (id) {
                    "read_aloud" -> {
                        { onIntent(ReadBookIntent.OpenReadMenuRoute(ReadBookMenuRoute.ReadAloud)) }
                    }

                    "replace" -> {
                        { onIntent(ReadBookIntent.MenuSettingReplace) }
                    }

                    else -> null
                },
            )
        }
        .toList()
}

@Composable
private fun BrightnessBar(
    brightness: Int,
    onBrightnessChange: (Int) -> Unit,
    brightnessAuto: Boolean,
    onToggleAuto: () -> Unit,
    onTogglePosition: () -> Unit,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
    colors: ReadMenuColors,
    menuConfig: ReadMenuConfig,
    backdrop: Backdrop? = null,
    buttonGlassEnabled: Boolean = false,
    glassThumbEnabled: Boolean = false,
    onBrightnessPreview: (Int) -> Unit,
) {
    var sliderValue by remember(vertical, buttonGlassEnabled) {
        mutableFloatStateOf(brightness.toFloat())
    }
    var sliderDragging by remember(vertical, buttonGlassEnabled) {
        mutableStateOf(false)
    }

    LaunchedEffect(brightness, brightnessAuto) {
        if (brightnessAuto) {
            sliderDragging = false
            sliderValue = brightness.toFloat()
        } else if (!sliderDragging) {
            sliderValue = brightness.toFloat()
        }
    }

    fun updateSliderValue(value: Float) {
        if (brightnessAuto) return
        sliderDragging = true
        sliderValue = value.coerceIn(0f, 100f)
        val target = value.roundToInt().coerceIn(0, 100)

        //直接先改亮度，如果在这里onBrightnessChange，会ANR
        onBrightnessPreview(target)
    }

    fun commitSliderValue(value: Float) {
        val target = value.roundToInt().coerceIn(0, 100)
        sliderDragging = false
        sliderValue = target.toFloat()
        if (brightnessAuto) return
        onBrightnessChange(target)
    }

    fun toggleAuto() {
        sliderDragging = false
        sliderValue = brightness.toFloat()
        onToggleAuto()
    }

    val brightnessValueDescription = stringResource(
        R.string.a11y_percent_value,
        sliderValue.roundToInt().coerceIn(0, 100),
    )

    if (vertical) {
        Column(
            modifier = modifier
                .width(if (buttonGlassEnabled) 64.dp else 56.dp)
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ReadMenuGlassIconButton(
                onClick = ::toggleAuto,
                icon = Icons.Filled.BrightnessAuto,
                colors = colors,
                backdrop = backdrop,
                menuConfig = menuConfig,
                glassEnabled = buttonGlassEnabled,
                selected = brightnessAuto,
                contentDescription = stringResource(R.string.brightness_follow_system),
            )
            AppVerticalSlider(
                value = sliderValue.coerceIn(0f, 100f),
                onValueChange = { value ->
                    if (brightnessAuto) return@AppVerticalSlider
                    updateSliderValue(value)
                },
                onValueChangeFinished = {
                    commitSliderValue(sliderValue)
                },
                valueRange = 0f..100f,
                enabled = !brightnessAuto,
                height = 168.dp,
                accessibilityLabel = stringResource(R.string.brightness),
                accessibilityValue = brightnessValueDescription,
            )
            ReadMenuGlassIconButton(
                onClick = onTogglePosition,
                icon = Icons.Filled.SwapHoriz,
                colors = colors,
                backdrop = backdrop,
                menuConfig = menuConfig,
                glassEnabled = buttonGlassEnabled,
                contentDescription = stringResource(R.string.brightness_bar_position),
            )
        }
    } else {
        val buttonSize = if (buttonGlassEnabled) 48.dp else 40.dp
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ReadMenuGlassIconButton(
                onClick = ::toggleAuto,
                icon = Icons.Filled.BrightnessAuto,
                colors = colors,
                backdrop = backdrop,
                menuConfig = menuConfig,
                glassEnabled = buttonGlassEnabled,
                selected = brightnessAuto,
                contentDescription = stringResource(R.string.brightness_follow_system),
            )
            ReadMenuSlider(
                value = sliderValue.coerceIn(0f, 100f),
                onValueChange = { value ->
                    if (brightnessAuto) return@ReadMenuSlider
                    updateSliderValue(value)
                },
                onValueChangeFinished = {
                    commitSliderValue(sliderValue)
                },
                onValueCommit = ::commitSliderValue,
                valueRange = 0f..100f,
                enabled = !brightnessAuto,
                backdrop = backdrop,
                glassThumbEnabled = glassThumbEnabled,
                accessibilityLabel = stringResource(R.string.brightness),
                accessibilityValue = brightnessValueDescription,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
            Spacer(Modifier.width(buttonSize))
        }
    }
}
