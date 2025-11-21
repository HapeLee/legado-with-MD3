package io.legado.app.ui.widget.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.base.AppTypography

@Composable
fun SettingItem(
    modifier: Modifier = Modifier,
    painter: Painter? = null,
    title: String,
    option: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .then(modifier)
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 8.dp)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        painter?.let {
            Icon(
                modifier = Modifier.padding(end = 8.dp).size(24.dp),
                painter = it,
                tint = colorScheme.onSurfaceVariant,
                contentDescription = "Icon"
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                color = colorScheme.onSurface,
                style = AppTypography.titleMedium,
                fontWeight = FontWeight.Normal
            )
            Text(
                text = description,
                color = colorScheme.onSurfaceVariant,
                style = AppTypography.labelMedium
            )
            option?.let {
                AnimatedText(
                    text = it,
                    style = AppTypography.labelMedium,
                    color = colorScheme.primary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        trailingContent?.let { composable ->
            Box(
                modifier = Modifier.fillMaxHeight()
                    .width(55.dp),
                contentAlignment = Alignment.Center
            ) {
                composable()
            }
        }
    }
}
