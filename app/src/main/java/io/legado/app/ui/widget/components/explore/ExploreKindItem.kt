package io.legado.app.ui.widget.components.explore

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
    isMiuix: Boolean
) {
    val color = if (isMiuix)
        MiuixTheme.colorScheme.surfaceContainer
    else
        MaterialTheme.colorScheme.secondaryContainer

    val contentColor = if (isMiuix)
        MiuixTheme.colorScheme.onSurface
    else
        MaterialTheme.colorScheme.secondary

    val unClickBackColor = if (isMiuix)
        MiuixTheme.colorScheme.surfaceContainer
    else
        MaterialTheme.colorScheme.surface

    val unClickColor = if (isMiuix)
        MiuixTheme.colorScheme.disabledOnSurface
    else
        MaterialTheme.colorScheme.primary

    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides Dp.Unspecified
    ) {

        val shape = MaterialTheme.shapes.medium

        if (isClickable) {
            GlassCard(
                onClick = onClick,
                shape = shape,
                containerColor = LegadoTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
                contentColor = LegadoTheme.colorScheme.onSurface,
                modifier = modifier
            ) {
                KindText(kind)
            }
        } else {
            GlassCard(
                shape = shape,
                containerColor = LegadoTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
                contentColor = LegadoTheme.colorScheme.primary,
                modifier = modifier,
                border = CardDefaults.outlinedCardBorder()
            ) {
                KindText(kind)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun KindText(
    kind: ExploreKind
) {
    AppText(
        text = kind.title,
        color = if (kind.url.isNullOrBlank())
            LegadoTheme.colorScheme.primary
        else
            LegadoTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        style = LegadoTheme.typography.labelMediumEmphasized,
        textAlign = TextAlign.Center,
        overflow = TextOverflow.Ellipsis,
        maxLines = 1
    )
}
