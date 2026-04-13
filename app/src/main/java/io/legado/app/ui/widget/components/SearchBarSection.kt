package io.legado.app.ui.widget.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.coroutines.CoroutineScope
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBarSection(
    modifier: Modifier = Modifier,
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = "搜索...",
    leadingIcon: @Composable (() -> Unit)? = {
        AppIcon(
            modifier = Modifier.padding(horizontal = 12.dp),
            imageVector = AppIcons.Search,
            contentDescription = null
        )
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

    val isMiuix = ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)
    val resolvedBackgroundColor = if (backgroundColor != Color.Unspecified) {
        backgroundColor
    } else {
        if (isMiuix) MiuixTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLow
    }

    val modifier = modifier
        .fillMaxWidth()
        .then(
            if (isMiuix) modifier.padding(vertical = 4.dp)
            else modifier
        )

    val searchTextField = @Composable {
        AppDenseTextField(
            state = textFieldState,
            modifier = modifier,
            placeholder = { AppText(placeholder) },
            leadingIcon = leadingIcon,
            lineLimits = TextFieldLineLimits.SingleLine,
            backgroundColor = if (!isMiuix) {
                Color.Transparent
            } else {
                resolvedBackgroundColor
            }
        )
    }

    if (isMiuix) {
        searchTextField()
    } else {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            shape = RoundedCornerShape(32.dp),
            color = resolvedBackgroundColor
        ) {
            searchTextField()
        }
    }
}
