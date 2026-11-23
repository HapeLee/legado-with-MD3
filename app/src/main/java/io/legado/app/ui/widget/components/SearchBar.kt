package io.legado.app.ui.widget.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.ComposeEngine
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.series.SmallPlainButton
import io.legado.app.ui.widget.components.coui.AppCouiSearchBar
import io.legado.app.ui.widget.components.coui.isCouiSearchMaterialAvailable
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.text.AppText
import io.oxygenx.theme.OxygenTheme
import io.oxygenx.ui.basic.SearchBar as OxygenSearchBar
import kotlinx.coroutines.CoroutineScope

private val DefaultSearchLeadingIcon: @Composable () -> Unit = {
    AppIcon(
        modifier = Modifier.padding(horizontal = 12.dp),
        imageVector = AppIcons.Search,
        contentDescription = null,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(
    modifier: Modifier = Modifier,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit = {},
    placeholder: String? = null,
    leadingIcon: @Composable (() -> Unit)? = DefaultSearchLeadingIcon,
    backgroundColor: Color = Color.Unspecified,
    scrollState: LazyListState? = null,
    scope: CoroutineScope = rememberCoroutineScope(),
    trailingIcon: @Composable (() -> Unit)? = null,
    autoFocus: Boolean = true,
    dropdownMenu: (@Composable (onDismiss: () -> Unit) -> Unit)? = null,
    onClear: (() -> Unit)? = null,
    clearDescription: String = stringResource(R.string.clear),
) {
    val resolvedPlaceholder = placeholder ?: stringResource(R.string.search_placeholder)
    val clearIcon: (@Composable () -> Unit)? = if (onClear != null && query.isNotEmpty()) {
        {
            SmallPlainButton(
                onClick = onClear,
                icon = AppIcons.Close,
                contentDescription = clearDescription,
            )
        }
    } else null
    val engine = LegadoTheme.composeEngine
    if (engine == ComposeEngine.Coui && isCouiSearchMaterialAvailable) {
        AppCouiSearchBar(
            modifier = modifier,
            query = query,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
            placeholder = resolvedPlaceholder,
            autoFocus = autoFocus,
            backgroundColor = backgroundColor,
            leadingIcon = leadingIcon?.takeUnless { it === DefaultSearchLeadingIcon },
            trailingIcon = trailingIcon,
            clearIcon = clearIcon,
            onClear = onClear,
            clearDescription = clearDescription,
        )
        return
    }

    val textFieldState = rememberTextFieldState(initialText = query)
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val latestQuery by rememberUpdatedState(query)
    val latestOnQueryChange by rememberUpdatedState(onQueryChange)
    var hasFocused by rememberSaveable { mutableStateOf(false) }

    val submitSearch: (String) -> Unit = { value ->
        onSearch(value)
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    LaunchedEffect(autoFocus) {
        if (autoFocus && !hasFocused) {
            focusRequester.requestFocus()
            // 某些情况下需要手动调用 show() 确保键盘弹出
            keyboardController?.show()
            hasFocused = true
        } else if (!autoFocus && hasFocused) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    LaunchedEffect(query) {
        if (query != textFieldState.text.toString()) {
            textFieldState.edit {
                replace(0, length, query)
            }
        }
    }

    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }
            .collect { newText ->
                if (newText != latestQuery) {
                    latestOnQueryChange(newText)
                }
            }
    }

    val resolvedTrailingIcon = trailingIcon ?: clearIcon
    when (engine) {
        ComposeEngine.Oxygen -> {
            val colors = OxygenTheme.colorScheme
            OxygenTheme(
                colorScheme = if (backgroundColor == Color.Unspecified) null
                else colors.copy(surfaceContainerLow = backgroundColor),
            ) {
                OxygenSearchBar(
                    modifier = modifier.padding(bottom = 4.dp).focusRequester(focusRequester),
                    value = textFieldState.text.toString(),
                    onValueChange = { value -> textFieldState.edit { replace(0, length, value) } },
                    placeholder = resolvedPlaceholder,
                    onSearch = { submitSearch(textFieldState.text.toString()) },
                    leadingIcon = if (leadingIcon === DefaultSearchLeadingIcon) null else leadingIcon ?: {},
                    trailingIcon = resolvedTrailingIcon,
                )
            }
        }
        ComposeEngine.Miuix -> {
            AppDenseTextField(
                state = textFieldState,
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .focusRequester(focusRequester),
                placeholder = { AppText(resolvedPlaceholder) },
                leadingIcon = leadingIcon,
                trailingIcon = resolvedTrailingIcon,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                onKeyboardAction = {
                    submitSearch(textFieldState.text.toString())
                },
                lineLimits = TextFieldLineLimits.SingleLine,
                backgroundColor = if (backgroundColor == Color.Unspecified) {
                    MaterialTheme.colorScheme.surfaceContainerLow
                } else backgroundColor,
                miuixUseSearchBarInputField = true,
                miuixSearchBarLabel = resolvedPlaceholder,
                miuixOnSearch = submitSearch,
            )
        }
        ComposeEngine.Material, ComposeEngine.Mochi, ComposeEngine.Coui -> {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                shape = RoundedCornerShape(32.dp),
                color = if (backgroundColor == Color.Unspecified) MaterialTheme.colorScheme.surfaceContainerLow
                else backgroundColor,
            ) {
                AppDenseTextField(
                    state = textFieldState,
                    modifier = modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = { AppText(resolvedPlaceholder) },
                    placeholderText = resolvedPlaceholder,
                    leadingIcon = leadingIcon,
                    trailingIcon = resolvedTrailingIcon,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    onKeyboardAction = {
                        submitSearch(textFieldState.text.toString())
                    },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    backgroundColor = Color.Transparent
                )
            }
        }
    }
}
