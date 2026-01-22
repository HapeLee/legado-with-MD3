package io.legado.app.ui.widget

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.ThemeState

@Composable
fun GlassBackIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    imageVector: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    contentDescription: String? = null
) {
    val themeOpacity by ThemeState.containerOpacity.collectAsState()

    val baseColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val glassColor = baseColor.copy(alpha = (themeOpacity / 100f).coerceAtLeast(0.6f))

    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier
            .padding(horizontal = 12.dp)
            .size(36.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = glassColor,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp)
        )
    }
}