package io.legado.app.ui.widget.components.button

import io.legado.app.ui.theme.ComposeEngine
import io.legado.app.ui.theme.LegadoTheme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme.composeEngine

@Composable
fun ConfirmDismissButtonsRow(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    dismissText: String,
    confirmText: String,
    dismissEnabled: Boolean = true,
    confirmEnabled: Boolean = true,
) {

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = when (LegadoTheme.composeEngine) {
            ComposeEngine.Miuix -> {
                Arrangement.spacedBy(12.dp)
            }
            ComposeEngine.Material, ComposeEngine.Mochi, ComposeEngine.Coui -> {
                Arrangement.spacedBy(12.dp, Alignment.End)
            }
        }
    ) {
        SecondaryButton(
            onClick = onDismiss,
            modifier = when (LegadoTheme.composeEngine) {
                ComposeEngine.Miuix -> {
                    Modifier.weight(1f)
                }
                ComposeEngine.Material, ComposeEngine.Mochi, ComposeEngine.Coui -> {
                    Modifier.widthIn(min = 88.dp)
                }
            },
            enabled = dismissEnabled,
            text = dismissText
        )
        PrimaryButton(
            onClick = onConfirm,
            modifier = when (LegadoTheme.composeEngine) {
                ComposeEngine.Miuix -> {
                    Modifier.weight(1f)
                }
                ComposeEngine.Material, ComposeEngine.Mochi, ComposeEngine.Coui -> {
                    Modifier.widthIn(min = 88.dp)
                }
            },
            enabled = confirmEnabled,
            text = confirmText
        )
    }
}
