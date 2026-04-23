package io.legado.app.ui.widget.components.button

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.widget.components.text.AppText
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon

@Composable
fun SmallTextButton(
    text: String,
    imageVector: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    if (ThemeResolver.isMiuixEngine(composeEngine)) {
        Card(
            onClick = onClick,
            modifier = modifier,
            showIndication = true,
            colors = CardDefaults.defaultColors(
                color = LegadoTheme.colorScheme.surfaceVariant,
                contentColor = LegadoTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiuixIcon(
                    imageVector = imageVector,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                AppText(
                    text = text,
                    style = LegadoTheme.typography.labelMedium
                )
            }
        }
    } else {
        TextButton(
            onClick = onClick,
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            shape = MaterialTheme.shapes.small
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            AppText(
                text = text,
                style = LegadoTheme.typography.labelMedium
            )
        }
    }
}

@Composable
fun SmallTonalTextButton(
    text: String,
    imageVector: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    if (ThemeResolver.isMiuixEngine(composeEngine)) {
        Card(
            onClick = onClick,
            modifier = modifier,
            showIndication = true,
            colors = CardDefaults.defaultColors(
                color = LegadoTheme.colorScheme.surfaceContainer,
                contentColor = LegadoTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiuixIcon(
                    imageVector = imageVector,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                AppText(
                    text = text,
                    style = LegadoTheme.typography.labelMedium
                )
            }
        }
    } else {
        FilledTonalButton(
            onClick = onClick,
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            AppText(
                text = text,
                style = LegadoTheme.typography.labelMedium
            )
        }
    }
}
