package io.legado.app.ui.widget.components.explore

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
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
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ExploreKindItem(
    kind: ExploreKind,
    isClickable: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isMiuix: Boolean,
    backgroundColor: androidx.compose.ui.graphics.Color = LegadoTheme.colorScheme.surfaceContainer,
    displayText: String = kind.title,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides Dp.Unspecified
    ) {

        val shape = MaterialTheme.shapes.medium

        if (isClickable) {
            GlassCard(
                onClick = onClick,
                shape = shape,
                containerColor = backgroundColor,
                contentColor = LegadoTheme.colorScheme.onSurface,
                modifier = modifier
            ) {
                KindText(
                    text = displayText,
                    isClickable = true,
                    trailingIcon = trailingIcon
                )
            }
        } else {
            GlassCard(
                shape = shape,
                containerColor = backgroundColor,
                contentColor = LegadoTheme.colorScheme.primary,
                modifier = modifier,
                border = CardDefaults.outlinedCardBorder()
            ) {
                KindText(
                    text = displayText,
                    isClickable = false,
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
    trailingIcon: (@Composable () -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        AppText(
            text = text,
            color = if (isClickable) LegadoTheme.colorScheme.onSurface else LegadoTheme.colorScheme.primary,
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
