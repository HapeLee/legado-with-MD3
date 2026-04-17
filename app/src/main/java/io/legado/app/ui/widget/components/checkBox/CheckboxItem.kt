package io.legado.app.ui.widget.components.checkBox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.text.AppText

@Composable
fun CheckboxItem(
    title: String,
    color: Color = LegadoTheme.colorScheme.onSheetContent,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val alpha = if (enabled) 1f else 0.5f

    GlassCard(
        cornerRadius = 12.dp,
        containerColor = if (checked && enabled) LegadoTheme.colorScheme.secondaryContainer else color,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { onCheckedChange(!checked) }
                .padding(vertical = 12.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppCheckbox(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                modifier = Modifier.alpha(alpha)
            )
            AppText(
                text = title,
                style = LegadoTheme.typography.bodyMediumEmphasized,
                maxLines = 1,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .alpha(alpha)
            )
        }
    }

}