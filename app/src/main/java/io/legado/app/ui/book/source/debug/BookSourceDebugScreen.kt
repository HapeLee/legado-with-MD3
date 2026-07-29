package io.legado.app.ui.book.source.debug

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.model.Debug
import io.legado.app.ui.about.MarkdownSheet
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.fadingEdge
import io.legado.app.ui.widget.components.AppFloatingActionButton
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.button.ToggleChip
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.utils.LogUtils
import java.util.Date

@Composable
fun BookSourceDebugScreen(
    state: BookSourceDebugUiState,
    onIntent: (BookSourceDebugIntent) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val listState = rememberLazyListState()
    val visibleEntries = state.entries.filter { entry ->
        when (state.filter) {
            BookSourceDebugFilter.All -> true
            BookSourceDebugFilter.Messages -> entry.kind == Debug.EventKind.Message || entry.kind == Debug.EventKind.Completed
            BookSourceDebugFilter.Sources -> entry.kind.isSourcePayload
            BookSourceDebugFilter.Errors -> entry.kind == Debug.EventKind.Error
        }
    }
    LaunchedEffect(visibleEntries.size, state.status) {
        if (state.status == BookSourceDebugStatus.Running && visibleEntries.isNotEmpty()) {
            listState.animateScrollToItem(visibleEntries.lastIndex)
        }
    }
    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = "调试",
                navigationIcon = { TopBarNavigationButton(onClick = onBack) },
                actions = {
                    TopBarActionButton(
                        onClick = { onIntent(BookSourceDebugIntent.Clear) },
                        imageVector = Icons.Default.ClearAll,
                        contentDescription = "清空日志",
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            AppFloatingActionButton(
                onClick = {
                    onIntent(
                        if (state.status == BookSourceDebugStatus.Running) BookSourceDebugIntent.Stop
                        else BookSourceDebugIntent.Start
                    )
                },
                icon = if (state.status == BookSourceDebugStatus.Running) Icons.Default.Stop else Icons.Default.PlayArrow,
                tooltipText = if (state.status == BookSourceDebugStatus.Running) "停止" else "开始调试",
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "controls", contentType = "controls") {
                DebugControls(state, onIntent)
            }
            item(key = "filters", contentType = "filters") {
                DebugChipRow {
                    items(BookSourceDebugFilter.entries, key = { it.name }) { filter ->
                        ToggleChip(
                            label = filter.title,
                            selected = state.filter == filter,
                            onToggle = { onIntent(BookSourceDebugIntent.SelectFilter(filter)) },
                        )
                    }
                }
            }
            if (visibleEntries.isEmpty()) {
                item(key = "empty") {
                    EmptyMessage(
                        message = if (state.status == BookSourceDebugStatus.Running) "等待调试日志…" else "输入内容并开始调试",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                    )
                }
            }
            items(visibleEntries, key = { it.id }, contentType = { it.kind }) { entry ->
                DebugEntryCard(
                    entry = entry,
                    modifier = Modifier.animateItem(),
                    onClick = { onIntent(BookSourceDebugIntent.ShowEntry(entry.id)) },
                )
            }
        }
    }
    val selectedEntry = state.entries.firstOrNull { it.id == state.selectedEntryId }
    MarkdownSheet(
        show = selectedEntry != null,
        title = selectedEntry?.kind?.title.orEmpty(),
        content = selectedEntry?.message.orEmpty(),
        onDismissRequest = { onIntent(BookSourceDebugIntent.DismissEntry) },
    )
}

@Composable
private fun DebugControls(
    state: BookSourceDebugUiState,
    onIntent: (BookSourceDebugIntent) -> Unit
) {
    val visibleExamples = remember(state.examples, state.target) {
        state.examples.filter { it.target == state.target }.take(8)
    }
    GlassCard {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                AppText(
                    text = state.sourceName,
                    style = LegadoTheme.typography.labelMediumEmphasized
                )
            }
            DebugChipRow {
                items(BookSourceDebugTarget.entries, key = { it.name }) { target ->
                    ToggleChip(
                        label = target.title,
                        selected = state.target == target,
                        onToggle = { onIntent(BookSourceDebugIntent.SelectTarget(target)) },
                    )
                }
            }
            AppTextField(
                value = state.query,
                onValueChange = { onIntent(BookSourceDebugIntent.SetQuery(it)) },
                label = state.target.hint,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.status != BookSourceDebugStatus.Loading,
                maxLines = 4,
            )
            if (visibleExamples.isNotEmpty()) {
                DebugChipRow {
                    items(visibleExamples, key = { "${it.target}:${it.value}" }) { example ->
                        ToggleChip(
                            label = example.title,
                            selected = state.target == example.target && state.query == example.value,
                            onToggle = { onIntent(BookSourceDebugIntent.UseExample(example)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DebugChipRow(content: LazyListScope.() -> Unit) {
    val state = rememberLazyListState()
    LazyRow(
        state = state,
        modifier = Modifier
            .fillMaxWidth()
            .fadingEdge(state, gradientWidth = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
fun DebugEntryCard(
    entry: BookSourceDebugEntryUi,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val containerColor = when (entry.kind) {
        Debug.EventKind.Message -> LegadoTheme.colorScheme.surfaceContainerHigh
        Debug.EventKind.SearchSource,
        Debug.EventKind.InfoSource,
        Debug.EventKind.TocSource,
        Debug.EventKind.ContentSource -> LegadoTheme.colorScheme.primaryContainer
        Debug.EventKind.Error -> LegadoTheme.colorScheme.errorContainer
        Debug.EventKind.Completed -> LegadoTheme.colorScheme.tertiaryContainer
    }
    val accentColor = when (entry.kind) {
        Debug.EventKind.Message -> LegadoTheme.colorScheme.onSurfaceVariant
        Debug.EventKind.SearchSource,
        Debug.EventKind.InfoSource,
        Debug.EventKind.TocSource,
        Debug.EventKind.ContentSource -> LegadoTheme.colorScheme.onPrimaryContainer
        Debug.EventKind.Error -> LegadoTheme.colorScheme.onErrorContainer
        Debug.EventKind.Completed -> LegadoTheme.colorScheme.onTertiaryContainer
    }
    GlassCard(
        modifier = modifier,
        onClick = onClick,
        containerColor = containerColor,
        contentColor = accentColor,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AppText(
                    text = entry.kind.title,
                    style = LegadoTheme.typography.labelMedium,
                    color = accentColor,
                )
                AppText(
                    text = "+%.3fs".format(entry.elapsedMillis / 1000.0),
                    style = LegadoTheme.typography.labelSmall,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                )
            }
            AppText(
                text = entry.message,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                style = LegadoTheme.typography.bodySmall,
            )
            AppText(
                text = LogUtils.logTimeFormat.format(Date(entry.timestamp)),
                style = LegadoTheme.typography.labelSmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val Debug.EventKind.title: String
    get() = when (this) {
        Debug.EventKind.Message -> "过程"
        Debug.EventKind.SearchSource -> "搜索/发现响应"
        Debug.EventKind.InfoSource -> "详情响应"
        Debug.EventKind.TocSource -> "目录响应"
        Debug.EventKind.ContentSource -> "正文响应"
        Debug.EventKind.Error -> "错误"
        Debug.EventKind.Completed -> "完成"
    }

private val BookSourceDebugTarget.title
    get() = when (this) {
        BookSourceDebugTarget.Search -> "搜索"
        BookSourceDebugTarget.Explore -> "发现"
        BookSourceDebugTarget.Info -> "详情"
        BookSourceDebugTarget.Toc -> "目录"
        BookSourceDebugTarget.Content -> "正文"
    }
private val BookSourceDebugTarget.hint
    get() = when (this) {
        BookSourceDebugTarget.Search -> "搜索关键字"
        BookSourceDebugTarget.Explore -> "发现 URL"
        BookSourceDebugTarget.Info -> "详情页 URL"
        BookSourceDebugTarget.Toc -> "目录页 URL"
        BookSourceDebugTarget.Content -> "正文页 URL"
    }
private val BookSourceDebugFilter.title
    get() = when (this) {
        BookSourceDebugFilter.All -> "全部"
        BookSourceDebugFilter.Messages -> "过程"
        BookSourceDebugFilter.Sources -> "响应"
        BookSourceDebugFilter.Errors -> "错误"
    }
