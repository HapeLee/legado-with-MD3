package io.legado.app.ui.widget.components.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderMenuVisualStateTest {

    @Test
    fun `gradient without blur keeps tint but not contrast content`() {
        val state = visualState(
            effect = ReaderMenuEffect.None,
            tintStyle = ReaderMenuTintStyle.Gradient,
        )

        assertTrue(state.isGradient)
        assertTrue(state.useTint)
        assertFalse(state.isProgressiveBlur)
        assertFalse(state.useContrastContent)
    }

    @Test
    fun `progressive haze enables contrast content`() {
        val state = visualState(
            effect = ReaderMenuEffect.Haze,
            tintStyle = ReaderMenuTintStyle.Gradient,
        )

        assertTrue(state.isProgressiveBlur)
        assertTrue(state.useContrastContent)
        assertTrue(state.useTint)
    }

    @Test
    fun `fill without blur can opt out of tint`() {
        val state = visualState(
            effect = ReaderMenuEffect.None,
            tintStyle = ReaderMenuTintStyle.Fill,
            tintFill = false,
        )

        assertFalse(state.isGradient)
        assertFalse(state.useTint)
    }

    @Test
    fun `fill with haze uses tint`() {
        val state = visualState(
            effect = ReaderMenuEffect.Haze,
            tintStyle = ReaderMenuTintStyle.Fill,
            tintFill = false,
        )

        assertFalse(state.isProgressiveBlur)
        assertTrue(state.useTint)
    }

    @Test
    fun `liquid glass does not enable progressive contrast content`() {
        val state = visualState(
            effect = ReaderMenuEffect.LiquidGlass,
            tintStyle = ReaderMenuTintStyle.Gradient,
        )

        assertTrue(state.isGradient)
        assertFalse(state.isProgressiveBlur)
        assertFalse(state.useContrastContent)
        assertTrue(state.useTint)
    }

    @Test
    fun `secondary surface disables style and tint`() {
        val state = visualState(
            effect = ReaderMenuEffect.Haze,
            tintStyle = ReaderMenuTintStyle.Gradient,
            styleEnabled = false,
            tintAllowed = false,
        )

        assertFalse(state.isGradient)
        assertFalse(state.isProgressiveBlur)
        assertFalse(state.useContrastContent)
        assertFalse(state.useTint)
    }

    private fun visualState(
        effect: ReaderMenuEffect,
        tintStyle: ReaderMenuTintStyle,
        styleEnabled: Boolean = true,
        tintAllowed: Boolean = true,
        tintFill: Boolean = true,
    ) = ReaderMenuVisualState(
        effect = effect,
        tintStyle = tintStyle,
        styleEnabled = styleEnabled,
        tintAllowed = tintAllowed,
        tintFill = tintFill,
    )
}
