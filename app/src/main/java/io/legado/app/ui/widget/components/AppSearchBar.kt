package io.legado.app.ui.widget.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.ExpandedFullScreenContainedSearchBar
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.SearchBarValue.Collapsed
import androidx.compose.material3.SearchBarValue.Expanded
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.InputField as MiuixInputField
import top.yukonga.miuix.kmp.basic.SearchBar as MiuixSearchBar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppSearchBar(
    modifier: Modifier = Modifier,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    label: String,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val isMiuix = ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)
    if (isMiuix) {
        var internalExpanded by remember { mutableStateOf(expanded) }
        var internalQuery by remember { mutableStateOf(query) }

        LaunchedEffect(expanded) {
            internalExpanded = expanded
        }

        LaunchedEffect(query) {
            if (query != internalQuery) {
                internalQuery = query
            }
        }

        MiuixSearchBar(
            modifier = modifier,
            inputField = {
                MiuixInputField(
                    query = internalQuery,
                    onQueryChange = { newQuery ->
                        if (newQuery.isNotEmpty() || internalExpanded) {
                            internalQuery = newQuery
                            onQueryChange(newQuery)
                        }
                    },
                    onSearch = {
                        onSearch(it)
                        onExpandedChange(false)
                    },
                    expanded = internalExpanded,
                    onExpandedChange = { newExpanded ->
                        internalExpanded = newExpanded
                        onExpandedChange(newExpanded)
                    },
                    label = label,
                    leadingIcon = leadingIcon,
                    trailingIcon = trailingIcon,
                )
            },
            expanded = internalExpanded,
            onExpandedChange = onExpandedChange,
            content = content,
        )
        return
    }

    val initialSearchBarValue = remember {
        if (expanded) SearchBarValue.Expanded else SearchBarValue.Collapsed
    }
    val searchBarState = rememberSearchBarState(initialValue = initialSearchBarValue)
    val textFieldState = rememberTextFieldState(initialText = query)
    val scope = rememberCoroutineScope()
    val latestQuery by rememberUpdatedState(query)
    val latestExpanded by rememberUpdatedState(expanded)
    val latestOnQueryChange by rememberUpdatedState(onQueryChange)
    val latestOnExpandedChange by rememberUpdatedState(onExpandedChange)
    val latestOnSearch by rememberUpdatedState(onSearch)

    LaunchedEffect(query) {
        if (query != textFieldState.text.toString()) {
            textFieldState.setTextAndPlaceCursorAtEnd(query)
        }
    }

    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }
            .distinctUntilChanged()
            .collect { newQuery ->
                if (newQuery != latestQuery) {
                    latestOnQueryChange(newQuery)
                }
            }
    }

    LaunchedEffect(expanded) {
        if (expanded && searchBarState.targetValue != Expanded) {
            searchBarState.animateToExpanded()
        } else if (!expanded && searchBarState.targetValue != Collapsed) {
            searchBarState.animateToCollapsed()
        }
    }

    LaunchedEffect(searchBarState) {
        snapshotFlow { searchBarState.currentValue == Expanded }
            .distinctUntilChanged()
            .collect { isExpanded ->
                if (isExpanded != latestExpanded) {
                    latestOnExpandedChange(isExpanded)
                }
            }
    }

    val inputField: @Composable () -> Unit = {
        SearchBarDefaults.InputField(
            textFieldState = textFieldState,
            searchBarState = searchBarState,
            onSearch = { keyword ->
                latestOnSearch(keyword)
                scope.launch { searchBarState.animateToCollapsed() }
            },
            placeholder = placeholder,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
        )
    }

    SearchBar(
        modifier = modifier.padding(horizontal = 16.dp),
        state = searchBarState,
        inputField = inputField
    )

    ExpandedFullScreenSearchBar(
        state = searchBarState,
        inputField = inputField,
        content = content
    )
}
