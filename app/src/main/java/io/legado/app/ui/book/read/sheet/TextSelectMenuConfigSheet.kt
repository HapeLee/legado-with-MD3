package io.legado.app.ui.book.read.sheet

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.legado.app.R
import io.legado.app.ui.book.read.ActionMenuItem
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.series.SmallTonalButton
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.reorderAccessibility
import io.legado.app.ui.widget.components.settingItem.TinyClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySettingItem
import io.legado.app.ui.widget.components.settingItem.TinySwitchSettingItem
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TextSelectMenuConfigSheet(
    show: Boolean,
    items: List<ActionMenuItem>,
    expandTextMenu: Boolean,
    showSelectMenuIcon: Boolean,
    onExpandTextMenuChange: (Boolean) -> Unit,
    onShowSelectMenuIconChange: (Boolean) -> Unit,
    onDismissRequest: () -> Unit,
    onSaved: (List<ActionMenuItem>) -> Unit
) {
    var draftItems by remember(show, items) {
        mutableStateOf(items)
    }

    var group1Expanded by remember(show) { mutableStateOf(true) }
    var group2Expanded by remember(show) { mutableStateOf(true) }
    var group3Expanded by remember(show) { mutableStateOf(false) }

    val group1Items = remember(draftItems) { draftItems.filter { it.showState == 0 } }
    val group2Items = remember(draftItems) { draftItems.filter { it.showState == 1 } }
    val group3Items = remember(draftItems) { draftItems.filter { it.showState == 2 } }

    fun moveWithinGroup(groupItems: List<ActionMenuItem>, from: Int, to: Int) {
        val fromIndex = draftItems.indexOfFirst { it.uniqueId == groupItems[from].uniqueId }
        val toIndex = draftItems.indexOfFirst { it.uniqueId == groupItems[to].uniqueId }
        if (fromIndex >= 0 && toIndex >= 0) {
            draftItems = draftItems.toMutableList().apply {
                add(toIndex, removeAt(fromIndex))
            }
        }
    }

    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(show) {
        if (show) {
            lazyListState.scrollToItem(0)
        }
    }
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromKey = from.key
        val toKey = to.key

        val fromIndexInDraft = draftItems.indexOfFirst { it.uniqueId == fromKey }
        val toIndexInDraft = draftItems.indexOfFirst { it.uniqueId == toKey }

        if (fromIndexInDraft != -1 && toIndexInDraft != -1) {
            val fromItem = draftItems[fromIndexInDraft]
            val toItem = draftItems[toIndexInDraft]
            if (fromItem.showState == toItem.showState) {
                draftItems = draftItems.toMutableList().apply {
                    add(toIndexInDraft, removeAt(fromIndexInDraft))
                }
            }
        }
    }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.edit_select_menu),
        endAction = {
            SmallTonalButton(
                onClick = {
                    onSaved(draftItems)
                    onDismissRequest()
                },
                icon = Icons.Default.Save,
                contentDescription = stringResource(R.string.save)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .padding(bottom = 16.dp),
        ) {
            LazyColumn(
                state = lazyListState,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .weight(1f),
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .animateItem()
                            .padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TinySwitchSettingItem(
                            title = stringResource(R.string.expand_text_menu),
                            checked = expandTextMenu,
                            onCheckedChange = onExpandTextMenuChange
                        )
                        TinySwitchSettingItem(
                            title = stringResource(R.string.show_select_menu_icon),
                            checked = showSelectMenuIcon,
                            onCheckedChange = onShowSelectMenuIconChange
                        )
                    }
                }

                stickyHeader(key = "group1") {
                    SheetHeader(
                        isCollapsed = !group1Expanded,
                        onToggle = { group1Expanded = !group1Expanded },
                        title = stringResource(R.string.primary_menu)
                    )
                }

                if (group1Expanded) {
                    itemsIndexed(group1Items, key = { _, item -> item.uniqueId }) { index, item ->
                        ReorderableItem(reorderableState, key = item.uniqueId) { isDragging ->
                            val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp)
                            NormalCard(
                                elevation = elevation,
                                cornerRadius = 12.dp,
                                containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.animateItem()
                            ) {
                                PrimaryMenuItemRow(
                                    item = item,
                                    showIcon = showSelectMenuIcon,
                                    onMoveToFolded = {
                                        draftItems = draftItems.map {
                                            if (it.uniqueId == item.uniqueId) it.copy(showState = 1) else it
                                        }
                                    },
                                    onMoveToHidden = {
                                        draftItems = draftItems.map {
                                            if (it.uniqueId == item.uniqueId) it.copy(showState = 2) else it
                                        }
                                    },
                                    dragHandleModifier = Modifier
                                        .reorderAccessibility(
                                            index = index,
                                            itemCount = group1Items.size,
                                            description = stringResource(
                                                R.string.a11y_reorder_named,
                                                item.title,
                                            ),
                                        ) { from, to ->
                                            moveWithinGroup(group1Items, from, to)
                                        }
                                        .draggableHandle()
                                )
                            }
                        }
                    }
                }


                stickyHeader(key = "group2") {
                    val subtitle = if (expandTextMenu) stringResource(R.string.merged_in_multiline) else null
                    SheetHeader(
                        isCollapsed = !group2Expanded,
                        onToggle = { group2Expanded = !group2Expanded },
                        title = stringResource(R.string.collapsed_menu),
                        subtitle = subtitle
                    )
                }

                if (group2Expanded) {
                    itemsIndexed(group2Items, key = { _, item -> item.uniqueId }) { index, item ->
                        ReorderableItem(reorderableState, key = item.uniqueId) { isDragging ->
                            val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp)
                            NormalCard(
                                elevation = elevation,
                                cornerRadius = 12.dp,
                                containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.animateItem()
                            ) {
                                FoldedMenuItemRow(
                                    item = item,
                                    showIcon = showSelectMenuIcon,
                                    onMoveToPrimary = {
                                        draftItems = draftItems.map {
                                            if (it.uniqueId == item.uniqueId) it.copy(showState = 0) else it
                                        }
                                    },
                                    onMoveToHidden = {
                                        draftItems = draftItems.map {
                                            if (it.uniqueId == item.uniqueId) it.copy(showState = 2) else it
                                        }
                                    },
                                    dragHandleModifier = Modifier
                                        .reorderAccessibility(
                                            index = index,
                                            itemCount = group2Items.size,
                                            description = stringResource(
                                                R.string.a11y_reorder_named,
                                                item.title,
                                            ),
                                        ) { from, to ->
                                            moveWithinGroup(group2Items, from, to)
                                        }
                                        .draggableHandle()
                                )
                            }
                        }
                    }
                }


                item (key="group_3"){
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                            .background(LegadoTheme.colorScheme.surfaceContainer)
                            .padding(top = 12.dp)
                    ) {
                        TinySettingItem(
                            title = stringResource(R.string.hidden_items),
                            color = LegadoTheme.colorScheme.surfaceContainerHigh,
                            expanded = group3Expanded,
                            onExpandChange = { expanded ->
                                group3Expanded = expanded
                                if (expanded) {
                                    coroutineScope.launch {
                                        delay(300.milliseconds)
                                        val targetIndex = lazyListState.layoutInfo.totalItemsCount - 1
                                        if (targetIndex >= 0) {
                                            lazyListState.animateScrollToItem(targetIndex)
                                        }
                                    }
                                }
                            },
                            expandContent = {
                                HiddenItemsFlowView(
                                    items = group3Items,
                                    showIcon = showSelectMenuIcon,
                                    onRestore = { item ->
                                        draftItems = draftItems.map {
                                            if (it.uniqueId == item.uniqueId) it.copy(showState = 0) else it
                                        }
                                        coroutineScope.launch {
                                            delay(200.milliseconds)
                                            val targetIndex = lazyListState.layoutInfo.totalItemsCount - 1
                                            if (targetIndex >= 0) {
                                                lazyListState.scrollToItem(targetIndex)
                                            }
                                        }
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrimaryMenuItemRow(
    item: ActionMenuItem,
    showIcon: Boolean,
    onMoveToFolded: () -> Unit,
    onMoveToHidden: () -> Unit,
    dragHandleModifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(all = 12.dp),
    ) {
        if (showIcon && item.iconDrawable != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(36.dp),
            ) {
                AsyncImage(
                    model = item.iconDrawable,
                    contentDescription = item.title,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        AppText(
            text = item.title,
            fontSize = 14.sp,
            color = LegadoTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            maxLines = 1
        )

        IconButton(
            onClick = onMoveToFolded,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = stringResource(R.string.collapsed_menu),
                tint = LegadoTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }

        IconButton(
            onClick = onMoveToHidden,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = Icons.Default.VisibilityOff,
                contentDescription = stringResource(R.string.hidden_items),
                tint = LegadoTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }

        Box(
            modifier = dragHandleModifier.size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = null,
                tint = LegadoTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun FoldedMenuItemRow(
    item: ActionMenuItem,
    showIcon: Boolean,
    onMoveToPrimary: () -> Unit,
    onMoveToHidden: () -> Unit,
    dragHandleModifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(all = 12.dp),
    ) {
        if (showIcon && item.iconDrawable != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(36.dp),
            ) {
                AsyncImage(
                    model = item.iconDrawable,
                    contentDescription = item.title,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        AppText(
            text = item.title,
            fontSize = 14.sp,
            color = LegadoTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            maxLines = 1
        )

        IconButton(
            onClick = onMoveToPrimary,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(R.string.primary_menu),
                tint = LegadoTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }

        IconButton(
            onClick = onMoveToHidden,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = Icons.Default.VisibilityOff,
                contentDescription = stringResource(R.string.hidden_items),
                tint = LegadoTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }

        Box(
            modifier = dragHandleModifier.size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = null,
                tint = LegadoTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun HiddenItemsFlowView(
    items: List<ActionMenuItem>,
    showIcon: Boolean,
    modifier: Modifier = Modifier,
    onRestore: (ActionMenuItem) -> Unit
) {
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item ->
            NormalCard(
                onClick = { onRestore(item) },
                cornerRadius = 8.dp,
                containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                ) {
                    if (showIcon && item.iconDrawable != null) {
                        AsyncImage(
                            model = item.iconDrawable,
                            contentDescription = item.title,
                            modifier = Modifier
                                .size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    AppText(
                        text = item.title,
                        fontSize = 12.sp,
                    )

                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = LegadoTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LazyItemScope.SheetHeader(
    title: String,
    subtitle: String? = null,
    isCollapsed: Boolean,
    onToggle: () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (!isCollapsed) 180f else 0f,
        label = "sheetHeaderArrow"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .animateItem()
            .background(LegadoTheme.colorScheme.surfaceContainer)
            .padding(top = 12.dp)
    ) {
        TinyClickableSettingItem(
            title = title,
            description = subtitle,
            color = LegadoTheme.colorScheme.surfaceContainerHigh,
            trailingContent = {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = LegadoTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(rotation),
                )
            },
            onClick = onToggle
        )
    }
}
