package io.legado.app.feature.manga.translation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.text.AppText

/**
 * Status block shared by the on-device OCR and inpainting model sections. The engine name and its
 * phase share one row, secondary copy stays muted, and every action uses the same button series so
 * neither panel grows its own one-off layout.
 */
@Composable
internal fun MangaModelSection(
    title: String,
    hint: String,
    status: String,
    statusColor: Color,
    progress: Float? = null,
    sizeText: String? = null,
    error: String? = null,
    actions: @Composable RowScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AppText(title, style = LegadoTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            AppText(status, style = LegadoTheme.typography.labelMedium, color = statusColor)
        }
        AppText(hint, style = LegadoTheme.typography.bodySmall, color = LegadoTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp))
        if (progress != null) Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.weight(1f))
            if (sizeText != null) AppText(sizeText, style = LegadoTheme.typography.labelSmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
        } else if (sizeText != null) AppText(sizeText, style = LegadoTheme.typography.labelSmall,
            color = LegadoTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        error?.let {
            AppText(it, style = LegadoTheme.typography.bodySmall, color = LegadoTheme.colorScheme.error,
                modifier = Modifier.padding(top = 6.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Ready and in-flight states read as the active choice; failures use the error color. */
@Composable
internal fun mangaModelStatusColor(ready: Boolean, failed: Boolean, running: Boolean): Color = when {
    failed -> LegadoTheme.colorScheme.error
    ready || running -> LegadoTheme.colorScheme.primary
    else -> LegadoTheme.colorScheme.onSurfaceVariant
}
