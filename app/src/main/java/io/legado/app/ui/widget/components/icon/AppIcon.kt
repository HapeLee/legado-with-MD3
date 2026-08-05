package io.legado.app.ui.widget.components.icon

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme

/**
 * 1. 基础 AppIcon 实现
 */
@Composable
fun AppIcon(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    val defaultTintColor = LegadoTheme.colorScheme.onSurface
    val finalTint = tint.takeOrElse { defaultTintColor }

    val colorFilter = remember(finalTint) {
        if (finalTint == Color.Unspecified) null else ColorFilter.tint(finalTint)
    }

    val semantics = if (contentDescription != null) {
        Modifier.semantics {
            this.contentDescription = contentDescription
            this.role = Role.Image
        }
    } else {
        Modifier
    }

    Box(
        modifier
            .defaultIconSize(painter)
            .paint(painter, colorFilter = colorFilter, contentScale = ContentScale.Fit)
            .then(semantics)
    )
}

@Composable
fun AppIcon(
    imageVector: ImageVector?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    if (imageVector == null) return

    AppIcon(
        painter = rememberVectorPainter(imageVector),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}

/**
 * 3. 辅助扩展函数：处理图标的默认尺寸
 * M3 和 MIUIX 内部都有 defaultSizeFor，如果 painter 没有固有尺寸，默认设为 24.dp
 */
private fun Modifier.defaultIconSize(painter: Painter): Modifier {
    return this.then(
        if (painter.intrinsicSize == Size.Unspecified || painter.intrinsicSize.isUnspecified) {
            Modifier.size(24.dp)
        } else {
            Modifier
        }
    )
}