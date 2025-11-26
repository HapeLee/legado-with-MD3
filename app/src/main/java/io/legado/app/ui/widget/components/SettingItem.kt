package io.legado.app.ui.widget.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.legado.app.base.AppTypography

@Preview(showBackground = true)
@Composable
fun PreviewSettingItemList() {
    MaterialTheme {
        Column(modifier = Modifier.padding(16.dp)) {

            SettingItem(
                title = "Only Title",
                description = null,
                option = null,
                onClick = {}
            )

            SettingItem(
                title = "Title + Description",
                description = "This is a description",
                option = null,
                onClick = {}
            )

            SettingItem(
                title = "Title + Option",
                description = null,
                option = "Dynamic option text",
                onClick = {}
            )

            SettingItem(
                title = "Title + Desc + Option",
                description = "Description content here",
                option = "Animated changing string",
                onClick = {}
            )

            SettingItem(
                imageVector = Icons.Default.Info,
                title = "With Icon",
                description = "Description",
                option = "Option",
                onClick = {}
            )

            SettingItem(
                title = "With Trailing",
                description = "Trailing icon on the right",
                option = null,
                trailingContent = {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null
                    )
                },
                onClick = {}
            )

        }
    }
}

@Composable
fun SettingItem(
    modifier: Modifier = Modifier,
    painter: Painter? = null,
    imageVector: ImageVector? = null,
    title: String,
    option: String? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    description: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .then(modifier)
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 8.dp)
            .padding(vertical = 12.dp)
            .heightIn(min = 56.dp),
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

        imageVector?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(24.dp),
                tint = colorScheme.onSurfaceVariant
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = if (description == null)
                Arrangement.Center
            else
                Arrangement.spacedBy(3.dp)
        ) {

            Text(
                text = title,
                color = colorScheme.onSurface,
                style = AppTypography.titleMedium,
                fontWeight = FontWeight.Normal
            )

            description?.let {
                Text(
                    text = it,
                    color = colorScheme.onSurfaceVariant,
                    style = AppTypography.labelMedium
                )
            }

            option?.let {
                AnimatedTextLine(
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
                modifier = Modifier
                    .width(55.dp),
                contentAlignment = Alignment.Center
            ) {
                composable()
            }
        }
    }
}