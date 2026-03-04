package io.legado.app.ui.widget.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.legado.app.ui.widget.components.button.SmallIconButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBarSection(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = "搜索...",
    leadingIcon: @Composable (() -> Unit)? = {
        SmallIconButton(
            icon = Icons.Default.Search,
            contentDescription = null,
            onClick = {})
    },
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    scrollState: LazyListState? = null,
    scope: CoroutineScope = rememberCoroutineScope(),
    trailingIcon: @Composable (() -> Unit)? = null,
    dropdownMenu: (@Composable (onDismiss: () -> Unit) -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }

    val textFieldState = rememberTextFieldState(initialText = query)

    LaunchedEffect(query) {
        if (query != textFieldState.text.toString()) {
            textFieldState.edit {
                replace(0, length, query)
            }
        }
    }

    LaunchedEffect(textFieldState.text) {
        snapshotFlow { textFieldState.text.toString() }
            .collect { newText ->
                if (newText != query) {
                    onQueryChange(newText)
                }
            }
    }

    val showScrollToTop by remember(scrollState) {
        derivedStateOf {
            (scrollState?.firstVisibleItemIndex ?: 0) > 0
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
        shape = RoundedCornerShape(32.dp),
        color = backgroundColor
    ) {
        TextField(
            state = textFieldState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = 4.dp),
            placeholder = { Text(placeholder) },
            leadingIcon = leadingIcon,
            trailingIcon = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .animateContentSize()
                ) {

                    AnimatedVisibility(
                        visible = textFieldState.text.isNotEmpty(),
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut()
                    ) {
                        SmallIconButton(
                            onClick = { textFieldState.clearText() },
                            icon = Icons.Default.Clear,
                            contentDescription = "清空输入"
                        )
                    }

                    AnimatedVisibility(
                        visible = showScrollToTop,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut()
                    ) {
                        SmallIconButton(
                            onClick = {
                                scope.launch {
                                    scrollState?.animateScrollToItem(0)
                                }
                            },
                            icon = Icons.Default.VerticalAlignTop,
                            contentDescription = "回到顶部"
                        )
                    }

                    if (trailingIcon != null) {
                        Box {
                            IconButton(
                                onClick = {
                                    if (dropdownMenu != null) showMenu = true
                                }
                            ) {
                                trailingIcon()
                            }

                            if (dropdownMenu != null) {
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    dropdownMenu { showMenu = false }
                                }
                            }
                        }
                    }
                }
            },
            lineLimits = TextFieldLineLimits.SingleLine,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            contentPadding = PaddingValues(
                top = 4.dp,
                bottom = 4.dp,
                start = 12.dp,
                end = 12.dp
            )
        )
    }
}