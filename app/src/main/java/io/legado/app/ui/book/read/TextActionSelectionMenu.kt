package io.legado.app.ui.book.read

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FindReplace
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import coil3.compose.AsyncImage
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ProvideAppDensity
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.text.AppText
import kotlin.math.roundToInt

@Composable
fun TextActionSelectionMenu(
    menuState: TextMenuState?,
    expandTextMenu: Boolean,
    showSelectMenuIcon: Boolean,
    onDismiss: () -> Unit,
    onItemClick: (ActionMenuItem) -> Unit,
    onOpenQuickMarking: () -> Boolean,
    onOpenManage: () -> Unit
) {

    var retainedMenuState by remember { mutableStateOf(menuState) }
    SideEffect {
        if (menuState != null) retainedMenuState = menuState
    }
    val visibilityState = remember { MutableTransitionState(false) }
    visibilityState.targetState = menuState != null
    val displayedMenuState = menuState ?: retainedMenuState ?: return
    if (!visibilityState.currentState && !visibilityState.targetState) return

    val localDensity = LocalDensity.current
    val containerSize = LocalWindowInfo.current.containerSize
    val menuCardWidth = with(localDensity) { containerSize.width.toDp() } - 32.dp
    val menuShadowPadding = 12.dp
    var showMoreMenu by remember { mutableStateOf(false) }
    var moreMenuAnchor by remember { mutableStateOf<IntRect?>(null) }
    val view = LocalView.current
    val windowLocation = IntArray(2).also(view::getLocationOnScreen)
    val moreVisibilityState = remember { MutableTransitionState(false) }
    moreVisibilityState.targetState = showMoreMenu
    LaunchedEffect(menuState) {
        if (menuState == null) {
            showMoreMenu = false
        }
    }
    val draftItems = displayedMenuState.items

    val primaryItems = remember(draftItems) { draftItems.filter { it.showState == 0 } }
    val collapsedItems = remember(draftItems) { draftItems.filter { it.showState == 1 } }
    val activeMultiItems = remember(draftItems) { draftItems.filter { it.showState == 0 || it.showState == 1 } }
    val handleItemClick: (ActionMenuItem) -> Unit = handleItemClick@{ item ->
        if (item.id == R.id.menu_mark && onOpenQuickMarking()) {
            showMoreMenu = false
        } else if (item.id != R.id.menu_mark) {
            onItemClick(item)
        }
    }

    val density = localDensity.density
    val shadowPaddingPx = with(localDensity) { menuShadowPadding.roundToPx() }
    val positionProvider = remember(displayedMenuState, density, shadowPaddingPx) {
        TextMenuPositionProvider(
            density = density,
            startX = displayedMenuState.startX,
            startTopY = displayedMenuState.startTopY,
            startBottomY = displayedMenuState.startBottomY,
            endX = displayedMenuState.endX,
            endBottomY = displayedMenuState.endBottomY,
            shadowPadding = shadowPaddingPx,
        )
    }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = {
            showMoreMenu = false
            onDismiss()
        },
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = !showMoreMenu,
            dismissOnClickOutside = !showMoreMenu,
        )
    ) {
        ProvideAppDensity {

            AnimatedVisibility(
                visibleState = visibilityState,
                enter = SelectionMenuMotion.enter(),
                exit = SelectionMenuMotion.exit(),
            ) {
                if (expandTextMenu) {
                    Box(modifier = Modifier.padding(menuShadowPadding)) {
                        Column(modifier = Modifier.width(menuCardWidth)) {
                            NormalCard(
                                modifier = Modifier.fillMaxWidth(),
                                containerColor = LegadoTheme.colorScheme.surfaceBright,
                                elevation = 12.dp,
                                cornerRadius = 12.dp,
                            ) {
                                MultiLineMenuView(
                                    items = activeMultiItems,
                                    showIcons = showSelectMenuIcon,
                                    onItemClick = handleItemClick,
                                    onManageClick = {
                                        onDismiss()
                                        onOpenManage()
                                    }
                                )
                            }
                        }
                    }
                } else {
                    val quickMenuScale by animateFloatAsState(
                        targetValue = if (showMoreMenu) 0.96f else 1f,
                        animationSpec = tween(durationMillis = 400),
                        label = "quickMenuScale"
                    )
                    val quickMenuAlpha by animateFloatAsState(
                        targetValue = if (showMoreMenu) 0.82f else 1f,
                        animationSpec = tween(durationMillis = 360),
                        label = "quickMenuAlpha"
                    )

                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = quickMenuScale
                                scaleY = quickMenuScale
                                alpha = quickMenuAlpha
                                transformOrigin = TransformOrigin(1f, 0.5f)
                            }
                            .padding(menuShadowPadding),
                    ) {
                        Column(modifier = Modifier.width(menuCardWidth)) {
                            NormalCard(
                                modifier = Modifier.fillMaxWidth(),
                                containerColor = LegadoTheme.colorScheme.surfaceBright,
                                elevation = 6.dp,
                                cornerRadius = 12.dp,
                            ) {
                                QuickMenuView(
                                    items = primaryItems,
                                    hasMore = collapsedItems.isNotEmpty(),
                                    showIcons = showSelectMenuIcon,
                                    onItemClick = handleItemClick,
                                    onMoreClick = { showMoreMenu = true },
                                    onMoreAnchorChanged = { moreMenuAnchor = it },
                                    onSettingsClick = {
                                        onDismiss()
                                        onOpenManage()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val anchor = moreMenuAnchor
    if (!expandTextMenu && anchor != null &&
        (moreVisibilityState.currentState || moreVisibilityState.targetState)
    ) {
        val morePositionProvider = remember(anchor, density, windowLocation[0], windowLocation[1]) {
            MoreMenuPositionProvider(
                density = density,
                anchorOnScreen = anchor,
                windowXOnScreen = windowLocation[0],
                windowYOnScreen = windowLocation[1],
                shadowPadding = shadowPaddingPx,
            )
        }
        Popup(
            popupPositionProvider = morePositionProvider,
            onDismissRequest = {
                showMoreMenu = false
                onDismiss()
            },
            properties = PopupProperties(
                focusable = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
            )
        ) {
            ProvideAppDensity {
                AnimatedVisibility(
                    visibleState = moreVisibilityState,
                    enter = fadeIn(animationSpec = tween(360)) + scaleIn(
                        initialScale = 0.94f,
                        transformOrigin = TransformOrigin(1f, 0.5f),
                        animationSpec = tween(400),
                    ),
                    exit = fadeOut(animationSpec = tween(240)) + scaleOut(
                        targetScale = 0.96f,
                        transformOrigin = TransformOrigin(1f, 0.5f),
                        animationSpec = tween(280),
                    ),
                ) {
                    Box(
                        modifier = Modifier.padding(menuShadowPadding),
                    ) {
                        NormalCard(
                            modifier = Modifier.widthIn(max = menuCardWidth),
                            containerColor = LegadoTheme.colorScheme.surfaceBright,
                            elevation = 6.dp,
                            cornerRadius = 12.dp,
                        ) {
                            MoreMenuView(
                                items = collapsedItems,
                                showIcons = showSelectMenuIcon,
                                onItemClick = handleItemClick,
                                onBack = { showMoreMenu = false },
                                onManageClick = {
                                    onDismiss()
                                    onOpenManage()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MultiLineMenuView(
    items: List<ActionMenuItem>,
    showIcons: Boolean,
    onItemClick: (ActionMenuItem) -> Unit,
    onManageClick: () -> Unit
) {
    FlowRow(
        modifier = Modifier
            .heightIn(max = 300.dp)
            .verticalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        items.forEach { item ->
            QuickMenuItem(
                item = item,
                showIcon = showIcons,
                onClick = { onItemClick(item) },
                verticalPadding = 12.dp,
            )
        }

        Row(
            modifier = Modifier
                .clickable(onClick = onManageClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = stringResource(R.string.edit_menu_items),
                tint = LegadoTheme.colorScheme.onSurface,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            AppText(
                text = stringResource(R.string.edit_menu_items),
                style = LegadoTheme.typography.labelSmallEmphasized
            )
        }
    }
}

@Composable
private fun QuickMenuView(
    items: List<ActionMenuItem>,
    hasMore: Boolean,
    showIcons: Boolean,
    onItemClick: (ActionMenuItem) -> Unit,
    onMoreClick: () -> Unit,
    onMoreAnchorChanged: (IntRect) -> Unit,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier.wrapContentWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(
                items = items,
                key = { _, item -> item.uniqueId },
            ) { index, item ->
                QuickMenuItem(
                    item = item,
                    showIcon = showIcons,
                    onClick = { onItemClick(item) },
                    startPadding = if (index == 0) 16.dp else 10.dp,
                    endPadding = 8.dp,
                )
            }
        }
        if (items.isNotEmpty()) {
            VerticalDivider(
                color = LegadoTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                modifier = Modifier
                    .height(20.dp)
                    .width(1.dp)
            )
        }
        Box(
            modifier = Modifier
                .onGloballyPositioned { coordinates ->
                    val position = coordinates.positionOnScreen()
                    onMoreAnchorChanged(
                        IntRect(
                            left = position.x.roundToInt(),
                            top = position.y.roundToInt(),
                            right = position.x.roundToInt() + coordinates.size.width,
                            bottom = position.y.roundToInt() + coordinates.size.height,
                        )
                    )
                }
                .clickable(onClick = if (hasMore) onMoreClick else onSettingsClick)
                .padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (hasMore) Icons.Default.MoreVert else Icons.Default.Settings,
                contentDescription = stringResource(if (hasMore) R.string.more_menu else R.string.setting),
                tint = LegadoTheme.colorScheme.onSurface,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun QuickMenuItem(
    item: ActionMenuItem,
    showIcon: Boolean,
    onClick: () -> Unit,
    startPadding: androidx.compose.ui.unit.Dp = 16.dp,
    endPadding: androidx.compose.ui.unit.Dp = 16.dp,
    verticalPadding: androidx.compose.ui.unit.Dp = 12.dp,
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(
                start = startPadding,
                end = endPadding,
                top = verticalPadding,
                bottom = verticalPadding,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val builtInIcon = if (showIcon) item.builtInIcon() else null
        if (builtInIcon != null) {
            Icon(
                imageVector = builtInIcon,
                contentDescription = item.title,
                tint = LegadoTheme.colorScheme.onSurface,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
        } else if (showIcon && item.iconDrawable != null) {
            AsyncImage(
                model = item.iconDrawable,
                contentDescription = item.title,
                modifier = Modifier
                    .size(16.dp)
            )

            Spacer(modifier = Modifier.height(3.dp))
        }
        AppText(
            text = item.title,
            style = LegadoTheme.typography.labelMedium,
            maxLines = 1
        )
    }
}

@Composable
private fun MoreMenuView(
    items: List<ActionMenuItem>,
    showIcons: Boolean,
    onItemClick: (ActionMenuItem) -> Unit,
    onBack: () -> Unit,
    onManageClick: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .width(IntrinsicSize.Max)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onBack)
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = LegadoTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }

        HorizontalDivider(
            color = LegadoTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            modifier = Modifier
                .height(1.dp)
                .fillMaxWidth(0.8f)
                .align(Alignment.CenterHorizontally)
        )

        Column(
            modifier = Modifier
                .heightIn(max = 240.dp)
                .verticalScroll(scrollState)
                .fillMaxWidth()
        ) {
            items.forEach { item ->
                MoreMenuItem(item = item, showIcon = showIcons, onClick = { onItemClick(item) })
            }
        }

        HorizontalDivider(
            color = LegadoTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            modifier = Modifier
                .height(1.dp)
                .fillMaxWidth(0.8f)
                .align(Alignment.CenterHorizontally)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onManageClick)
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = stringResource(R.string.edit_menu_items),
                tint = LegadoTheme.colorScheme.onSurface,
                modifier = Modifier
                    .size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            AppText(
                text = stringResource(R.string.edit_menu_items),
                style = LegadoTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun MoreMenuItem(
    item: ActionMenuItem,
    showIcon: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val builtInIcon = if (showIcon) item.builtInIcon() else null
        if (builtInIcon != null) {
            Icon(
                imageVector = builtInIcon,
                contentDescription = item.title,
                tint = LegadoTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
        } else if (showIcon && item.iconDrawable != null) {
            AsyncImage(
                model = item.iconDrawable,
                contentDescription = item.title,
                modifier = Modifier
                    .size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        AppText(
            text = item.title,
            style = LegadoTheme.typography.labelMedium
        )
    }
}

private fun ActionMenuItem.builtInIcon(): ImageVector? = when (id) {
    R.id.menu_copy -> Icons.Outlined.ContentCopy
    R.id.menu_share_str -> Icons.Outlined.Share
    R.id.menu_browser -> Icons.Outlined.Language
    R.id.menu_aloud -> Icons.AutoMirrored.Outlined.VolumeUp
    R.id.menu_bookmark -> Icons.Outlined.Bookmark
    R.id.menu_mark -> Icons.Outlined.BorderColor
    R.id.menu_dict -> Icons.AutoMirrored.Outlined.MenuBook
    R.id.menu_replace -> Icons.Outlined.FindReplace
    R.id.menu_edit -> Icons.Outlined.Edit
    R.id.menu_ai_clean, R.id.menu_ai_rewrite -> Icons.Outlined.SmartToy
    R.id.menu_search_content -> Icons.Outlined.Search
    else -> null
}


private class MoreMenuPositionProvider(
    private val density: Float,
    private val anchorOnScreen: IntRect,
    private val windowXOnScreen: Int,
    private val windowYOnScreen: Int,
    private val shadowPadding: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val margin = (16 * density).roundToInt()
        val anchor = IntRect(
            left = anchorOnScreen.left - windowXOnScreen,
            top = anchorOnScreen.top - windowYOnScreen,
            right = anchorOnScreen.right - windowXOnScreen,
            bottom = anchorOnScreen.bottom - windowYOnScreen,
        )
        val x = (anchor.right - popupContentSize.width + shadowPadding).coerceIn(
            margin - shadowPadding,
            (windowSize.width - popupContentSize.width - margin + shadowPadding)
                .coerceAtLeast(margin - shadowPadding),
        )
        val spaceBelow = windowSize.height - anchor.top - margin + shadowPadding
        val spaceAbove = anchor.bottom - margin + shadowPadding
        val preferredY = if (
            popupContentSize.height <= spaceBelow || spaceBelow >= spaceAbove
        ) {
            // The first row covers the three-dot button and the menu expands downward.
            anchor.top - shadowPadding
        } else {
            // Near the bottom edge, expand upward with the last row over the button.
            anchor.bottom - popupContentSize.height + shadowPadding
        }
        val y = preferredY.coerceIn(
            margin - shadowPadding,
            (windowSize.height - popupContentSize.height - margin + shadowPadding)
                .coerceAtLeast(margin - shadowPadding),
        )
        return IntOffset(x, y)
    }
}

internal class TextMenuPositionProvider(
    private val density: Float,
    private val startX: Int,
    private val startTopY: Int,
    private val startBottomY: Int,
    private val endX: Int,
    private val endBottomY: Int,
    private val shadowPadding: Int,
    private val placeOppositeHalf: Boolean = false,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val x: Int
        val y: Int

        val marginHorizontal = (16 * density).toInt()
        val marginVertical = (12 * density).toInt()
        val textMargin = (4 * density).toInt()
        // ReaderCanvasSurface 的选择柄圆心挂在行底下 7dp，直径再占 14dp。
        val cursorHandleClearance = (14 * density).toInt()

        val cardHeight = popupContentSize.height - shadowPadding * 2
        val isSpaceEnoughAtTop = startTopY > cardHeight + textMargin + marginVertical
        // 仅阅读区域最顶部 10% 的选区优先在下方展开；其余位置遵循原本“有上方空间
        // 就放上方”的策略，避免菜单在普通位置不必要地遮挡后文。
        val preferBelowForTopSelection = startTopY < windowSize.height / 10
        val isSpaceEnoughBelowSelection = windowSize.height - endBottomY >
                cardHeight + textMargin + cursorHandleClearance + marginVertical

        if (placeOppositeHalf) {
            x = startX - shadowPadding
            val selectionCenterY = (startTopY + endBottomY) / 2
            y = if (selectionCenterY < windowSize.height / 2) {
                endBottomY + cursorHandleClearance + textMargin - shadowPadding
            } else {
                startTopY - popupContentSize.height + shadowPadding - textMargin
            }
        } else if (!preferBelowForTopSelection && isSpaceEnoughAtTop) {
            x = startX - shadowPadding
            y = startTopY - popupContentSize.height + shadowPadding - textMargin
        } else if (isSpaceEnoughBelowSelection) {
            x = startX - shadowPadding
            // 顶部选区没有空间放菜单时，必须避开整个选区及末端把手，不能只从首行下方弹出。
            y = endBottomY + cursorHandleClearance + textMargin - shadowPadding
        } else if (isSpaceEnoughAtTop) {
            x = startX - shadowPadding
            y = startTopY - popupContentSize.height + shadowPadding - textMargin
        } else {
            x = endX - shadowPadding
            y = endBottomY + cursorHandleClearance + textMargin - shadowPadding
        }

        val minX = marginHorizontal - shadowPadding
        val minY = marginVertical - shadowPadding
        val finalX = x.coerceIn(
            minX,
            (windowSize.width - popupContentSize.width - marginHorizontal + shadowPadding)
                .coerceAtLeast(minX),
        )
        val finalY = y.coerceIn(
            minY,
            (windowSize.height - popupContentSize.height - marginVertical + shadowPadding)
                .coerceAtLeast(minY),
        )

        return IntOffset(finalX, finalY)
    }
}
