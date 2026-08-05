package io.legado.app.ui.book.read

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Toc
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.chrisbanes.haze.HazeState
import io.legado.app.R
import io.legado.app.constant.ReadMenuBlurMode
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.menuItem.MenuItemIcon
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.reader.ReaderMenuEffect
import io.legado.app.ui.widget.components.reader.ReaderMenuPlacement
import io.legado.app.ui.widget.components.reader.ReaderMenuTintStyle
import io.legado.app.ui.widget.components.reader.ReaderMenuVisualState
import io.legado.app.ui.widget.components.reader.readerMenuHazeEffect
import io.legado.app.ui.widget.components.reader.readerMenuLiquidGlassAvailable
import io.legado.app.ui.widget.components.reader.readerMenuSurfaceBrush
import io.legado.app.ui.widget.components.settingItem.LocalSliderDragState
import io.legado.app.ui.widget.components.text.AppText
import kotlin.math.roundToInt

@Composable
internal fun MenuTitleBar(
    state: ReadBookUiState,
    colors: ReadMenuColors,
    onIntent: (ReadBookIntent) -> Unit,
    backdrop: Backdrop?,
    hazeState: HazeState?,
    titleBarMode: String,
) {

    val topBarBorderWidth = state.menuConfig.readMenuBorderWidth
    val topBarBorderColor = readMenuBorderColor(state.menuConfig)
    val isSliderDragging = LocalSliderDragState.current?.isDragging == true
    val topBarSurfaceAlpha = state.menuConfig.readMenuBlurAlpha.coerceIn(0, 100)
    val dragTopBarAlpha = topBarSurfaceAlpha.coerceAtMost(30)
    val animatedTopBarAlpha by animateFloatAsState(
        targetValue = if (isSliderDragging) dragTopBarAlpha.toFloat() else topBarSurfaceAlpha.toFloat(),
        animationSpec = tween(durationMillis = 200),
        label = "ReadMenuTopBarAlpha",
    )
    val topBarAlpha = animatedTopBarAlpha / 100f
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
    val titleTextColor = readMenuTextColor(state.menuConfig)
    val labelStyle = LegadoTheme.typography.labelSmallEmphasized.copy(
        shadow = menuTextShadow
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
                        surfaceAlpha = animatedTopBarAlpha.roundToInt(),
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
        val useTitleCapsule = state.menuConfig.readMenuTopBarTitleCapsule
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
                            shadow = menuTextShadow
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

                    MenuTitleGlassButton(
                        onClick = {
                            onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.MoreActions))
                        },
                        icon = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.more_actions),
                        state = state,
                        colors = colors,
                        backdrop = backdrop,
                    )
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
                    shadow = menuTextShadow
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
internal fun ReadMenuGlassIconButton(
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
internal fun ReadMenuGlassButtonSurface(
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
    val containerColor = readMenuIconButtonContainerColor(
        selected = selected,
        iconStyle = iconStyle,
    )
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
                        Modifier.semantics { this.contentDescription = contentDescription }
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
                        indication = if (glassEnabled) null else LocalIndication.current,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { onIntent(ReadBookIntent.OpenBookInfo) },
                        onLongClick = { onIntent(ReadBookIntent.OpenChapterUrl) },
                    )
                } else {
                    Modifier.clickable(
                        indication = if (glassEnabled) null else LocalIndication.current,
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
private fun MergedGlassIconButton(
    icon: ImageVector,
    tint: Color,
    contentDescription: String?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        role = Role.Button,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        role = Role.Button,
                        onClick = onClick,
                    )
                }
            )
            .semantics { contentDescription?.let { this.contentDescription = it } },
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
private fun MergedGlassDivider(tint: Color) {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(20.dp)
            .background(tint.copy(alpha = 0.15f))
            .clearAndSetSemantics { }
    )
}

@Composable
private fun ChangeSourceMenuItems(
    dismiss: () -> Unit,
    onBookChange: () -> Unit,
    onChapterChange: () -> Unit,
) {
    RoundDropdownMenuItem(
        text = stringResource(R.string.change_origin),
        onClick = { dismiss(); onBookChange() },
    )
    RoundDropdownMenuItem(
        text = stringResource(R.string.chapter_change_source),
        onClick = { dismiss(); onChapterChange() },
    )
}

@Composable
private fun RefreshMenuItems(
    dismiss: () -> Unit,
    onRefreshDur: () -> Unit,
    onRefreshAfter: () -> Unit,
) {
    RoundDropdownMenuItem(
        text = stringResource(R.string.menu_refresh_dur),
        onClick = { dismiss(); onRefreshDur() },
    )
    RoundDropdownMenuItem(
        text = stringResource(R.string.menu_refresh_after),
        onClick = { dismiss(); onRefreshAfter() },
    )
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
                    MergedGlassIconButton(
                        icon = Icons.Default.Extension,
                        tint = tint,
                        contentDescription = stringResource(R.string.custom_button),
                        onClick = { onIntent(ReadBookIntent.SourceCustomButton(false)) },
                        onLongClick = { onIntent(ReadBookIntent.SourceCustomButton(true)) },
                    )
                    MergedGlassDivider(tint)
                }

                MergedGlassIconButton(
                    icon = Icons.Default.SwapHoriz,
                    tint = tint,
                    contentDescription = stringResource(R.string.change_origin),
                    onClick = { onIntent(ReadBookIntent.MenuChangeSource) },
                    onLongClick = { sourceExpanded = true },
                )
                MergedGlassDivider(tint)

                // Refresh
                MergedGlassIconButton(
                    icon = Icons.Default.Refresh,
                    tint = tint,
                    contentDescription = stringResource(R.string.menu_refresh_dur),
                    onClick = { onIntent(ReadBookIntent.MenuRefreshDur) },
                    onLongClick = { refreshExpanded = true },
                )
                MergedGlassDivider(tint)

                // Download
                MergedGlassIconButton(
                    icon = Icons.Default.CloudDownload,
                    tint = tint,
                    contentDescription = stringResource(R.string.offline_cache),
                    onClick = { onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.Download)) },
                )
                MergedGlassDivider(tint)
            } else if (state.isLocalBook && !compact) {
                // TXT directory rule
                if (state.isLocalTxt) {
                    MergedGlassIconButton(
                        icon = Icons.Default.Toc,
                        tint = tint,
                        contentDescription = stringResource(R.string.txt_toc_rule),
                        onClick = { onIntent(ReadBookIntent.MenuTocRegex) },
                    )
                    MergedGlassDivider(tint)
                }

                // Text encoding
                MergedGlassIconButton(
                    icon = Icons.Default.Translate,
                    tint = tint,
                    contentDescription = stringResource(R.string.set_charset),
                    onClick = { onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.Charset)) },
                )
                MergedGlassDivider(tint)
            }

            // MoreVert - overflow menu
            MergedGlassIconButton(
                icon = AppIcons.MoreVert,
                tint = tint,
                contentDescription = stringResource(R.string.more_actions),
                onClick = { onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.MoreActions)) },
            )
        }

        // Dropdown menus
        if (!state.isLocalBook) {
            RoundDropdownMenu(
                expanded = sourceExpanded,
                onDismissRequest = { sourceExpanded = false },
            ) { dismiss ->
                ChangeSourceMenuItems(
                    dismiss = dismiss,
                    onBookChange = { onIntent(ReadBookIntent.MenuBookChangeSource) },
                    onChapterChange = { onIntent(ReadBookIntent.MenuChapterChangeSource) },
                )
            }

            RoundDropdownMenu(
                expanded = refreshExpanded,
                onDismissRequest = { refreshExpanded = false },
            ) { dismiss ->
                RefreshMenuItems(
                    dismiss = dismiss,
                    onRefreshDur = { onIntent(ReadBookIntent.MenuRefreshDur) },
                    onRefreshAfter = { onIntent(ReadBookIntent.MenuRefreshAfter) },
                )
            }
        }

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
            ChangeSourceMenuItems(
                dismiss = dismiss,
                onBookChange = { onIntent(ReadBookIntent.MenuBookChangeSource) },
                onChapterChange = { onIntent(ReadBookIntent.MenuChapterChangeSource) },
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
            onClick = { onIntent(ReadBookIntent.MenuRefreshDur) },
            onLongClick = { expanded = true },
            icon = Icons.Default.Refresh,
            contentDescription = stringResource(R.string.menu_refresh_dur),
            state = state,
            colors = colors,
            backdrop = backdrop,
        )

        RoundDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) { dismiss ->
            RefreshMenuItems(
                dismiss = dismiss,
                onRefreshDur = { onIntent(ReadBookIntent.MenuRefreshDur) },
                onRefreshAfter = { onIntent(ReadBookIntent.MenuRefreshAfter) },
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

private fun readMenuTopBarButtonLiquidGlassEnabled(
    backdrop: Backdrop?,
    menuConfig: ReadMenuConfig,
): Boolean {
    return menuConfig.readMenuTopBarLiquidGlassButtons &&
            readerMenuLiquidGlassAvailable(backdrop)
}

private fun readMenuTopBarHazeEnabled(
    hazeState: HazeState?,
    menuConfig: ReadMenuConfig,
): Boolean {
    return hazeState != null && menuConfig.readMenuTopBarBlurMode == ReadMenuBlurMode.Haze
}

@Composable
private fun readMenuIconButtonContainerColor(selected: Boolean, iconStyle: Int): Color {
    return when {
        selected -> LegadoTheme.colorScheme.secondaryContainer
        iconStyle == 1 -> LegadoTheme.colorScheme.surfaceContainerLow
        else -> Color.Transparent
    }
}
