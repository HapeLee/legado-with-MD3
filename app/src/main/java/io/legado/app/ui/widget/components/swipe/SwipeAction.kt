package io.legado.app.ui.widget.components.swipe

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class SwipeAction(
    val icon: ImageVector,
    val background: Color,
    val onSwipe: () -> Unit,
    val hapticFeedback: Boolean = true
)
