package io.legado.app.ui.book.read

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.kyant.backdrop.Backdrop
import dev.chrisbanes.haze.HazeState
import io.legado.app.R
import io.legado.app.constant.ReadMenuBlurMode
import io.legado.app.data.repository.ReadPreferences
import io.legado.app.ui.book.read.sheet.AutoReadContent
import io.legado.app.ui.book.read.sheet.ReadAloudContent
import io.legado.app.ui.book.read.sheet.ReadStyleContent
import io.legado.app.ui.book.read.sheet.TypographyPage
import io.legado.app.ui.book.read.sheet.TypographySection
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.series.SmallTonalButton
import io.legado.app.ui.widget.components.reader.ReaderMenuEffect
import io.legado.app.ui.widget.components.reader.ReaderMenuPlacement
import io.legado.app.ui.widget.components.reader.ReaderMenuTintStyle
import io.legado.app.ui.widget.components.reader.ReaderMenuVisualState
import io.legado.app.ui.widget.components.reader.readerMenuHazeEffect
import io.legado.app.ui.widget.components.reader.readerMenuLiquidGlassAvailable
import io.legado.app.ui.widget.components.reader.readerMenuSurfaceBrush
import io.legado.app.ui.widget.components.settingItem.LocalSliderDragState
import io.legado.app.ui.widget.components.settingItem.SliderDragState
import kotlin.math.roundToInt

/**
 * Compose replacement for ReadMenu — main reading menu overlay.
 */
@Composable
fun ReadBookMenuBar(
    state: ReadBookUiState,
    preferences: ReadPreferences,
    eyeProtectionActive: Boolean,
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
    val dialogLikeRoute = currentRoute == ReadBookMenuRoute.InformationConfig ||
            currentRoute == ReadBookMenuRoute.PaddingConfig
    var readStylePage by remember { mutableIntStateOf(0) }
    val sliderDragState = remember { SliderDragState() }
    LaunchedEffect(currentRoute) {
        if (currentRoute != ReadBookMenuRoute.ReadStyle) {
            readStylePage = 0
        }
        sliderDragState.stopDragging()
    }
    val hideTopBar = dialogLikeRoute
    val menuColors = readMenuColors(preferences.readBarStyle)

    CompositionLocalProvider(LocalSliderDragState provides sliderDragState) {
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
                        eyeProtectionActive = eyeProtectionActive,
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
                        eyeProtectionActive = eyeProtectionActive,
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
                    eyeProtectionActive = eyeProtectionActive,
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
    eyeProtectionActive: Boolean,
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
    val dialogLikeRoute = route == ReadBookMenuRoute.InformationConfig ||
            route == ReadBookMenuRoute.PaddingConfig
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
    val bottomBarBorderColor = readMenuBorderColor(state.menuConfig)
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
    val isSliderDragging = LocalSliderDragState.current?.isDragging == true
    val menuSurfaceAlpha = state.menuConfig.readMenuBlurAlpha.coerceIn(0, 100)
    // 拖动时降低不透明度（≤30%），露出背后排版以便实时预览；透明度变化带过渡动画
    val dragMenuAlpha = menuSurfaceAlpha.coerceAtMost(30)
    val animatedSurfaceAlpha by animateFloatAsState(
        targetValue = if (isSliderDragging) dragMenuAlpha.toFloat() else menuSurfaceAlpha.toFloat(),
        animationSpec = tween(durationMillis = 200),
        label = "ReadMenuSurfaceAlpha",
    )
    val bottomBarFillAlpha by animateFloatAsState(
        targetValue = when {
            isSliderDragging -> dragMenuAlpha / 100f
            expanded -> (menuSurfaceAlpha / 100f).coerceAtLeast(0.85f)
            else -> menuSurfaceAlpha / 100f
        },
        animationSpec = tween(durationMillis = 200),
        label = "ReadMenuFillAlpha",
    )
    val bottomBarTextColor = readMenuTextColor(state.menuConfig)
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
                        surfaceAlphaOverride = animatedSurfaceAlpha.roundToInt(),
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
                        surfaceAlphaOverride = animatedSurfaceAlpha.roundToInt(),
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
                    )
                }

                is ReadBookMenuContent.Route -> when (val targetRoute = target.route) {
                    ReadBookMenuRoute.Main -> {
                    MenuBottomBar(
                        state = state,
                        eyeProtectionEnabled = eyeProtectionActive,
                        colors = colors,
                        onIntent = onIntent,
                        context = context,
                        bottomPadding = if (extendSurfaceToNavigationBar) navBarHeight + 16.dp else 16.dp,
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
                                onOpenInformationConfig = {
                                    onIntent(ReadBookIntent.OpenReadMenuRoute(ReadBookMenuRoute.InformationConfig))
                                },
                                onOpenPaddingConfig = {
                                    onIntent(ReadBookIntent.OpenReadMenuRoute(ReadBookMenuRoute.PaddingConfig))
                                },
                                onOpenMoreConfig = {
                                    onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.MoreConfig))
                                },
                                onOpenBgTextConfig = { index ->
                                    onIntent(ReadBookIntent.OpenBgTextConfig(index))
                                },
                                onOpenTypographyConfig = {
                                    onIntent(ReadBookIntent.OpenReadMenuRoute(ReadBookMenuRoute.TypographyConfig))
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
                                eyeProtectionEnabled = eyeProtectionActive,
                                onIntent = onIntent,
                                styleConfig = state.styleConfig,
                            )
                        }
                    }

                    ReadBookMenuRoute.TypographyConfig -> {
                        ReadBookMenuRoutePage(
                            title = stringResource(R.string.compose_type),
                            maxHeight = maxHeight,
                            scrollContent = false,
                            animateSize = false,
                            bottomPadding = if (extendSurfaceToNavigationBar) navBarHeight else 0.dp,
                            onBack = { onIntent(ReadBookIntent.ReadMenuBack) },
                        ) {
                            TypographyPage(
                                config = state.sheetConfig,
                                onIntent = onIntent,
                                onOpenFontSelect = {
                                    onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.FontSelect))
                                },
                                onOpenTitleFontSelect = {
                                    onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.TitleFontSelect))
                                },
                                onOpenShadowSet = {
                                    onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.ShadowSet))
                                },
                                onOpenUnderlineConfig = {
                                    onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.UnderlineConfig))
                                },
                                onOpenHighlightRule = {
                                    onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.HighlightRuleConfig))
                                },
                                sameTitleRemoved = state.sameTitleRemoved,
                                onOpenPaddingConfig = {
                                    onIntent(ReadBookIntent.OpenReadMenuRoute(ReadBookMenuRoute.PaddingConfig))
                                },
                            )
                        }
                    }

                    ReadBookMenuRoute.InformationConfig -> {
                        ReadBookMenuRoutePage(
                            title = stringResource(R.string.information),
                            maxHeight = maxHeight,
                            scrollContent = false,
                            animateSize = false,
                            bottomPadding = if (extendSurfaceToNavigationBar) navBarHeight else 0.dp,
                            onBack = { onIntent(ReadBookIntent.ReadMenuBack) },
                        ) {
                            TypographyPage(
                                config = state.sheetConfig,
                                onIntent = onIntent,
                                onOpenFontSelect = {},
                                onOpenTitleFontSelect = {},
                                onOpenShadowSet = {},
                                onOpenUnderlineConfig = {},
                                onOpenHighlightRule = {},
                                section = TypographySection.Information,
                                onOpenPaddingConfig = {
                                    onIntent(ReadBookIntent.OpenReadMenuRoute(ReadBookMenuRoute.PaddingConfig))
                                },
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
                            TypographyPage(
                                config = state.sheetConfig,
                                onIntent = onIntent,
                                onOpenFontSelect = {},
                                onOpenTitleFontSelect = {},
                                onOpenShadowSet = {},
                                onOpenUnderlineConfig = {},
                                onOpenHighlightRule = {},
                                section = TypographySection.Padding,
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

private fun readMenuBottomBarButtonLiquidGlassEnabled(
    backdrop: Backdrop?,
    menuConfig: ReadMenuConfig,
): Boolean {
    return menuConfig.readMenuBottomBarLiquidGlassButtons &&
            readerMenuLiquidGlassAvailable(backdrop)
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
private fun Modifier.readMenuBottomBarHazeEffect(
    state: HazeState,
    colors: ReadMenuColors,
    shape: Shape,
    menuConfig: ReadMenuConfig,
    visualState: ReaderMenuVisualState,
    blurRadiusDp: Int? = null,
    surfaceAlphaOverride: Int? = null,
): Modifier {
    return clip(shape)
        .readerMenuHazeEffect(
            state = state,
            visualState = visualState,
            placement = ReaderMenuPlacement.Bottom,
            baseColor = colors.background,
            tintColor = readMenuTintColor(menuConfig),
            blurRadius = blurRadiusDp ?: menuConfig.readMenuBlurRadius,
            surfaceAlpha = surfaceAlphaOverride ?: menuConfig.readMenuBlurAlpha,
        )
}

@Composable
private fun readMenuColors(readBarStyle: Int): ReadMenuColors {
    val themeBackground = LegadoTheme.colorScheme.surfaceContainerHigh
    val themeContent = LegadoTheme.colorScheme.onSurface
    return when (readBarStyle) {
        2 -> ReadMenuColors(
            background = LegadoTheme.colorScheme.surfaceContainerHigh,
            content = LegadoTheme.colorScheme.primary,
        )

        else -> ReadMenuColors(themeBackground, themeContent)
    }
}
