package io.legado.app.ui.widget.components.button

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.ComposeEngine
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.card.NormalCard
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ActionChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (LegadoTheme.composeEngine) {
        ComposeEngine.Miuix -> {
            NormalCard(
                modifier = modifier
                    .minimumInteractiveComponentSize()
                    .semantics { role = Role.Button },
                cornerRadius = 12.dp,
                onClick = onClick,
                containerColor = MiuixTheme.colorScheme.surfaceContainer,
            ) {
                MiuixText(
                    text = label,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = LegadoTheme.typography.labelMediumEmphasized,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        ComposeEngine.Material, ComposeEngine.Mochi -> {
            AssistChip(
                onClick = onClick,
                modifier = modifier,
                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}
