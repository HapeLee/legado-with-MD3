package io.legado.app.ui.widget

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveHorizontalPadding
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.text.AppText

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CollapsibleHeader(
    modifier: Modifier = Modifier,
    showIcon: Boolean = true,
    isCollapsed: Boolean,
    onToggle: () -> Unit,
    title: String,
    subtitle: String? = null,
    titleContent: (@Composable ColumnScope.() -> Unit)? = null
) {
    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .adaptiveHorizontalPadding(vertical = 4.dp),
        cornerRadius = 12.dp,
        containerColor = LegadoTheme.colorScheme.surfaceContainer,
        onClick = onToggle
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                if (titleContent != null) {
                    titleContent()
                } else {
                    AppText(
                        text = title,
                        style = LegadoTheme.typography.bodySmallEmphasized.copy(
                            fontWeight = FontWeight.Bold,
                            color = LegadoTheme.colorScheme.primary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    subtitle?.let {
                        AppText(
                            text = it,
                            style = LegadoTheme.typography.labelSmallEmphasized.copy(
                                color = LegadoTheme.colorScheme.onSurfaceVariant
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            val rotation by animateFloatAsState(
                targetValue = if (isCollapsed) 0f else 180f,
                label = "arrowRotation"
            )

            if (showIcon) {
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = if (isCollapsed) "展开" else "折叠",
                    modifier = Modifier.rotate(rotation),
                    tint = LegadoTheme.colorScheme.primary
                )
            }
        }
    }
}