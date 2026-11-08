package io.legado.app.ui.widget.components

import androidx.compose.material3.Checkbox
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import io.legado.app.ui.theme.ComposeEngine
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.RadioButton as MiuixRadioButton

@Composable
fun AppRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    when (composeEngine) {
        ComposeEngine.Miuix -> {
            MiuixRadioButton(
                selected = selected,
                onClick = onClick,
                modifier = modifier,
                enabled = enabled
            )
        }
        ComposeEngine.Material, ComposeEngine.Mochi -> {
            RadioButton(
                selected = selected,
                onClick = onClick,
                modifier = modifier,
                enabled = enabled
            )
        }
    }
}
