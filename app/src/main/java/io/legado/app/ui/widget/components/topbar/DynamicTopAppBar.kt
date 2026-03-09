package io.legado.app.ui.widget.components.topbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import io.legado.app.ui.widget.components.AdaptiveAnimatedText
import io.legado.app.ui.widget.components.AnimatedTextLine
import io.legado.app.ui.widget.components.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.SearchBarSection
import io.legado.app.ui.widget.components.button.TopBarActionButton
import io.legado.app.ui.widget.components.button.TopbarNavigationButton
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.rules.ListUiState


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DynamicTopAppBar(
    title: String,
    subtitle: String? = null,
    state: ListUiState<T>,
    scrollBehavior: TopAppBarScrollBehavior,
    onBackClick: (() -> Unit)? = null,
    onSearchToggle: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    searchPlaceholder: String,
    searchLeadingIcon: ImageVector = Icons.Default.Search,
    searchTrailingIcon: @Composable (() -> Unit)? = null,
    searchDropdownMenu: (@Composable (onDismiss: () -> Unit) -> Unit)? = null,
    onClearSelection: () -> Unit,
    topBarActions: @Composable RowScope.() -> Unit = {},
    dropDownMenuContent: @Composable (ColumnScope.(dismiss: () -> Unit) -> Unit)? = null,
    bottomContent: @Composable (ColumnScope.(TopAppBarScrollBehavior) -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }
    val isSelecting = state.selectedIds.isNotEmpty()

    Column {
        GlassMediumFlexibleTopAppBar(
            title = {
                val titleText = when {
                    state.isLoading -> "请稍后..."
                    isSelecting -> "已选择 ${state.selectedIds.size}/${state.items.size}"
                    else -> title
                }
                AdaptiveAnimatedText(
                    text = titleText,
                    useCharMode = isSelecting || state.isLoading,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            subtitle = subtitle?.let {
                { AnimatedTextLine(text = it) }
            },
            navigationIcon = {
                if (isSelecting || onBackClick != null) {
                    TopbarNavigationButton(
                        onClick = { if (isSelecting) onClearSelection() else onBackClick?.invoke() },
                        imageVector = if (isSelecting) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = if (isSelecting) "取消选择" else "返回"
                    )
                }
            },
            actions = {
                if (!isSelecting) {
                    TopBarActionButton(
                        onClick = { onSearchToggle(!state.isSearch) },
                        imageVector = Icons.Default.Search,
                        contentDescription = "搜索"
                    )

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
            scrollBehavior = scrollBehavior
        )

        AnimatedVisibility(
            visible = state.isSearch && !isSelecting,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            SearchBarSection(
                query = state.searchKey,
                onQueryChange = onSearchQueryChange,
                placeholder = searchPlaceholder,
                leadingIcon = { Icon(searchLeadingIcon, null) },
                trailingIcon = searchTrailingIcon,
                dropdownMenu = searchDropdownMenu
            )
        }

        bottomContent?.invoke(this, scrollBehavior)
    }
}
