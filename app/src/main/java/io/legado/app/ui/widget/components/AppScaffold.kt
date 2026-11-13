package io.legado.app.ui.widget.components

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import io.legado.app.domain.model.settings.hasBackgroundImage
import io.legado.app.ui.theme.ComposeEngine
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LocalAppUiConfiguration
import io.legado.app.ui.theme.LocalHasMiuixPopupHost
import io.legado.app.ui.theme.LocalHazeState
import io.legado.app.ui.theme.LocalMiuixBarBackdrop
import io.legado.app.ui.theme.LocalTopBarBackdrop
import io.legado.app.ui.theme.LocalMochiBackdropRegistry
import io.legado.app.ui.theme.LocalMochiScrollBlurProgress
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.widget.components.coui.AppCouiTopBarHost
import io.legado.app.ui.widget.components.coui.LocalAppCouiTopBarHost
import io.legado.app.ui.widget.components.coui.LocalCouiPageBackdrop
import ui.feice.mochi.effect.glass.LocalMochiBackdrop
import ui.feice.mochi.effect.glass.rememberMochiBackdrop
import ui.feice.mochi.effect.glass.mochiBackdropSource
import ui.feice.mochi.ios.IosGlass
import ui.feice.mochi.effect.mask.mochiScrollEdgeBlur
import ui.feice.mochi.theme.MochiMaterialsScope
import ui.feice.mochi.theme.MochiTheme
import top.yukonga.miuix.kmp.blur.layerBackdrop as miuixLayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop as rememberMiuixLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.FabPosition as MiuixFabPosition
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    modifier: Modifier = Modifier,
    topBar: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    contentColor: Color = contentColorFor(MiuixTheme.colorScheme.surface),
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    alwaysDrawBehindBars: Boolean = false,
    /**
     * 内容不作为模糊 / 液态玻璃的采样源。
     *
     * haze 的 `hazeSource` 与液态玻璃的 `layerBackdrop` 都会把整棵内容录进 GraphicsLayer；
     * 内容里若有 AndroidView（WebView 等 interop view），Compose 会把它一并画进那张离屏
     * RenderNode（`AndroidViewHolder.draw` → `AndroidComposeView.drawAndroidView`）。
     * Chromium 在「把网页画进别人的图层」这条路径上不稳定，部分设备会表现为网页闪烁，
     * 因此 WebView 类页面必须把两个采样源一起关掉。
     */
    disableContentSampling: Boolean = false,
    content: @Composable (PaddingValues) -> Unit
) {
    val isDark = LegadoTheme.isDark
    val configuration = LocalAppUiConfiguration.current
    val themeSettings = configuration.theme
    val hasImageBg = themeSettings.hasBackgroundImage(isDark)
    val composeEngine = LegadoTheme.composeEngine
    val hasBars = topBar != null || bottomBar != null
    val hazeState = if (composeEngine == ComposeEngine.Material && hasBars && themeSettings.enableBlur) {
        remember { HazeState() }
    } else null
    val nativeSamplingSupported = !disableContentSampling &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val liquidGlassEnabled = composeEngine == ComposeEngine.Material && topBar != null && themeSettings.topBarButtonStyle == "liquid"
    val backdropBaseColor = LegadoTheme.colorScheme.background
    // COUI 的顶栏材质是外观的一部分，不再受 enableBlur 控制。
    val couiBackdrop = if (composeEngine == ComposeEngine.Coui && ThemeResolver.isCouiSupported() &&
        nativeSamplingSupported && topBar != null
    ) {
        rememberCouiBackdrop {
            drawRect(backdropBaseColor)
            drawContent()
        }
    } else null
    val couiTopBarHost = if (composeEngine == ComposeEngine.Coui && topBar != null) {
        val view = LocalView.current
        remember(couiBackdrop, view) { AppCouiTopBarHost(couiBackdrop, view) }
    } else null
    val density = LocalDensity.current
    val miuixEngine = composeEngine == ComposeEngine.Miuix
    val miuixSurfaceColor = MiuixTheme.colorScheme.surface
    // Miuix 的顶/底栏模糊同样常开，enableBlur 只作用于 Material / Mochi。
    val miuixBarBlur = miuixEngine && nativeSamplingSupported && hasBars
    val miuixGlassMenus = miuixEngine && themeSettings.useHyperOs4Components &&
        ThemeResolver.isBlurSupported()
    val miuixBackdrop = if (nativeSamplingSupported && (miuixBarBlur || miuixGlassMenus)) {
        rememberMiuixLayerBackdrop {
            // The scaffold surface sits outside this recording. Match its visible base color.
            drawRect(miuixSurfaceColor)
            drawContent()
        }
    } else null
    val mochiTopBarGlassEnabled = composeEngine == ComposeEngine.Mochi &&
        nativeSamplingSupported && topBar != null && themeSettings.mochiTopBarLiquidGlass
    val mochiScrollBlur = composeEngine == ComposeEngine.Mochi &&
        nativeSamplingSupported && topBar != null && themeSettings.mochiScrollEdgeBlurEnabled
    val mochiScrollProgress = if (mochiScrollBlur) remember { mutableStateOf<() -> Float>({ 1f }) } else null
    val mochiBackdrop = if (composeEngine == ComposeEngine.Mochi && nativeSamplingSupported &&
        (mochiTopBarGlassEnabled || bottomBar != null && configuration.appShell.useFloatingBottomBarLiquidGlass)) {
        rememberMochiBackdrop(backdropBaseColor)
    } else null
    val mochiRegistry = LocalMochiBackdropRegistry.current
    val mochiOwner = remember { Any() }
    if (composeEngine == ComposeEngine.Mochi) {
        DisposableEffect(mochiRegistry, mochiBackdrop) {
            mochiRegistry?.register(mochiOwner, mochiBackdrop)
            onDispose { mochiRegistry?.unregister(mochiOwner) }
        }
    }
    val contentDrawsBehindBars =
        alwaysDrawBehindBars || hazeState != null || miuixBarBlur || mochiBackdrop != null ||
            mochiScrollBlur || couiBackdrop != null

    val containerColor = if (hasImageBg) {
        Color.Transparent
    } else {
        LegadoTheme.colorScheme.background
    }

    val miuixContainerColor = if (hasImageBg && miuixBackdrop == null) {
        Color.Transparent
    } else {
        miuixSurfaceColor
    }
    val topBarBackgroundBackdrop = if (liquidGlassEnabled) rememberLayerBackdrop {
        drawRect(backdropBaseColor)
        drawContent()
    } else null
    val topBarContentBackdrop = if (liquidGlassEnabled && !disableContentSampling) rememberLayerBackdrop() else null
    val topBarBackdrop = if (topBarBackgroundBackdrop != null && topBarContentBackdrop != null) {
        rememberCombinedBackdrop(topBarBackgroundBackdrop, topBarContentBackdrop)
    } else topBarBackgroundBackdrop
    val parentGlassOverlay = LocalAppGlassOverlay.current
    val glassOverlay = if (miuixGlassMenus) parentGlassOverlay ?: remember { AppGlassOverlayState() } else null
    if (miuixGlassMenus && parentGlassOverlay != null) {
        val owner = remember { Any() }
        // Publish the same recording used by the bars. A null source also masks older pages.
        SideEffect { parentGlassOverlay.registerBackdrop(owner, miuixBackdrop) }
        DisposableEffect(parentGlassOverlay, owner) {
            onDispose { parentGlassOverlay.unregisterBackdrop(owner) }
        }
    }

    CompositionLocalProvider(
        LocalHazeState provides hazeState,
        LocalTopBarBackdrop provides topBarBackdrop,
        LocalMiuixBarBackdrop provides miuixBackdrop,
        LocalHasMiuixPopupHost provides (composeEngine == ComposeEngine.Miuix),
        LocalMochiBackdrop provides mochiBackdrop,
        LocalMochiScrollBlurProgress provides mochiScrollProgress,
        LocalAppGlassOverlay provides glassOverlay,
    ) {
        when (composeEngine) {
            ComposeEngine.Miuix -> {
                val miuixFabPosition = when (floatingActionButtonPosition) {
                    FabPosition.End -> MiuixFabPosition.End
                    FabPosition.Center -> MiuixFabPosition.Center
                    else -> MiuixFabPosition.End
                }
                Box(modifier = modifier.fillMaxSize()) {
                    if (miuixBackdrop == null) {
                        BackgroundImageContent(isDark = isDark, hazeState = null)
                    }
                    MiuixScaffold(
                        modifier = Modifier.fillMaxSize(),
                        topBar = { topBar?.invoke() },
                        bottomBar = { bottomBar?.invoke() },
                        snackbarHost = snackbarHost,
                        floatingActionButton = floatingActionButton,
                        floatingActionButtonPosition = miuixFabPosition,
                        containerColor = miuixContainerColor,
                        contentWindowInsets = contentWindowInsets
                    ) { paddingValues ->
                        // Insets belong to this scaffold's actual bars, not a global navigation preference.
                        val scaffoldPadding = paddingValues
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (miuixBackdrop != null) {
                                        Modifier.miuixLayerBackdrop(miuixBackdrop)
                                    } else Modifier
                                )
                                .then(
                                    if (contentDrawsBehindBars) Modifier
                                    else Modifier
                                        .padding(scaffoldPadding)
                                        .consumeWindowInsets(scaffoldPadding)
                                )
                        ) {
                            if (miuixBackdrop != null) {
                                BackgroundImageContent(isDark = isDark, hazeState = null)
                            }
                            content(
                                if (contentDrawsBehindBars) scaffoldPadding
                                else PaddingValues(0.dp)
                            )
                        }
                    }
                    if (glassOverlay != null && parentGlassOverlay == null) {
                        AppGlassOverlayLayer(glassOverlay)
                    }
                }
            }

            ComposeEngine.Material, ComposeEngine.Mochi, ComposeEngine.Coui -> {
                Box(modifier = modifier.fillMaxSize()) {
                    if (mochiBackdrop == null && !mochiScrollBlur && couiBackdrop == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (topBarBackgroundBackdrop != null) {
                                        Modifier.layerBackdrop(topBarBackgroundBackdrop)
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            BackgroundImageContent(isDark = isDark, hazeState = hazeState)
                        }
                    }
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        topBar = {
                            if (topBar != null) {
                                if (composeEngine == ComposeEngine.Mochi) {
                                    val materials = MochiTheme.materials.let {
                                        if (mochiScrollBlur) it else it.copy(scrollEdge = null)
                                    }
                                    val barMaterials = if (mochiTopBarGlassEnabled) {
                                        materials.copy(control = remember(isDark) { IosGlass.regular(isDark) })
                                    } else {
                                        materials.copy(bar = null, control = null, clear = null)
                                    }
                                    MochiMaterialsScope(barMaterials) { topBar() }
                                } else if (composeEngine == ComposeEngine.Coui) {
                                    CompositionLocalProvider(LocalAppCouiTopBarHost provides couiTopBarHost) { topBar() }
                                } else {
                                    topBar()
                                }
                            }
                        },
                        bottomBar = { bottomBar?.invoke() },
                        snackbarHost = snackbarHost,
                        floatingActionButton = {
                            // 只向独立栏位提供页面源，正文里的 FAB 不得采样自身。
                            CompositionLocalProvider(LocalCouiPageBackdrop provides couiBackdrop) {
                                floatingActionButton()
                            }
                        },
                        floatingActionButtonPosition = floatingActionButtonPosition,
                        containerColor = containerColor,
                        contentColor = contentColor,
                        contentWindowInsets = contentWindowInsets
                    ) { paddingValues ->
                        val scaffoldPadding = couiTopBarHost?.scrollBehavior?.state
                            ?.contentPadding(paddingValues, density) ?: paddingValues
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(if (couiBackdrop != null) Modifier.couiBackdrop(couiBackdrop) else Modifier)
                                .mochiBackdropSource(mochiBackdrop)
                                .then(
                                    if (mochiScrollProgress != null && scaffoldPadding.calculateTopPadding() > 0.dp) {
                                        Modifier.mochiScrollEdgeBlur(scaffoldPadding.calculateTopPadding()) { mochiScrollProgress.value() }
                                    } else Modifier
                                )
                                .then(
                                    if (topBarContentBackdrop != null) {
                                        Modifier.layerBackdrop(topBarContentBackdrop)
                                    } else {
                                        Modifier
                                    }
                                )
                                .then(
                                    if (hazeState != null && !disableContentSampling) Modifier.hazeSource(hazeState)
                                    else Modifier
                                )
                                .then(
                                    if (contentDrawsBehindBars) Modifier
                                    else Modifier
                                        .padding(scaffoldPadding)
                                        .consumeWindowInsets(scaffoldPadding)
                                )
                        ) {
                            if (mochiBackdrop != null || mochiScrollBlur || couiBackdrop != null) {
                                BackgroundImageContent(isDark = isDark, hazeState = hazeState)
                            }
                            content(
                                if (contentDrawsBehindBars) scaffoldPadding
                                else PaddingValues(0.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun BackgroundImageContent(
    isDark: Boolean,
    hazeState: HazeState?
) {
    val themeSettings = LocalAppUiConfiguration.current.theme
    val hasImageBg = themeSettings.hasBackgroundImage(isDark)
    val bgImagePath = if (isDark) {
        themeSettings.backgroundImageDark
    } else {
        themeSettings.backgroundImageLight
    }
    val blur = if (isDark) {
        themeSettings.backgroundImageDarkBlurring
    } else {
        themeSettings.backgroundImageBlurring
    }

    if (hasImageBg && !bgImagePath.isNullOrBlank()) {
        if (hazeState != null) {
            AsyncImage(
                model = bgImagePath,
                contentDescription = null,
                imageLoader = org.koin.compose.koinInject(),
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState),
                contentScale = ContentScale.Crop
            )
        } else {
            AsyncImage(
                model = bgImagePath,
                contentDescription = null,
                imageLoader = org.koin.compose.koinInject(),
                modifier = Modifier
                    .fillMaxSize()
                    .blur(blur.dp),
                contentScale = ContentScale.Crop
            )
        }
    }
}
