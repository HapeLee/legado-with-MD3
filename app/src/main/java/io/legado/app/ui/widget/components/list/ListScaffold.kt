package io.legado.app.ui.widget.components.list

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingToolbarDefaults.ScreenOffset
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.chrisbanes.haze.HazeState
import io.legado.app.ui.theme.responsiveHazeEffect
import io.legado.app.ui.theme.responsiveHazeSource
import io.legado.app.ui.widget.components.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.SelectionActions
import io.legado.app.ui.widget.components.SelectionBottomBar
import io.legado.app.ui.widget.components.topbar.DynamicTopAppBar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> ListScaffold(
    title: String,
    state: ListUiState<T>,
    subtitle: String? = null,
    onBackClick: (() -> Unit)? = null,
    onSearchToggle: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    searchPlaceholder: String = "搜索...",
    topBarActions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable (ColumnScope.(TopAppBarScrollBehavior) -> Unit)? = null,
    dropDownMenuContent: @Composable (ColumnScope.(dismiss: () -> Unit) -> Unit)? = null,
    selectionActions: SelectionActions? = null,
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
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    content: @Composable (PaddingValues) -> Unit
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val hazeState = remember { HazeState() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Box(modifier = Modifier.responsiveHazeEffect(state = hazeState)) {
                DynamicTopAppBar(
                    title = title,
                    subtitle = subtitle,
                    state = state,
                    scrollBehavior = scrollBehavior,
                    onBackClick = onBackClick,
                    onSearchToggle = onSearchToggle,
                    onSearchQueryChange = onSearchQueryChange,
                    searchPlaceholder = searchPlaceholder,
                    onClearSelection = { selectionActions?.onSelectInvert?.invoke() },
                    topBarActions = topBarActions,
                    dropDownMenuContent = dropDownMenuContent,
                    bottomContent = bottomContent
                )
            }
        },
        floatingActionButton = floatingActionButton
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .responsiveHazeSource(hazeState)
        ) {
            content(paddingValues)

            AnimatedVisibility(
                visible = state.selectedIds.isNotEmpty() && selectionActions != null,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp + ScreenOffset)
                    .zIndex(1f),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                selectionActions?.let { actions ->
                    SelectionBottomBar(
                        onSelectAll = actions.onSelectAll,
                        onSelectInvert = actions.onSelectInvert,
                        primaryAction = actions.primaryAction,
                        secondaryActions = actions.secondaryActions
                    )
                }
            }
        }
    }
}