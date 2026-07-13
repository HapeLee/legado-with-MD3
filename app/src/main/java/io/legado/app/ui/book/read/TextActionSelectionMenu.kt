package io.legado.app.ui.book.read

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.platform.LocalDensity
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
import coil.compose.AsyncImage
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.text.AppText

enum class MenuMode {
    Quick,
    More
}

@Composable
fun TextActionSelectionMenu(
    menuState: TextMenuState?,
    expandTextMenu: Boolean,
    onDismiss: () -> Unit,
    onItemClick: (ActionMenuItem) -> Unit,
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
    val maxMenuWidth = with(localDensity){ containerSize.width.toDp() } - 32.dp
    var mode by remember { mutableStateOf(MenuMode.Quick) }
    LaunchedEffect(menuState) {
        if (menuState == null) mode = MenuMode.Quick
    }
    val draftItems = displayedMenuState.items
    
    val primaryItems = remember(draftItems) { draftItems.filter { it.showState == 0 } }
    val collapsedItems = remember(draftItems) { draftItems.filter { it.showState == 1 } }
    val activeMultiItems = remember(draftItems) { draftItems.filter { it.showState == 0 || it.showState == 1 } }

    val density = localDensity.density
    val positionProvider = remember(displayedMenuState, density) {
        TextMenuPositionProvider(
            density = density,
            startX = displayedMenuState.startX,
            startTopY = displayedMenuState.startTopY,
            startBottomY = displayedMenuState.startBottomY,
            endX = displayedMenuState.endX,
            endBottomY = displayedMenuState.endBottomY
        )
    }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {

        AnimatedVisibility(
            visibleState = visibilityState,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
        ) {
            NormalCard(
                modifier = Modifier.widthIn(max = maxMenuWidth),
                containerColor = LegadoTheme.colorScheme.surfaceBright,
                elevation = 6.dp,
                cornerRadius = 12.dp,
            ) {
                if (expandTextMenu) {
                    MultiLineMenuView(
                        items = activeMultiItems,
                        onItemClick = onItemClick,
                        onManageClick = {
                            onDismiss()
                            onOpenManage()
                        }
                    )
                } else {
                    AnimatedContent(
                        targetState = mode,
                        label = "TextActionMenuLevel",
                    ) { targetMode ->
                        if (targetMode == MenuMode.Quick) {
                            QuickMenuView(
                                items = primaryItems,
                                hasMore = collapsedItems.isNotEmpty(),
                                onItemClick = onItemClick,
                                onMoreClick = { mode = MenuMode.More },
                                onSettingsClick = {
                                    onDismiss()
                                    onOpenManage()
                                }
                            )
                        } else {
                            MoreMenuView(
                                items = collapsedItems,
                                onItemClick = onItemClick,
                                onBack = { mode = MenuMode.Quick },
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
    onItemClick: (ActionMenuItem) -> Unit,
    onManageClick: () -> Unit
) {
    FlowRow(
        modifier = Modifier
            .heightIn(max = 300.dp)
            .verticalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.forEach { item ->
            QuickMenuItem(item = item, onClick = { onItemClick(item) })
        }

        Row(
            modifier = Modifier
                .clickable(onClick = onManageClick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
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
    onItemClick: (ActionMenuItem) -> Unit,
    onMoreClick: () -> Unit,
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
            items(items, key = { it.uniqueId }) { item ->
                QuickMenuItem(item = item, onClick = { onItemClick(item) })
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
                .clickable(onClick = if (hasMore) onMoreClick else onSettingsClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
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
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (item.iconDrawable != null) {
            AsyncImage(
                model = item.iconDrawable,
                contentDescription = item.title,
                modifier = Modifier
                    .size(16.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = LegadoTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp)
            )
        }
        
        Column(
            modifier = Modifier
                .heightIn(max = 240.dp)
                .verticalScroll(scrollState)
                .fillMaxWidth()
        ) {
            items.forEach { item ->
                MoreMenuItem(item = item, onClick = { onItemClick(item) })
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onManageClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = stringResource(R.string.edit_menu_items),
                tint = LegadoTheme.colorScheme.onSurface,
                modifier = Modifier
                    .size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
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
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (item.iconDrawable != null) {
            AsyncImage(
                model = item.iconDrawable,
                contentDescription = item.title,
                modifier = Modifier
                    .size(16.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        AppText(
            text = item.title,
            style = LegadoTheme.typography.labelMedium
        )
    }
}


private class TextMenuPositionProvider(
    private val density: Float,
    private val startX: Int,
    private val startTopY: Int,
    private val startBottomY: Int,
    private val endX: Int,
    private val endBottomY: Int
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

        val isSpaceEnoughAtTop = startTopY > popupContentSize.height + textMargin + marginVertical
        
        if (isSpaceEnoughAtTop) {
            x = startX
            y = startTopY - popupContentSize.height - textMargin
        } else if (windowSize.height - startBottomY > popupContentSize.height + textMargin + marginVertical) {
            x = startX
            y = startBottomY + textMargin
        } else {
            x = endX
            y = endBottomY + textMargin
        }

        val finalX = x.coerceIn(marginHorizontal, (windowSize.width - popupContentSize.width - marginHorizontal).coerceAtLeast(marginHorizontal))
        val finalY = y.coerceIn(marginVertical, (windowSize.height - popupContentSize.height - marginVertical).coerceAtLeast(marginVertical))

        return IntOffset(finalX, finalY)
    }
}
