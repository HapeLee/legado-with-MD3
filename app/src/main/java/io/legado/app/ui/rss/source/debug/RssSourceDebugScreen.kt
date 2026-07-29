package io.legado.app.ui.rss.source.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import io.legado.app.model.Debug
import io.legado.app.ui.about.MarkdownSheet
import io.legado.app.ui.book.source.debug.BookSourceDebugFilter
import io.legado.app.ui.book.source.debug.BookSourceDebugStatus
import io.legado.app.ui.book.source.debug.DebugChipRow
import io.legado.app.ui.book.source.debug.DebugEntryCard
import io.legado.app.ui.theme.LegadoTheme
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

@Composable
fun RssSourceDebugScreen(
    state: RssSourceDebugUiState,
    onIntent: (RssSourceDebugIntent) -> Unit,
    onBack: () -> Unit
) {
    val scroll = GlassTopAppBarDefaults.defaultScrollBehavior();
    val list = rememberLazyListState()
    val visibleExamples = remember(state.examples, state.target) {
        state.examples.filter { it.target == state.target }.take(8)
    }
    val shown = state.entries.filter {
        when (state.filter) {
            BookSourceDebugFilter.All -> true
            BookSourceDebugFilter.Messages -> it.kind == Debug.EventKind.Message || it.kind == Debug.EventKind.Completed
            BookSourceDebugFilter.Sources -> it.kind.isSourcePayload
            BookSourceDebugFilter.Errors -> it.kind == Debug.EventKind.Error
        }
    }
    LaunchedEffect(shown.size) {
        if (state.status == BookSourceDebugStatus.Running && shown.isNotEmpty()) list.animateScrollToItem(
            shown.lastIndex
        )
    }
    AppScaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = "调试",
                navigationIcon = { TopBarNavigationButton(onClick = onBack) },
                actions = {
                    TopBarActionButton(
                        { onIntent(RssSourceDebugIntent.Clear) },
                        Icons.Default.ClearAll,
                        "清空日志"
                    )
                },
                scrollBehavior = scroll,
            )
        },
        floatingActionButton = {
            AppFloatingActionButton(
                onClick = { onIntent(if (state.status == BookSourceDebugStatus.Running) RssSourceDebugIntent.Stop else RssSourceDebugIntent.Start) },
                icon = if (state.status == BookSourceDebugStatus.Running) Icons.Default.Stop else Icons.Default.PlayArrow,
                tooltipText = if (state.status == BookSourceDebugStatus.Running) "停止" else "开始调试",
            )
        },
    ) { padding ->
        LazyColumn(
            state = list, modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                16.dp,
                padding.calculateTopPadding() + 12.dp,
                16.dp,
                padding.calculateBottomPadding() + 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item("controls") {
                GlassCard {
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            AppText(
                                text = state.sourceName,
                                style = LegadoTheme.typography.labelMediumEmphasized,
                            )
                        }
                        DebugChipRow {
                            items(RssSourceDebugTarget.entries, key = { it.name }) { target ->
                                ToggleChip(
                                    target.label,
                                    state.target == target,
                                    { onIntent(RssSourceDebugIntent.SelectTarget(target)) })
                            }
                        }
                        AppTextField(
                            value = state.query,
                            onValueChange = { onIntent(RssSourceDebugIntent.SetQuery(it)) },
                            modifier = Modifier.fillMaxWidth(),
                            label = state.target.hint,
                            maxLines = 4,
                        )
                        if (visibleExamples.isNotEmpty()) {
                            DebugChipRow {
                                items(visibleExamples, key = { "${it.target}:${it.value}" }) { example ->
                                    ToggleChip(
                                        example.title,
                                        state.query == example.value,
                                        { onIntent(RssSourceDebugIntent.UseExample(example)) })
                                }
                            }
                        }
                    }
                }
            }
            item("filters") {
                DebugChipRow {
                    items(BookSourceDebugFilter.entries, key = { it.name }) { filter ->
                        ToggleChip(
                            filter.label,
                            state.filter == filter,
                            { onIntent(RssSourceDebugIntent.SelectFilter(filter)) })
                    }
                }
            }
            if (shown.isEmpty()) item("empty") {
                EmptyMessage(
                    message = "输入内容并开始调试",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                )
            }
            items(shown, key = { it.id }) { entry ->
                DebugEntryCard(
                    entry = entry,
                    modifier = Modifier.animateItem(),
                    onClick = { onIntent(RssSourceDebugIntent.ShowEntry(entry.id)) },
                )
            }
        }
    }
    val selected = state.entries.firstOrNull { it.id == state.selectedEntryId }
    MarkdownSheet(
        show = selected != null,
        title = "日志详情",
        content = selected?.message.orEmpty(),
        onDismissRequest = { onIntent(RssSourceDebugIntent.DismissEntry) },
    )
}

private val RssSourceDebugTarget.label
    get() = when (this) {
        RssSourceDebugTarget.Search -> "搜索"; RssSourceDebugTarget.Sort -> "分类"; RssSourceDebugTarget.Content -> "内容"
    }
private val RssSourceDebugTarget.hint
    get() = when (this) {
        RssSourceDebugTarget.Search -> "搜索关键字"; RssSourceDebugTarget.Sort -> "分类 URL"; RssSourceDebugTarget.Content -> "内容页 URL 或 @js 链接"
    }
private val BookSourceDebugFilter.label
    get() = when (this) {
        BookSourceDebugFilter.All -> "全部"; BookSourceDebugFilter.Messages -> "过程"; BookSourceDebugFilter.Sources -> "响应"; BookSourceDebugFilter.Errors -> "错误"
    }
