package io.legado.app.ui.widget.components.button

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import io.legado.app.ui.theme.ThemeResolver

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
    val isMiuix = ThemeResolver.isMiuixEngine(composeEngine)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isMiuix) {
            Arrangement.spacedBy(12.dp)
        } else {
            Arrangement.spacedBy(12.dp, Alignment.End)
        }
    ) {
        SecondaryButton(
            onClick = onDismiss,
            modifier = if (isMiuix) {
                Modifier.weight(1f)
            } else {
                Modifier.widthIn(min = 88.dp)
            },
            enabled = dismissEnabled,
            text = dismissText
        )
        PrimaryButton(
            onClick = onConfirm,
            modifier = if (isMiuix) {
                Modifier.weight(1f)
            } else {
                Modifier.widthIn(min = 88.dp)
            },
            enabled = confirmEnabled,
            text = confirmText
        )
    }
}
