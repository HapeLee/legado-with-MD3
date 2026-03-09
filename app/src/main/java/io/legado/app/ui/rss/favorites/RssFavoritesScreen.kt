package io.legado.app.ui.rss.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.legado.app.R
import io.legado.app.ui.rss.read.ReadRssActivity
import io.legado.app.ui.widget.components.EmptyMessageView
import io.legado.app.ui.widget.components.card.SelectionItemCard
import io.legado.app.ui.widget.components.divider.PillDivider
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.rules.RuleListScaffold
import io.legado.app.utils.startActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssFavoritesScreen(
    onBackClick: () -> Unit,
    viewModel: RssFavoritesViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(groups) {
        if (state.currentGroup.isEmpty() && groups.isNotEmpty()) {
            viewModel.onGroupChange(groups.first())
        }
    }

    RuleListScaffold(
        title = stringResource(R.string.favorite),
        state = state,
        onBackClick = onBackClick,
        onSearchToggle = viewModel::onSearchToggle,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onClearSelection = viewModel::clearSelection,
        onSelectAll = viewModel::selectAll,
        onSelectInvert = viewModel::selectInvert,
        onDeleteSelected = { viewModel.deleteSelected() },
        snackbarHostState = snackbarHostState,
        selectionSecondaryActions = emptyList(),
        topBarActions = {},
        dropDownMenuContent = { dismiss ->
            RoundDropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Group, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.all))
                    }
                },
                onClick = {
                    viewModel.onGroupChange("")
                    dismiss()
                }
            )
            groups.forEach { group ->
                RoundDropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.AutoMirrored.Outlined.Label,
                                null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(group)
                        }
                    },
                    onClick = {
                        viewModel.onGroupChange(group)
                        dismiss()
                    }
                )
            }
            PillDivider()
            RoundDropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DeleteSweep, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.delete_select_group))
                    }
                },
                onClick = {
                    viewModel.deleteGroup(state.currentGroup)
                    dismiss()
                }
            )
            RoundDropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.delete_all))
                    }
                },
                onClick = {
                    viewModel.deleteAll()
                    dismiss()
                }
            )
        },
        bottomContent = {
            if (groups.size > 1) {
                PrimaryScrollableTabRow(
                    selectedTabIndex = groups.indexOf(state.currentGroup).coerceAtLeast(0),
                    edgePadding = 16.dp,
                    divider = {}
                ) {
                    groups.forEach { group ->
                        Tab(
                            selected = state.currentGroup == group,
                            onClick = { viewModel.onGroupChange(group) },
                            text = { Text(group) }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        if (state.items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                EmptyMessageView(
                    message = "还没有收藏订阅！",
                    isLoading = state.isLoading
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = paddingValues,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.items, key = { "${it.origin}|${it.link}" }) { rssStar ->
                    val id = "${rssStar.origin}|${rssStar.link}"
                    val isSelected = state.selectedIds.contains(id)
                    SelectionItemCard(
                        title = rssStar.title,
                        subtitle = rssStar.pubDate,
                        isSelected = isSelected,
                        inSelectionMode = state.selectedIds.isNotEmpty(),
                        onToggleSelection = {
                            if (state.selectedIds.isNotEmpty()) {
                                viewModel.toggleSelection(rssStar)
                            } else {
                                context.startActivity<ReadRssActivity> {
                                    putExtra("title", rssStar.title)
                                    putExtra("origin", rssStar.origin)
                                    putExtra("link", rssStar.link)
                                }
                            }
                        },
                        trailingAction = {
                            if (!rssStar.image.isNullOrBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(rssStar.image)
                                        .setHeader("sourceOrigin", rssStar.origin)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(width = 80.dp, height = 50.dp)
                                        .padding(start = 8.dp)
                                        .clip(MaterialTheme.shapes.small),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        },
                        dropdownContent = { dismiss ->
                            RoundDropdownMenuItem(
                                text = { Text(stringResource(R.string.delete)) },
                                onClick = {
                                    viewModel.deleteStar(rssStar)
                                    dismiss()
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}
