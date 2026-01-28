package io.legado.app.ui.widget.components.rules

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingToolbarDefaults.ScreenOffset
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.legado.app.R
import io.legado.app.ui.widget.components.ActionItem
import io.legado.app.ui.widget.components.AnimatedText
import io.legado.app.ui.widget.components.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.SearchBarSection
import io.legado.app.ui.widget.components.SelectionBottomBar
import io.legado.app.ui.widget.components.button.SmallTopBarButton

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> RuleListScaffold(
    title: String,
    state: RuleActionState<T>,
    onBackClick: () -> Unit,
    onSearchToggle: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    searchPlaceholder: String,
    searchLeadingIcon: ImageVector = Icons.Default.Search,
    searchTrailingIcon: @Composable (() -> Unit)? = null,
    searchDropdownMenu: (@Composable (onDismiss: () -> Unit) -> Unit)? = null,
    topBarActions: @Composable RowScope.() -> Unit = {},
    stickySubContent: @Composable (ColumnScope.() -> Unit)? = null,
    dropDownMenuContent: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit = {},
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onSelectInvert: () -> Unit,
    selectionSecondaryActions: List<ActionItem>,
    onDeleteSelected: (Set<Any>) -> Unit,
    onAddClick: (() -> Unit)? = null,
    floatingActionButton: @Composable () -> Unit = {
        onAddClick?.let { onClick ->
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                    TooltipAnchorPosition.Above
                ),
                tooltip = { PlainTooltip { Text("添加") } },
                state = rememberTooltipState(),
            ) {
                FloatingActionButton(
                    modifier = Modifier.animateFloatingActionButton(
                        visible = state.selectedIds.isEmpty(),
                        alignment = Alignment.BottomEnd,
                    ),
                    onClick = onClick
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
            }
        }
    },
    snackbarHostState: SnackbarHostState,
    content: @Composable (PaddingValues) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.del_msg)) },
            confirmButton = {
                OutlinedButton(
                    onClick = {
                        onDeleteSelected(state.selectedIds)
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                GlassMediumFlexibleTopAppBar(
                    title = {
                        val titleText = when {
                            state.isUploading -> "请稍后..."
                            state.selectedIds.isNotEmpty() -> "已选择 ${state.selectedIds.size}/${state.items.size}"
                            else -> title
                        }
                        AnimatedText(text = titleText)
                    },
                    navigationIcon = {
                        SmallTopBarButton(
                            onClick = {
                                if (state.selectedIds.isNotEmpty()) onClearSelection() else onBackClick()
                            },
                            imageVector = if (state.selectedIds.isNotEmpty()) {
                                Icons.Default.Close
                            } else {
                                Icons.AutoMirrored.Filled.ArrowBack
                            },
                            contentDescription = if (state.selectedIds.isNotEmpty()) "取消选择" else "返回"
                        )
                    },
                    actions = {
                        if (!state.selectedIds.isNotEmpty()) {
                            IconButton(onClick = { onSearchToggle(!state.isSearch) }) {
                                Icon(Icons.Default.Search, null)
                            }
                            topBarActions()
                            Box {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(Icons.Default.MoreVert, null)
                                }
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }) {
                                    dropDownMenuContent { showMenu = false }
                                }
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior
                )

                AnimatedVisibility(visible = state.isSearch && !state.selectedIds.isNotEmpty()) {
                    SearchBarSection(
                        query = state.searchKey,
                        onQueryChange = onSearchQueryChange,
                        placeholder = searchPlaceholder,
                        leadingIcon = { Icon(searchLeadingIcon, null) },
                        trailingIcon = searchTrailingIcon,
                        dropdownMenu = searchDropdownMenu
                    )
                }

                AnimatedVisibility(
                    visible = stickySubContent != null
                ) {
                    stickySubContent?.let { it() }
                }
            }
        },
        floatingActionButton = floatingActionButton,
        content = { paddingValues ->
            Box(modifier = Modifier.fillMaxSize()) {
                content(paddingValues)
                AnimatedVisibility(
                    visible = state.selectedIds.isNotEmpty(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = -ScreenOffset)
                        .padding(bottom = 16.dp)
                        .zIndex(1f),
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
                    SelectionBottomBar(
                        onSelectAll = onSelectAll,
                        onSelectInvert = onSelectInvert,
                        primaryAction = ActionItem(
                            text = stringResource(R.string.delete),
                            icon = { Icon(Icons.Default.Delete, null) },
                            onClick = { showDeleteConfirmDialog = true }
                        ),
                        secondaryActions = selectionSecondaryActions
                    )
                }
            }
        }
    )
}