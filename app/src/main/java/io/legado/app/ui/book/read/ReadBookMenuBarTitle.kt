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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payment
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kyant.backdrop.Backdrop
import dev.chrisbanes.haze.HazeState
import io.legado.app.R
import io.legado.app.constant.ReadMenuBlurMode
import io.legado.app.data.entities.BookSource
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.icon.AppIcon
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
    eyeProtectionActive: Boolean,
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
    val useTitleCapsule = state.menuConfig.readMenuTopBarTitleCapsule

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
                if (topBarBorderWidth > 0 && !useTitleCapsule) {
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

                if (useTitleCapsule) {
                    // 胶囊自己就装了书名 + 章名，不再受「标题栏模式」的换行控制；
                    // 只有 mode "3"（不显示）才整体隐藏，否则默认 mode "1" 会让标题整个消失。
                    if (titleBarMode != "3") {
                        TitleCapsuleGlassLayout(
                            state = state,
                            colors = colors,
                            onIntent = onIntent,
                            backdrop = backdrop,
                            titleTextColor = titleTextColor,
                        )
                    }
                } else if (titleBarMode != "1" && titleBarMode != "3") {
                    AppText(
                        text = state.bookName,
                        modifier = Modifier
                            .weight(1f)
                            .combinedClickable(
                                onClick = { onIntent(ReadBookIntent.OpenBookInfo) },
                                onLongClick = { onIntent(ReadBookIntent.OpenBookInfoDirect) },
                            )
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

            // Right group: actions — 按钮由配置决定，见 ReadBookSheet.TitleBarActionConfig
            TitleBarActionButtons(
                state = state,
                colors = colors,
                onIntent = onIntent,
                backdrop = backdrop,
                eyeProtectionActive = eyeProtectionActive,
                merged = state.menuConfig.readMenuTopBarMergeButtons,
            )
        }

        // Book name on its own line (mode "1") — hidden when capsule is active
        if (titleBarMode == "1" && !useTitleCapsule) {
            AppText(
                text = state.bookName,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onIntent(ReadBookIntent.OpenBookInfo) },
                        onLongClick = { onIntent(ReadBookIntent.OpenBookInfoDirect) },
                    )
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
                        ReadMenuSourceDropdown(
                            expanded = sourceMenuExpanded,
                            onDismissRequest = { sourceMenuExpanded = false },
                            bookSource = state.bookSource,
                            payVisible = state.chapterPayable,
                            onLogin = { onIntent(ReadBookIntent.ShowLogin) },
                            onPay = { onIntent(ReadBookIntent.PayAction) },
                            onEdit = { onIntent(ReadBookIntent.OpenSourceEdit) },
                            onDisable = { onIntent(ReadBookIntent.DisableSource) },
                        )
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
    customIconPath: String? = null,
    selected: Boolean = false,
) {
    ReadMenuGlassButtonSurface(
        onClick = onClick,
        colors = colors,
        backdrop = backdrop,
        menuConfig = state.menuConfig,
        glassEnabled = readMenuTopBarButtonLiquidGlassEnabled(backdrop, state.menuConfig),
        iconStyle = state.menuConfig.titleBarIconStyle,
        modifier = modifier,
        onLongClick = onLongClick,
        selected = selected,
        menuBorderEnabled = state.menuConfig.readMenuBorderWidth > 0 &&
                state.menuConfig.readMenuTopBarTitleCapsule,
        contentDescription = contentDescription,
    ) { tint ->
        if (customIconPath.isNullOrBlank()) {
            AppIcon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
        } else {
            AsyncImage(
                model = customIconPath,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape),
            )
        }
    }
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
    menuBorderEnabled: Boolean = false,
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
        menuBorderEnabled = menuBorderEnabled,
        contentDescription = contentDescription,
    ) { tint ->
        AppIcon(
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
    menuBorderEnabled: Boolean = false,
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
        menuBorderEnabled -> BorderStroke(
            menuConfig.readMenuBorderWidth.dp,
            Color(readMenuBorderColor(menuConfig)),
        )
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
    val border = when {
        state.menuConfig.readMenuBorderWidth > 0 -> BorderStroke(
            state.menuConfig.readMenuBorderWidth.dp,
            Color(readMenuBorderColor(state.menuConfig)),
        )

        iconStyle == 2 -> BorderStroke(
            1.dp,
            LegadoTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        )

        else -> null
    }

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
                    Modifier
                        .clip(pillShape)
                        .background(containerColor, pillShape)
                }
            )
            .then(if (border != null) Modifier.border(border, pillShape) else Modifier)
            .then(
                Modifier.combinedClickable(
                    indication = if (glassEnabled) null else LocalIndication.current,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = { onIntent(ReadBookIntent.OpenBookInfo) },
                    onLongClick = { onIntent(ReadBookIntent.OpenBookInfoDirect) },
                )
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
                    // 章名独占一层点击：与胶囊整体（进书籍详情）区分开，
                    // 沿用非胶囊顶栏那套「点击开章节网页 / 长按切浏览器」。
                    // indication 置空，避免与外层胶囊的 ripple 叠加。
                    modifier = Modifier
                        .then(
                            if (!state.isLocalBook) {
                                Modifier.combinedClickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                    onClick = { onIntent(ReadBookIntent.OpenChapterUrl) },
                                    onLongClick = {
                                        onIntent(ReadBookIntent.ToggleReadUrlInBrowser)
                                    },
                                )
                            } else {
                                Modifier
                            }
                        ),
                    style = LegadoTheme.typography.labelSmall,
                    color = titleTextColor.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // 书源入口：胶囊把整行标题压成一行后，登录/付费/编辑/禁用书源
        // 在原「章名 + 书源」行里已经不可达，这里补一个小圆点入口兜底。
        // 圆点只有 8dp，热区撑到 24dp × 满高才点得中；不给 ripple，免得看起来像按钮。
        if (!state.isLocalBook && state.bookSource != null) {
            var sourceMenuExpanded by remember { mutableStateOf(false) }
            val bookSourceLabel = stringResource(R.string.book_source)
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(24.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { sourceMenuExpanded = true },
                    )
                    .semantics { contentDescription = bookSourceLabel },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(titleTextColor, CircleShape),
                )
                ReadMenuSourceDropdown(
                    expanded = sourceMenuExpanded,
                    onDismissRequest = { sourceMenuExpanded = false },
                    bookSource = state.bookSource,
                    payVisible = state.chapterPayable,
                    onLogin = { onIntent(ReadBookIntent.ShowLogin) },
                    onPay = { onIntent(ReadBookIntent.PayAction) },
                    onEdit = { onIntent(ReadBookIntent.OpenSourceEdit) },
                    onDisable = { onIntent(ReadBookIntent.DisableSource) },
                )
            }
        }
    }
}

/**
 * 书源操作菜单：登录 / 章节付费 / 编辑书源 / 禁用书源。
 *
 * 顶栏胶囊尾部、顶栏「章名 + 书源」行、书籍抽屉三处共用这一份，
 * 保证条目与显隐判定不会各写一遍后分叉。
 */
@Composable
internal fun ReadMenuSourceDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    bookSource: BookSource?,
    payVisible: Boolean,
    onLogin: () -> Unit,
    onPay: () -> Unit,
    onEdit: () -> Unit,
    onDisable: () -> Unit,
) {
    RoundDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
    ) {
        if (!bookSource?.loginUrl.isNullOrBlank()) {
            RoundDropdownMenuItem(
                leadingIcon = { MenuItemIcon(Icons.AutoMirrored.Filled.Login) },
                text = stringResource(R.string.login),
                onClick = {
                    onDismissRequest()
                    onLogin()
                },
            )
        }
        if (payVisible) {
            RoundDropdownMenuItem(
                leadingIcon = { MenuItemIcon(Icons.Default.Payment) },
                text = stringResource(R.string.chapter_pay),
                onClick = {
                    onDismissRequest()
                    onPay()
                },
            )
        }
        RoundDropdownMenuItem(
            leadingIcon = { MenuItemIcon(Icons.Default.Edit) },
            text = stringResource(R.string.edit_source),
            onClick = {
                onDismissRequest()
                onEdit()
            },
        )
        RoundDropdownMenuItem(
            leadingIcon = { MenuItemIcon(Icons.Default.Block) },
            text = stringResource(R.string.disable_source),
            onClick = {
                onDismissRequest()
                onDisable()
            },
        )
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
    selected: Boolean = false,
    customIconPath: String? = null,
) {
    val iconTint = if (selected) LegadoTheme.colorScheme.primary else tint
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
        if (customIconPath.isNullOrBlank()) {
            AppIcon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp),
            )
        } else {
            AsyncImage(
                model = customIconPath,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape),
            )
        }
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

/**
 * 「刷新章节」「换源」这类按钮的长按二级菜单。
 * 顶栏、底栏、浮动图标三处共用，保证同一个按钮在哪个位置长按行为都一致。
 */
@Composable
internal fun ReadMenuLongPressDropdown(
    menu: ReadMenuLongPressMenu?,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onIntent: (ReadBookIntent) -> Unit,
) {
    if (menu == null) return
    RoundDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
    ) { dismiss ->
        when (menu) {
            ReadMenuLongPressMenu.ChangeSource -> ChangeSourceMenuItems(
                dismiss = dismiss,
                onBookChange = { onIntent(ReadBookIntent.MenuBookChangeSource) },
                onChapterChange = { onIntent(ReadBookIntent.MenuChapterChangeSource) },
            )

            ReadMenuLongPressMenu.Refresh -> RefreshMenuItems(
                dismiss = dismiss,
                onRefreshDur = { onIntent(ReadBookIntent.MenuRefreshDur) },
                onRefreshAfter = { onIntent(ReadBookIntent.MenuRefreshAfter) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MenuTitleBarMergedGlassButton(
    state: ReadBookUiState,
    colors: ReadMenuColors,
    backdrop: Backdrop?,
    glassEnabled: Boolean,
    actions: List<ReadMenuActionDef>,
    itemContent: @Composable (ReadMenuActionDef) -> Unit,
) {
    val pillShape = RoundedCornerShape(50)
    val tint = LegadoTheme.colorScheme.onSurfaceVariant
    val iconStyle = state.menuConfig.titleBarIconStyle

    Box {
        Row(
            modifier = Modifier
                .height(40.dp)
                .then(
                    if (glassEnabled) {
                        Modifier.readMenuLiquidGlass(
                            backdrop = backdrop,
                            colors = colors,
                            shape = pillShape,
                            useTopBarStyle = true,
                            useLens = true,
                            blurRadius = 32.dp,
                            interactive = true,
                            menuConfig = state.menuConfig,
                        )
                    } else {
                        val containerColor = when (iconStyle) {
                            1 -> LegadoTheme.colorScheme.surfaceContainerLow
                            else -> Color.Transparent
                        }
                        Modifier.background(containerColor, pillShape)
                    }
                )
                .then(
                    when {
                        state.menuConfig.readMenuBorderWidth > 0 &&
                                state.menuConfig.readMenuTopBarTitleCapsule ->
                            Modifier.border(
                                BorderStroke(
                                    state.menuConfig.readMenuBorderWidth.dp,
                                    Color(readMenuBorderColor(state.menuConfig)),
                                ),
                                pillShape,
                            )

                        !glassEnabled && iconStyle == 2 ->
                            Modifier.border(
                                BorderStroke(1.dp, tint.copy(alpha = 0.45f)),
                                pillShape,
                            )

                        else -> Modifier
                    }
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actions.forEachIndexed { index, def ->
                if (index > 0) {
                    MergedGlassDivider(tint)
                }
                itemContent(def)
            }
        }
    }
}

/**
 * 顶栏右侧动作区。按钮来自 [loadTitleBarActions]：顶栏专属按钮 + 浮动图标/底栏的共享图标池，
 * 顺序与开关由「顶栏按钮」配置页决定，最多 [MAX_TITLE_BAR_ACTIONS] 个。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.TitleBarActionButtons(
    state: ReadBookUiState,
    colors: ReadMenuColors,
    onIntent: (ReadBookIntent) -> Unit,
    backdrop: Backdrop?,
    eyeProtectionActive: Boolean,
    merged: Boolean,
) {
    val context = LocalContext.current
    val customIcons = state.menuConfig.titleBarCustomIcons
    val actions = remember(
        context,
        state.menuConfig.titleBarActions,
        state.menuConfig.titleBarCompact,
        state.isLocalBook,
        state.isLocalTxt,
        state.bookSource,
        state.isReadAloudRunning,
        state.isAutoPage,
        state.translationMode,
        state.useMultiSpeaker,
        state.multiRoleCast,
        eyeProtectionActive,
    ) {
        loadTitleBarActions(
            context = context,
            state = state,
            eyeProtectionActive = eyeProtectionActive,
            onIntent = onIntent,
        )
    }
    var expandedMenuId by remember { mutableStateOf<String?>(null) }

    val itemContent: @Composable (ReadMenuActionDef) -> Unit = { def ->
        val longPressMenu = def.longPressMenu
        val onLongClick = when {
            longPressMenu != null -> ({ expandedMenuId = def.id })
            def.onLongClick != null -> def.onLongClick
            else -> null
        }
        Box {
            if (merged) {
                MergedGlassIconButton(
                    icon = def.icon,
                    tint = LegadoTheme.colorScheme.onSurfaceVariant,
                    contentDescription = def.label,
                    onClick = def.onClick,
                    onLongClick = onLongClick,
                    selected = def.isActive,
                    customIconPath = customIcons[def.id],
                )
            } else {
                MenuTitleGlassButton(
                    onClick = def.onClick,
                    icon = def.icon,
                    state = state,
                    colors = colors,
                    backdrop = backdrop,
                    onLongClick = onLongClick,
                    contentDescription = def.label,
                    customIconPath = customIcons[def.id],
                    selected = def.isActive,
                )
            }
            ReadMenuLongPressDropdown(
                menu = longPressMenu,
                expanded = expandedMenuId == def.id,
                onDismissRequest = { expandedMenuId = null },
                onIntent = onIntent,
            )
        }
    }

    if (merged) {
        MenuTitleBarMergedGlassButton(
            state = state,
            colors = colors,
            backdrop = backdrop,
            glassEnabled = readMenuTopBarButtonLiquidGlassEnabled(backdrop, state.menuConfig),
            actions = actions,
            itemContent = itemContent,
        )
    } else {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actions.forEach { def -> itemContent(def) }
        }
    }
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
