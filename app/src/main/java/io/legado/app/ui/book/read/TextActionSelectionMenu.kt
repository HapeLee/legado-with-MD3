package io.legado.app.ui.book.read

import io.legado.app.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import coil.compose.AsyncImage
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.text.AppText

enum class MenuMode {
    Quick,
    More
}

@Composable
fun TextActionSelectionMenu(
    menuState: TextMenuState,
    onDismiss: () -> Unit,
    onItemClick: (ActionMenuItem) -> Unit,
    onOpenManage: () -> Unit
) {
    var mode by remember { mutableStateOf(MenuMode.Quick) }
    val draftItems = menuState.items

    val density = LocalDensity.current.density
    val positionProvider = remember(menuState, density) {
        TextMenuPositionProvider(
            density = density,
            startX = menuState.startX,
            startTopY = menuState.startTopY,
            startBottomY = menuState.startBottomY,
            endX = menuState.endX,
            endBottomY = menuState.endBottomY
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
        val widthModifier = Modifier.widthIn(max = 300.dp)

        NormalCard(
            modifier = widthModifier.wrapContentHeight(),
            containerColor = LegadoTheme.colorScheme.surface,
            elevation = 6.dp,
            cornerRadius = 12.dp
        ) {
            if (mode == MenuMode.Quick) {
                QuickMenuView(
                    items = draftItems,
                    onItemClick = onItemClick,
                    onMoreClick = { mode = MenuMode.More }
                )
            } else {
                MoreMenuView(
                    items = draftItems,
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

@Composable
private fun QuickMenuView(
    items: List<ActionMenuItem>,
    onItemClick: (ActionMenuItem) -> Unit,
    onMoreClick: () -> Unit
) {
    val enabledItems = remember(items) { items.filter { it.enabled } }

    Row(
        modifier = Modifier
            .wrapContentWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(enabledItems, key = { it.uniqueId }) { item ->
                QuickMenuItem(item = item, onClick = { onItemClick(item) })
            }
        }
        VerticalDivider(
            color = LegadoTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            modifier = Modifier
                .height(20.dp)
                .width(1.dp)
        )
        Box(
            modifier = Modifier
                .clickable(onClick = onMoreClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MoreHoriz,
                contentDescription = stringResource(R.string.more_menu),
                tint = LegadoTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp)
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
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (item.iconDrawable != null) {
            AsyncImage(
                model = item.iconDrawable,
                contentDescription = item.title,
                modifier = Modifier
                    .size(16.dp)
                    .padding(end = 4.dp)
            )
        }
        AppText(
            text = item.title,
            fontSize = 13.sp,
            color = LegadoTheme.colorScheme.onSurface,
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
    val disabledItems = remember(items) { items.filter { !it.enabled } }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .width(IntrinsicSize.Max)
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onBack)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = LegadoTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
        
        Column(
            modifier = Modifier
                .heightIn(max = 240.dp)
                .verticalScroll(scrollState)
                .fillMaxWidth()
        ) {
            disabledItems.forEach { item ->
                MoreMenuItem(item = item, onClick = { onItemClick(item) })
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onManageClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = stringResource(R.string.edit_menu_items),
                tint = LegadoTheme.colorScheme.primary,
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 8.dp)
            )
            AppText(
                text = stringResource(R.string.edit_menu_items),
                fontSize = 13.sp,
                color = LegadoTheme.colorScheme.primary,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
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
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (item.iconDrawable != null) {
            AsyncImage(
                model = item.iconDrawable,
                contentDescription = item.title,
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 8.dp)
            )
        }
        AppText(
            text = item.title,
            fontSize = 13.sp,
            color = LegadoTheme.colorScheme.onSurface,
            maxLines = 1
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
