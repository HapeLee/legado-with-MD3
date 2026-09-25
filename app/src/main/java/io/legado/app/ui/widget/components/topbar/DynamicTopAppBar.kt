package io.legado.app.ui.widget.components.topbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import io.legado.app.R
import io.legado.app.ui.theme.adaptiveHorizontalPadding
import io.legado.app.ui.widget.components.SearchBar
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.list.ListUiState
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DynamicTopAppBar(
    title: String,
    subtitle: String? = null,
    state: ListUiState<T>,
    scrollBehavior: GlassTopAppBarScrollBehavior,
    onBackClick: (() -> Unit)? = null,
    backNavigationIcon: ImageVector = AppIcons.Back,
    showSearchAction: Boolean = true,
    onSearchToggle: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: (String) -> Unit = {},
    searchPlaceholder: String?,
    searchLeadingIcon: ImageVector = Icons.Default.Search,
    searchTrailingIcon: @Composable (() -> Unit)? = null,
    searchDropdownMenu: (@Composable (onDismiss: () -> Unit) -> Unit)? = null,
    onClearSelection: () -> Unit,
    topBarActions: @Composable RowScope.() -> Unit = {},
    /**
     * 选择模式下是否保留顶栏右侧 actions 区（搜索 / [topBarActions] / 更多菜单）。
     *
     * 默认关闭：多数列表页进入选择态后只需要「退出选择」和底部操作栏。
     * 书架管理需要打开 —— 它支持跨分组多选，选中后还要继续搜索、切分组才能挑别的书。
     */
    keepActionsInSelection: Boolean = false,
    /**
     * 选择态标题。跨分组多选时 [ListUiState.selectedIds] 可能多于当前列表条目，
     * 默认的 "Selected 已选/总数" 口径会失真，调用方可以在这里给出自己的文案；
     * 为 null 时退回默认计数。
     */
    selectionTitle: String? = null,
    dropDownMenuContent: @Composable (ColumnScope.(dismiss: () -> Unit) -> Unit)? = null,
    bottomContent: @Composable (ColumnScope.(GlassTopAppBarScrollBehavior) -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }
    val isSelecting = state.selectedIds.isNotEmpty()


    GlassMediumFlexibleTopAppBar(
        modifier = Modifier
            .fillMaxWidth(),
        title = when {
            state.isLoading -> stringResource(R.string.list_loading_title)
            isSelecting -> selectionTitle ?: stringResource(
                R.string.list_selected_count,
                state.selectedIds.size,
                state.items.size
            )
            else -> title
        },
        useCharMode = isSelecting || state.isLoading,
        subtitle = subtitle,
        navigationIcon = {
            if (isSelecting || onBackClick != null) {
                TopBarNavigationButton(
                    onClick = { if (isSelecting) onClearSelection() else onBackClick?.invoke() },
                    imageVector = if (isSelecting) AppIcons.Close else backNavigationIcon,
                    contentDescription = stringResource(
                        if (isSelecting) R.string.cancel_select else R.string.back
                    )
                )
            }
        },
        actions = {
            // 选择态默认收起整个 actions 区，只有显式声明的调用方才继续显示
            if (!isSelecting || keepActionsInSelection) {
                if (showSearchAction) {
                    TopBarActionButton(
                        onClick = { onSearchToggle(!state.isSearch) },
                        imageVector = AppIcons.Search,
                        contentDescription = stringResource(R.string.search)
                    )
                }

                topBarActions()

                dropDownMenuContent?.let { content ->
                    Box {
                        TopBarActionButton(
                            onClick = { showMenu = true },
                            imageVector = AppIcons.MoreVert,
                            contentDescription = stringResource(R.string.more_menu)
                        )
                        RoundDropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) { dismiss ->
                            content(dismiss)
                        }
                    }
                }
            }
        },
        scrollBehavior = scrollBehavior,
        bottomContent = {
            AnimatedVisibility(
                modifier = Modifier
                    .adaptiveHorizontalPadding(),
                // 保留 actions 时必须同步保留输入框，否则选择态点搜索按钮会没有任何反馈
                visible = state.isSearch && (!isSelecting || keepActionsInSelection),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                SearchBar(
                    query = state.searchKey,
                    onQueryChange = onSearchQueryChange,
                    onSearch = onSearchSubmit,
                    placeholder = searchPlaceholder,
                    trailingIcon = searchTrailingIcon,
                    dropdownMenu = searchDropdownMenu
                )
            }

            bottomContent?.invoke(this, scrollBehavior)
        }
    )
}
