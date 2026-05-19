@file:OptIn(ExperimentalScrollBarApi::class)

package io.legado.app.ui.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.constant.AppConst.appInfo
import io.legado.app.ui.util.BlurredBar
import io.legado.app.ui.util.LocalAppState
import io.legado.app.ui.util.LocalIsWideScreen
import io.legado.app.ui.util.LocalNavigator
import io.legado.app.ui.util.MiuixNavigator
import io.legado.app.ui.util.pageContentPadding
import io.legado.app.ui.util.pageScrollModifiers
import io.legado.app.ui.util.rememberBlurBackdrop
import io.legado.app.ui.util.shouldShowSplitPane
import io.legado.app.ui.widget.components.effect.BgEffectBackground
import io.legado.app.ui.widget.components.effect.ColorBlendToken
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.graphics.BlendMode as ComposeBlendMode

@Composable
fun MiuixAboutScreen(
    state: AboutUiState,
    onIntent: (AboutIntent) -> Unit,
    onBack: () -> Unit = {},
    versionName: String = appInfo.versionName,
) {
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()
    val isWideScreen = shouldShowSplitPane()
    val licenseTitle = stringResource(R.string.about_license_title)

    val scrollProgress by remember {
        derivedStateOf {
            when {
                lazyListState.firstVisibleItemIndex > 0 -> 1f

                else -> {
                    val spacer =
                        lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "logoSpacer" }
                    if (spacer != null && spacer.size > 0) {
                        (lazyListState.firstVisibleItemScrollOffset.toFloat() / spacer.size).coerceIn(
                            0f,
                            1f
                        )
                    } else {
                        0f
                    }
                }
            }
        }
    }

    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null && scrollProgress == 1f
    val barColor = if (blurActive) {
        Color.Transparent
    } else {
        if (scrollProgress == 1f) MiuixTheme.colorScheme.surface else Color.Transparent
    }

    val navigator = remember(licenseTitle) {
        object : MiuixNavigator {
            override fun pop() {
                onBack()
            }

            override fun push(route: Any) {
                if (route == "License") {
                    onIntent(AboutIntent.ShowMdFile(licenseTitle, "LICENSE.md"))
                }
            }
        }
    }

    CompositionLocalProvider(
        LocalNavigator provides navigator,
        LocalIsWideScreen provides isWideScreen
    ) {
        Scaffold(
            topBar = {
                BlurredBar(backdrop, blurActive) {
                    SmallTopAppBar(
                        title = stringResource(R.string.about),
                        scrollBehavior = topAppBarScrollBehavior,
                        color = barColor,
                        titleColor = MiuixTheme.colorScheme.onSurface.copy(
                            alpha = ((scrollProgress - 0.35f) / 0.65f).coerceIn(0f, 1f),
                        ),
                        defaultWindowInsetsPadding = false,
                        navigationIcon = {
                            TopBarNavigationButton(onClick = onBack)
                        },
                    )
                }
            },
        ) { innerPadding ->
            Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
                AboutContent(
                    padding = innerPadding,
                    topAppBarScrollBehavior = topAppBarScrollBehavior,
                    lazyListState = lazyListState,
                    scrollProgress = scrollProgress,
                    versionName = versionName,
                    onIntent = onIntent,
                )
            }
        }
    }
}

@Composable
private fun AboutContent(
    padding: PaddingValues,
    topAppBarScrollBehavior: ScrollBehavior,
    lazyListState: LazyListState,
    scrollProgress: Float,
    versionName: String,
    onIntent: (AboutIntent) -> Unit,
) {
    val appState = LocalAppState.current
    val isWideScreen = LocalIsWideScreen.current
    val privacyPolicyTitle = stringResource(R.string.about_privacy_policy_title)
    val licenseTitle = stringResource(R.string.about_license_title)
    val disclaimerTitle = stringResource(R.string.about_disclaimer_title)

    val backdrop = rememberBlurBackdrop()
    var blurRadius by remember { mutableFloatStateOf(60f) }
    var noiseCoefficient by remember { mutableFloatStateOf(BlurDefaults.NoiseCoefficient) }
    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var saturation by remember { mutableFloatStateOf(1f) }

    val scrollPadding = pageContentPadding(
        padding,
        PaddingValues(0.dp), // outerPadding placeholder
        isWideScreen,
        extraStart = WindowInsets.displayCutout.asPaddingValues()
            .calculateLeftPadding(LayoutDirection.Ltr) + 16.dp,
        extraEnd = WindowInsets.displayCutout.asPaddingValues()
            .calculateRightPadding(LayoutDirection.Ltr) + 16.dp,
    )
    val logoPadding = pageContentPadding(
        padding,
        PaddingValues(0.dp),
        isWideScreen,
        extraTop = 40.dp,
        extraStart = WindowInsets.displayCutout.asPaddingValues()
            .calculateLeftPadding(LayoutDirection.Ltr) + 16.dp,
        extraEnd = WindowInsets.displayCutout.asPaddingValues()
            .calculateRightPadding(LayoutDirection.Ltr) + 16.dp,
    )

    val isInDark = isSystemInDarkTheme()
    val dynamicBackground = isRuntimeShaderSupported()

    val cardBlend =
        if (isInDark) ColorBlendToken.Overlay_Thin_Light else ColorBlendToken.Pured_Regular_Light
    val logoBlend = remember(isInDark) {
        if (isInDark) {
            listOf(
                BlendColorEntry(Color(0xe6a1a1a1), BlurBlendMode.ColorDodge),
                BlendColorEntry(Color(0x4de6e6e6), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xff1af500), BlurBlendMode.Lab),
            )
        } else {
            listOf(
                BlendColorEntry(Color(0xcc4a4a4a), BlurBlendMode.ColorBurn),
                BlendColorEntry(Color(0xff4f4f4f), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xff1af200), BlurBlendMode.Lab),
            )
        }
    }

    val density = LocalDensity.current
    var logoHeightDp by remember { mutableStateOf(300.dp) }

    val versionCodeProgress = ((scrollProgress - 0.05f) / 0.15f).coerceIn(0f, 1f)
    val projectNameProgress = ((scrollProgress - 0.20f) / 0.15f).coerceIn(0f, 1f)
    val iconProgress = ((scrollProgress - 0.35f) / 0.15f).coerceIn(0f, 1f)

    BgEffectBackground(
        dynamicBackground = dynamicBackground,
        isOs3Effect = true,
        isFullSize = true,
        modifier = Modifier.fillMaxSize(),
        bgModifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier,
        alpha = { 1f - scrollProgress },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = logoPadding.calculateTopPadding() + 52.dp,
                    start = logoPadding.calculateLeftPadding(LayoutDirection.Ltr),
                    end = logoPadding.calculateRightPadding(LayoutDirection.Ltr),
                )
                .onSizeChanged { size ->
                    with(density) { logoHeightDp = size.height.toDp() }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(88.dp)
                    .graphicsLayer {
                        clip = true
                        shape = RoundedCornerShape(24.dp)
                        alpha = 1 - iconProgress
                        scaleX = 1 - (iconProgress * 0.05f)
                        scaleY = 1 - (iconProgress * 0.05f)
                    }
                    .background(Color.White),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.scale(1.1f)
                )
            }
            Text(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 5.dp)
                    .graphicsLayer {
                        alpha = 1 - projectNameProgress
                        scaleX = 1 - (projectNameProgress * 0.05f)
                        scaleY = 1 - (projectNameProgress * 0.05f)
                    }
                    .then(
                        if (backdrop != null) {
                            Modifier
                                .textureBlur(
                                    backdrop = backdrop,
                                    shape = RoundedCornerShape(16.dp),
                                    blurRadius = 150f,
                                    noiseCoefficient = noiseCoefficient,
                                    colors = BlurColors(
                                        blendColors = logoBlend,
                                    ),
                                    contentBlendMode = ComposeBlendMode.DstIn,
                                )
                        } else {
                            Modifier
                        },
                    ),
                text = stringResource(R.string.app_name),
                color = MiuixTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 35.sp,
            )
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = 1 - versionCodeProgress
                        scaleX = 1 - (versionCodeProgress * 0.05f)
                        scaleY = 1 - (versionCodeProgress * 0.05f)
                    },
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                text = versionName,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }

        // Scrollable content
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .pageScrollModifiers(
                    appState.enableScrollEndHaptic,
                    appState.showTopAppBar,
                    topAppBarScrollBehavior,
                ),
            contentPadding = PaddingValues(
                top = scrollPadding.calculateTopPadding(),
                start = scrollPadding.calculateLeftPadding(LayoutDirection.Ltr),
                end = scrollPadding.calculateRightPadding(LayoutDirection.Ltr),
            ),
        ) {
            // Transparent spacer matching logo height
            item(key = "logoSpacer") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(
                            logoHeightDp + 52.dp + logoPadding.calculateTopPadding() - scrollPadding.calculateTopPadding() + 126.dp,
                        ),
                    contentAlignment = Alignment.TopCenter,
                    content = { },
                )
            }

            item(key = "about") {
                Column(
                    modifier = Modifier
                        .fillParentMaxHeight()
                        .padding(bottom = scrollPadding.calculateBottomPadding()),
                ) {
                    Card(
                        modifier = Modifier
                            .then(
                                if (backdrop != null) {
                                    Modifier
                                        .textureBlur(
                                            backdrop = backdrop,
                                            shape = RoundedCornerShape(16.dp),
                                            blurRadius = blurRadius,
                                            noiseCoefficient = noiseCoefficient,
                                            colors = BlurColors(
                                                blendColors = cardBlend,
                                                brightness = brightness,
                                                contrast = contrast,
                                                saturation = saturation,
                                            ),
                                        )
                                } else {
                                    Modifier
                                },
                            ),
                        colors = CardDefaults.defaultColors(
                            if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer,
                            Color.Transparent,
                        ),
                    ) {
                        ArrowPreference(
                            title = stringResource(R.string.check_update),
                            onClick = { onIntent(AboutIntent.CheckUpdate) },
                        )
                        ArrowPreference(
                            title = stringResource(R.string.contributors),
                            endActions = {
                                ValueText("GitHub")
                            },
                            onClick = { onIntent(AboutIntent.OpenUrl("https://github.com/HapeLee/legado-with-MD3")) },
                        )
                    }

                    Card(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .then(
                                if (backdrop != null) {
                                    Modifier
                                        .textureBlur(
                                            backdrop = backdrop,
                                            shape = RoundedCornerShape(16.dp),
                                            blurRadius = blurRadius,
                                            noiseCoefficient = noiseCoefficient,
                                            colors = BlurColors(
                                                blendColors = cardBlend,
                                                brightness = brightness,
                                                contrast = contrast,
                                                saturation = saturation,
                                            ),
                                        )
                                } else {
                                    Modifier
                                },
                            ),
                        colors = CardDefaults.defaultColors(
                            if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer,
                            Color.Transparent,
                        ),
                    ) {
                        ArrowPreference(
                            title = stringResource(R.string.privacy_policy),
                            onClick = {
                                onIntent(
                                    AboutIntent.ShowMdFile(
                                        privacyPolicyTitle,
                                        "privacyPolicy.md"
                                    )
                                )
                            },
                        )
                        ArrowPreference(
                            title = stringResource(R.string.license),
                            onClick = { onIntent(AboutIntent.ShowMdFile(licenseTitle, "LICENSE.md")) },
                        )
                        ArrowPreference(
                            title = stringResource(R.string.disclaimer),
                            onClick = {
                                onIntent(
                                    AboutIntent.ShowMdFile(
                                        disclaimerTitle,
                                        "disclaimer.md"
                                    )
                                )
                            },
                        )
                    }

                    Card(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .then(
                                if (backdrop != null) {
                                    Modifier
                                        .textureBlur(
                                            backdrop = backdrop,
                                            shape = RoundedCornerShape(16.dp),
                                            blurRadius = blurRadius,
                                            noiseCoefficient = noiseCoefficient,
                                            colors = BlurColors(
                                                blendColors = cardBlend,
                                                brightness = brightness,
                                                contrast = contrast,
                                                saturation = saturation,
                                            ),
                                        )
                                } else {
                                    Modifier
                                },
                            ),
                        colors = CardDefaults.defaultColors(
                            if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer,
                            Color.Transparent,
                        ),
                    ) {
                        ArrowPreference(
                            title = stringResource(R.string.crash_log),
                            onClick = { onIntent(AboutIntent.ShowCrashLogs) },
                        )
                        ArrowPreference(
                            title = stringResource(R.string.save_log),
                            onClick = { onIntent(AboutIntent.SaveLog) },
                        )
                        ArrowPreference(
                            title = stringResource(R.string.create_heap_dump),
                            onClick = { onIntent(AboutIntent.CreateHeapDump) },
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
        VerticalScrollBar(
            adapter = rememberScrollBarAdapter(lazyListState),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
            trackPadding = scrollPadding,
        )
    }
}

@Suppress("FunctionName")
@Composable
private fun ValueText(text: String) {
    Text(
        text = text,
        fontSize = MiuixTheme.textStyles.body2.fontSize,
        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
    )
}
