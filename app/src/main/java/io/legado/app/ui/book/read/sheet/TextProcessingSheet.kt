package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookContentProcess
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.domain.model.TextProcessAction
import io.legado.app.ui.book.read.ContentProcessConfigUiState
import io.legado.app.ui.book.read.ContentProcessItemUi
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.book.read.ReplaceRuleItemUi
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.ConfigListEntry
import io.legado.app.ui.widget.components.ConfigListEntryRow
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.series.MediumToggleButton
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.progressIndicator.AppCircularProgressIndicator
import io.legado.app.ui.widget.components.reorderAccessibility
import io.legado.app.ui.widget.components.tabRow.CardTabRow
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private enum class ReplaceFilter {
    All,
    Enabled,
    Disabled,
    Effective,
}

/** 一次拖动排序的落点信息：把 [draggedId] 移到 [anchorId] 的旁边（[afterAnchor] 为 true 时在其后）。 */
private class ReplaceMove(
    val draggedId: Long,
    val anchorId: Long,
    val afterAnchor: Boolean,
)

/**
 * 把列表第 [from] 项移到第 [to] 项位置，返回移动后的列表与落点信息。
 * 仅操作当前显示（已筛选）的子列表，[from] 或 [to] 越界时返回 null。
 */
private fun moveInList(
    list: List<ReplaceRuleItemUi>,
    from: Int,
    to: Int,
): Pair<List<ReplaceRuleItemUi>, ReplaceMove>? {
    if (from == to || from !in list.indices || to !in list.indices) return null
    val moved = list[from]
    val newList = list.toMutableList().apply { add(to, removeAt(from)) }
    val anchorId = if (to > from) newList[to - 1].id else newList[to + 1].id
    return newList to ReplaceMove(moved.id, anchorId, to > from)
}

@Composable
fun TextProcessingSheet(
    show: Boolean,
    book: Book?,
    allRules: ImmutableList<ReplaceRuleItemUi>,
    effectiveRules: ImmutableList<ReplaceRule>,
    replaceEnabled: Boolean,
    contentProcessState: ContentProcessConfigUiState,
    onIntent: (ReadBookIntent) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val pagerState = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    var replaceQuery by rememberSaveable(show) { mutableStateOf("") }
    var contentQuery by rememberSaveable(show) { mutableStateOf("") }
    var replaceFilter by rememberSaveable(show) { mutableStateOf(ReplaceFilter.Effective) }

    val maxHeight = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.height.toDp() * 0.72f
    }
    LaunchedEffect(show) {
        if (show) pagerState.scrollToPage(0)
    }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        animateContentSize = false,
        contentPaddingEnabled = false,
        modifier = Modifier.heightIn(max = maxHeight),
    ) {
        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
            ReaderBookHeader(book = book)
        }
        CardTabRow(
            tabTitles = listOf(
                stringResource(R.string.replace_purify),
                stringResource(R.string.content_processes),
            ),
            selectedTabIndex = pagerState.currentPage,
            onTabSelected = { scope.launch { pagerState.animateScrollToPage(it) } },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp, end = 16.dp),
            tabEndContent = { index ->
                if (index == 0) {
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(16.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                onDismissRequest()
                                onIntent(ReadBookIntent.MenuSettingReplace)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuOpen,
                            contentDescription = stringResource(R.string.open),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            },
        )
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { page ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            ) {
                if (page == 0) {
                    ReplaceRulesPage(
                        query = replaceQuery,
                        onQueryChange = { replaceQuery = it },
                        filter = replaceFilter,
                        onFilterChange = { replaceFilter = it },
                        allRules = allRules,
                        effectiveRules = effectiveRules,
                        replaceEnabled = replaceEnabled,
                        onIntent = onIntent,
                    )
                } else {
                    ContentProcessesPage(
                        query = contentQuery,
                        onQueryChange = { contentQuery = it },
                        state = contentProcessState,
                        onIntent = onIntent,
                    )
                }
            }
        }
    }

    val deletingItem = contentProcessState.deleteItem
    AppAlertDialog(
        show = deletingItem != null,
        onDismissRequest = { onIntent(ReadBookIntent.DismissDeleteContentProcess) },
        title = stringResource(R.string.delete),
        text = stringResource(R.string.content_process_delete_confirm_simple),
        confirmText = stringResource(R.string.ok),
        onConfirm = { onIntent(ReadBookIntent.ConfirmDeleteContentProcess) },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { onIntent(ReadBookIntent.DismissDeleteContentProcess) },
    )
}

@Composable
private fun ReplaceRulesPage(
    query: String,
    onQueryChange: (String) -> Unit,
    filter: ReplaceFilter,
    onFilterChange: (ReplaceFilter) -> Unit,
    allRules: List<ReplaceRuleItemUi>,
    effectiveRules: List<ReplaceRule>,
    replaceEnabled: Boolean,
    onIntent: (ReadBookIntent) -> Unit,
) {
    val effectiveIds = remember(effectiveRules) { effectiveRules.mapTo(hashSetOf()) { it.id } }
    var rules by remember(allRules, effectiveIds, filter, query) {
        mutableStateOf(
            allRules.filter { rule ->
                val matchesFilter = when (filter) {
                    ReplaceFilter.All -> true
                    ReplaceFilter.Enabled -> rule.enabled
                    ReplaceFilter.Disabled -> !rule.enabled
                    ReplaceFilter.Effective -> rule.id in effectiveIds
                }
                matchesFilter && rule.matches(query)
            }
        )
    }
    var filterExpanded by remember { mutableStateOf(false) }
    var lastMove by remember { mutableStateOf<ReplaceMove?>(null) }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        moveInList(rules, from.index, to.index)?.let { (newRules, move) ->
            rules = newRules
            lastMove = move
        }
    }
    LaunchedEffect(reorderState.isAnyItemDragging) {
        if (!reorderState.isAnyItemDragging) {
            lastMove?.let { move ->
                onIntent(
                    ReadBookIntent.MoveReplaceRule(move.draggedId, move.anchorId, move.afterAnchor)
                )
            }
            lastMove = null
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompactTextProcessSearch(
                query = query,
                onQueryChange = onQueryChange,
                modifier = Modifier.weight(1f),
            )
            CompactTextProcessTool(
                selected = replaceEnabled,
                icon = Icons.Default.FindReplace,
                contentDescription = stringResource(R.string.replace_purify),
                onClick = { onIntent(ReadBookIntent.ChangeReplaceRule(!replaceEnabled)) },
            )
            Box {
                CompactTextProcessTool(
                    selected = filter != ReplaceFilter.Effective,
                    icon = Icons.Default.FilterList,
                    contentDescription = stringResource(R.string.replace_filter),
                    onClick = { filterExpanded = true },
                )
                RoundDropdownMenu(
                    expanded = filterExpanded,
                    onDismissRequest = { filterExpanded = false },
                ) { dismiss ->
                    ReplaceFilter.entries.forEach { option ->
                        RoundDropdownMenuItem(
                            text = stringResource(option.labelRes),
                            isSelected = option == filter,
                            onClick = {
                                onFilterChange(option)
                                dismiss()
                            },
                        )
                    }
                }
            }
        }
        if (rules.isEmpty()) {
            EmptyMessage(
                message = stringResource(R.string.replace_filter_empty),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(rules, key = { it.id }) { rule ->
                    ReorderableItem(reorderState, key = rule.id) { _ ->
                        ReplaceRuleItem(
                            rule = rule,
                            onIntent = onIntent,
                            dragHandleModifier = Modifier
                                .reorderAccessibility(
                                    index = rules.indexOf(rule),
                                    itemCount = rules.size,
                                    description = stringResource(
                                        R.string.a11y_reorder_named,
                                        rule.name,
                                    ),
                                ) { from, to ->
                                    moveInList(rules, from, to)?.let { (newRules, move) ->
                                        rules = newRules
                                        onIntent(
                                            ReadBookIntent.MoveReplaceRule(
                                                move.draggedId,
                                                move.anchorId,
                                                move.afterAnchor,
                                            )
                                        )
                                    }
                                }
                                .draggableHandle(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplaceRuleItem(
    rule: ReplaceRuleItemUi,
    onIntent: (ReadBookIntent) -> Unit,
    dragHandleModifier: Modifier = Modifier,
) {
    NormalCard(
        onClick = { onIntent(ReadBookIntent.OpenReplaceEditor(rule.id, rule.pattern)) },
        modifier = Modifier.fillMaxWidth(),
        containerColor = LegadoTheme.colorScheme.onSheetContent,
    ) {
        ConfigListEntryRow(
            entry = ConfigListEntry(
                id = rule.id.toString(),
                enabled = rule.enabled,
                label = rule.displayNameGroup,
                subtitle = buildString {
                    append(rule.pattern)
                    append(" → ")
                    append(rule.replacement.ifEmpty { "∅" })
                },
            ),
            onToggleEnabled = {
                onIntent(ReadBookIntent.SetReplaceRuleEnabled(rule.id, !rule.enabled))
            },
            dragHandleModifier = dragHandleModifier,
        )
    }
}

@Composable
private fun ContentProcessesPage(
    query: String,
    onQueryChange: (String) -> Unit,
    state: ContentProcessConfigUiState,
    onIntent: (ReadBookIntent) -> Unit,
) {
    var viewingItem by remember { mutableStateOf<ContentProcessItemUi?>(null) }
    val items = remember(state.items, query) {
        state.items.filter {
            query.isBlank() || it.selectedText.contains(query, true) ||
                    it.replacementText.contains(query, true)
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        CompactTextProcessSearch(
            query = query,
            onQueryChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        )
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AppCircularProgressIndicator()
            }

            state.errorMessage != null -> AppText(
                text = state.errorMessage,
                color = LegadoTheme.colorScheme.error,
            )

            items.isEmpty() -> EmptyMessage(
                message = stringResource(R.string.content_process_empty),
                modifier = Modifier.fillMaxWidth(),
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    ContentProcessRow(
                        item = item,
                        onClick = { viewingItem = item },
                        onIntent = onIntent,
                    )
                }
            }
        }
    }

    viewingItem?.let { item ->
        AppAlertDialog(
            data = item,
            onDismissRequest = { viewingItem = null },
            title = contentProcessTitle(item),
            content = {
                Column {
                    AppText(stringResource(R.string.ai_text_clean_before))
                    AppText(item.selectedText, modifier = Modifier.padding(bottom = 8.dp))
                    AppText(stringResource(R.string.ai_text_clean_after))
                    AppText(item.replacementText.ifEmpty { stringResource(R.string.ai_text_clean_delete) })
                }
            },
            confirmText = stringResource(R.string.ok),
            onConfirm = { viewingItem = null },
        )
    }
}

@Composable
private fun ContentProcessRow(
    item: ContentProcessItemUi,
    onClick: () -> Unit,
    onIntent: (ReadBookIntent) -> Unit,
) {
    NormalCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        containerColor = LegadoTheme.colorScheme.onSheetContent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                AppText(
                    text = contentProcessTitle(item),
                    style = LegadoTheme.typography.labelLargeEmphasized,
                )
                AppText(
                    text = item.selectedText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                )
            }
            MediumToggleButton(
                checked = item.enabled,
                onCheckedChange = {
                    onIntent(ReadBookIntent.ToggleContentProcess(item.id, it))
                },
                icon = Icons.Default.VisibilityOff,
                iconChecked = Icons.Default.Visibility,
                contentDescription = stringResource(R.string.enable),
            )
            MediumTonalButton(
                onClick = { onIntent(ReadBookIntent.RequestDeleteContentProcess(item)) },
                icon = Icons.Default.Delete,
                contentDescription = stringResource(R.string.delete),
            )
        }
    }
}

@Composable
private fun CompactTextProcessSearch(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .height(32.dp)
            .clip(shape)
            .background(LegadoTheme.colorScheme.surfaceContainerLow),
        singleLine = true,
        textStyle = LegadoTheme.typography.bodySmall.copy(color = LegadoTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(LegadoTheme.colorScheme.primary),
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Default.Search, null, modifier = Modifier.size(14.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) AppText(
                        text = stringResource(R.string.search),
                        style = LegadoTheme.typography.labelSmall,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                    inner()
                }
            }
        },
    )
}

@Composable
private fun CompactTextProcessTool(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) LegadoTheme.colorScheme.secondaryContainer
                else LegadoTheme.colorScheme.surfaceContainerLow
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, modifier = Modifier.size(16.dp))
    }
}

private fun ReplaceRuleItemUi.matches(query: String): Boolean = query.isBlank() ||
        name.contains(query, true) || pattern.contains(query, true) ||
        replacement.contains(query, true) || group.orEmpty().contains(query, true)

private val ReplaceRuleItemUi.displayNameGroup: String
    get() = if (group.isNullOrBlank()) name else "$name ($group)"

private val ReplaceFilter.labelRes: Int
    get() = when (this) {
        ReplaceFilter.All -> R.string.replace_filter_all
        ReplaceFilter.Enabled -> R.string.replace_filter_enabled
        ReplaceFilter.Disabled -> R.string.replace_filter_disabled
        ReplaceFilter.Effective -> R.string.replace_filter_effective
    }

@Composable
private fun contentProcessTitle(item: ContentProcessItemUi): String {
    val kind = when (item.kind) {
        BookContentProcess.KIND_AI_CLEAN -> stringResource(R.string.content_process_ai_clean)
        BookContentProcess.KIND_AI_REWRITE -> stringResource(R.string.content_process_ai_rewrite)
        BookContentProcess.KIND_USER_UNDERLINE -> stringResource(R.string.content_process_user_underline)
        BookContentProcess.KIND_USER_HIGHLIGHT -> stringResource(R.string.content_process_user_highlight)
        else -> item.kind
    }
    val action = when (item.actionType) {
        TextProcessAction.TYPE_DELETE -> stringResource(R.string.content_process_delete_action)
        TextProcessAction.TYPE_INSERT_BEFORE,
        TextProcessAction.TYPE_INSERT_AFTER -> stringResource(R.string.content_process_insert_action)

        else -> stringResource(R.string.content_process_replace_action)
    }
    return "$kind · $action"
}
