package io.legado.app.ui.widget.components

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import io.legado.app.domain.model.settings.hasBackgroundImage
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LocalAppUiConfiguration
import io.legado.app.ui.theme.LocalHazeState
import io.legado.app.ui.theme.LocalTopBarBackdrop
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.theme.responsiveHazeSource
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.FabPosition as MiuixFabPosition
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold

/**
 * App 的壳层：负责背景图 / Haze / 液态玻璃 backdrop / 窗口 inset 的组装，并在 Miuix 与
 * Material3 两套 Scaffold 之间按当前引擎二选一。
 *
 * M1-3s 从 `:core:ui` 搬进 `:core:designsystem/commonMain`（包名不变 ⇒ 114 处消费方 import
 * 零改动）。它依赖的两个 CompositionLocal（[LocalHazeState] / [LocalTopBarBackdrop]）与
 * `responsiveHazeSource` 早已由 M1-3d / M1-3r 落在共享层，所以本文件不需要新契约——
 * 唯一不可跨端的是 `koinInject<ImageLoader>()`。
 *
 * **去掉 `imageLoader = koinInject()` 不改变任何行为**：`App` 实现
 * `coil3.SingletonImageLoader.Factory` 且 `newImageLoader(context)` 就是 `get()`（返回 Koin 里
 * 那个单例），省略该参数后 `AsyncImage` 经 `SingletonImageLoader` 拿到的**是同一个实例**，
 * 配置（crossfade / Decoder / `CoverInterceptor`）一字不差——与 M1-3p 在
 * `AppContainerBackground` 上的处理同源。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable (HazeState) -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    contentColor: Color = contentColorFor(MiuixTheme.colorScheme.surface),
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    alwaysDrawBehindBars: Boolean = false,
    disableHazeSource: Boolean = false,
    content: @Composable (PaddingValues) -> Unit
) {
    val isDark = LegadoTheme.isDark
    val configuration = LocalAppUiConfiguration.current
    val themeSettings = configuration.theme
    val hasImageBg = themeSettings.hasBackgroundImage(isDark)
    val hazeState = remember { HazeState() }
    val liquidGlassEnabled = configuration.theme.topBarButtonStyle == "liquid"
    val composeEngine = LegadoTheme.composeEngine
    val contentDrawsBehindBars =
        alwaysDrawBehindBars || themeSettings.enableBlur || themeSettings.enableProgressiveBlur

    val containerColor = if (hasImageBg) {
        Color.Transparent
    } else {
        LegadoTheme.colorScheme.background
    }

    val miuixContainerColor = if (hasImageBg) {
        Color.Transparent
    } else {
        MiuixTheme.colorScheme.surface
    }
    val topBarBackdropBaseColor = LegadoTheme.colorScheme.background
    val topBarBackgroundBackdrop = rememberLayerBackdrop {
        drawRect(topBarBackdropBaseColor)
        drawContent()
    }
    val topBarContentBackdrop = rememberLayerBackdrop { drawContent() }
    val topBarBackdrop = rememberCombinedBackdrop(
        topBarBackgroundBackdrop,
        topBarContentBackdrop
    )

    CompositionLocalProvider(
        LocalHazeState provides if (themeSettings.enableBlur) hazeState else null,
        LocalTopBarBackdrop provides if (liquidGlassEnabled) topBarBackdrop else null,
    ) {
        when {
            ThemeResolver.isMiuixEngine(composeEngine) -> {
                val miuixFabPosition = when (floatingActionButtonPosition) {
                    FabPosition.End -> MiuixFabPosition.End
                    FabPosition.Center -> MiuixFabPosition.Center
                    else -> MiuixFabPosition.End
                }
                Box(modifier = modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (liquidGlassEnabled) {
                                    Modifier.layerBackdrop(topBarBackgroundBackdrop)
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        BackgroundImageContent(isDark = isDark, hazeState = hazeState)
                    }
                    MiuixScaffold(
                        modifier = Modifier.fillMaxSize(),
                        topBar = {
                            topBar(hazeState)
                        },
                        bottomBar = bottomBar,
                        snackbarHost = snackbarHost,
                        floatingActionButton = floatingActionButton,
                        floatingActionButtonPosition = miuixFabPosition,
                        containerColor = miuixContainerColor,
                        contentWindowInsets = contentWindowInsets
                    ) { paddingValues ->
                        val scaffoldPadding = if (configuration.appShell.useFloatingBottomBar) {
                            PaddingValues(top = paddingValues.calculateTopPadding())
                        } else {
                            paddingValues
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (liquidGlassEnabled) {
                                        Modifier.layerBackdrop(topBarContentBackdrop)
                                    } else {
                                        Modifier
                                    }
                                )
                                .then(
                                    if (!disableHazeSource) Modifier.responsiveHazeSource(hazeState)
                                    else Modifier
                                )
                                .then(
                                    if (contentDrawsBehindBars) Modifier
                                    else Modifier
                                        .padding(scaffoldPadding)
                                        .consumeWindowInsets(scaffoldPadding)
                                )
                        ) {
                            content(
                                if (contentDrawsBehindBars) scaffoldPadding
                                else PaddingValues(0.dp)
                            )
                        }
                    }
                }
            }

            else -> {
                Box(modifier = modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (liquidGlassEnabled) {
                                    Modifier.layerBackdrop(topBarBackgroundBackdrop)
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        BackgroundImageContent(isDark = isDark, hazeState = hazeState)
                    }
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        topBar = {
                            topBar(hazeState)
                        },
                        bottomBar = bottomBar,
                        snackbarHost = snackbarHost,
                        floatingActionButton = floatingActionButton,
                        floatingActionButtonPosition = floatingActionButtonPosition,
                        containerColor = containerColor,
                        contentColor = contentColor,
                        contentWindowInsets = contentWindowInsets
                    ) { paddingValues ->
                        val scaffoldPadding = if (configuration.appShell.useFloatingBottomBar) {
                            PaddingValues(top = paddingValues.calculateTopPadding())
                        } else {
                            paddingValues
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (liquidGlassEnabled) {
                                        Modifier.layerBackdrop(topBarContentBackdrop)
                                    } else {
                                        Modifier
                                    }
                                )
                                .then(
                                    if (!disableHazeSource) Modifier.responsiveHazeSource(hazeState)
                                    else Modifier
                                )
                                .then(
                                    if (contentDrawsBehindBars) Modifier
                                    else Modifier
                                        .padding(scaffoldPadding)
                                        .consumeWindowInsets(scaffoldPadding)
                                )
                        ) {
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
    hazeState: HazeState
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
        if (themeSettings.enableBlur) {
            AsyncImage(
                model = bgImagePath,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState),
                contentScale = ContentScale.Crop
            )
        } else {
            AsyncImage(
                model = bgImagePath,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(blur.dp),
                contentScale = ContentScale.Crop
            )
        }
    }
}
