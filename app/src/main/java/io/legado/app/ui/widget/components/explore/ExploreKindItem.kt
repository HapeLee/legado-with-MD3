package io.legado.app.ui.widget.components.explore

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.text.AppText

@Composable
fun ExploreKindItem(
    kind: ExploreKind,
    isClickable: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isMiuix: Boolean,
    backgroundColor: androidx.compose.ui.graphics.Color = LegadoTheme.colorScheme.surfaceContainer,
    displayText: String = kind.title,
    isSelected: Boolean = false,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides Dp.Unspecified
    ) {

        val cornerRadius = 12.dp
        val containerColor = if (isSelected) {
            LegadoTheme.colorScheme.primaryContainer
        } else {
            backgroundColor
        }
        val contentColor = if (isSelected) {
            LegadoTheme.colorScheme.onPrimaryContainer
        } else if (isClickable) {
            LegadoTheme.colorScheme.onSurface
        } else {
            LegadoTheme.colorScheme.primary
        }

        if (isClickable) {
            GlassCard(
                onClick = onClick,
                cornerRadius = cornerRadius,
                containerColor = containerColor,
                contentColor = contentColor,
                modifier = modifier,
            ) {
                KindText(
                    text = displayText,
                    isClickable = true,
                    contentColor = contentColor,
                    trailingIcon = trailingIcon
                )
            }
        } else {
            GlassCard(
                cornerRadius = cornerRadius,
                containerColor = containerColor,
                contentColor = contentColor,
                modifier = modifier,
            ) {
                KindText(
                    text = displayText,
                    isClickable = false,
                    contentColor = contentColor,
                    trailingIcon = trailingIcon
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun KindText(
    text: String,
    isClickable: Boolean,
    contentColor: androidx.compose.ui.graphics.Color = LegadoTheme.colorScheme.onSurface,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        AppText(
            text = text,
            color = contentColor,
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = if (trailingIcon == null) 0.dp else 18.dp),
            style = LegadoTheme.typography.labelMediumEmphasized,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1
        )
        if (trailingIcon != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp)
            ) {
                trailingIcon()
            }
        }
    }
}
