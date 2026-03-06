package io.legado.app.ui.widget.components.heatmap

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class HeatmapMode {
    COUNT, TIME
}

data class HeatmapConfig(
    val cellSize: Dp = 16.dp,
    val cellSpacing: Dp = 4.dp,
    val cornerRadius: Dp = 4.dp,
    val gradientWidth: Dp = 16.dp,
    val legendSize: Dp = 12.dp
)