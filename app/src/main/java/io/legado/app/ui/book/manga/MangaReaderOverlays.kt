package io.legado.app.ui.book.manga

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.chrisbanes.haze.HazeState
import io.legado.app.R
import io.legado.app.ui.book.read.ReadMenuSlider
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ProvideThemeOverride
import io.legado.app.ui.theme.ThemeColorSpec
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.theme.buildThemeOverrideState
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.reader.ReaderMenuAction
import io.legado.app.ui.widget.components.reader.ReaderMenuAnimatedBottom
import io.legado.app.ui.widget.components.reader.ReaderMenuAnimatedTop
import io.legado.app.ui.widget.components.reader.ReaderMenuDismissLayer
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

private val MangaMenuSurfaceColor: Color
    @androidx.compose.runtime.Composable
    @androidx.compose.runtime.ReadOnlyComposable
    get() = LegadoTheme.colorScheme.surfaceContainerHigh
private val MangaMenuButtonColor: Color
    @androidx.compose.runtime.Composable
    @androidx.compose.runtime.ReadOnlyComposable
    get() = LegadoTheme.colorScheme.surfaceContainerLow

// 保留液态玻璃的模糊取样，同时用较高表面覆盖避免高对比度漫画页清晰穿透。
private const val MangaMenuGlassAlpha = 0.7f
private val MangaFooterContentColor = Color.White
private val MangaFooterTextShadow = Shadow(
    color = Color.Black.copy(alpha = 0.78f),
    offset = Offset(1.5f, 1.5f),
    blurRadius = 3f,
)

@Composable
internal fun BoxScope.MangaFooter(state: MangaReaderUiState) {
    val settings = state.settings
    if (settings.hideFooter) return
    val alignment = when (settings.footerAlignment) {
        1 -> Alignment.BottomCenter
        2 -> Alignment.BottomEnd
        else -> Alignment.BottomStart
    }
    val page = state.pages.getOrNull(state.currentItemIndex) as? MangaReaderItemUi.Page ?: return
    val progress = if (page.chapterCount <= 0 || page.pageCount <= 0) 0.0 else {
        (page.chapterIndex.toDouble() + (page.pageIndex + 1.0) / page.pageCount) / page.chapterCount
    }
    val pageLabel = stringResource(R.string.manga_reader_page_label)
    val chapterLabel = stringResource(R.string.manga_reader_chapter_label)
    val progressLabel = stringResource(R.string.manga_reader_progress_label)
    val text = buildString {
        if (!settings.hideChapterName) append(page.chapterName).append(' ')
        if (!settings.hidePageNumber) {
            if (!settings.hidePageNumberLabel) append(pageLabel).append(' ')
            append("${page.pageIndex + 1}/${page.pageCount} ")
        }
        if (!settings.hideChapter) {
            if (!settings.hideChapterLabel) append(chapterLabel).append(' ')
            append("${page.chapterIndex + 1}/${page.chapterCount} ")
        }
        if (!settings.hideProgress) {
            if (!settings.hideProgressLabel) append(progressLabel).append(' ')
            append("%.1f%%".format((progress * 100).coerceAtMost(100.0)))
        }
    }.trim()
    AnimatedText(
        text = text,
        color = MangaFooterContentColor,
        style = LegadoTheme.typography.labelSmall.copy(shadow = MangaFooterTextShadow),
        modifier = Modifier
            .align(alignment)
            .navigationBarsPadding()
            .padding(all = 16.dp),
    )
}

@Composable
internal fun BoxScope.MangaReaderMenu(
    state: MangaReaderUiState,
    onIntent: (MangaReaderIntent) -> Unit,
    backdrop: Backdrop? = null,
    hazeState: HazeState? = null,
    menuSeedColor: Color? = null,
) {
    val readingPageDescription = stringResource(R.string.manga_reader_page_semantics)
    val isDark = LegadoTheme.isDark
    val menuTheme = remember(menuSeedColor, state.settings.menuPaletteStyle, isDark) {
        menuSeedColor?.let { seedColor ->
            buildThemeOverrideState(
                seedColor = seedColor,
                isDark = isDark,
                paletteStyle = ThemeResolver.resolvePaletteStyle(state.settings.menuPaletteStyle),
                colorSpec = ThemeColorSpec.SPEC_2021,
                usePureBlack = false,
            )
        }
    }
    ProvideThemeOverride(menuTheme) {
        MangaReaderMenuContent(state, onIntent, backdrop, hazeState, readingPageDescription)
    }
}

@Composable
private fun BoxScope.MangaReaderMenuContent(
    state: MangaReaderUiState,
    onIntent: (MangaReaderIntent) -> Unit,
    backdrop: Backdrop?,
    hazeState: HazeState?,
    readingPageDescription: String,
) {
    ReaderMenuDismissLayer(
        visible = state.menuVisible,
        onDismiss = { onIntent(MangaReaderIntent.HideMenu) },
    )
    ReaderMenuAnimatedTop(
        visible = state.menuVisible,
    ) {
        MangaMenuTopBar(state, onIntent, backdrop)
    }
    ReaderMenuAnimatedBottom(
        visible = state.menuVisible,
    ) {
        MangaMenuBottomBar(state, onIntent, backdrop, readingPageDescription, hazeState)
    }
}

// ========== Top Bar ==========

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MangaMenuTopBar(
    state: MangaReaderUiState,
    onIntent: (MangaReaderIntent) -> Unit,
    backdrop: Backdrop?,
) {
    val topBarGlass = state.settings.menuTopBarLiquidGlass && readerMenuLiquidGlassAvailable(backdrop)
    val compact = state.settings.menuTopBarCompact
    // 顶栏始终透明无表面背景，只显示胶囊与按钮（悬浮于漫画内容上）
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top
                )
            )
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MangaMenuIconButton(
                icon = AppIcons.Back,
                description = stringResource(R.string.back),
                glassEnabled = topBarGlass,
                backdrop = backdrop,
                onClick = { onIntent(MangaReaderIntent.BackPressed) },
            )
            MangaTitleCapsule(state, onIntent, glassEnabled = topBarGlass, backdrop = backdrop)
            if (!compact) {
                MangaMenuIconButton(
                    Icons.Filled.SwapHoriz,
                    stringResource(R.string.change_origin),
                    glassEnabled = topBarGlass,
                    backdrop = backdrop,
                ) { onIntent(MangaReaderIntent.ChangeSource) }
            }
            if (!compact) {
                MangaMenuIconButton(
                    Icons.Filled.Refresh,
                    stringResource(R.string.refresh),
                    glassEnabled = topBarGlass,
                    backdrop = backdrop,
                ) { onIntent(MangaReaderIntent.RefreshChapter) }
            }
            MangaMenuIconButton(
                Icons.Filled.MoreVert,
                stringResource(R.string.more_actions),
                glassEnabled = topBarGlass,
                backdrop = backdrop,
            ) {
                onIntent(MangaReaderIntent.OpenSourceActions)
            }
        }
    }
}

@Composable
private fun RowScope.MangaTitleCapsule(
    state: MangaReaderUiState,
    onIntent: (MangaReaderIntent) -> Unit,
    glassEnabled: Boolean = false,
    backdrop: Backdrop? = null,
) {
    val pillShape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .weight(1f)
            .height(40.dp)
            .then(
                if (glassEnabled) {
                    Modifier.readerMenuLiquidGlass(
                        backdrop = backdrop,
                        shape = pillShape,
                        surfaceBrush = readerMenuSurfaceBrush(
                            style = ReaderMenuTintStyle.Fill,
                            placement = ReaderMenuPlacement.Top,
                            color = MangaMenuSurfaceColor,
                            alpha = MangaMenuGlassAlpha,
                        ),
                        blurRadius = 32.dp,
                        lensRadius = 24.dp,
                        useLens = false,
                    )
                } else {
                    Modifier
                        .clip(pillShape)
                        .background(MangaMenuButtonColor, pillShape)
                }
            )
            .clickable(
                indication = if (glassEnabled) null else LocalIndication.current,
                interactionSource = remember { MutableInteractionSource() },
                onClick = { onIntent(MangaReaderIntent.OpenBookInfo) },
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column {
            Text(
                state.bookName,
                style = LegadoTheme.typography.labelMediumEmphasized,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.chapterName.isNotBlank()) {
                Text(
                    state.chapterName,
                    style = LegadoTheme.typography.labelSmall,
                    color = LegadoTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ========== Bottom Bar ==========

@Composable
private fun MangaMenuBottomBar(
    state: MangaReaderUiState,
    onIntent: (MangaReaderIntent) -> Unit,
    backdrop: Backdrop?,
    readingPageDescription: String,
    hazeState: HazeState? = null,
) {
    // 悬浮底栏玻璃化表面；贴边底栏只玻璃化操作控件，和文章阅读菜单保持一致。
    val glass = state.settings.menuBottomBarLiquidGlass && readerMenuLiquidGlassAvailable(backdrop)
    val floating = state.settings.menuBottomBarFloating
    val configOpen = state.settingsCategory != null
    // 文章阅读菜单在展开时禁用渐进样式，并以实心毛玻璃承载设置内容。
    val blur = (state.settings.menuBottomBarBlur || (!floating && glass && configOpen)) &&
            hazeState != null
    val glassOnBar = floating && glass
    val glassOnControls = !floating && glass
    // 悬浮展开用实心（不透明），非悬浮展开降低表面不透明度露出背后漫画内容
    val surfaceAlpha = when {
        floating && configOpen -> 85
        configOpen -> 55
        else -> 75
    }
    val shape = if (floating) {
        RoundedCornerShape(32.dp)
    } else {
        RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    }
    val bottomBarVisualState = ReaderMenuVisualState(
        effect = ReaderMenuEffect.Haze,
        tintStyle = if (floating || configOpen) {
            ReaderMenuTintStyle.Fill
        } else {
            ReaderMenuTintStyle.Gradient
        },
        styleEnabled = !configOpen,
        tintAllowed = true,
        tintFill = true,
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (floating) {
                    Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                } else {
                    Modifier
                }
            )
            .then(
                when {
                    // 液态玻璃只用于悬浮底栏表面
                    glassOnBar -> Modifier.readerMenuLiquidGlass(
                        backdrop = backdrop,
                        shape = shape,
                        surfaceBrush = readerMenuSurfaceBrush(
                            style = ReaderMenuTintStyle.Fill,
                            placement = ReaderMenuPlacement.Bottom,
                            color = MangaMenuSurfaceColor,
                            alpha = MangaMenuGlassAlpha,
                        ),
                        blurRadius = 24.dp,
                        lensRadius = 24.dp,
                        useLens = true,
                    )

                    // 模糊开关开启时用毛玻璃，非悬浮时渐进渐变；先按形状裁剪保留圆角
                    blur -> Modifier
                        .clip(shape)
                        .readerMenuHazeEffect(
                            state = hazeState,
                            visualState = bottomBarVisualState,
                            placement = ReaderMenuPlacement.Bottom,
                            baseColor = MangaMenuSurfaceColor,
                            tintColor = null,
                            blurRadius = 32,
                            surfaceAlpha = surfaceAlpha,
                        )

                    // 非悬浮底栏使用自下而上的渐变背景
                    !floating -> Modifier
                        .clip(shape)
                        .background(
                            readerMenuSurfaceBrush(
                                style = ReaderMenuTintStyle.Gradient,
                                placement = ReaderMenuPlacement.Bottom,
                                color = MangaMenuSurfaceColor,
                                alpha = surfaceAlpha / 100f,
                            )
                        )

                    else -> Modifier
                }
            ),
        shape = shape,
        color = when {
            glassOnBar || blur -> Color.Transparent
            floating -> MangaMenuSurfaceColor
            else -> Color.Transparent
        },
        border = if (!glassOnBar && !blur && floating) {
            BorderStroke(1.dp, LegadoTheme.colorScheme.outlineVariant)
        } else {
            null
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (!floating) Modifier.navigationBarsPadding() else Modifier),
        ) {
            AnimatedContent(
                targetState = state.settingsCategory != null,
                transitionSpec = {
                    (slideInVertically { it / 4 } + fadeIn())
                        .togetherWith(slideOutVertically { -it / 4 } + fadeOut())
                        .using(SizeTransform(clip = true))
                },
                label = "mangaMenuExpand",
            ) { isSettings ->
                if (isSettings) {
                    MangaSettingsPanel(state, onIntent)
                } else {
                    Column(Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)) {
                        if (state.pageCount > 1) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                MangaMenuIconButton(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    stringResource(R.string.previous_chapter),
                                    glassEnabled = glassOnControls,
                                    backdrop = backdrop,
                                ) {
                                    onIntent(MangaReaderIntent.PreviousChapter)
                                }
                                ReadMenuSlider(
                                    value = state.currentPage.toFloat()
                                        .coerceIn(0f, (state.pageCount - 1).toFloat()),
                                    onValueChange = { onIntent(MangaReaderIntent.SeekToPage(it.toInt())) },
                                    valueRange = 0f..(state.pageCount - 1).toFloat(),
                                    steps = (state.pageCount - 2).coerceAtLeast(0),
                                    accessibilityLabel = readingPageDescription,
                                    modifier = Modifier.weight(1f),
                                    backdrop = backdrop,
                                    glassThumbEnabled = glassOnControls,
                                )
                                MangaMenuIconButton(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    stringResource(R.string.next_chapter),
                                    glassEnabled = glassOnControls,
                                    backdrop = backdrop,
                                ) {
                                    onIntent(MangaReaderIntent.NextChapter)
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        MangaMenuToolRow(
                            buildList {
                                add(
                                ReaderMenuAction(
                                    Icons.AutoMirrored.Filled.MenuBook,
                                    stringResource(R.string.chapter_list)
                                ) {
                                    onIntent(MangaReaderIntent.OpenCatalog)
                                })
                                if (state.cacheAvailable) add(
                                    ReaderMenuAction(
                                        Icons.Filled.CloudDownload,
                                        stringResource(R.string.offline_cache),
                                    ) { onIntent(MangaReaderIntent.OpenCacheActions) }
                                )
                                add(
                                ReaderMenuAction(
                                    icon = Icons.Filled.AutoMode,
                                    description = if (state.autoReadEnabled) {
                                        stringResource(R.string.stop)
                                    } else {
                                        stringResource(R.string.manga_reader_auto_short)
                                    },
                                    onClick = { onIntent(MangaReaderIntent.ToggleAutoRead) },
                                    onLongClick = {
                                        onIntent(
                                            MangaReaderIntent.OpenSettings(
                                                MangaReaderSettingsCategory.AUTO_READ
                                            )
                                        )
                                    },
                                )
                                )
                                add(
                                ReaderMenuAction(
                                    Icons.Filled.Tune,
                                    stringResource(R.string.manga_reader_page_settings)
                                ) {
                                    onIntent(
                                        MangaReaderIntent.OpenSettings(
                                            MangaReaderSettingsCategory.READER
                                        )
                                    )
                                })
                                add(
                                ReaderMenuAction(
                                    Icons.Filled.FilterAlt,
                                    stringResource(R.string.manga_reader_filter_short)
                                ) {
                                    onIntent(
                                        MangaReaderIntent.OpenSettings(
                                            MangaReaderSettingsCategory.FILTER
                                        )
                                    )
                                })
                                add(
                                ReaderMenuAction(
                                    Icons.Filled.TouchApp,
                                    stringResource(R.string.manga_reader_click_area_short)
                                ) {
                                    onIntent(
                                        MangaReaderIntent.OpenSettings(
                                            MangaReaderSettingsCategory.CLICK_ACTIONS
                                        )
                                    )
                                })
                            },
                            glassEnabled = glassOnControls,
                            backdrop = backdrop,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MangaMenuToolRow(
    actions: List<ReaderMenuAction>,
    glassEnabled: Boolean,
    backdrop: Backdrop?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        actions.forEach { action ->
            MangaMenuIconButton(
                icon = action.icon,
                description = action.description,
                glassEnabled = glassEnabled,
                backdrop = backdrop,
                onClick = action.onClick,
                onLongClick = action.onLongClick,
            )
        }
        repeat((5 - actions.size).coerceAtLeast(0)) {
            Spacer(Modifier.size(40.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MangaMenuIconButton(
    icon: ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    glassEnabled: Boolean = false,
    backdrop: Backdrop? = null,
    onClick: () -> Unit,
) {
    val tint = LegadoTheme.colorScheme.onSurfaceVariant
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(40.dp)
            .then(
                if (glassEnabled) {
                    Modifier.readerMenuLiquidGlass(
                        backdrop = backdrop,
                        shape = CircleShape,
                        surfaceBrush = readerMenuSurfaceBrush(
                            style = ReaderMenuTintStyle.Fill,
                            placement = ReaderMenuPlacement.Top,
                            color = MangaMenuSurfaceColor,
                            alpha = MangaMenuGlassAlpha,
                        ),
                        blurRadius = 32.dp,
                        lensRadius = 24.dp,
                        useLens = true,
                        interactive = true,
                    )
                } else {
                    Modifier
                        .clip(CircleShape)
                        .background(MangaMenuButtonColor, CircleShape)
                }
            )
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        indication = if (glassEnabled) null else LocalIndication.current,
                        interactionSource = interactionSource,
                        role = Role.Button,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier.clickable(
                        indication = if (glassEnabled) null else LocalIndication.current,
                        interactionSource = interactionSource,
                        role = Role.Button,
                        onClick = onClick,
                    )
                }
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
internal fun BoxScope.ReaderStatusOverlay(
    state: MangaReaderUiState,
    onIntent: (MangaReaderIntent) -> Unit,
) {
    if (state.isLoading) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = LegadoTheme.colorScheme.surface,
        ) {
            Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }
    state.errorMessage?.let { message ->
        val errorText = when (message) {
            is MangaReaderText.Dynamic -> message.value
            is MangaReaderText.Resource -> stringResource(
                message.resId,
                *message.args.toTypedArray(),
            )
        }
        Surface(modifier = Modifier.fillMaxSize(), color = LegadoTheme.colorScheme.surface) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                AppText(errorText, modifier = Modifier.padding(24.dp))
                Button(onClick = { onIntent(MangaReaderIntent.Retry) }) { Text(stringResource(R.string.retry)) }
            }
        }
    }
}
