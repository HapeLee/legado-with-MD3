package io.legado.app.ui.widget.components.checkBox

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.unit.dp
import com.itos.coui.checkbox.Checkbox as CouiCheckbox
import io.legado.app.ui.theme.ComposeEngine
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import top.yukonga.miuix.kmp.basic.Checkbox as MiuixCheckbox
import ui.feice.mochi.basic.MochiCheckbox

@Composable
fun AppCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    includeStateSemantics: Boolean = true
) {
    val state = if (checked) ToggleableState.On else ToggleableState.Off
    val semanticModifier = if (includeStateSemantics) {
        modifier.semantics {
            toggleableState = state
        }
    } else {
        modifier
    }

    when (composeEngine) {
        ComposeEngine.Miuix -> {
            MiuixCheckbox(
                state = state,
                onClick = onCheckedChange?.let { { it(!checked) } },
                modifier = semanticModifier,
                enabled = enabled
            )
        }
        ComposeEngine.Mochi -> {
            MochiCheckbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = if (onCheckedChange != null) {
                    semanticModifier.minimumInteractiveComponentSize()
                } else {
                    semanticModifier
                },
                enabled = enabled,
            )
        }
        ComposeEngine.Coui -> {
            val interaction = if (onCheckedChange == null) Modifier else Modifier
                .minimumInteractiveComponentSize()
                .toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Checkbox,
                    onValueChange = onCheckedChange,
                    interactionSource = null,
                    indication = null,
                )
            CouiCheckbox(
                modifier = semanticModifier.then(interaction).size(24.dp),
                checked = checked,
                enabled = enabled,
            )
        }
        ComposeEngine.Material -> {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = semanticModifier,
                enabled = enabled
            )
        }
    }
}

@Composable
fun AppTriStateCheckbox(
    state: ToggleableState,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    when (composeEngine) {
        ComposeEngine.Miuix -> {
            MiuixCheckbox(
                state = state,
                onClick = onClick,
                modifier = modifier,
                enabled = enabled
            )
        }
        ComposeEngine.Material, ComposeEngine.Mochi, ComposeEngine.Coui -> {
            TriStateCheckbox(
                state = state,
                onClick = onClick,
                modifier = modifier,
                enabled = enabled
            )
        }
    }
}
