package io.legado.app.ui.widget.components.divider

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.ui.config.themeConfig.ThemeConfig

@Composable
fun SettingItemDivider(
    modifier: Modifier = Modifier
) {
    if (!ThemeConfig.enableItemDivider) return

    val thickness = ThemeConfig.itemDividerWidth.dp
    val lengthPercent = ThemeConfig.itemDividerLength / 100f
    val dividerColor = if (ThemeConfig.itemDividerColor != 0) {
        Color(ThemeConfig.itemDividerColor)
    } else {
        Color.Gray.copy(alpha = 0.3f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(lengthPercent)
                .height(thickness)
                .clip(CircleShape)
                .background(dividerColor)
        )
    }
}