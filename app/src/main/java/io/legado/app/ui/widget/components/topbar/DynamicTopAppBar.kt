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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import io.legado.app.ui.theme.adaptiveHorizontalPadding
import io.legado.app.ui.widget.components.SearchBar
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
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
    searchPlaceholder: String,
    searchLeadingIcon: ImageVector = Icons.Default.Search,
    searchTrailingIcon: @Composable (() -> Unit)? = null,
    searchDropdownMenu: (@Composable (onDismiss: () -> Unit) -> Unit)? = null,
    onClearSelection: () -> Unit,
    topBarActions: @Composable RowScope.() -> Unit = {},
    dropDownMenuContent: @Composable (ColumnScope.(dismiss: () -> Unit) -> Unit)? = null,
    bottomContent: @Composable (ColumnScope.(GlassTopAppBarScrollBehavior) -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }
    val isSelecting = state.selectedIds.isNotEmpty()


    GlassMediumFlexibleTopAppBar(
        modifier = Modifier
            .fillMaxWidth(),
        title = when {
            state.isLoading -> "请稍后..."
            isSelecting -> "已选择 ${state.selectedIds.size}/${state.items.size}"
            else -> title
        },
        useCharMode = isSelecting || state.isLoading,
        subtitle = subtitle,
        navigationIcon = {
            if (isSelecting || onBackClick != null) {
                TopBarNavigationButton(
                    onClick = { if (isSelecting) onClearSelection() else onBackClick?.invoke() },
                    imageVector = if (isSelecting) AppIcons.Close else backNavigationIcon,
                    contentDescription = if (isSelecting) "取消选择" else "返回"
                )
            }
        },
        actions = {
            if (!isSelecting) {
                if (showSearchAction) {
                    TopBarActionButton(
                        onClick = { onSearchToggle(!state.isSearch) },
                        imageVector = AppIcons.Search,
                        contentDescription = "搜索"
                    )
                }

                topBarActions()

                dropDownMenuContent?.let { content ->
                    Box {
                        TopBarActionButton(
                            onClick = { showMenu = true },
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "更多"
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
                visible = state.isSearch && !isSelecting,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                SearchBar(
                    query = state.searchKey,
                    onQueryChange = onSearchQueryChange,
                    onSearch = onSearchSubmit,
                    placeholder = searchPlaceholder,
                    leadingIcon = {
                        AppIcon(
                            imageVector = searchLeadingIcon,
                            contentDescription = null
                        )
                    },
                    trailingIcon = searchTrailingIcon,
                    dropdownMenu = searchDropdownMenu
                )
            }

            bottomContent?.invoke(this, scrollBehavior)
        }
    )
}

