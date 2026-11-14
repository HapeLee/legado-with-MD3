package io.legado.app.ui.widget.components.list

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults.ScreenOffset
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.legado.app.R
import io.legado.app.ui.widget.components.AppFloatingActionButton
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.SelectionActions
import io.legado.app.ui.widget.components.SelectionBottomBar
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.menu.AppActionGroup
import io.legado.app.ui.widget.components.topbar.DynamicTopAppBar
import kotlinx.collections.immutable.ImmutableList
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarScrollBehavior

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> ListScaffold(
    title: String,
    state: ListUiState<T>,
    subtitle: String? = null,
    onBackClick: (() -> Unit)? = null,
    backNavigationIcon: ImageVector = AppIcons.Back,
    showSearchAction: Boolean = true,
    onSearchToggle: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: (String) -> Unit = {},
    searchTrailingIcon: @Composable (() -> Unit)? = null,
    searchPlaceholder: String? = null,
    topBarActions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable (ColumnScope.(GlassTopAppBarScrollBehavior) -> Unit)? = null,
    dropDownMenuContent: @Composable (ColumnScope.(dismiss: () -> Unit) -> Unit)? = null,
    /** 声明式溢出菜单；与 [dropDownMenuContent] 二选一，非空时优先。 */
    menuGroups: ImmutableList<AppActionGroup>? = null,
    onMenuAction: (String) -> Unit = {},
    onClearSelection: (() -> Unit)? = null,
    selectionActions: SelectionActions? = null,
    /**
     * 选择模式下是否保留顶栏右侧 actions 区（搜索 / [topBarActions] / 更多菜单），
     * 默认关闭；详见 [DynamicTopAppBar]。需要跨分组多选的书架管理会打开它。
     */
    keepActionsInSelection: Boolean = false,
    /** 选择态自定义标题，跨分组多选时默认计数口径会失真，详见 [DynamicTopAppBar]。 */
    selectionTitle: String? = null,
    onAddClick: (() -> Unit)? = null,
    floatingActionButton: @Composable () -> Unit = {
        onAddClick?.let { onClick ->
            AppFloatingActionButton(
                onClick = onClick,
                modifier = Modifier.animateFloatingActionButton(
                    visible = state.selectedIds.isEmpty(),
                    alignment = Alignment.BottomEnd,
                ),
                tooltipText = stringResource(R.string.add),
                icon = Icons.Default.Add
            )
        }
    },
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    scrollBehavior: GlassTopAppBarScrollBehavior? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    val scrollBehavior = scrollBehavior ?: GlassTopAppBarDefaults.defaultScrollBehavior()
    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .padding(bottom = 72.dp)
            )
        },
        topBar = {
            DynamicTopAppBar(
                title = title,
                subtitle = subtitle,
                state = state,
                scrollBehavior = scrollBehavior,
                onBackClick = onBackClick,
                backNavigationIcon = backNavigationIcon,
                showSearchAction = showSearchAction,
                onSearchToggle = onSearchToggle,
                onSearchQueryChange = onSearchQueryChange,
                onSearchSubmit = onSearchSubmit,
                searchTrailingIcon = searchTrailingIcon,
                searchPlaceholder = searchPlaceholder,
                onClearSelection = { onClearSelection?.invoke() ?: selectionActions?.onClearSelection?.invoke() },
                keepActionsInSelection = keepActionsInSelection,
                selectionTitle = selectionTitle,
                topBarActions = topBarActions,
                dropDownMenuContent = dropDownMenuContent,
                menuGroups = menuGroups,
                onMenuAction = onMenuAction,
                bottomContent = bottomContent
            )
        },
        floatingActionButton = floatingActionButton,
        contentWindowInsets = contentWindowInsets,
        // 多选栏是页面级浮层：必须画在正文采样源之外，否则 COUI 玻璃会采样到自己上（native 闪退）。
        overlay = {
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
    ) { paddingValues ->
        content(paddingValues)
    }
}
