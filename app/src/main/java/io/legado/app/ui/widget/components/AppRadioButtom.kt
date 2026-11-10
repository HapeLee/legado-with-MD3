package io.legado.app.ui.widget.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.itos.coui.radiobutton.RadioButton as CouiRadioButton
import io.legado.app.ui.theme.ComposeEngine
import io.legado.app.ui.theme.LegadoTheme.composeEngine
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
        ComposeEngine.Coui -> {
            val interaction = if (onClick == null) Modifier else Modifier
                .minimumInteractiveComponentSize()
                .selectable(
                    selected = selected,
                    enabled = enabled,
                    role = Role.RadioButton,
                    onClick = onClick,
                    interactionSource = null,
                    indication = null,
                )
            CouiRadioButton(
                modifier = modifier.then(interaction).size(24.dp),
                selected = selected,
                enabled = enabled,
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
