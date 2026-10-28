package io.legado.app.ui.book.read

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.graphics.graphicsLayer
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
import io.legado.app.domain.model.settings.ReadAloudTimerMode
import io.legado.app.ui.book.read.sheet.AutoReadContent
import io.legado.app.ui.book.read.sheet.ReadAloudContent
import io.legado.app.ui.book.read.sheet.ReadStyleContent
import io.legado.app.ui.book.read.sheet.ThemeListContent
import io.legado.app.ui.book.read.sheet.TypographyPage
import io.legado.app.ui.book.read.sheet.TypographySection
import io.legado.app.ui.book.readaloud.ReadAloudTimerConfig
import io.legado.app.ui.book.readaloud.ReadAloudTimerSheet
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.series.SmallTonalButton
import io.legado.app.ui.widget.components.reader.ReaderMenuAnimatedBottom
import io.legado.app.ui.widget.components.reader.ReaderMenuAnimatedTop
import io.legado.app.ui.widget.components.reader.ReaderMenuDismissLayer
import io.legado.app.ui.widget.components.reader.ReaderMenuEffect
import io.legado.app.ui.widget.components.reader.ReaderMenuPlacement
import io.legado.app.ui.widget.components.reader.ReaderMenuTintStyle
import io.legado.app.ui.widget.components.reader.ReaderMenuVisualState
import io.legado.app.ui.widget.components.reader.readerMenuHazeEffect
import io.legado.app.ui.widget.components.reader.readerMenuLiquidGlassAvailable
import io.legado.app.ui.widget.components.reader.readerMenuSurfaceBrush
import io.legado.app.ui.widget.components.settingItem.LocalSliderDragState
import io.legado.app.ui.widget.components.settingItem.SliderDragState
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.coroutines.flow.StateFlow
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
    seekState: StateFlow<ReadSeekUiState>,
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
            currentRoute == ReadBookMenuRoute.PaddingConfig ||
            currentRoute == ReadBookMenuRoute.ThemeList
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
        ReaderMenuDismissLayer(
            visible = state.menuVisible || searchMenuVisible,
            onDismiss = {
                if (searchMenuVisible) {
                    onIntent(ReadBookIntent.HideSearchMenu)
                } else {
                    onIntent(ReadBookIntent.HideMenu)
                }
            },
        )

        ReaderMenuAnimatedTop(
            visible = state.menuVisible && !hideTopBar,
        ) {
            Column {
                MenuTitleBar(
                    state = state,
                    colors = menuColors,
                    onIntent = onIntent,
                    backdrop = backdrop,
                    hazeState = hazeState,
                    titleBarMode = preferences.titleBarMode,
                    eyeProtectionActive = eyeProtectionActive,
                )
                if (state.menuConfig.showTitleBarIcons && state.menuConfig.titleBarIconPosition <= 1) {
                    FloatingIconRow(
                        state = state,
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

        // 编辑面板期间（menuHiddenBySheet）**不销毁面板**：面板挂在 AnimatedVisibility 里，
        // 退场动画一结束内容就被销毁、恢复时重建，而重建那一瞬绘制跟不上 ⇒ 顶部一条带子晚出。
        // 撑住 visible 就没有这一瞬。收起/弹出改在**绘制层**做位移（graphicsLayer.translationY，
        // 只改绘制、不占布局）⇒ 面板既不重建、尺寸与位置也一字节不动；手感照原版滑入滑出（同一个 spring）。
        val panelVisible = state.menuVisible || searchMenuVisible
        val keepPanelAlive = state.menuHiddenBySheet
        val editSlideProgress by animateFloatAsState(
            targetValue = if (keepPanelAlive) 1f else 0f,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "ReadMenuEditSlide",
        )
        // 位移距离取"整屏高"，不是"面板自身高度"：居中面板被 offset 抬升过，它的布局盒本已贴在
        // 屏幕底部，只滑自身高度会让面板上面一截还留在屏幕里；一整屏高 ⇒ 抬升多少都必定完全离场。
        val editSlideDistancePx = LocalWindowInfo.current.containerSize.height.toFloat()

        // Bottom menu + floating icon row (bottom positions)
        ReaderMenuAnimatedBottom(
            visible = panelVisible || keepPanelAlive,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                // 只有"背景弹窗（ThemeList 路由）长按编辑期间"会动：进编辑 0→1 滑出屏幕，
                // 返回 1→0 滑回来。其它路由/其它界面 keepPanelAlive 恒 false ⇒ 位移恒 0，动画不受影响。
                // 只在 dialog-like 路由挂这层（主菜单连修饰符链都不变 —— 别碰其它界面的动画）；
                // 编辑这段 dialogLikeRoute 稳定不变 ⇒ 不会中途换链、也就不存在"动画开始时才分配 layer"。
                modifier = if (dialogLikeRoute) {
                    Modifier.graphicsLayer {
                        translationY = editSlideProgress * editSlideDistancePx
                    }
                } else {
                    Modifier
                },
            ) {
                if (state.menuVisible &&
                    state.menuConfig.showTitleBarIcons &&
                    state.menuConfig.titleBarIconPosition >= 2
                ) {
                    FloatingIconRow(
                        state = state,
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
                    seekState = seekState,
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
    seekState: StateFlow<ReadSeekUiState>,
) {
    val route = when (contentTarget) {
        ReadBookMenuContent.Search -> ReadBookMenuRoute.Main
        is ReadBookMenuContent.Route -> contentTarget.route
    }
    val expanded = route != ReadBookMenuRoute.Main
    val dialogLikeRoute = route == ReadBookMenuRoute.InformationConfig ||
            route == ReadBookMenuRoute.PaddingConfig ||
            route == ReadBookMenuRoute.ThemeList
    val density = LocalDensity.current
    val windowSize = LocalWindowInfo.current.containerSize
    var surfaceHeightPx by remember { mutableIntStateOf(0) }
    val morphProgress by animateFloatAsState(
        targetValue = if (dialogLikeRoute) 1f else 0f,
        label = "ReadBookMenuMorph",
    )
    // 朗读设置这一页是我们自己加的按钮最密的一页（播放条 + 定时 + 上下章 + 四行开关
    // + 两根滑块 + 底部五个动作），沿用官方那 64% 的高度装不下，只能上下滑才看全，
    // 所以只给这一路抬高上限，其它路由的观感保持不变。
    val maxHeight = with(density) {
        val fraction = if (route == ReadBookMenuRoute.ReadAloud) 0.92f else 0.64f
        windowSize.height.toDp() * fraction
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
    // 阅读主题弹窗"非网格开销"的实测值。**放在这里（路由分支外）而不是分支内**：
    // 面板挂在 AnimatedVisibility 上，隐藏后子树会被销毁、分支内的 remember 归零 ⇒ 恢复时会先退回
    // 首帧兜底值再长高（面板居中 ⇒ 顶边最后才到位，看着像"上半截延后出现"）。
    // key 用 navBarHeight：导航栏高度变了（旋转/三键↔手势）开销也跟着变，旧的实测值要作废重测。
    var themeListOverheadPx by remember(navBarHeight) { mutableIntStateOf(0) }
    var themeListGridHeightPx by remember(navBarHeight) { mutableIntStateOf(0) }
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
    // 面板的窗口内边距**不能用 safeDrawing**（它含 IME）：键盘弹起时底部内边距会变成键盘高度，
    // 而面板高度又被 maxHeight 钉住 ⇒ 内容整体被顶上去（表现为"弹窗飞顶"，且 surfaceH/lift 都不变）。
    // 用 systemBars + displayCutout（= safeDrawing 去掉 IME）：面板永远不因键盘避让。
    val surfaceWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)

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
                        surfaceWindowInsets.only(surfaceWindowInsetSides)
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
            // 朗读定时卡片的开合：放在 AnimatedContent 内的 when 之外，
            // 菜单路由切换时随 LaunchedEffect 关闭，不会残留
            var readAloudTimerSheetOpen by remember { mutableStateOf(false) }
            LaunchedEffect(target) { readAloudTimerSheetOpen = false }
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
                        seekState = seekState,
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

                    ReadBookMenuRoute.ThemeList -> {
                        val routeBottomPadding =
                            if (extendSurfaceToNavigationBar) navBarHeight else 0.dp
                        // 面板高度"实测法"：网格高 = 面板上限 − 非网格部分实测开销（两个实测值在菜单栏顶层，
                        // 不随面板重建丢失，见 navBarHeight 附近的声明）。
                        // 开销 = 面板实测高 − 网格实测高（与网格高无关、是常数 ⇒ 一次校正即收敛）；
                        // 首帧兜底 —— 只在还没测到值时用。
                        // ⚠️ 约束：它必须 **≥ 真实开销**，否则首帧网格比可用空间高 ⇒ 面板顶到上限 ⇒
                        // 上面那句"没被夹住才接受测量"永远不成立 ⇒ 一次都收敛不了，网格底部还会被裁。
                        // （加了"主题/排版"分段行后真实开销 ≈112+44≈156dp，故从 116 抬到 164，留几 dp 余量。）
                        val maxHeightPx = with(density) { maxHeight.roundToPx() }
                        val fallbackOverheadPx = with(density) {
                            (164.dp + routeBottomPadding).roundToPx()
                        }
                        val themeListOverheadPxValue =
                            if (themeListOverheadPx > 0) themeListOverheadPx else fallbackOverheadPx
                        // 只在面板没被上限夹住时接受测量：顶满之后数值不再变，不会越算越小或来回抖
                        if (themeListGridHeightPx > 0 && surfaceHeightPx in 1 until maxHeightPx) {
                            val measuredOverheadPx = surfaceHeightPx - themeListGridHeightPx
                            if (measuredOverheadPx > 0 && measuredOverheadPx != themeListOverheadPx) {
                                themeListOverheadPx = measuredOverheadPx
                            }
                        }
                        val themeListGridMaxHeight = with(density) {
                            (maxHeightPx - themeListOverheadPxValue)
                                .coerceAtLeast(200.dp.roundToPx())
                                .toDp()
                        }
                        ReadBookMenuRoutePage(
                            title = stringResource(R.string.theme_list_title),
                            maxHeight = maxHeight,
                            scrollContent = false,
                            animateSize = false,
                            bottomPadding = routeBottomPadding,
                            onBack = { onIntent(ReadBookIntent.ReadMenuBack) },
                        ) {
                            ThemeListContent(
                                onIntent = onIntent,
                                onToggleDayNight = { onIntent(ReadBookIntent.ToggleDayNight) },
                                onOpenBgTextConfig = { index ->
                                    onIntent(
                                        ReadBookIntent.UpdateConfig(ConfigUpdate.StyleSelect(index))
                                    )
                                    onIntent(ReadBookIntent.OpenBgTextConfig(index))
                                },
                                gridMaxHeight = themeListGridMaxHeight,
                                onGridMeasured = { themeListGridHeightPx = it },
                                modifier = Modifier.padding(horizontal = 16.dp),
                                styleConfig = state.styleConfig,
                            )
                        }
                    }

                    ReadBookMenuRoute.ReadAloud -> {
                        // 定时卡片叠在菜单页之上：它是宿主无关的读写组件，
                        // 由 ReadBookMenuBar 自己的布尔状态控制，不占 activeSheet。
                        ReadAloudTimerSheet(
                            show = readAloudTimerSheetOpen,
                            config = ReadAloudTimerConfig(
                                mode = ReadAloudTimerMode.fromStorage(state.readAloudTimerMode),
                                minutes = state.readAloudTtsTimer,
                                chapters = state.readAloudTimerChapters,
                                finishCurrentChapterAfterTimer =
                                    state.readAloudFinishCurrentChapterAfterTimer,
                            ),
                            onDismissRequest = { readAloudTimerSheetOpen = false },
                            onSetMode = {
                                onIntent(
                                    ReadBookIntent.SetReadAloudTimerMode(it.storageValue)
                                )
                            },
                            onSetMinutes = { onIntent(ReadBookIntent.SetReadAloudTtsTimer(it)) },
                            onSetChapters = {
                                onIntent(ReadBookIntent.SetReadAloudTimerChapters(it))
                            },
                            onSetFinishCurrentChapterAfterTimer = {
                                onIntent(ReadBookIntent.SetFinishCurrentChapterAfterTimer(it))
                            },
                        )
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
                                onShowTimerSettings = { readAloudTimerSheetOpen = true },
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
            AppText(
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
