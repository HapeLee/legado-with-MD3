package io.legado.app.ui.book.info

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.domain.model.BookSearchScope
import io.legado.app.domain.usecase.ChangeSourceMigrationOptions
import io.legado.app.ui.book.changecover.ChangeCoverIntent
import io.legado.app.ui.book.changecover.ChangeCoverViewModel
import io.legado.app.ui.book.changecover.CoverOption
import io.legado.app.ui.book.group.GroupEditSheet
import io.legado.app.ui.book.search.ScopeSelectSheet
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.modalBottomSheet.SheetAction
import kotlinx.collections.immutable.persistentListOf
import io.legado.app.ui.theme.LocalAppUiConfiguration
import io.legado.app.ui.widget.components.button.series.SmallPlainButton
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.card.SelectionItemCard
import io.legado.app.ui.widget.components.changeSource.ChangeSourceSheet
import io.legado.app.ui.widget.components.checkBox.AppCheckbox
import io.legado.app.ui.widget.components.image.cover.CoilBookCover
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.progressIndicator.AppLinearProgressIndicator
import io.legado.app.ui.widget.components.tabRow.CardTabRow
import io.legado.app.ui.widget.components.text.AppText
import org.koin.androidx.compose.koinViewModel

@Composable
fun WebFileSheet(
    show: Boolean,
    files: List<BookInfoWebFile>,
    title: String,
    onDismissRequest: () -> Unit,
    onSelect: (BookInfoWebFile) -> Unit,
) {
    AppModalBottomSheet(show = show, onDismissRequest = onDismissRequest, title = title) {
        if (files.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp), contentAlignment = Alignment.Center
            ) {
                Text(text = stringResource(R.string.empty))
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(files, key = { it.name }) { file ->
                    GlassCard(onClick = { onSelect(file) }) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(if (file.name.endsWith("zip") || file.name.endsWith("rar") || file.name.endsWith("7z")) Icons.Outlined.FolderZip else Icons.Outlined.Image, null)
                            Text(text = file.name, modifier = Modifier.weight(1f), style = LegadoTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun GroupSelectSheet(
    show: Boolean,
    groups: List<BookGroup>,
    currentGroupId: Long,
    onDismissRequest: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    var selectedGroupId by remember(currentGroupId, show) { mutableLongStateOf(currentGroupId) }
    var editingGroup by remember(show) { mutableStateOf<BookGroup?>(null) }
    var showAddGroup by remember(show) { mutableStateOf(false) }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.group_select),
        startSheetAction = SheetAction(
            onClick = { showAddGroup = true },
            icon = Icons.Default.Add,
            contentDescription = stringResource(R.string.group_add),
        ),
        endSheetAction = SheetAction(
            onClick = { onConfirm(selectedGroupId) },
            icon = Icons.Default.Check,
            contentDescription = stringResource(R.string.confirm),
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            LazyColumn(
                modifier = Modifier.heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(groups, key = { it.groupId }) { group ->
                    val isSelected = (selectedGroupId and group.groupId) != 0L
                    SelectionItemCard(
                        title = group.groupName,
                        isSelected = isSelected,
                        onToggleSelection = {
                            selectedGroupId = if (isSelected) {
                                selectedGroupId - group.groupId
                            } else {
                                selectedGroupId + group.groupId
                            }
                        },
                        leadingContent = {
                            AppCheckbox(
                                checked = isSelected,
                                onCheckedChange = {
                                    selectedGroupId = if (it) {
                                        selectedGroupId + group.groupId
                                    } else {
                                        selectedGroupId - group.groupId
                                    }
                                }
                            )
                        },
                        trailingAction = {
                            SmallPlainButton(
                                onClick = { editingGroup = group },
                                icon = Icons.Default.Edit,
                                contentDescription = stringResource(R.string.edit)
                            )
                        },
                        containerColor = LegadoTheme.colorScheme.surfaceContainerLow
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
    GroupEditSheet(
        show = showAddGroup || editingGroup != null,
        group = editingGroup,
        onDismissRequest = {
            showAddGroup = false
            editingGroup = null
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChangeCoverSheet(
    show: Boolean,
    name: String,
    author: String,
    onDismissRequest: () -> Unit,
    onSelect: (String) -> Unit,
    viewModel: ChangeCoverViewModel = koinViewModel(key = "cover-$name-$author"),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val groups by viewModel.enabledGroups.collectAsStateWithLifecycle(initialValue = emptyList())
    val enabledSources by viewModel.enabledSources
        .collectAsStateWithLifecycle(initialValue = emptyList<BookSourcePart>())
    val isDark = LegadoTheme.isDark
    val useDefaultCover = LocalAppUiConfiguration.current.cover.useDefaultCover
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }

    // 编辑页在 show=false 时同样组合本 Sheet，因此只有真正打开面板才读缓存/自动补搜，
    // 隐藏时停止搜索，避免对全部启用书源执行搜索规则与登录检测脚本。
    LaunchedEffect(show, name, author, isDark) {
        if (show) {
            viewModel.onIntent(ChangeCoverIntent.Shown(name, author, isDark))
        }
    }
    DisposableEffect(show) {
        onDispose {
            viewModel.onIntent(ChangeCoverIntent.Hidden)
        }
    }
    LaunchedEffect(state.pendingSelection) {
        state.pendingSelection?.let { coverUrl ->
            onSelect(coverUrl)
            viewModel.onIntent(ChangeCoverIntent.SelectionConsumed)
        }
    }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.change_cover_source),
        endSheetActions = persistentListOf(
            SheetAction(
                onClick = { viewModel.onIntent(ChangeCoverIntent.ToggleSearch) },
                icon = if (state.isSearching) Icons.Default.MoreVert else Icons.Default.Refresh,
                contentDescription = stringResource(
                    if (state.isSearching) R.string.more_menu else R.string.refresh
                ),
            ),
            SheetAction(
                onClick = { showFilterSheet = true },
                icon = Icons.Default.FilterList,
                contentDescription = stringResource(R.string.screen),
            ),
        ),
    ) {
        if (state.isSearching) {
            AppLinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(12.dp))
        }
        if (useDefaultCover) {
            AppText(
                text = stringResource(R.string.use_default_cover_s),
                style = LegadoTheme.typography.bodySmall
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                CoverSectionTitle(stringResource(R.string.cover_albums))
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                val defaultCoverLabel = stringResource(R.string.default_cover)
                val tabTitles = remember(state.albums, defaultCoverLabel) {
                    listOf(defaultCoverLabel) + state.albums.map { it.name }
                }
                CardTabRow(
                    tabTitles = tabTitles,
                    selectedTabIndex = state.selectedTabIndex.coerceIn(0, tabTitles.lastIndex),
                    onTabSelected = { index ->
                        viewModel.onIntent(ChangeCoverIntent.SelectTab(index))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    // 标签少时等分占满宽度；标签多时启用横划，避免被挤成省略号。
                    scrollable = tabTitles.size > 3,
                )
            }
            if (state.selectedTabIndex == 0) {
                // 默认封面 tab 只给一张占位封面；下方不显示重复文案。
                state.options.firstOrNull { it.isDefault }?.let { option ->
                    item(key = option.key) {
                        CoverOptionItem(option, name, author, showLabel = false) {
                            viewModel.onIntent(ChangeCoverIntent.SelectOption(option))
                        }
                    }
                }
            } else {
                val albumOptions = state.options.filter { it.albumPath != null }
                items(albumOptions, key = { it.key }) { option ->
                    CoverOptionItem(option, name, author, showLabel = false) {
                        viewModel.onIntent(ChangeCoverIntent.SelectOption(option))
                    }
                }
                if (albumOptions.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        AppText(
                            text = stringResource(R.string.cover_picker_album_no_images),
                            style = LegadoTheme.typography.bodySmall
                        )
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                CoverSectionTitle(stringResource(R.string.cover_picker_online_section))
            }
            val onlineOptions = state.options.filter { !it.isDefault && it.albumPath == null }
            items(onlineOptions, key = { it.key }) { option ->
                CoverOptionItem(option, name, author) {
                    viewModel.onIntent(ChangeCoverIntent.SelectOption(option))
                }
            }
            if (onlineOptions.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    AppText(
                        text = stringResource(R.string.empty),
                        style = LegadoTheme.typography.bodySmall
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    // 复用换源页的范围选择组件：onApplyScope 启用草稿模式，确认时才提交，
    // 因此下面三个即时切换回调不会被调用。
    ScopeSelectSheet(
        show = showFilterSheet,
        onDismissRequest = { showFilterSheet = false },
        isAll = state.scope.isAll,
        onSelectAll = {},
        groups = groups,
        selectedGroups = state.scope.displayNames,
        onToggleGroup = {},
        sources = enabledSources,
        selectedSources = state.scope.sourceUrls,
        onToggleSource = {},
        isSourceScope = state.scope.isSource,
        onApplyScope = { selection ->
            viewModel.onIntent(
                ChangeCoverIntent.ApplyScope(
                    groupNames = selection.groupNames,
                    sources = selection.sources.map {
                        BookSearchScope.ScopeSourceItem(it.bookSourceName, it.bookSourceUrl)
                    },
                    isSourceScope = selection.isSourceScope,
                )
            )
            showFilterSheet = false
        }
    )
}

@Composable
private fun CoverSectionTitle(text: String) {
    AppText(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        style = LegadoTheme.typography.labelMediumEmphasized,
    )
}

@Composable
private fun CoverOptionItem(
    option: CoverOption,
    name: String,
    author: String,
    showLabel: Boolean = true,
    onClick: () -> Unit,
) {
    val selectLabel = stringResource(R.string.cover_picker_select_cover, option.label)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .semantics(mergeDescendants = true) {
                contentDescription = selectLabel
                role = Role.Button
            }
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        CoilBookCover(
            name = name,
            author = author,
            path = option.coverUrl,
            sourceOrigin = option.origin.takeIf { it.isNotEmpty() },
            modifier = Modifier
                .width(112.dp)
                .aspectRatio(5f / 7f),
        )
        if (showLabel) {
            AppText(
                text = option.label,
                style = LegadoTheme.typography.labelSmallEmphasized,
                maxLines = 2
            )
        }
    }
}

@Composable
fun ChangeSourceSheet(
    data: Book?,
    onDismissRequest: () -> Unit,
    onReplace: (BookSource, Book, List<BookChapter>, ChangeSourceMigrationOptions) -> Unit,
    onAddAsNew: (Book, List<BookChapter>) -> Unit,
) {
    var cachedData by remember { mutableStateOf(data) }

    if (data != null) {
        cachedData = data
    }

    val currentData = cachedData
    if (currentData != null) {
        ChangeSourceSheet(
            show = data != null,
            oldBook = currentData,
            onDismissRequest = onDismissRequest,
            onReplace = onReplace,
            onAddAsNew = onAddAsNew,
        )
    }
}
