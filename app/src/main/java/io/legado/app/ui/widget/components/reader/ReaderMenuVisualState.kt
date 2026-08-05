package io.legado.app.ui.widget.components.reader

import androidx.compose.runtime.Immutable

enum class ReaderMenuEffect {
    None,
    Haze,
    LiquidGlass,
}

enum class ReaderMenuTintStyle {
    Fill,
    Gradient,
}

/**
 * Resolves reader-menu appearance from independent style and effect inputs.
 *
 * This deliberately contains no novel-, manga-, or settings-specific state so both readers can
 * share the same visual rules while keeping their actions and navigation separate.
 */
@Immutable
data class ReaderMenuVisualState(
    val effect: ReaderMenuEffect,
    val tintStyle: ReaderMenuTintStyle,
    val styleEnabled: Boolean,
    val tintAllowed: Boolean,
    val tintFill: Boolean,
) {
    val isGradient: Boolean
        get() = styleEnabled && tintStyle == ReaderMenuTintStyle.Gradient

    val isProgressiveBlur: Boolean
        get() = effect == ReaderMenuEffect.Haze && isGradient

    val useContrastContent: Boolean
        get() = isProgressiveBlur

    val useTint: Boolean
        get() = tintAllowed && (isGradient || effect != ReaderMenuEffect.None || tintFill)
}
