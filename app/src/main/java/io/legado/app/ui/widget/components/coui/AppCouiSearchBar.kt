package io.legado.app.ui.widget.components.coui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.offset
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.itos.coui.effect.CouiFloatingRenderer
import com.itos.coui.search.CouiSearchBarDefaults
import com.itos.coui.search.SearchBar as CouiSearchBar
import com.itos.coui.search.rememberCouiSearchBarState
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import timber.log.Timber

// The pinned SearchBar creates its renderer internally and cannot accept a fallback renderer.
internal val isCouiSearchMaterialAvailable: Boolean by lazy {
    if (!ThemeResolver.isCouiSupported()) return@lazy false

    try {
        CouiFloatingRenderer.pair()
        true
    } catch (error: IllegalArgumentException) {
        Timber.w(error, "COUI search material is unavailable")
        false
    } catch (error: UnsupportedOperationException) {
        Timber.w(error, "COUI search material is unavailable")
        false
    }
}

@Composable
internal fun AppCouiSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    placeholder: String,
    autoFocus: Boolean,
    backgroundColor: Color,
    leadingIcon: (@Composable () -> Unit)?,
    trailingIcon: (@Composable () -> Unit)?,
    clearIcon: (@Composable () -> Unit)?,
    onClear: (() -> Unit)?,
    clearDescription: String,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(autoFocus) }
    var text by rememberSaveable { mutableStateOf(query) }
    var previousAutoFocus by rememberSaveable { mutableStateOf(autoFocus) }
    LaunchedEffect(autoFocus) {
        if (autoFocus != previousAutoFocus) {
            expanded = autoFocus
            previousAutoFocus = autoFocus
        }
    }
    LaunchedEffect(query) { text = query }
    val state = rememberCouiSearchBarState(expanded)
    val dark = LegadoTheme.isDark
    val nativeColors = CouiSearchBarDefaults.colors(dark)
    val toolbarBackdrop = LocalAppCouiTopBarHost.current?.backdrop
        ?.takeIf { backgroundColor == Color.Unspecified }
    val fallbackBackground = if (backgroundColor == Color.Unspecified) {
        nativeColors.fallbackBackground
    } else backgroundColor
    // In sheets and list content, a separate solid source keeps the glass from sampling its own subtree.
    val backdrop = toolbarBackdrop ?: rememberCouiBackdrop { drawRect(fallbackBackground) }
    Box(modifier.fillMaxWidth()) {
        if (toolbarBackdrop == null) {
            Spacer(Modifier.matchParentSize().couiBackdrop(backdrop))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            leadingIcon?.invoke()
            CouiSearchBar(
                modifier = Modifier.weight(1f).layout { measurable, constraints ->
                    // Legado's caller already supplies the page gutter.
                    val margin = CouiSearchBarDefaults.horizontalMargin.roundToPx()
                    val field = measurable.measure(constraints.offset(horizontal = 2 * margin))
                    layout(field.width - 2 * margin, field.height) { field.placeRelative(-margin, 0) }
                },
                state = state,
                query = text,
                onQueryChange = { value ->
                    val needsClear = value.isEmpty() && text.isNotEmpty() && onClear != null
                    text = value
                    if (needsClear) onClear() else onQueryChange(value)
                },
                onExpandedChange = { expanded = it },
                placeholder = placeholder,
                cancelText = stringResource(R.string.cancel),
                clearDescription = clearDescription,
                dark = dark,
                backdrop = backdrop,
                onSearch = {
                    onSearch(text)
                    expanded = false
                },
                colors = nativeColors.copy(fallbackBackground = fallbackBackground),
            )
            // COUI provides clear while expanded. Keep the existing clear action accessible when idle.
            if (!expanded) clearIcon?.invoke()
            trailingIcon?.invoke()
        }
    }
}
