package io.legado.app.lib.theme

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import androidx.core.content.ContextCompat
import io.legado.app.R

/**
 * Provides rounded-corner backgrounds and selector drawables for the AI UI screens.
 * Keeps the AI port self-contained without depending on legacy View theme helpers.
 */
object UiCorner {

    private const val PANEL_RADIUS_DP = 16f
    private const val ACTION_RADIUS_DP = 12f

    fun panelRadius(context: Context): Float {
        return PANEL_RADIUS_DP * context.resources.displayMetrics.density
    }

    fun actionRadius(context: Context): Float {
        return ACTION_RADIUS_DP * context.resources.displayMetrics.density
    }

    fun panelRounded(context: Context, bgColor: Int, radius: Float): Drawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(bgColor)
            cornerRadius = radius
        }
    }

    fun actionSelector(bgColor: Int, pressedColor: Int, radius: Float): Drawable {
        val normal = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(bgColor)
            cornerRadius = radius
        }
        val pressed = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(pressedColor)
            cornerRadius = radius
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
    }

    /**
     * Convert a dp value to pixels, applying the device's display density.
     */
    fun scaledDp(dp: Float): Float {
        return dp * android.content.res.Resources.getSystem().displayMetrics.density
    }
}
