package io.legado.app.ui.widget.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBarSection(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = "搜索书名",
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    scrollState: LazyListState? = null,
    scope: CoroutineScope = rememberCoroutineScope(),
    trailingIcon: @Composable (() -> Unit)? = null,
    dropdownMenu: (@Composable (onDismiss: () -> Unit) -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }

    val showScrollToTop by remember(scrollState) {
        derivedStateOf {
            (scrollState?.firstVisibleItemIndex ?: 0) > 0
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(32.dp),
        color = backgroundColor
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            placeholder = { Text(placeholder) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .animateContentSize()
                ) {
                    AnimatedVisibility(
                        visible = query.isNotEmpty(),
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut()
                    ) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Clear, "清空输入")
                        }
                    }

                    AnimatedVisibility(
                        visible = showScrollToTop,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut()
                    ) {
                        IconButton(onClick = {
                            scope.launch { scrollState?.animateScrollToItem(0) }
                        }) {
                            Icon(Icons.Default.VerticalAlignTop, "回到顶部")
                        }
                    }

                    // 外部自定义按钮
                    if (trailingIcon != null) {
                        Box {
                            IconButton(onClick = { if (dropdownMenu != null) showMenu = true }) {
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
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            )
        )
    }
}