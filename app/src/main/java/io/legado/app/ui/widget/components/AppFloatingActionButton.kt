package io.legado.app.ui.widget.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.widget.components.text.AppText
import top.yukonga.miuix.kmp.basic.FloatingActionButton as MiuixFloatingActionButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tooltipText: String? = null,
    containerColor: Color = LegadoTheme.colorScheme.primaryContainer,
    contentColor: Color = LegadoTheme.colorScheme.onPrimaryContainer,
    content: @Composable () -> Unit
) {
    val isMiuix = ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)

    if (isMiuix) {
        MiuixFloatingActionButton(
            onClick = onClick,
            modifier = modifier,
            content = content
        )
    } else {
        if (tooltipText != null) {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                    TooltipAnchorPosition.Above
                ),
                tooltip = { PlainTooltip { AppText(tooltipText) } },
                state = rememberTooltipState(),
            ) {
                FloatingActionButton(
                    onClick = onClick,
                    modifier = modifier,
                    containerColor = containerColor,
                    contentColor = contentColor,
                    content = content
                )
            }
        } else {
            FloatingActionButton(
                onClick = onClick,
                modifier = modifier,
                containerColor = containerColor,
                contentColor = contentColor,
                content = content
            )
        }
    }
}
