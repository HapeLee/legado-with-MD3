package io.legado.app.ui.book.info

import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material.icons.filled.PauseCircleOutline
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.domain.usecase.ChangeSourceMigrationOptions
import io.legado.app.ui.book.changecover.ChangeCoverViewModel
import io.legado.app.ui.book.changesource.ChangeBookSourceComposeViewModel
import io.legado.app.ui.book.changesource.ChangeSourceConfig
import io.legado.app.ui.book.changesource.ChangeSourceMigrationOptionsSheet
import io.legado.app.ui.book.group.GroupEditSheet
import io.legado.app.ui.book.search.ScopeSelectSheet
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.book.source.manage.BookSourceActivity
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.series.MediumPlainButton
import io.legado.app.ui.widget.components.button.series.SmallPlainButton
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.card.SelectionItemCard
import io.legado.app.ui.widget.components.changeSource.ChangeSourceSheet
import io.legado.app.ui.widget.components.checkBox.AppCheckbox
import io.legado.app.ui.widget.components.image.cover.CoilBookCover
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.progressIndicator.AppCircularProgressIndicator
import io.legado.app.ui.widget.components.progressIndicator.AppLinearProgressIndicator
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.StartActivityContract
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
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
        startAction = {
            MediumPlainButton(
                onClick = { showAddGroup = true },
                icon = Icons.Default.Add
            )
        },
        endAction = {
            MediumPlainButton(
                onClick = { onConfirm(selectedGroupId) },
                icon = Icons.Default.Check
            )
        }
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
                                icon = Icons.Default.Edit
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
    val items by viewModel.dataFlow.collectAsStateWithLifecycle(initialValue = emptyList<SearchBook>())
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()

    LaunchedEffect(name, author) {
        viewModel.initData(name, author)
    }
    DisposableEffect(show) {
        onDispose {
            viewModel.stopSearch()
        }
    }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.change_cover_source),
        endAction = {
            MediumPlainButton(
                onClick = { viewModel.startOrStopSearch() },
                icon = if (isSearching) Icons.Default.MoreVert else Icons.Default.Refresh
            )
        }
    ) {
        if (isSearching) {
            AppLinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(12.dp))
        }
        LazyVerticalGrid(columns = GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { it.bookUrl + it.originName }) { item ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            onSelect(item.coverUrl.orEmpty())
                        }
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CoilBookCover(
                        name = item.name,
                        author = item.author,
                        path = item.coverUrl,
                        sourceOrigin = item.origin,
                        modifier = Modifier
                            .width(112.dp)
                            .aspectRatio(5f / 7f),
                    )
                    AppText(
                        text = item.originName,
                        style = LegadoTheme.typography.labelSmallEmphasized,
                        maxLines = 2
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
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
