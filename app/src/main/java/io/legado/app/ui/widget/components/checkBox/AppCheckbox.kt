package io.legado.app.ui.widget.components.checkBox

import androidx.compose.material3.Checkbox
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import io.legado.app.ui.theme.ThemeResolver
import top.yukonga.miuix.kmp.basic.Checkbox as MiuixCheckbox

@Composable
fun AppCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    includeStateSemantics: Boolean = true
) {
    val state = if (checked) ToggleableState.On else ToggleableState.Off
    val checkboxStateDescription = stringResource(
        if (checked) R.string.a11y_selected else R.string.a11y_not_selected
    )
    val semanticModifier = if (includeStateSemantics) {
        modifier.semantics {
            stateDescription = checkboxStateDescription
        }
    } else {
        modifier
    }

    if (ThemeResolver.isMiuixEngine(composeEngine)) {
        MiuixCheckbox(
            state = state,
            onClick = onCheckedChange?.let { { it(!checked) } },
            modifier = semanticModifier,
            enabled = enabled
        )
    } else {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = semanticModifier,
            enabled = enabled
        )
    }
}

@Composable
fun AppTriStateCheckbox(
    state: ToggleableState,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    if (ThemeResolver.isMiuixEngine(composeEngine)) {
        MiuixCheckbox(
            state = state,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled
        )
    } else {
        TriStateCheckbox(
            state = state,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled
        )
    }
}
