package io.legado.app.ui.widget.components.swipe

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class SwipeAction(
    val icon: ImageVector,
    val background: Color,
    val onSwipe: () -> Unit,
    val hapticFeedback: Boolean = true,
    val contentDescription: String? = null,
    /** 删除类操作由数据刷新移除列表项，不立即回弹；普通操作保持自动复位。 */
    val resetAfterSwipe: Boolean = true,
)
